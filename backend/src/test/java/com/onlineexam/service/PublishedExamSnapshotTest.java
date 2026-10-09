package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublishedExamSnapshotTest {
  private JdbcTemplate jdbc;
  private StoreService persistence;
  private ExamService exams;
  private SubmissionService submissions;
  private Map<String, Object> frozenPaper;
  private Map<String, Object> frozenQuestion;
  private Map<String, Object> examRow;
  private String snapshotJson;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = mock(JdbcTemplate.class);
    persistence = new StoreService(jdbc, new ObjectMapper());
    var logs = mock(SystemLogService.class);
    exams = new ExamService(persistence, logs);
    submissions = new SubmissionService(persistence, exams, logs, mock(WrongBookService.class));
    frozenPaper = new LinkedHashMap<>(Map.of("id", "p1", "name", "Original paper",
        "durationMinutes", 30, "totalScore", 5, "passScore", 3, "questionIds", List.of("q1")));
    frozenQuestion = new LinkedHashMap<>(Map.of("id", "q1", "title", "Original question", "type", "single",
        "options", new ArrayList<>(List.of("A", "B")), "answer", List.of("A"), "score", 5));
    snapshotJson = new ObjectMapper().writeValueAsString(Map.of("schemaVersion", 1, "paper", frozenPaper, "questions", List.of(frozenQuestion)));
    examRow = new LinkedHashMap<>(Map.of("id", "e1", "teacher_id", "t1", "paper_id", "p1", "name", "Exam",
        "target_class_ids_json", "[\"c1\"]", "start_time", Instant.now().minusSeconds(3600).toString(),
        "end_time", Instant.now().plusSeconds(7200).toString(), "published", 1));
    when(jdbc.queryForList("select * from exam where deleted=0 order by start_time desc,id")).thenReturn(List.of(examRow));
    when(jdbc.queryForList("select * from paper where deleted=0")).thenReturn(List.of(livePaperRow()));
    when(jdbc.queryForList("select * from question where deleted=0")).thenReturn(List.of(liveQuestionRow()));
    when(jdbc.queryForList("select exam_id,content_json from exam_snapshot")).thenReturn(
        List.of(Map.of("exam_id", "e1", "content_json", snapshotJson)));
  }

  private Map<String, Object> livePaperRow() {
    return Map.of("id", "p1", "teacher_id", "t1", "name", "Edited paper", "duration_minutes", 120,
        "total_score", 100, "pass_score", 60, "question_ids_json", "[\"q1\"]");
  }

  private Map<String, Object> liveQuestionRow() {
    return Map.of("id", "q1", "teacher_id", "t1", "title", "Edited question", "type", "single",
        "options_json", "[\"B\",\"C\",\"D\"]", "answer_json", "[\"B\"]", "score", 100);
  }

  private Map<String, Object> exam(Store store) { return store.exams.getFirst(); }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> deliveredQuestions(Map<String, Object> response) {
    return (List<Map<String, Object>>) response.get("questions");
  }

  @Test
  void publishedDeliveryUsesOriginalQuestionOptionsAndPaperSettings() {
    Store store = persistence.readStore();
    var response = exams.buildExamSnapshot(store, exam(store), false, null, null);
    var question = deliveredQuestions(response).getFirst();
    assertEquals("Original question", question.get("title"));
    assertEquals(List.of("A", "B"), question.get("options"));
    assertEquals(List.of("A"), question.get("answer"));
    assertEquals(5, question.get("score"));
    assertEquals(30, response.get("durationMinutes"));
    assertEquals(5, response.get("totalScore"));
    assertEquals(3, response.get("passScore"));
  }

  @Test
  void frozenContentSurvivesSoftDeletedBankAndPaper() {
    when(jdbc.queryForList("select * from paper where deleted=0")).thenReturn(List.of());
    when(jdbc.queryForList("select * from question where deleted=0")).thenReturn(List.of());
    Store store = persistence.readStore();
    assertEquals("Original question", deliveredQuestions(exams.buildExamSnapshot(store, exam(store), false, null, null))
        .getFirst().get("title"));
  }

  @Test
  void studentResponseRedactsAnswersAndDoesNotExposeRawSnapshot() throws Exception {
    Store store = persistence.readStore();
    var response = exams.buildExamSnapshot(store, exam(store), true, null, Map.of("q1", List.of(1, 0)));
    var question = deliveredQuestions(response).getFirst();
    assertFalse(question.containsKey("answer"));
    assertEquals(List.of("B", "A"), question.get("options"));
    String json = new ObjectMapper().writeValueAsString(response);
    assertFalse(json.contains("\"answer\""));
    assertFalse(json.contains("content_json"));
    assertFalse(json.contains("examSnapshots"));
    assertFalse(json.contains("snapshotJson"));
    assertEquals(List.of("A", "B"), deliveredQuestions(exams.buildExamSnapshot(store, exam(store), false, null, null))
        .getFirst().get("options"));
  }

  @Test
  void scoringAndReviewUseOriginalAnswerAndFullScore() {
    Store store = persistence.readStore();
    var submission = new LinkedHashMap<String, Object>(Map.of("id", "s1", "examId", "e1", "studentId", "u1",
        "answers", List.of(Map.of("questionId", "q1", "answer", List.of("A")))));
    submissions.gradeSubmission(store, submission);
    assertEquals(5, submission.get("autoScore"));
    assertEquals(5, submission.get("finalScore"));
    var review = submissions.buildSubmissionReview(store, submission);
    assertEquals(5, review.get("totalScore"));
    assertEquals(3, review.get("passScore"));
    assertEquals(30, review.get("durationMinutes"));
  }

  @Test
  void studentDeadlineAndOptionOrderUseFrozenPaper() {
    Store store = persistence.readStore();
    var session = exams.ensureStudentSession(store, exam(store), Map.of("id", "u1", "role", "student", "name", "Student"));
    assertFalse(session.containsKey("error"));
    assertEquals(1800, Instant.parse(String.valueOf(session.get("deadlineAt"))).getEpochSecond()
        - Instant.parse(String.valueOf(session.get("startedAt"))).getEpochSecond());
    assertEquals(2, ((List<?>) ((Map<?, ?>) session.get("optionOrder")).get("q1")).size());
  }

  @Test
  void legacyMissingSnapshotCannotSilentlyUseCurrentBankForDeliveryOrScoring() {
    when(jdbc.queryForList("select exam_id,content_json from exam_snapshot")).thenReturn(List.of());
    Store store = persistence.readStore();
    assertThrows(ResponseStatusException.class, () -> exams.buildExamSnapshot(store, exam(store), true, null, null));
    var submission = new LinkedHashMap<String, Object>(Map.of("examId", "e1", "finalScore", 7));
    assertThrows(ResponseStatusException.class, () -> submissions.gradeSubmission(store, submission));
    assertEquals(7, submission.get("finalScore"));
    assertEquals("MISSING", exams.decorateExam(store, exam(store)).get("contentVersionStatus"));
  }

  @Test
  void draftPreviewStillUsesLiveContent() {
    examRow.put("published", 0);
    when(jdbc.queryForList("select exam_id,content_json from exam_snapshot")).thenReturn(List.of());
    Store store = persistence.readStore();
    assertEquals("Edited question", deliveredQuestions(exams.buildExamSnapshot(store, exam(store), false, null, null))
        .getFirst().get("title"));
  }

  @Test
  void firstPublicationPersistsServerOwnedSnapshot() throws Exception {
    when(jdbc.queryForList("select * from paper where id=? and deleted=0 for update", "p1")).thenReturn(List.of(livePaperRow()));
    when(jdbc.queryForList("select * from question where id=? and deleted=0 for update", "q1")).thenReturn(List.of(liveQuestionRow()));
    var record = new LinkedHashMap<String, Object>(Map.of("id", "e1", "teacherId", "t1", "paperId", "p1", "published", true));
    record.put("snapshot", Map.of("answer", "forged"));
    persistence.saveRecord("exams", record);
    var jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(eq("insert into exam_snapshot(exam_id,content_json) values(?,?)"), eq("e1"), jsonCaptor.capture());
    var stored = new ObjectMapper().readTree(jsonCaptor.getValue());
    assertEquals("Edited question", stored.at("/questions/0/title").asText());
    assertEquals("B", stored.at("/questions/0/answer/0").asText());
    assertFalse(stored.toString().contains("forged"));
  }

  @Test
  void publishingIncompletePaperFailsBeforeSavingExam() {
    when(jdbc.queryForList("select * from paper where id=? and deleted=0 for update", "p1")).thenReturn(List.of(livePaperRow()));
    assertThrows(ResponseStatusException.class, () -> persistence.saveRecord("exams", Map.of("id", "e1", "teacherId", "t1", "paperId", "p1", "published", true)));
    verify(jdbc, never()).update(startsWith("insert into exam("), any(Object[].class));
  }

  @Test
  void republishingNeverReplacesExistingSnapshot() {
    when(jdbc.queryForList("select content_json from exam_snapshot where exam_id=? for update", "e1"))
        .thenReturn(List.of(Map.of("content_json", snapshotJson)));
    persistence.saveRecord("exams", Map.of("id", "e1", "teacherId", "t1", "paperId", "p1", "published", true));
    verify(jdbc, never()).update(eq("insert into exam_snapshot(exam_id,content_json) values(?,?)"), anyString(), anyString());
    verify(jdbc, never()).queryForList("select * from question where id=? and deleted=0 for update", "q1");
  }

  @Test
  void frozenExamCannotSwapItsPaper() {
    when(jdbc.queryForList("select content_json from exam_snapshot where exam_id=? for update", "e1"))
        .thenReturn(List.of(Map.of("content_json", snapshotJson)));
    assertThrows(ResponseStatusException.class, () -> persistence.saveRecord("exams", Map.of("id", "e1", "paperId", "p2", "published", false)));
  }

  private org.springframework.test.web.servlet.MockMvc studentHttp() {
    when(jdbc.queryForList("select id,role,username,name,department_id,class_id,major from user_account order by id"))
        .thenReturn(List.of(Map.of("id", "u1", "role", "student", "name", "Student", "class_id", "c1")));
    var controller = new com.onlineexam.controller.ExamController(persistence, exams, submissions,
        mock(SystemLogService.class), mock(ExcelExportService.class));
    return org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new com.onlineexam.config.GlobalExceptionHandler()).build();
  }

  @Test
  void studentHttpDeliversFrozenQuestionWithoutPrivateAnswers() throws Exception {
    // Successful session persistence must report one affected row, not Mockito's default zero.
    when(jdbc.update(startsWith("insert into submission("), any(Object[].class))).thenReturn(1);
    studentHttp().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/exams/e1/detail")
        .header("X-User-Id", "u1"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.questions[0].title").value("Original question"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.questions[0].answer").doesNotExist())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.examSnapshots").doesNotExist())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalScore").value(5));
  }

  @Test
  void missingLegacyVersionReturnsExplicitHttpConflictRatherThanGenericServerError() throws Exception {
    when(jdbc.queryForList("select exam_id,content_json from exam_snapshot")).thenReturn(List.of());
    studentHttp().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/exams/e1/detail")
        .header("X-User-Id", "u1"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value(
            "考试缺少发布版本，不能使用当前题库代替；请联系教师恢复原始版本"));
  }

  @Test
  void mutableTeacherResponseCannotModifyThePrivateVersion() {
    Store store = persistence.readStore();
    var response = exams.buildExamSnapshot(store, exam(store), false, null, null);
    var question = deliveredQuestions(response).getFirst();
    question.put("title", "Mutated by caller");
    @SuppressWarnings("unchecked") var options = (List<Object>) question.get("options");
    options.clear();
    var unchanged = deliveredQuestions(exams.buildExamSnapshot(store, exam(store), false, null, null)).getFirst();
    assertEquals("Original question", unchanged.get("title"));
    assertEquals(List.of("A", "B"), unchanged.get("options"));
  }

  @Test
  void malformedVersionCannotFallBackToLiveQuestions() {
    when(jdbc.queryForList("select exam_id,content_json from exam_snapshot"))
        .thenReturn(List.of(Map.of("exam_id", "e1", "content_json", "{\"schemaVersion\":1,\"paper\":{}}")));
    Store store = persistence.readStore();
    assertThrows(ResponseStatusException.class, () -> exams.buildExamSnapshot(store, exam(store), true, null, null));
  }

  @Test
  void savedLegacyGradesRemainVisibleWithoutInventingPassThresholds() {
    when(jdbc.queryForList("select exam_id,content_json from exam_snapshot")).thenReturn(List.of());
    Store store = persistence.readStore();
    var saved = new LinkedHashMap<String, Object>(Map.of("examId", "e1", "finalScore", 7, "status", "已完成",
        "answerDetail", List.of(Map.of("fullScore", 10, "score", 7))));
    var review = submissions.buildSubmissionReview(store, saved);
    assertEquals(7, review.get("finalScore"));
    assertEquals(saved.get("answerDetail"), review.get("answerDetail"));
    assertEquals("MISSING", review.get("contentVersionStatus"));
    assertEquals("版本待恢复", review.get("passStatus"));
  }

  @Test
  void inconsistentTotalsPreventPublication() {
    Map<String, Object> paper = new LinkedHashMap<>(livePaperRow());
    paper.put("total_score", 99);
    when(jdbc.queryForList("select * from paper where id=? and deleted=0 for update", "p1")).thenReturn(List.of(paper));
    when(jdbc.queryForList("select * from question where id=? and deleted=0 for update", "q1")).thenReturn(List.of(liveQuestionRow()));
    assertThrows(ResponseStatusException.class, () -> persistence.saveRecord("exams",
        Map.of("id", "e1", "teacherId", "t1", "paperId", "p1", "published", true)));
  }

  @Test
  void withdrawnLegacyExamWithSubmissionsIsNotAnUntouchedDraft() {
    examRow.put("published", 0);
    when(jdbc.queryForList("select exam_id,content_json from exam_snapshot")).thenReturn(List.of());
    when(jdbc.queryForList("select * from submission order by updated_at desc,id"))
        .thenReturn(List.of(Map.of("id", "s1", "exam_id", "e1", "final_score", 4, "status", "已完成")));
    Store store = persistence.readStore();
    assertEquals("MISSING", exams.decorateExam(store, exam(store)).get("contentVersionStatus"));
    assertThrows(ResponseStatusException.class, () -> exams.buildExamSnapshot(store, exam(store), false, null, null));
  }

  @Test
  void classAnalysisUsesFrozenPassThresholdAndQuestionTitles() {
    when(jdbc.queryForList("select id,role,username,name,department_id,class_id,major from user_account order by id"))
        .thenReturn(List.of(Map.of("id", "t1", "role", "teacher"),
            Map.of("id", "u1", "role", "student", "class_id", "c1")));
    when(jdbc.queryForList("select * from submission order by updated_at desc,id"))
        .thenReturn(List.of(Map.of("id", "s1", "exam_id", "e1", "student_id", "u1", "final_score", 4,
            "status", "已完成", "answer_detail_json", "[{\"questionId\":\"q1\",\"correct\":true}]")));
    var response = new com.onlineexam.controller.ClassAnalysisController(persistence).classAnalysis("t1", "e1");
    var body = (Map<?, ?>) response.getBody();
    assertEquals(5, body.get("totalScore"));
    assertEquals(3, body.get("passScore"));
    var classResult = (Map<?, ?>) ((List<?>) body.get("classes")).getFirst();
    assertEquals(100.0, classResult.get("passRate"));
    var question = (Map<?, ?>) ((List<?>) classResult.get("questionStats")).getFirst();
    assertEquals("Original question", question.get("title"));
  }

  @Test
  void aiGradingMissingVersionFailsBeforeCallingProviderOrCircuitBreaker() {
    var source = new Store();
    source.users.add(Map.of("id", "t1", "role", "teacher"));
    source.exams.add(Map.of("id", "e1", "teacherId", "t1", "paperId", "p1", "published", true));
    source.submissions.add(Map.of("id", "s1", "examId", "e1", "finalScore", 7));
    var storage = mock(StoreService.class);
    when(storage.readStore()).thenReturn(source);
    var provider = mock(org.springframework.web.client.RestTemplate.class);
    var circuit = mock(AiCircuitBreaker.class);
    when(circuit.allowRequest("grade")).thenReturn(true);
    var service = new AiService(storage, mock(SystemLogService.class), provider, new ObjectMapper(), Runnable::run, circuit);
    org.springframework.test.util.ReflectionTestUtils.setField(service, "apiKey", "test-key-not-a-real-credential");
    assertThrows(ResponseStatusException.class, () -> service.gradeSubmission("t1", Map.of("submissionId", "s1")));
    verifyNoInteractions(provider, circuit);
    assertEquals(7, source.submissions.getFirst().get("finalScore"));
  }

  @Test
  void exportedLegacyScoresDoNotClaimAnUnknownPassThreshold() throws Exception {
    var rows = List.<Map<String, Object>>of(Map.of("score", 7, "totalScore", 0, "passScore", 0, "contentVersionStatus", "MISSING"));
    byte[] bytes = new ExcelExportService().generateScoreExcel("Legacy", rows);
    try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      var row = workbook.getSheetAt(0).getRow(1);
      assertEquals("版本待恢复", row.getCell(6).getStringCellValue());
      assertEquals("版本待恢复", row.getCell(7).getStringCellValue());
      assertEquals(7.0, row.getCell(5).getNumericCellValue());
    }
  }

  @Test
  void transactionCompletionInvalidatesAConcurrentCacheRefill() {
    when(jdbc.queryForList("select content_json from exam_snapshot where exam_id=? for update", "e1"))
        .thenReturn(List.of(Map.of("content_json", snapshotJson)));
    org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
    try {
      persistence.saveRecord("exams", Map.of("id", "e1", "paperId", "p1", "published", true));
      Store cachedDuringTransaction = persistence.readStore();
      org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
          .forEach(callback -> callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED));
      assertNotSame(cachedDuringTransaction, persistence.readStore());
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void legacyPublishedExamCannotBeSilentlyBackfilledOnEdit() {
    when(jdbc.queryForList("select * from exam where id=? for update", "e1")).thenReturn(List.of(examRow));
    assertThrows(ResponseStatusException.class, () -> persistence.saveRecord("exams", Map.of("id", "e1", "teacherId", "t1", "paperId", "p1", "published", true)));
  }
}
