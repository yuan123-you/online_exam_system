package com.onlineexam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/** Provider HTTP/SSE protocol, shared by chat, practice and every generation batch. */
final class AiStreamClient {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiStreamClient.class);
  record Result(String content, String reasoning) {}
  static final class Failure extends IOException {
    Failure(String message) { super(message); }
  }
  @FunctionalInterface interface ChunkConsumer { void accept(String type, String text) throws IOException; }
  @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
  static final class Cancellation {
    private boolean cancelled;
    private Thread worker;
    synchronized void check() {
      if (cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    void cancel() {
      Thread current;
      synchronized (this) { cancelled = true; current = worker; notifyAll(); }
      // Socket I/O on a virtual thread is interruptible; disconnect() on the caller can block.
      if (current != null) current.interrupt();
    }
    synchronized void attach(Thread value) { check(); worker = value; }
    synchronized void detach() { worker = null; }
    synchronized void pause(long millis) throws InterruptedException {
      long end = System.nanoTime() + millis * 1_000_000;
      while (true) {
        check(); long remaining = end - System.nanoTime();
        if (remaining <= 0) return;
        wait(Math.max(1, remaining / 1_000_000));
      }
    }
  }
  private final ObjectMapper mapper;
  private final Sleeper sleeper;
  AiStreamClient(ObjectMapper mapper) { this(mapper, null); }
  AiStreamClient(ObjectMapper mapper, Sleeper sleeper) { this.mapper = mapper; this.sleeper = sleeper; }

  Result stream(String url, String key, Supplier<Map<String, Object>> request, boolean thinking,
                ChunkConsumer consumer, Cancellation cancellation) throws IOException, InterruptedException {
    return stream(url, key, request, thinking, consumer, cancellation, "");
  }

  Result stream(String url, String key, Supplier<Map<String, Object>> request, boolean thinking,
                ChunkConsumer consumer, Cancellation cancellation, String fallbackModel) throws IOException, InterruptedException {
    cancellation.check();
    var task = new java.util.concurrent.FutureTask<Result>(() -> {
      cancellation.attach(Thread.currentThread());
      try { return streamBlocking(url, key, request, thinking, consumer, cancellation, fallbackModel); }
      finally { cancellation.detach(); }
    });
    Thread.startVirtualThread(task);
    try { return task.get(); }
    catch (InterruptedException interrupted) { cancellation.cancel(); throw interrupted; }
    catch (java.util.concurrent.ExecutionException failed) {
      cancellation.check();
      Throwable cause = failed.getCause();
      if (cause instanceof IOException io) throw io;
      if (cause instanceof InterruptedException interrupted) throw interrupted;
      if (cause instanceof RuntimeException runtime) throw runtime;
      throw new IOException("AI 请求失败", cause);
    }
  }

  private Result streamBlocking(String url, String key, Supplier<Map<String, Object>> request,
      boolean thinking, ChunkConsumer consumer, Cancellation cancellation, String fallbackModel) throws IOException, InterruptedException {
    boolean useFallback = false;
    for (int attempt = 1; attempt <= 3; attempt++) {
      cancellation.check();
      HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
      boolean emitted = false, providerAccepted = false;
      Map<String, Object> requestBody = new java.util.LinkedHashMap<>(request.get());
      String primaryModel = String.valueOf(requestBody.get("model"));
      boolean canFallback = !useFallback && fallbackModel != null && !fallbackModel.isBlank()
          && !fallbackModel.equals(primaryModel) && attempt < 3;
      if (useFallback) {
        requestBody.put("model", fallbackModel);
        if ("glm-4-flash".equals(fallbackModel)) requestBody.remove("thinking");
      }
      String selectedModel = String.valueOf(requestBody.get("model"));
      long delay = 1000L * (1L << (attempt - 1));
      try {
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + key);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setConnectTimeout(10_000); connection.setReadTimeout(180_000); connection.setDoOutput(true);
        try (var out = connection.getOutputStream()) { mapper.writeValue(out, requestBody); }
        int status = connection.getResponseCode();
        if (status != 200) {
          String body = "";
          if (connection.getErrorStream() != null) {
            try (var in = connection.getErrorStream()) { body = new String(in.readNBytes(8192), StandardCharsets.UTF_8); }
          }
          String code = providerCode(body);
          log.warn("AI upstream rejected request: HTTP {}, providerCode={}, attempt={}/3", status, code, attempt);
          boolean quota = "1113".equals(code) || body.contains("余额不足") || body.contains("insufficient_quota");
          boolean modelUnavailable = status == 404 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
          boolean retryable = !quota && (modelUnavailable && (status != 404 || canFallback));
          delay = retryDelay(connection.getHeaderField("Retry-After"), delay);
          if (!retryable || attempt == 3 || delay > 30_000) throw new Failure(errorMessage(status, quota));
          if (canFallback) {
            useFallback = true;
            log.warn("AI model fallback: primary={}, fallback={}, status={}, providerCode={}", primaryModel, fallbackModel, status, code);
          }
        } else {
          providerAccepted = true;
          StringBuilder content = new StringBuilder(), reasoning = new StringBuilder(), data = new StringBuilder();
          boolean finished = false;
          try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            while (true) {
              String line = reader.readLine();
              cancellation.check();
              if (line == null && data.isEmpty()) break;
              if (line != null && line.startsWith("data:")) {
                if (!data.isEmpty()) data.append('\n');
                data.append(line.substring(5).stripLeading());
              } else if (line != null && !line.isEmpty()) continue;
              else if (!data.isEmpty()) {
                String payload = data.toString(); data.setLength(0);
                if ("[DONE]".equals(payload)) { finished = true; break; }
                JsonNode chunk;
                try { chunk = mapper.readTree(payload); }
                catch (IOException malformed) { throw new Failure("AI 响应格式错误，请重试"); }
                if (chunk.has("error")) throw new Failure("AI 上游返回错误，请稍后重试或联系管理员检查模型配置");
                JsonNode choice = chunk.path("choices").path(0);
                String reason = choice.path("finish_reason").asText("");
                if (!reason.isEmpty() && !"stop".equals(reason)) throw new Failure("AI 回答未完整生成，请减少题目数量或重试");
                if ("stop".equals(reason)) finished = true;
                JsonNode delta = choice.path("delta");
                String thought = delta.path("reasoning_content").asText("");
                String text = delta.path("content").asText("");
                if (thinking && !thought.isEmpty()) {
                  reasoning.append(thought); emitted = true; consumer.accept("reasoning", thought);
                }
                if (!text.isEmpty()) { content.append(text); emitted = true; consumer.accept("content", text); }
                if (finished) break;
              }
              if (line == null) break;
            }
          }
          if (!finished || content.toString().isBlank()) throw new Failure("AI 响应为空或意外中断，请重试");
          log.info("AI stream completed: model={}, attempt={}", selectedModel, attempt);
          return new Result(content.toString(), reasoning.toString());
        }
      } catch (Failure failure) {
        if (!providerAccepted || emitted || !canFallback) throw failure;
        useFallback = true;
        log.warn("AI model fallback before output: primary={}, fallback={}", primaryModel, fallbackModel);
      } catch (IOException network) {
        cancellation.check();
        if (emitted || attempt == 3) throw new Failure("AI 连接中断或响应超时，请重试");
        if (canFallback) {
          useFallback = true;
          log.warn("AI model fallback after connection failure: primary={}, fallback={}", primaryModel, fallbackModel);
        }
      } finally {
        connection.disconnect();
      }
      cancellation.check();
      if (sleeper != null) sleeper.sleep(delay); else cancellation.pause(delay);
    }
    throw new Failure("AI 请求失败，请稍后重试");
  }
  private String providerCode(String body) {
    try { return mapper.readTree(body).path("error").path("code").asText(""); }
    catch (Exception ignored) { return ""; }
  }
  private static long retryDelay(String value, long fallback) {
    if (value == null) return fallback;
    try { return Math.max(0, Math.multiplyExact(Long.parseLong(value.trim()), 1000)); }
    catch (Exception ignored) {
      try { return Math.max(0, Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis()); }
      catch (Exception invalid) { return fallback; }
    }
  }
  private static String errorMessage(int status, boolean quota) {
    if (quota) return "AI 账户额度不足，请联系管理员检查账户额度和模型配置";
    return switch (status) {
      case 401 -> "AI API 密钥无效或已过期，请联系管理员更新";
      case 403 -> "AI 模型访问权限不足，请联系管理员检查配置";
      case 429 -> "AI 服务请求受限，本次请求未完成，请稍后重试";
      case 502, 503, 504 -> "AI 服务暂时不可用，本次请求未完成，请稍后重试";
      default -> "AI 请求失败（HTTP " + status + "），请联系管理员检查模型配置";
    };
  }
}
