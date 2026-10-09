package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiQuestionBusinessTest {
  private StoreService storage;
  private Store store;
  private FakeAiService service;
  private final ObjectMapper json = new ObjectMapper();

  private static class FakeAiService extends AiService {
    String response;
    FakeAiService(StoreService storage, AiCircuitBreaker circuit) {
      super(storage, mock(SystemLogService.class), mock(RestTemplate.class), new ObjectMapper(), Runnable::run, circuit);
    }
    @Override String callAiApi(String systemPrompt, String userPrompt) { return response; }
  }

  @org.junit.jupiter.api.AfterEach void noImplicitUpdateDuringCreation() {
    verify(storage, never()).saveRecord(anyString(), anyMap());
  }

  @BeforeEach void setUp() {
    storage = mock(StoreService.class);
    store = new Store();
    store.users.add(Map.of("id", "t1", "role", "teacher"));
    when(storage.readStore()).thenReturn(store);
    var circuit = mock(AiCircuitBreaker.class);
    when(circuit.allowRequest("generate")).thenReturn(true);
    service = new FakeAiService(storage, circuit);
    org.springframework.test.util.ReflectionTestUtils.setField(service, "apiKey", "test-key-not-real");
    org.springframework.test.util.ReflectionTestUtils.setField(service, "rateLimitPerMinute", 60);
  }

  private static Map<String, Object> candidate() {
    return new LinkedHashMap<>(Map.of("title", "What is 1 + 1?", "subject", "Math", "type", "single",
        "options", List.of("A. 1", "B. 2"), "answer", List.of("B"), "score", 5));
  }

  private static Map<String, Object> changed(String field, Object value) {
    var question = candidate(); question.put(field, value); return question;
  }

  static Stream<Arguments> invalidCandidates() {
    return Stream.of(
        Arguments.of("blank title", changed("title", " ")),
        Arguments.of("unsupported type", changed("type", "essay-unknown")),
        Arguments.of("zero score", changed("score", 0)),
        Arguments.of("negative score", changed("score", -1)),
        Arguments.of("fractional score", changed("score", 2.5)),
        Arguments.of("missing options", changed("options", List.of())),
        Arguments.of("duplicate options", changed("options", List.of("Same", "Same"))),
        Arguments.of("empty option", changed("options", List.of("A. 1", " "))),
        Arguments.of("too many options", changed("options", List.of("A", "B", "C", "D", "E"))),
        Arguments.of("missing answer", changed("answer", List.of())),
        Arguments.of("out of range answer", changed("answer", List.of("D"))),
        Arguments.of("invented answer", changed("answer", List.of("Z"))),
        Arguments.of("answer prose is not a letter", changed("answer", List.of("Because something else"))),
        Arguments.of("multiple single answers", changed("answer", List.of("A", "B"))),
        Arguments.of("unknown difficulty", changed("difficulty", "impossible")),
        Arguments.of("malformed answer", changed("answer", Map.of("value", "B"))),
        Arguments.of("label without option content", changed("options", List.of("A.", "B. 2")))
    );
  }

  @ParameterizedTest(name="{0}") @MethodSource("invalidCandidates")
  void invalidCandidateCannotEnterFormalQuestionBank(String label, Map<String, Object> question) {
    var response = service.importQuestions("t1", Map.of("questions", List.of(question)));
    assertEquals(HttpStatus.OK, response.getStatusCode());
    var body = (Map<?, ?>) response.getBody();
    assertEquals(0, body.get("importedCount"));
    assertEquals(1, ((List<?>) body.get("errors")).size());
    verify(storage, never()).createRecord(eq("questions"), anyMap());
  }

  @Test void malformedRootReturnsBadRequestInsteadOfClassCastException() {
    var response = assertDoesNotThrow(() -> service.importQuestions("t1", Map.of("questions", "not an array")));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    verify(storage, never()).createRecord(anyString(), anyMap());
  }

  @Test void malformedEntryDoesNotPreventValidItemsFromBeingImported() {
    var response = assertDoesNotThrow(() -> service.importQuestions("t1", Map.of("questions", List.of(42, candidate()))));
    var body = (Map<?, ?>) response.getBody();
    assertEquals(1, body.get("importedCount"));
    var error = (Map<?, ?>) ((List<?>) body.get("errors")).get(0);
    assertEquals(0, error.get("index"));
    verify(storage).createRecord(eq("questions"), anyMap());
  }

  @Test void mixedBatchSavesOnlyValidRecordsWithStableErrorIndices() {
    var response = service.importQuestions("t1", Map.of("questions", List.of(candidate(), changed("answer", List.of("D")), candidate())));
    var body = (Map<?, ?>) response.getBody();
    assertEquals(2, body.get("importedCount"));
    assertEquals(1, ((Map<?, ?>) ((List<?>) body.get("errors")).get(0)).get("index"));
    verify(storage, times(2)).createRecord(eq("questions"), anyMap());
  }

  @Test void importedChoiceAnswerUsesTheActualOptionValueSentByStudents() {
    var raw = candidate(); raw.put("id", "forged-id"); raw.put("teacherId", "another-teacher");
    service.importQuestions("t1", Map.of("questions", List.of(raw)));
    @SuppressWarnings({"unchecked", "rawtypes"}) org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass((Class) Map.class);
    verify(storage).createRecord(eq("questions"), captor.capture());
    assertEquals(List.of("B. 2"), captor.getValue().get("answer"));
    assertEquals("t1", captor.getValue().get("teacherId"));
    assertNotEquals("forged-id", captor.getValue().get("id"));
  }

  private org.springframework.http.ResponseEntity<?> generate(String response) {
    service.response = response;
    return service.generateQuestions("t1", Map.of("subject", "Math", "type", "single", "count", 1, "difficulty", "easy"));
  }

  @Test void malformedModelOutputIsNotInventedIntoASuccessfulPlaceholderQuestion() {
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, generate("provider output is not JSON").getStatusCode());
    verify(storage, never()).createRecord(anyString(), anyMap());
  }

  @Test void emptyModelArrayIsNotSuccessfulGeneration() {
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, generate("[]").getStatusCode());
  }

  @Test void validJsonWithAnInvalidQuestionStillFailsGeneration() throws Exception {
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, generate(json.writeValueAsString(List.of(changed("answer", List.of("Z"))))).getStatusCode());
  }

  @Test void fencedValidResponseIsNormalizedWithoutChangingResponseShape() throws Exception {
    var response = generate("```json\n" + json.writeValueAsString(List.of(candidate())) + "\n```");
    assertEquals(HttpStatus.OK, response.getStatusCode());
    var body = (Map<?, ?>) response.getBody();
    var question = (Map<?, ?>) ((List<?>) body.get("questions")).get(0);
    assertEquals(List.of("B"), question.get("answer")); // Preserve the existing preview/AI-practice letter contract.
    assertEquals(1, body.get("totalCount"));
    assertEquals(true, body.get("aiUsed"));
  }

  @Test void originalChoiceLabelsAreScoredAgainstFrozenOptionsNotShuffledDisplayPositions() {
    Map<String, Object> original = Map.of("id", "q1", "title", "Question", "type", "single",
        "options", List.of("A. one", "B. two"), "answer", List.of("A"), "score", 5);
    var exam = Map.<String, Object>of("id", "e1", "paperId", "p1", "published", true);
    store.exams.add(exam);
    store.examSnapshots.put("e1", Map.of("schemaVersion", 1, "paper", Map.of("id", "p1", "questionIds", List.of("q1")), "questions", List.of(original)));
    var submission = new LinkedHashMap<String, Object>(Map.of("examId", "e1",
        "answers", List.of(Map.of("questionId", "q1", "answer", List.of("A. one")))));
    var grading = new SubmissionService(storage, mock(ExamService.class), mock(SystemLogService.class), mock(WrongBookService.class));
    grading.gradeSubmission(store, submission);
    assertEquals(5, submission.get("finalScore"));
    assertEquals(List.of("A"), original.get("answer")); // Published source is not rewritten.
  }

  static Stream<Arguments> validTypes() {
    var single = candidate();
    var multiple = changed("type", "multiple"); multiple.put("answer", List.of("A", "B"));
    var judge = changed("type", "judge"); judge.put("answer", List.of("A"));
    List<Arguments> cases = new ArrayList<>();
    cases.add(Arguments.of(single, List.of("B. 2")));
    cases.add(Arguments.of(multiple, List.of("A. 1", "B. 2")));
    cases.add(Arguments.of(judge, List.of("A. 1")));
    for (String type : List.of("fill", "short", "coding")) {
      var question = changed("type", type);
      question.put("options", List.of()); question.put("answer", List.of("Reference text"));
      cases.add(Arguments.of(question, List.of("Reference text")));
    }
    return cases.stream();
  }

  @ParameterizedTest @MethodSource("validTypes")
  void allSupportedTypesRemainImportable(Map<String, Object> candidate, List<String> expectedAnswer) {
    var response = service.importQuestions("t1", Map.of("questions", List.of(candidate)));
    assertEquals(1, ((Map<?, ?>) response.getBody()).get("importedCount"));
    @SuppressWarnings({"unchecked", "rawtypes"}) org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass((Class) Map.class);
    verify(storage).createRecord(eq("questions"), captor.capture());
    assertEquals(expectedAnswer, captor.getValue().get("answer"));
  }

  @Test void optionsWithDifferentLabelsButTheSameContentAreStillDuplicates() {
    var question = candidate(); question.put("options", List.of("A. Same", "B. Same"));
    var response = service.importQuestions("t1", Map.of("questions", List.of(question)));
    assertEquals(0, ((Map<?, ?>) response.getBody()).get("importedCount"));
    verify(storage, never()).createRecord(anyString(), anyMap());
  }

  @Test void difficultyTextUsedByTheExistingPromptMapsToStoredDifficulty() {
    var response = service.importQuestions("t1", Map.of("questions", List.of(changed("difficulty", "中等"))));
    assertEquals(1, ((Map<?, ?>) response.getBody()).get("importedCount"));
    @SuppressWarnings({"unchecked", "rawtypes"}) org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass((Class) Map.class);
    verify(storage).createRecord(eq("questions"), captor.capture());
    assertEquals("medium", captor.getValue().get("difficulty"));
  }

  private int practiceScore(List<String> options, List<String> correct, List<String> selected) throws Exception {
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    when(jdbc.queryForList("SELECT id FROM practice_session WHERE id = ? AND user_id = ?", "ps1", "u1"))
        .thenReturn(List.of(Map.of("id", "ps1")));
    when(jdbc.queryForList("SELECT id, question_index, question_data, user_answer_json FROM practice_question WHERE session_id = ? AND user_id = ? ORDER BY question_index ASC", "ps1", "u1"))
        .thenReturn(List.of(Map.of("id", "pq1", "question_index", 0,
            "question_data", json.writeValueAsString(Map.of("type", "single", "options", options, "answer", correct, "score", 5)),
            "user_answer_json", json.writeValueAsString(selected))));
    var response = new PracticeSessionService(jdbc, json).submitSession("u1", "ps1", Map.of());
    assertEquals(HttpStatus.OK, response.getStatusCode());
    return ((Number) ((Map<?, ?>) response.getBody()).get("earnedScore")).intValue();
  }

  @Test void practiceCannotGuessAnAnswerLetterFromAnUnrelatedWord() throws Exception {
    assertEquals(0, practiceScore(List.of("A. one", "B. two"), List.of("B"), List.of("Banana")));
  }

  @Test void practiceDoesNotAwardMarksWhenReferenceAndSelectionBothHaveNoValidLetter() throws Exception {
    assertEquals(0, practiceScore(List.of("A. one", "B. two"), List.of("QQQ"), List.of("xyz")));
  }

  @Test void practiceSupportsRealOptionValuesInsteadOfComparingEmptyExtractedLetterLists() throws Exception {
    assertEquals(0, practiceScore(List.of("red", "green"), List.of("green"), List.of("red")));
    assertEquals(5, practiceScore(List.of("red", "green"), List.of("green"), List.of("green")));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void invalidStructuredResultDoesNotPoisonRetriesWithTheRawResponseCache(boolean practice) throws Exception {
    var provider = mock(RestTemplate.class);
    var circuit = mock(AiCircuitBreaker.class);
    when(circuit.allowRequest(practice ? "practice" : "generate")).thenReturn(true);
    if (practice) store.users.add(Map.of("id", "s1", "role", "student"));
    var realService = new AiService(storage, mock(SystemLogService.class), provider, json, Runnable::run, circuit);
    org.springframework.test.util.ReflectionTestUtils.setField(realService, "apiKey", "test-key-not-real");
    org.springframework.test.util.ReflectionTestUtils.setField(realService, "apiUrl", "https://provider.invalid/mock");
    org.springframework.test.util.ReflectionTestUtils.setField(realService, "model", "test-model");
    org.springframework.test.util.ReflectionTestUtils.setField(realService, "concurrentLimit", 1);
    org.springframework.test.util.ReflectionTestUtils.setField(realService, "rateLimitPerMinute", 60);
    Map bad = Map.of("choices", List.of(Map.of("message", Map.of("content", "invalid JSON"))));
    Map good = Map.of("choices", List.of(Map.of("message", Map.of("content", json.writeValueAsString(List.of(candidate()))))));
    when(provider.exchange(anyString(), eq(org.springframework.http.HttpMethod.POST), any(org.springframework.http.HttpEntity.class), eq(Map.class)))
        .thenReturn(org.springframework.http.ResponseEntity.ok(bad), org.springframework.http.ResponseEntity.ok(good));
    var request = Map.<String, Object>of("subject", "Math", "type", "single", "count", 1, "difficulty", "easy");
    var first = practice ? realService.practiceQuestions("s1", request) : realService.generateQuestions("t1", request);
    var second = practice ? realService.practiceQuestions("s1", request) : realService.generateQuestions("t1", request);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, first.getStatusCode());
    assertEquals(HttpStatus.OK, second.getStatusCode());
    verify(provider, times(2)).exchange(anyString(), eq(org.springframework.http.HttpMethod.POST), any(org.springframework.http.HttpEntity.class), eq(Map.class));
  }

  @Test void previewOptionsCannotMakeAnIncorrectPracticeSelectionShareTheCorrectKey() throws Exception {
    var raw = candidate(); raw.put("options", List.of("A. one", "A")); raw.put("answer", List.of("B"));
    var response = generate(json.writeValueAsString(List.of(raw)));
    assertEquals(HttpStatus.OK, response.getStatusCode());
    var preview = (Map<?, ?>) ((List<?>) ((Map<?, ?>) response.getBody()).get("questions")).get(0);
    @SuppressWarnings("unchecked") var options = (List<String>) preview.get("options");
    @SuppressWarnings("unchecked") var answer = (List<String>) preview.get("answer");
    assertEquals(0, practiceScore(options, answer, List.of("A")));
    assertEquals(List.of("A. one", "B. A"), options);
    assertEquals(5, practiceScore(options, answer, List.of("B")));
  }

  @Test void successfulPreviewWithExactTextReferenceRemainsValidOnUnchangedImport() throws Exception {
    var raw = candidate(); raw.put("options", List.of("A. one", "A")); raw.put("answer", List.of("A. one"));
    var response = generate(json.writeValueAsString(List.of(raw)));
    assertEquals(HttpStatus.OK, response.getStatusCode());
    var preview = (Map<?, ?>) ((List<?>) ((Map<?, ?>) response.getBody()).get("questions")).get(0);
    var imported = service.importQuestions("t1", Map.of("questions", List.of(preview)));
    assertEquals(1, ((Map<?, ?>) imported.getBody()).get("importedCount"));
  }

  @Test void legacyAmbiguousPracticeKeysCannotAwardMarksToTheWrongOption() throws Exception {
    assertEquals(0, practiceScore(List.of("A. one", "A"), List.of("B"), List.of("A")));
  }

  @Test void canonicalBankRecordRemainsImportableWithoutAmbiguousBareLetterValues() {
    var raw = candidate(); raw.put("options", List.of("A. one", "A")); raw.put("answer", List.of("B"));
    var imported = service.importQuestions("t1", Map.of("questions", List.of(raw)));
    assertEquals(1, ((Map<?, ?>) imported.getBody()).get("importedCount"));
    @SuppressWarnings({"unchecked", "rawtypes"}) org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass((Class) Map.class);
    verify(storage).createRecord(eq("questions"), captor.capture());
    assertEquals(List.of("A. one", "B. A"), captor.getValue().get("options"));
    assertEquals(List.of("B. A"), captor.getValue().get("answer"));
    var repeated = service.importQuestions("t1", Map.of("questions", List.of(captor.getValue())));
    assertEquals(1, ((Map<?, ?>) repeated.getBody()).get("importedCount"));
  }

  @Test void letterInAnUnrelatedWordCannotBeGuessedIntoAnAnswer() throws Exception {
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, generate(json.writeValueAsString(List.of(changed("answer", List.of("Because"))))).getStatusCode());
  }
}
