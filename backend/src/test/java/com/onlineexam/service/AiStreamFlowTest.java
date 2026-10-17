package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiStreamFlowTest {
  HttpServer server;
  AiService service;
  AiCircuitBreaker circuit;
  String providerReply;
  java.util.Queue<Integer> statuses = new java.util.concurrent.ConcurrentLinkedQueue<>();
  java.util.Queue<String> replies = new java.util.concurrent.ConcurrentLinkedQueue<>();
  List<String> requestBodies = new java.util.concurrent.CopyOnWriteArrayList<>();
  static class Events extends SseEmitter {
    List<String> frames = new java.util.concurrent.CopyOnWriteArrayList<>();
    boolean completed;
    boolean stopOnChunk;
    java.util.function.Consumer<Throwable> errorHandler;
    @Override public void onError(java.util.function.Consumer<Throwable> handler) {
      errorHandler = handler;
      super.onError(handler);
    }
    @Override public void send(SseEventBuilder builder) {
      StringBuilder frame = new StringBuilder();
      builder.build().forEach(part -> frame.append(part.getData()));
      frames.add(frame.toString());
      if (stopOnChunk && frame.toString().contains("event:chunk")) errorHandler.accept(new java.io.IOException("client disconnected"));
    }
    @Override public void complete() { completed = true; }
  }
  @BeforeEach void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat", exchange -> {
      requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String reply = replies.poll();
      byte[] bytes = (reply != null ? reply : providerReply).getBytes(StandardCharsets.UTF_8);
      Integer status = statuses.poll();
      exchange.sendResponseHeaders(status != null ? status : 200, bytes.length);
      try (var out = exchange.getResponseBody()) { out.write(bytes); }
    });
    server.start();
    StoreService storage = mock(StoreService.class);
    Store store = new Store(); store.users.add(Map.of("id", "s1", "role", "student"));
    when(storage.readStore()).thenReturn(store);
    circuit = mock(AiCircuitBreaker.class); when(circuit.allowRequest(anyString())).thenReturn(true);
    service = new AiService(storage, mock(SystemLogService.class), mock(RestTemplate.class),
      new ObjectMapper(), Runnable::run, circuit);
    ReflectionTestUtils.setField(service, "apiKey", "test-not-real");
    ReflectionTestUtils.setField(service, "apiUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
    ReflectionTestUtils.setField(service, "model", "configured-model");
    ReflectionTestUtils.setField(service, "concurrentLimit", 2);
    ReflectionTestUtils.setField(service, "rateLimitPerMinute", 60);
  }
  @AfterEach void cleanup() {
    server.stop(0);
    assertEquals(0, ((java.util.concurrent.atomic.AtomicInteger) ReflectionTestUtils.getField(service, "activeSseConnections")).get());
    var semaphore = (java.util.concurrent.Semaphore) ReflectionTestUtils.getField(service, "aiApiSemaphore");
    if (semaphore != null) assertEquals(2, semaphore.availablePermits());
  }
  Events invoke(String mode) {
    Events events = new Events();
    if (mode.equals("chat")) service.chatStream("s1", Map.of("message", "解释微积分"), events);
    else service.practiceQuestionsStream("s1", Map.of("subject", "高等数学", "count", mode.equals("batch") ? 21 : 1), events);
    return events;
  }
  @ParameterizedTest @ValueSource(strings = {"chat", "practice", "batch"})
  void emptyProviderStreamMustNeverReportSuccess(String mode) {
    providerReply = "data: [DONE]\n\n";
    var events = invoke(mode);
    assertTrue(events.completed);
    assertTrue(events.frames.stream().anyMatch(s -> s.contains("event:error")), events.frames.toString());
    assertFalse(events.frames.stream().anyMatch(s -> s.contains("event:complete")));
    verify(circuit, never()).recordSuccess(anyString());
  }
  @ParameterizedTest @ValueSource(strings = {"practice", "batch"})
  void invalidQuestionJsonMustNotCreateSuccessfulPractice(String mode) throws Exception {
    providerReply = "data: " + new ObjectMapper().writeValueAsString(Map.of("choices", List.of(Map.of("delta", Map.of("content", "not questions"))))) + "\n\ndata: [DONE]\n\n";
    var events = invoke(mode);
    assertTrue(events.frames.stream().anyMatch(s -> s.contains("event:error")));
    assertFalse(events.frames.stream().anyMatch(s -> s.contains("event:complete")));
  }
  String stream(String text) throws Exception {
    return "data: " + new ObjectMapper().writeValueAsString(Map.of("choices", List.of(Map.of("delta", Map.of("content", text))))) + "\n\ndata: [DONE]\n\n";
  }
  String questions(int count) throws Exception {
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < count; i++) result.add(Map.of("title", "Question " + i, "type", "single",
        "options", List.of("A. one", "B. two"), "answer", List.of("A"), "score", 5));
    return new ObjectMapper().writeValueAsString(result);
  }
  @Test void normalChatHasExactlyOneTerminalEvent() throws Exception {
    providerReply = stream("微积分是研究变化的数学分支");
    var events = invoke("chat");
    assertEquals(1, events.frames.stream().filter(s -> s.contains("event:complete")).count());
    assertFalse(events.frames.stream().anyMatch(s -> s.contains("event:error")));
    verify(circuit).recordSuccess("chat");
  }
  @Test void validPracticeQuestionsComplete() throws Exception {
    providerReply = stream(questions(1));
    var events = invoke("practice");
    assertEquals(1, events.frames.stream().filter(s -> s.contains("event:complete")).count());
    verify(circuit).recordSuccess("practice");
  }
  @Test void successfulBatchesMergeAllQuestions() throws Exception {
    replies.add(stream(questions(20))); replies.add(stream(questions(1)));
    var events = invoke("batch");
    String frame = events.frames.stream().filter(s -> s.contains("event:complete")).findFirst().orElseThrow();
    var payload = new ObjectMapper().readTree(frame.substring(frame.indexOf("data:") + 5).trim());
    assertEquals(21, new ObjectMapper().readTree(payload.path("content").asText()).size());
    assertEquals(21, payload.path("generatedTotal").asInt());
    assertEquals(2, requestBodies.size());
    verify(circuit).recordSuccess("practice");
  }
  @Test void laterBatchFailureNeverReportsOverallSuccess() throws Exception {
    replies.add(stream(questions(20))); replies.add("data: [DONE]\n\n");
    var events = invoke("batch");
    assertTrue(events.frames.stream().anyMatch(s -> s.contains("event:error")));
    assertFalse(events.frames.stream().anyMatch(s -> s.contains("event:complete")));
    verify(circuit, never()).recordSuccess(anyString());
  }

  @Test void followUpUsesNewCountRatherThanPreviousCount() throws Exception {
    providerReply = stream(questions(2));
    Events events = new Events();
    service.practiceQuestionsStream("s1", Map.of("customPrompt", "再出2道题", "messages", List.of(
        Map.of("role", "user", "content", "出5道高等数学单选题"),
        Map.of("role", "assistant", "content", questions(5)))), events);
    assertTrue(events.frames.stream().anyMatch(s -> s.contains("event:complete")), events.frames.toString());
    assertFalse(events.frames.stream().anyMatch(s -> s.contains("event:error")));
  }

  @Test void invalidInputDoesNotReserveCircuitProbe() {
    Events events = new Events();
    service.chatStream("s1", Map.of("message", " "), events);
    verify(circuit, never()).allowRequest(anyString());
    assertTrue(events.frames.stream().anyMatch(s -> s.contains("event:error")));
  }
  @Test void rateLimitedRequestDoesNotReserveCircuitProbe() {
    ReflectionTestUtils.setField(service, "rateLimitPerMinute", 0);
    var events = invoke("practice");
    verify(circuit, never()).allowRequest(anyString());
    assertTrue(events.frames.stream().anyMatch(s -> s.contains("event:error")));
  }

  @Test void slowHeartbeatClientDoesNotBlockOtherConnections() throws Exception {
    var queued = new ArrayList<Runnable>();
    ReflectionTestUtils.setField(service, "aiTaskExecutor", (java.util.concurrent.Executor) queued::add);
    var blocked = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var healthy = new java.util.concurrent.CountDownLatch(1);
    Events slow = new Events() {
      @Override public void send(SseEventBuilder builder) {
        if (builder.build().stream().anyMatch(p -> p.getData().toString().contains(":heartbeat"))) {
          blocked.countDown();
          try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        super.send(builder);
      }
    };
    Events fast = new Events() {
      @Override public void send(SseEventBuilder builder) {
        if (builder.build().stream().anyMatch(p -> p.getData().toString().contains(":heartbeat"))) healthy.countDown();
        super.send(builder);
      }
    };
    providerReply = "data: [DONE]\n\n";
    try {
      service.chatStream("s1", Map.of("message", "one"), slow);
      assertTrue(blocked.await(2, java.util.concurrent.TimeUnit.SECONDS));
      service.chatStream("s1", Map.of("message", "two"), fast);
      assertTrue(healthy.await(2, java.util.concurrent.TimeUnit.SECONDS));
    } finally { release.countDown(); queued.forEach(Runnable::run); }
  }

  @Test void clientCancellationIsNotAnUpstreamFailure() throws Exception {
    providerReply = stream("a healthy response");
    Events events = new Events(); events.stopOnChunk = true;
    service.chatStream("s1", Map.of("message", "test"), events);
    verify(circuit, never()).recordFailure(anyString());
    verify(circuit, never()).recordSuccess(anyString());
    assertFalse(events.frames.stream().anyMatch(s -> s.contains("event:complete")));
  }

  @ParameterizedTest @ValueSource(strings = {"chat", "practice", "batch"})
  void servicePropagatesConfiguredFallbackAcrossAllStreamingModes(String mode) throws Exception {
    ReflectionTestUtils.setField(service, "model", "glm-4.7-flash");
    ReflectionTestUtils.setField(service, "fallbackModel", "glm-4-flash");
    replies.add("{\"error\":{\"code\":\"1305\"}}"); statuses.add(429);
    replies.add(stream(mode.equals("chat") ? "fallback response" : questions(mode.equals("batch") ? 20 : 1)));
    statuses.add(200);
    if (mode.equals("batch")) { replies.add(stream(questions(1))); statuses.add(200); }
    var events = invoke(mode);
    assertTrue(events.frames.stream().anyMatch(s -> s.contains("event:complete")), events.frames.toString());
    assertFalse(events.frames.stream().anyMatch(s -> s.contains("event:error")));
    ObjectMapper mapper = new ObjectMapper();
    assertEquals("glm-4.7-flash", mapper.readTree(requestBodies.get(0)).path("model").asText());
    assertEquals("glm-4-flash", mapper.readTree(requestBodies.get(1)).path("model").asText());
    assertFalse(mapper.readTree(requestBodies.get(1)).has("thinking"));
  }

  @Test void failedRequestsRemainReportableThroughHealthEndpoint() {
    providerReply = "data: [DONE]\n\n";
    invoke("chat");
    var response = assertDoesNotThrow(service::getHealthStatus);
    var performance = (Map<?, ?>) ((Map<?, ?>) response.getBody()).get("performance");
    assertEquals(1, performance.get("totalRequests"));
    assertEquals(0, performance.get("successRequests"));
    assertEquals(1, performance.get("failedRequests"));
    assertEquals(0L, performance.get("avgResponseTimeMs"));
  }
  @Test void failedRequestsDoNotChangeSuccessfulResponseAverage() throws Exception {
    providerReply = stream("normal answer");
    invoke("chat");
    var before = (Map<?, ?>) ((Map<?, ?>) service.getHealthStatus().getBody()).get("performance");
    providerReply = "data: [DONE]\n\n";
    invoke("chat");
    var after = (Map<?, ?>) ((Map<?, ?>) service.getHealthStatus().getBody()).get("performance");
    assertEquals(before.get("avgResponseTimeMs"), after.get("avgResponseTimeMs"));
    assertEquals(2, after.get("totalRequests"));
    assertEquals(1, after.get("successRequests"));
    assertEquals(1, after.get("failedRequests"));
  }

  @Test void streamedMathQuestionsReturnValidJsonWithoutLosingLatex() throws Exception {
    String formula = "$\\lim_{x \\to 0} \\frac{\\sin x}{x}$";
    ObjectMapper mapper = new ObjectMapper();
    String raw = mapper.writeValueAsString(List.of(Map.of("title", formula, "type", "single",
        "options", List.of("A. 1", "B. 2"), "answer", List.of("A"), "score", 5)))
        .replace("\\\\", "\\");
    providerReply = stream(raw);
    var events = invoke("practice");
    String complete = events.frames.stream().filter(frame -> frame.contains("event:complete")).findFirst().orElseThrow();
    var envelope = mapper.readTree(complete.substring(complete.indexOf("data:") + 5).trim());
    var questions = mapper.readTree(envelope.path("content").asText());
    assertEquals(formula, questions.get(0).path("title").asText());
    assertEquals("A", questions.get(0).path("answer").get(0).asText());
  }

}
