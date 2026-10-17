package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class AiStreamClientTest {
  HttpServer server;
  int status;
  int primaryStatus;
  String primaryResponse;
  List<String> models = new java.util.concurrent.CopyOnWriteArrayList<>();
  String response;
  String retryAfter;
  AtomicInteger requests = new AtomicInteger();
  List<Long> waits = new ArrayList<>();
  List<String> chunks = new ArrayList<>();
  AiStreamClient client;
  boolean keepOpen;
  java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

  @BeforeEach void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat", exchange -> {
      requests.incrementAndGet();
      var request = new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes());
      String model = request.path("model").asText();
      models.add(model);
      boolean primary = primaryStatus > 0 && "glm-4.7-flash".equals(model);
      if (retryAfter != null) exchange.getResponseHeaders().set("Retry-After", retryAfter);
      byte[] bytes = (primary ? primaryResponse : response).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(primary ? primaryStatus : status, keepOpen ? 0 : bytes.length);
      try (var out = exchange.getResponseBody()) {
        out.write(bytes); out.flush();
        if (keepOpen) try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      }
    });
    server.start(); status = 200;
    client = new AiStreamClient(new ObjectMapper(), waits::add);
  }
  @AfterEach void cleanup() { release.countDown(); server.stop(0); }
  AiStreamClient.Result call() throws Exception {
    return client.stream("http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "test-key",
      () -> Map.of("model", "configured-model", "stream", true), false,
      (type, text) -> chunks.add(text), new AiStreamClient.Cancellation());
  }
  static String content(String text) {
    return "data: {\"choices\":[{\"delta\":{\"content\":\"" + text + "\"}}]}\n\n";
  }
  @Test void emptyDoneIsFailureNotSuccess() {
    response = "data: [DONE]\n\n";
    assertThrows(AiStreamClient.Failure.class, this::call);
  }
  @Test void missingCompletionDoesNotAcceptPartialAnswerOrReplayIt() {
    response = content("partial");
    assertThrows(AiStreamClient.Failure.class, this::call);
    assertEquals(List.of("partial"), chunks);
    assertEquals(1, requests.get());
  }
  @Test void doneCompletesNonemptyAnswer() throws Exception {
    response = content("hello") + "data:[DONE]\n\n";
    assertEquals("hello", call().content());
    assertEquals(1, requests.get());
  }
  @Test void finishReasonStopCanCompleteWithoutDoneMarker() throws Exception {
    response = content("hello") + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n";
    assertEquals("hello", call().content());
  }
  @Test void tokenLimitIsFailureEvenWithDoneMarker() {
    response = content("partial") + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n";
    assertThrows(AiStreamClient.Failure.class, this::call);
  }
  @Test void retryBudgetHonorsRetryAfterAndHasTerminalMessage() {
    status = 429; response = "{\"error\":{\"code\":\"1302\",\"message\":\"rate limit\"}}"; retryAfter = "2";
    var error = assertThrows(AiStreamClient.Failure.class, this::call);
    assertEquals(3, requests.get());
    assertEquals(List.of(2000L, 2000L), waits);
    assertFalse(error.getMessage().contains("正在"));
  }
  @Test void quotaIsNotRetriedOrDisguisedAsRateLimit() {
    status = 429; response = "{\"error\":{\"code\":\"1113\",\"message\":\"余额不足\"}}";
    var error = assertThrows(AiStreamClient.Failure.class, this::call);
    assertTrue(error.getMessage().contains("额度"));
    assertEquals(1, requests.get());
  }
  @Test void authenticationFailureIsNotRetriedOrLeaked() {
    status = 401; response = "secret-provider-details";
    var error = assertThrows(AiStreamClient.Failure.class, this::call);
    assertFalse(error.getMessage().contains(response));
    assertEquals(1, requests.get());
  }
  @Test void providerErrorInsideHttp200IsFailure() {
    response = "data: {\"error\":{\"message\":\"failed\"}}\n\n";
    assertThrows(AiStreamClient.Failure.class, this::call);
  }
  @Test void alreadyCancelledRequestNeverCallsProvider() {
    var cancellation = new AiStreamClient.Cancellation(); cancellation.cancel();
    assertThrows(java.util.concurrent.CancellationException.class, () -> client.stream(
      "http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "key", () -> Map.of(), false,
      (type, text) -> {}, cancellation));
    assertEquals(0, requests.get());
  }
  @Test void upstreamFinalFrameAtEofIsNotDiscarded() throws Exception {
    response = content("hello") + "data: [DONE]\n";
    assertEquals("hello", call().content());
  }
  @Test void finishReasonCompletesWithoutWaitingForProviderToClose() throws Exception {
    keepOpen = true;
    response = content("hello") + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n";
    var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
      try { return call(); } catch (Exception e) { throw new RuntimeException(e); }
    });
    try { assertEquals("hello", future.get(2, java.util.concurrent.TimeUnit.SECONDS).content()); }
    finally { release.countDown(); }
  }

  @Test void transientRateLimitCanRecoverOnNextAttempt() throws Exception {
    status = 429; response = "{\"error\":{\"code\":\"1302\"}}";
    client = new AiStreamClient(new ObjectMapper(), millis -> {
      waits.add(millis); status = 200; response = content("recovered") + "data: [DONE]\n\n";
    });
    assertEquals("recovered", call().content());
    assertEquals(2, requests.get());
  }
  @Test void longCooldownDoesNotRetryBeforeRetryAfter() {
    status = 429; response = "{}"; retryAfter = "60";
    assertThrows(AiStreamClient.Failure.class, this::call);
    assertEquals(1, requests.get());
    assertTrue(waits.isEmpty());
  }
  @Test void cancellationInterruptsBackoffWithoutAnotherRequest() throws Exception {
    status = 429; response = "{}"; retryAfter = "30";
    client = new AiStreamClient(new ObjectMapper());
    var cancellation = new AiStreamClient.Cancellation();
    var future = java.util.concurrent.CompletableFuture.runAsync(() -> {
      try {
        client.stream("http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "key", () -> Map.of(),
            false, (type, text) -> {}, cancellation);
        fail("cancelled stream completed");
      } catch (java.util.concurrent.CancellationException expected) {
      } catch (Exception failure) { throw new RuntimeException(failure); }
    });
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
    while (requests.get() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
    cancellation.cancel();
    future.get(2, java.util.concurrent.TimeUnit.SECONDS);
    assertEquals(1, requests.get());
  }

  @Test void cancellationClosesAnActiveProviderStream() throws Exception {
    keepOpen = true; response = content("partial");
    var cancellation = new AiStreamClient.Cancellation();
    var received = new java.util.concurrent.CountDownLatch(1);
    var future = java.util.concurrent.CompletableFuture.runAsync(() -> {
      try {
        client.stream("http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "key", () -> Map.of(),
            false, (type, text) -> received.countDown(), cancellation);
        fail("cancelled stream completed");
      } catch (java.util.concurrent.CancellationException expected) {
      } catch (Exception failure) { throw new RuntimeException(failure); }
    });
    try {
      assertTrue(received.await(2, java.util.concurrent.TimeUnit.SECONDS));
      var cancel = java.util.concurrent.CompletableFuture.runAsync(cancellation::cancel);
      cancel.get(2, java.util.concurrent.TimeUnit.SECONDS);
      future.get(2, java.util.concurrent.TimeUnit.SECONDS);
    } finally { release.countDown(); }
  }

  AiStreamClient.Result callWithFallback() throws Exception {
    return client.stream("http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "test-key",
      () -> Map.of("model", "glm-4.7-flash", "stream", true), false,
      (type, text) -> chunks.add(text), new AiStreamClient.Cancellation(), "glm-4-flash");
  }
  @Test void busyPrimaryFallsBackToConfiguredSecondary() throws Exception {
    primaryStatus = 429; primaryResponse = "{\"error\":{\"code\":\"1305\"}}";
    response = content("fallback answer") + "data: [DONE]\n\n";
    assertEquals("fallback answer", callWithFallback().content());
    assertEquals(List.of("glm-4.7-flash", "glm-4-flash"), models);
  }
  @Test void fallbackFailureCannotBounceBackToPrimaryOrExceedBudget() {
    primaryStatus = 429; primaryResponse = "{}"; status = 503; response = "{}";
    assertThrows(AiStreamClient.Failure.class, this::callWithFallback);
    assertEquals(List.of("glm-4.7-flash", "glm-4-flash", "glm-4-flash"), models);
  }
  @Test void authenticationErrorNeverSwitchesModels() {
    primaryStatus = 401; primaryResponse = "{}"; response = "{}";
    assertThrows(AiStreamClient.Failure.class, this::callWithFallback);
    assertEquals(List.of("glm-4.7-flash"), models);
  }
  @Test void permissionErrorNeverSwitchesModels() {
    primaryStatus = 403; primaryResponse = "{}"; response = "{}";
    assertThrows(AiStreamClient.Failure.class, this::callWithFallback);
    assertEquals(List.of("glm-4.7-flash"), models);
  }
  @Test void emptyPrimaryCanFallBackBeforeAnyOutput() throws Exception {
    primaryStatus = 200; primaryResponse = "data: [DONE]\n\n";
    response = content("fallback answer") + "data: [DONE]\n\n";
    assertEquals("fallback answer", callWithFallback().content());
    assertEquals(List.of("glm-4.7-flash", "glm-4-flash"), models);
  }
  @Test void partialPrimaryIsNeverReplayedOnFallback() {
    primaryStatus = 200; primaryResponse = content("partial"); response = "{}";
    assertThrows(AiStreamClient.Failure.class, this::callWithFallback);
    assertEquals(List.of("glm-4.7-flash"), models);
    assertEquals(List.of("partial"), chunks);
  }
  @Test void successfulPrimaryDoesNotCallFallback() throws Exception {
    response = content("primary answer") + "data: [DONE]\n\n";
    assertEquals("primary answer", callWithFallback().content());
    assertEquals(List.of("glm-4.7-flash"), models);
  }

}
