package com.onlineexam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.common.JsonHelper;
import com.onlineexam.controller.ExamController;
import com.onlineexam.controller.SubmissionController;
import com.onlineexam.repository.SubmissionRepository;
import com.onlineexam.service.*;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionExpiryPolicyTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private StoreService storage;
  private SubmissionRepository repository;
  private Fixture fixture;
  private SystemLogService logs;
  private WrongBookService wrongBook;
  private SubmissionController controller;
  private ExamController examController;
  private TransactionTemplate transaction;

  static class Fixture {
    StoreService.Store metadata = new StoreService.Store();
    StoreService.Store stale;
  }
  static class Storage extends StoreService {
    private final Fixture fixture;
    private final SubmissionRepository repository;
    Storage(JdbcTemplate jdbc, Fixture fixture) {
      super(jdbc, new ObjectMapper());
      this.fixture = fixture;
      this.repository = new SubmissionRepository(jdbc, new JsonHelper(new ObjectMapper()));
    }
    @Override public Store readStore() {
      if (fixture.stale != null) return fixture.stale;
      Store result = new Store();
      result.users = fixture.metadata.users;
      result.exams = fixture.metadata.exams;
      result.examSnapshots = fixture.metadata.examSnapshots;
      result.submissions = repository.findAll();
      return result;
    }
  }
  @Configuration @EnableTransactionManagement(proxyTargetClass = true)
  static class Config {
    @Bean DataSource dataSource() {
      return new DriverManagerDataSource("jdbc:h2:mem:expiry_" + UUID.randomUUID()
          + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
    }
    @Bean JdbcTemplate jdbc(DataSource dataSource) { return spy(new JdbcTemplate(dataSource)); }
    @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    @Bean Fixture fixture() { return new Fixture(); }
    @Bean StoreService storage(JdbcTemplate jdbc, Fixture fixture) { return new Storage(jdbc, fixture); }
  }

  @BeforeEach void setUp() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = context.getBean(JdbcTemplate.class);
    storage = context.getBean(StoreService.class);
    fixture = context.getBean(Fixture.class);
    repository = new SubmissionRepository(jdbc, new JsonHelper(new ObjectMapper()));
    transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    jdbc.execute("""
        create table submission(id varchar(64) primary key,exam_id varchar(64),student_id varchar(64),student_name varchar(50),
        answers_json varchar(5000),answer_detail_json varchar(5000),switch_count int,suspicious boolean,suspicious_reasons_json varchar(2000),
        auto_score int,final_score int,status varchar(20),started_at timestamp,deadline_at timestamp,submitted_at timestamp,updated_at timestamp,
        manual_extended_minutes int,graded_by varchar(50),question_order_json varchar(2000),option_order_json varchar(2000),
        revision bigint not null default 0,unique(exam_id,student_id))
        """);
    fixture.metadata.users.add(Map.of("id", "u1", "name", "Student", "role", "student", "classId", "c1"));
    fixture.metadata.exams.add(new LinkedHashMap<>(Map.of("id", "e1", "paperId", "p1", "published", true,
        "targetClassIds", List.of("c1"), "startTime", Instant.now().minusSeconds(600).toString(),
        "endTime", Instant.now().plusSeconds(3600).toString(), "antiCheatLimit", 3)));
    fixture.metadata.examSnapshots.put("e1", Map.of("schemaVersion", 1,
        "paper", Map.of("id", "p1", "name", "Paper", "durationMinutes", 60, "questionIds", List.of("q1"), "totalScore", 100, "passScore", 60),
        "questions", List.of(Map.of("id", "q1", "title", "Question", "type", "single", "options", List.of("A", "B"), "answer", List.of("A"), "score", 100))));
    jdbc.execute("create table exam(id varchar(64) primary key,end_time timestamp,deleted int default 0)");
    jdbc.update("insert into exam(id,end_time) values(?,?)", "e1", java.sql.Timestamp.from(Instant.parse((String) fixture.metadata.exams.getFirst().get("endTime"))));
    logs = mock(SystemLogService.class);
    wrongBook = mock(WrongBookService.class);
    var exams = new ExamService(storage, logs);
    var grading = new SubmissionService(storage, exams, logs, wrongBook);
    controller = new SubmissionController(storage, exams, grading, logs);
    examController = new ExamController(storage, exams, grading, logs, mock(ExcelExportService.class));
  }

  @AfterEach void tearDown() { jdbc.execute("drop all objects"); context.close(); }

  private void seed(Instant deadline) {
    var row = new LinkedHashMap<String, Object>();
    row.put("id", "s1"); row.put("examId", "e1"); row.put("studentId", "u1"); row.put("studentName", "Student");
    row.put("status", "进行中"); row.put("startedAt", Instant.now().minusSeconds(600).toString());
    row.put("deadlineAt", deadline.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString()); row.put("updatedAt", Instant.now().toString());
    row.put("answers", answers("A"));
    storage.saveRecord("submissions", row);
  }
  private List<Map<String, Object>> answers(String value) { return List.of(Map.of("questionId", "q1", "answer", List.of(value))); }
  private Map<String, Object> body() { return Map.of("examId", "e1", "answers", answers("B"), "switchCount", 0); }
  private Map<String, Object> current() { return repository.findAll().getFirst(); }
  private void setGlobalEnd(Instant end) {
    fixture.metadata.exams.getFirst().put("endTime", end.toString());
    jdbc.update("update exam set end_time=? where id='e1'", java.sql.Timestamp.from(end));
  }
  private Map<?, ?> review() {
    var response = controller.submitSubmission("u1", body());
    assertEquals(200, response.getStatusCode().value());
    return (Map<?, ?>) ((Map<?, ?>) response.getBody()).get("submission");
  }

  @Test void expiredIndividualDeadlineGradesOnlySavedAnswers() {
    Instant deadline = Instant.now().minusSeconds(60);
    seed(deadline);
    assertEquals(100, review().get("finalScore"));
    assertEquals(answers("A"), current().get("answers"));
    assertEquals(deadline.toEpochMilli(), Instant.parse((String) current().get("submittedAt")).toEpochMilli());
  }

  @Test void globalEndDoesNotPreventSettlingAnExistingSession() {
    seed(Instant.now().minusSeconds(60));
    setGlobalEnd(Instant.now().minusSeconds(60));
    assertEquals(100, review().get("finalScore"));
  }

  @Test void lateSaveCannotRenewDeadlineOrOverwriteSavedAnswers() {
    seed(Instant.now().minusSeconds(60));
    var before = current();
    assertTrue(controller.saveSubmission("u1", body()).getStatusCode().is4xxClientError());
    assertEquals(before, current());
  }

  @Test void manualSubmissionBeforeDeadlineStillUsesCurrentRequestAnswers() {
    seed(Instant.now().plusSeconds(600));
    assertEquals(0, review().get("finalScore"));
    assertEquals(answers("B"), current().get("answers"));
  }

  @Test void validTeacherExtensionAfterGlobalEndStillAcceptsCurrentAnswers() {
    seed(Instant.now().plusSeconds(600));
    setGlobalEnd(Instant.now().minusSeconds(60));
    var extended = current(); extended.put("manualExtendedMinutes", 30);
    storage.saveRecord("submissions", extended);
    assertEquals(0, review().get("finalScore"));
    assertEquals(answers("B"), current().get("answers"));
  }

  @Test void shortenedGlobalEndClampsAnOrdinarySessionDeadline() {
    seed(Instant.now().plusSeconds(600));
    setGlobalEnd(Instant.now().minusSeconds(60));
    assertEquals(100, review().get("finalScore"));
  }

  @Test void repeatedLateSubmissionsCannotRegradeOrRepeatSideEffects() {
    seed(Instant.now().minusSeconds(60));
    assertEquals(100, review().get("finalScore"));
    long revision = (Long) current().get("revision");
    assertEquals(100, review().get("finalScore"));
    assertEquals(revision, current().get("revision"));
    verify(wrongBook, times(1)).syncFromSubmission(any(), anyMap());
    verify(logs, times(1)).log(anyMap(), anyString(), anyString());
  }

  @Test void expiredSubjectiveAnswersRemainPendingManualGrading() {
    seed(Instant.now().minusSeconds(60));
    fixture.metadata.examSnapshots.put("e1", Map.of("schemaVersion", 1,
        "paper", Map.of("id", "p1", "durationMinutes", 60, "questionIds", List.of("q1"), "totalScore", 100),
        "questions", List.of(Map.of("id", "q1", "title", "Explain", "type", "short", "answer", List.of("A"), "score", 100))));
    assertEquals("待阅卷", review().get("status"));
    assertEquals(answers("A"), current().get("answers"));
    verifyNoInteractions(wrongBook);
  }

  @Test void restoringExpiredExamSettlesItAndReturnsFinishedState() {
    seed(Instant.now().minusSeconds(60));
    var response = examController.examDetail("u1", "e1");
    assertEquals(200, response.getStatusCode().value());
    assertEquals("finished", ((Map<?, ?>) response.getBody()).get("sessionState"));
    assertEquals("已完成", current().get("status"));
    assertEquals(100, current().get("finalScore"));
  }

  @Test void aMissingDeadlineFailsClosedInsteadOfAcceptingLateAnswers() {
    seed(Instant.now().minusSeconds(60));
    jdbc.update("update submission set deadline_at=null where id='s1'");
    assertTrue(controller.submitSubmission("u1", body()).getStatusCode().is4xxClientError());
    assertEquals("进行中", current().get("status"));
    assertEquals(answers("A"), current().get("answers"));
  }

  @Test void staleExpiryCannotOverwriteANewTeacherExtension() {
    seed(Instant.now().minusSeconds(60));
    fixture.stale = storage.readStore();
    var extended = current(); extended.put("deadlineAt", Instant.now().plusSeconds(600).toString());
    extended.put("manualExtendedMinutes", 30); storage.saveRecord("submissions", extended);
    assertEquals(409, assertThrows(ResponseStatusException.class, this::review).getStatusCode().value());
    assertEquals("进行中", current().get("status"));
    assertEquals(30, current().get("manualExtendedMinutes"));
    assertEquals(answers("A"), current().get("answers"));
    verifyNoInteractions(wrongBook, logs);
  }

  @Test void earlyAutomaticTimeoutProbeDoesNotSubmitAnExtendedSession() {
    seed(Instant.now().plusSeconds(600));
    var response = controller.submitSubmission("u1", Map.of("examId", "e1", "answers", answers("B"), "autoSubmit", true));
    assertEquals(200, response.getStatusCode().value());
    assertEquals("active", ((Map<?, ?>) response.getBody()).get("sessionState"));
    assertEquals("进行中", current().get("status"));
    assertEquals(answers("A"), current().get("answers"));
    assertEquals(0L, current().get("revision"));
  }

  @Test void backgroundSweepSettlesTheLatestSavedDraftWithoutAnyBrowserRequest() {
    seed(Instant.now().plusSeconds(600));
    assertEquals(200, controller.saveSubmission("u1", body()).getStatusCode().value());
    var saved = current();
    saved.put("deadlineAt", Instant.now().minusSeconds(60).toString());
    storage.saveRecord("submissions", saved);
    var exams = new ExamService(storage, logs);
    var grading = new SubmissionService(storage, exams, logs, wrongBook);
    var sweep = new ExpiredSubmissionScheduler(storage, grading);
    sweep.settleExpiredSessions();
    assertEquals("已完成", current().get("status"));
    assertEquals(answers("B"), current().get("answers"));
    assertEquals(0, current().get("finalScore"));
    long revision = (Long) current().get("revision");
    sweep.settleExpiredSessions();
    assertEquals(revision, current().get("revision"));
    verify(wrongBook, times(1)).syncFromSubmission(any(), anyMap());
  }

  @Test void missingPublishedVersionCannotInventAnEmptyFullSubmission() {
    seed(Instant.now().minusSeconds(60));
    fixture.metadata.examSnapshots.clear();
    assertEquals(409, assertThrows(ResponseStatusException.class, this::review).getStatusCode().value());
    assertEquals("进行中", current().get("status"));
    assertEquals(answers("A"), current().get("answers"));
    verifyNoInteractions(wrongBook, logs);
  }

  @Test void expiredSettlementIgnoresClientAttemptsToResetSwitchCount() {
    seed(Instant.now().minusSeconds(60));
    var saved = current(); saved.put("switchCount", 10); storage.saveRecord("submissions", saved);
    assertEquals(true, review().get("suspicious"));
    assertEquals(10, current().get("switchCount"));
    assertEquals(answers("A"), current().get("answers"));
  }

  @Test void secondarySyncFailureCannotUndoTheCommittedGradeOrInviteRegrading() {
    seed(Instant.now().minusSeconds(60));
    doThrow(new IllegalStateException("injected wrong-book outage")).when(wrongBook).syncFromSubmission(any(), anyMap());
    assertEquals(100, review().get("finalScore"));
    assertEquals(100, review().get("finalScore"));
    verify(wrongBook, times(1)).syncFromSubmission(any(), anyMap());
    verify(logs, times(1)).log(anyMap(), anyString(), anyString());
  }

  @Test void failedFinalWriteDoesNotPublishAnyGradeOrSuccessSideEffects() {
    seed(Instant.now().minusSeconds(60));
    doThrow(new org.springframework.dao.DataIntegrityViolationException("injected grade write failure"))
        .when(jdbc).update(startsWith("update submission"), any(Object[].class));
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class, this::review);
    assertEquals("进行中", current().get("status"));
    assertEquals(answers("A"), current().get("answers"));
    verifyNoInteractions(wrongBook, logs);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void staleCachedScheduleCannotAcceptAnswersAfterTheAuthoritativeGlobalEnd(boolean submit) {
    seed(Instant.now().plusSeconds(600));
    // The shared read view still contains the old future end; only the database has the new deadline.
    jdbc.update("update exam set end_time=? where id='e1'", java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
    var rejected = assertThrows(ResponseStatusException.class,
        () -> { if (submit) controller.submitSubmission("u1", body()); else controller.saveSubmission("u1", body()); });
    assertEquals(409, rejected.getStatusCode().value());
    assertEquals(answers("A"), current().get("answers"));
    assertEquals("进行中", current().get("status"));
    verifyNoInteractions(wrongBook, logs);
  }

  @Test void anOutdatedEarlierGlobalEndCannotPrematurelySettleACurrentlyValidSession() {
    seed(Instant.now().plusSeconds(600));
    fixture.metadata.exams.getFirst().put("endTime", Instant.now().minusSeconds(60).toString());
    assertEquals(409, assertThrows(ResponseStatusException.class, this::review).getStatusCode().value());
    assertEquals("进行中", current().get("status"));
    assertEquals(answers("A"), current().get("answers"));
    verifyNoInteractions(wrongBook, logs);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void waitingForARowLockCannotAcceptAnswersAfterExpiry(boolean submit) throws Exception {
    Instant deadline = Instant.now().plusSeconds(3);
    seed(deadline);
    var waiting = new CountDownLatch(1);
    var pool = Executors.newSingleThreadExecutor();
    Future<Integer>[] future = new Future[1];
    try {
      transaction.executeWithoutResult(status -> {
        jdbc.queryForList("select id from submission where id=? for update", "s1");
        doAnswer(invocation -> { waiting.countDown(); return invocation.callRealMethod(); })
            .when(jdbc).queryForList(contains("for update"), any(Object[].class));
        doAnswer(invocation -> { waiting.countDown(); return invocation.callRealMethod(); })
            .when(jdbc).update(startsWith("update submission"), any(Object[].class));
        future[0] = pool.submit(() -> {
          try { return (submit ? controller.submitSubmission("u1", body()) : controller.saveSubmission("u1", body())).getStatusCode().value(); }
          catch (ResponseStatusException exception) { return exception.getStatusCode().value(); }
        });
        try {
          assertTrue(waiting.await(2, TimeUnit.SECONDS));
          Thread.sleep(Math.max(0, Duration.between(Instant.now(), deadline).toMillis()) + 100);
        } catch (InterruptedException exception) { throw new RuntimeException(exception); }
      });
      assertEquals(409, future[0].get(5, TimeUnit.SECONDS));
      assertEquals(answers("A"), current().get("answers"));
      assertEquals("进行中", current().get("status"));
    } finally { pool.shutdownNow(); }
  }
}
