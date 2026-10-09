package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiGradingIntegrityTest {
  StoreService storage;
  Store store;
  SystemLogService logs;
  AiCircuitBreaker circuit;
  FakeAi service;
  Map<String, Object> submission;

  static class FakeAi extends AiService {
    AtomicInteger calls = new AtomicInteger();
    IntFunction<String> response = i -> "{\"score\":7,\"comment\":\"Valid feedback\"}";
    FakeAi(StoreService storage, SystemLogService logs, AiCircuitBreaker circuit, Executor executor) {
      super(storage, logs, mock(RestTemplate.class), new ObjectMapper(), executor, circuit);
      ReflectionTestUtils.setField(this, "apiKey", "test-not-real");
      ReflectionTestUtils.setField(this, "rateLimitPerMinute", 60);
    }
    @Override String callAiApi(String system, String prompt) { return response.apply(calls.getAndIncrement()); }
  }

  @BeforeEach void setUp() {
    storage = mock(StoreService.class); logs = mock(SystemLogService.class);
    circuit = mock(AiCircuitBreaker.class); when(circuit.allowRequest("grade")).thenReturn(true);
    store = new Store(); when(storage.readStore()).thenReturn(store);
    store.users.add(Map.of("id", "t1", "role", "teacher"));
    store.users.add(Map.of("id", "t2", "role", "teacher"));
    store.exams.add(Map.of("id", "e1", "teacherId", "t1", "paperId", "p1", "published", true));
    store.examSnapshots.put("e1", Map.of("schemaVersion", 1, "paper", Map.of("id", "p1", "questionIds", List.of("q1", "q2")),
        "questions", List.of(Map.of("id", "q1"), Map.of("id", "q2"))));
    submission = new LinkedHashMap<>(Map.of("id", "s1", "examId", "e1", "status", "待阅卷", "finalScore", 3,
        "answerDetail", List.of(detail("q1", "short", 0), detail("q2", "single", 3))));
    store.submissions.add(submission);
    service = new FakeAi(storage, logs, circuit, Runnable::run);
  }
  private Map<String, Object> detail(String id, String type, int score) {
    return new LinkedHashMap<>(Map.of("questionId", id, "type", type, "score", score, "fullScore", 10,
        "title", "Explain the result", "answer", List.of("matching answer"), "expectedAnswer", List.of("matching answer")));
  }
  private org.springframework.http.ResponseEntity<?> grade() { return service.gradeSubmission("t1", Map.of("submissionId", "s1")); }
  private void unavailable() {
    var result = assertDoesNotThrow(this::grade);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
    assertFalse(((Map<?, ?>) result.getBody()).containsKey("aiScore"));
    verify(storage, never()).saveRecord(anyString(), anyMap());
    verifyNoInteractions(logs);
  }

  static Stream<String> malformed() {
    return Stream.of("not json", "null", "[]", "{}", "{\"score\":null,\"comment\":\"x\"}",
        "{\"score\":\"7\",\"comment\":\"x\"}", "{\"score\":2.5,\"comment\":\"x\"}",
        "{\"score\":-1,\"comment\":\"x\"}", "{\"score\":11,\"comment\":\"x\"}",
        "{\"score\":4294967297,\"comment\":\"x\"}", "{\"score\":7}",
        "{\"score\":7,\"comment\":\"  \"}", "{\"score\":7,\"comment\":123}");
  }
  @ParameterizedTest @MethodSource("malformed") void invalidModelResultCannotBecomeSuccessfulScore(String raw) {
    service.response = i -> raw;
    unavailable();
    verify(circuit, never()).recordSuccess("grade");
    verify(circuit).recordFailure("grade");
  }
  static Stream<String> strictMalformed() {
    return Stream.of("[{\"score\":7,\"comment\":\"valid\"}]",
        "{\"score\":7.0000000000000001,\"comment\":\"valid\"}",
        "prefix {\"score\":7,\"comment\":\"valid\"} suffix",
        "{\"score\":7,\"comment\":\"valid\"} {\"score\":3,\"comment\":\"other\"}",
        "{\"score\":11,\"score\":7,\"comment\":\"valid\"}");
  }
  @ParameterizedTest @MethodSource("strictMalformed") void entireModelResultMustBeAnUnambiguousIntegerGrade(String raw) {
    service.response = i -> raw;
    unavailable(); verify(circuit, never()).recordSuccess("grade");
  }
  @Test void jsonMarkdownEnvelopeRemainsSupported() {
    service.response = i -> "```json\n{\"score\":7,\"comment\":\"valid\"}\n```";
    assertEquals(HttpStatus.OK, grade().getStatusCode());
  }
  @Test void providerFailureDoesNotBecomeKeywordScore() {
    service.response = i -> { throw new IllegalStateException("provider failed"); };
    unavailable();
  }
  @Test void circuitClosingBetweenRequestAndQuestionDoesNotFabricateScore() {
    submission.put("answerDetail", List.of(detail("q1", "short", 0), detail("q2", "coding", 0)));
    when(circuit.allowRequest("grade")).thenReturn(true, false);
    unavailable(); assertTrue(service.calls.get() <= 1);
  }
  @Test void oneSuccessfulQuestionCannotHideAnotherFailedQuestion() {
    submission.put("answerDetail", List.of(detail("q1", "short", 0), detail("q2", "coding", 0)));
    service.response = i -> i == 0 ? "{\"score\":7,\"comment\":\"valid\"}" : "invalid";
    unavailable();
    assertEquals(3, submission.get("finalScore"));
  }
  @Test void rejectedExecutorReturnsUnavailable() {
    service = new FakeAi(storage, logs, circuit, task -> { throw new RejectedExecutionException("full"); });
    unavailable();
  }
  @Test void interruptionReturnsFailureAndLateTasksCannotPublishResults() {
    List<Runnable> tasks = new ArrayList<>();
    service = new FakeAi(storage, logs, circuit, tasks::add);
    Thread.currentThread().interrupt();
    try {
      unavailable();
      assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); }
    tasks.forEach(Runnable::run);
    assertEquals(3, submission.get("finalScore"));
    for (Object raw : (List<?>) submission.get("answerDetail")) assertFalse(((Map<?, ?>)raw).containsKey("aiScore"));
  }
  @ParameterizedTest @MethodSource("unsubmitted") void unsubmittedSessionCannotBeAiGraded(String status) {
    submission.put("status", status);
    assertEquals(HttpStatus.CONFLICT, grade().getStatusCode());
    assertEquals(0, service.calls.get()); verifyNoInteractions(logs);
  }
  static Stream<String> unsubmitted() { return Stream.of("进行中", "已结束", "", "unknown"); }
  @Test void missingPublishedVersionStillFailsClosed() {
    store.examSnapshots.clear();
    assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, this::grade).getStatusCode());
    assertEquals(0, service.calls.get());
  }
  @Test void validScorePreservesOrderAndNeverChangesManualGrade() {
    var result = grade(); assertEquals(HttpStatus.OK, result.getStatusCode());
    var body = (Map<?, ?>) result.getBody(); assertEquals(10, body.get("aiScore")); assertEquals(3, body.get("manualScore"));
    List<?> details = (List<?>) body.get("details"); assertEquals("q1", ((Map<?, ?>) details.get(0)).get("questionId"));
    assertEquals("q2", ((Map<?, ?>) details.get(1)).get("questionId"));
    assertEquals(3, submission.get("finalScore"));
    assertFalse(((Map<?, ?>) ((List<?>) submission.get("answerDetail")).get(0)).containsKey("aiScore"));
    verify(circuit).recordSuccess("grade"); verify(circuit, times(1)).allowRequest("grade"); verify(storage, never()).saveRecord(anyString(), anyMap());
  }
  @Test void unansweredSubjectiveAnswerRemainsZeroWithoutProviderCall() {
    ((Map<String,Object>) ((List<?>)submission.get("answerDetail")).get(0)).put("answer", List.of());
    var result = grade(); assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals(3, ((Map<?,?>)result.getBody()).get("aiScore")); assertEquals(0, service.calls.get());
  }
  @Test void invalidCachedGradeIsEvictedSoRetryCanRecover() {
    RestTemplate transport = mock(RestTemplate.class);
    var actual = new AiService(storage, logs, transport, new ObjectMapper(), Runnable::run, circuit);
    ReflectionTestUtils.setField(actual, "apiKey", "test-not-real");
    ReflectionTestUtils.setField(actual, "apiUrl", "https://example.invalid/test");
    ReflectionTestUtils.setField(actual, "model", "test");
    ReflectionTestUtils.setField(actual, "rateLimitPerMinute", 60);
    ReflectionTestUtils.setField(actual, "concurrentLimit", 1);
    var invalid = org.springframework.http.ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", "{}")))));
    var valid = org.springframework.http.ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", "{\"score\":7,\"comment\":\"valid\"}")))));
    when(transport.exchange(anyString(), eq(org.springframework.http.HttpMethod.POST), any(org.springframework.http.HttpEntity.class), eq(Map.class)))
        .thenReturn((org.springframework.http.ResponseEntity) invalid, (org.springframework.http.ResponseEntity) valid);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, actual.gradeSubmission("t1", Map.of("submissionId", "s1")).getStatusCode());
    assertEquals(HttpStatus.OK, actual.gradeSubmission("t1", Map.of("submissionId", "s1")).getStatusCode());
    verify(transport, times(2)).exchange(anyString(), eq(org.springframework.http.HttpMethod.POST), any(org.springframework.http.HttpEntity.class), eq(Map.class));
  }
  @Test void otherTeacherIsRejectedBeforeProviderCall() {
    assertEquals(HttpStatus.FORBIDDEN, service.gradeSubmission("t2", Map.of("submissionId","s1")).getStatusCode());
    assertEquals(0, service.calls.get());
  }
}
