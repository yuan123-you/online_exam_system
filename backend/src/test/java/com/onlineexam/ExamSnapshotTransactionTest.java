package com.onlineexam;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.*;

class ExamSnapshotTransactionTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private StoreService store;

  @Configuration
  @EnableTransactionManagement(proxyTargetClass = true)
  static class Config {
    @Bean DataSource dataSource() {
      return new DriverManagerDataSource("jdbc:h2:mem:snapshot_" + UUID.randomUUID()
          + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
    @Bean JdbcTemplate jdbc(DataSource dataSource) { return new JdbcTemplate(dataSource); }
    @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }
    @Bean StoreService storeService(JdbcTemplate jdbc) { return new StoreService(jdbc, new ObjectMapper()); }
  }

  @BeforeEach
  void setUp() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = context.getBean(JdbcTemplate.class);
    store = context.getBean(StoreService.class);
    // A portable fixture for the same columns/constraints; production uses MySQL JSON.
    jdbc.execute("create table exam(id varchar(64) primary key, teacher_id varchar(64), paper_id varchar(64), name varchar(100), target_class_ids_json clob, start_time timestamp, end_time timestamp, anti_cheat_limit int, published boolean, deleted int)");
    jdbc.execute("create table paper(id varchar(64) primary key, teacher_id varchar(64), name varchar(100), duration_minutes int, total_score int, pass_score int, question_ids_json varchar(2000), paper_type varchar(50), source_tag varchar(100), deleted int default 0)");
    jdbc.execute("create table question(id varchar(64) primary key, teacher_id varchar(64), subject varchar(100), knowledge_point varchar(100), difficulty varchar(20), type varchar(20), title varchar(100), explanation varchar(5000), options_json varchar(2000), answer_json varchar(2000), score int, source_tag varchar(100), deleted int default 0)");
    jdbc.execute("create table exam_snapshot(exam_id varchar(64) primary key references exam(id), content_json varchar(20000) not null, created_at timestamp default current_timestamp)");
    jdbc.execute("create table department(id varchar(64),name varchar(100))");
    jdbc.execute("create table class_info(id varchar(64),name varchar(100),major varchar(100),department_id varchar(64))");
    jdbc.execute("create table user_account(id varchar(64),role varchar(20),username varchar(100),name varchar(100),department_id varchar(64),class_id varchar(64),major varchar(100))");
    jdbc.execute("create table submission(id varchar(64),exam_id varchar(64),updated_at timestamp)");
    jdbc.execute("create table wrong_book_entry(id varchar(64))");
    jdbc.execute("create table system_log(id varchar(64),actor_id varchar(64),action varchar(100),detail varchar(100),time timestamp)");
    jdbc.execute("create table question_backup(id varchar(64),teacher_id varchar(64),questions_json varchar(2000),question_count int,created_at timestamp)");
    jdbc.execute("create table notification(id varchar(64),created_at timestamp)");
    jdbc.update("insert into paper(id,teacher_id,name,duration_minutes,total_score,pass_score,question_ids_json) values('p1','t1','Paper',30,5,3,'[\"q1\"]')");
    jdbc.update("insert into question(id,teacher_id,title,type,options_json,answer_json,score) values('q1','t1','Original','single','[\"A\",\"B\"]','[\"A\"]',5)");
  }

  @AfterEach void tearDown() {
    jdbc.execute("drop all objects");
    context.close();
  }

  private Map<String, Object> exam(String id, boolean published) {
    return Map.of("id", id, "teacherId", "t1", "paperId", "p1", "name", "Exam", "published", published,
        "targetClassIds", List.of("c1"), "startTime", "2026-10-08T00:00:00Z", "endTime", "2026-10-08T02:00:00Z");
  }

  @Test
  void firstPublishPersistsVersionThatLaterBankEditsAndRepublishingCannotRewrite() throws Exception {
    store.saveRecord("exams", exam("e1", false));
    assertEquals(0, jdbc.queryForObject("select count(*) from exam_snapshot", Integer.class));
    store.saveRecord("exams", exam("e1", true));
    String original = jdbc.queryForObject("select content_json from exam_snapshot where exam_id='e1'", String.class);
    assertEquals("A", new ObjectMapper().readTree(original).at("/questions/0/answer/0").asText());
    jdbc.update("update question set title='Changed',answer_json='[\"B\"]',score=100 where id='q1'");
    jdbc.update("update paper set duration_minutes=120,total_score=100 where id='p1'");
    store.saveRecord("exams", exam("e1", false));
    store.saveRecord("exams", exam("e1", true));
    assertEquals(original, jdbc.queryForObject("select content_json from exam_snapshot where exam_id='e1'", String.class));
    assertEquals(1, jdbc.queryForObject("select count(*) from exam_snapshot", Integer.class));
  }

  @Test
  void reloadedApplicationReadsFrozenVersionInsteadOfChangedDatabaseRows() {
    store.saveRecord("exams", exam("e1", true));
    jdbc.update("update question set title='Changed',answer_json='[\"B\"]',score=100 where id='q1'");
    jdbc.update("update paper set duration_minutes=120,total_score=100 where id='p1'");
    StoreService reloaded = new StoreService(jdbc, new ObjectMapper());
    StoreService.Store data = reloaded.readStore();
    var service = new com.onlineexam.service.ExamService(reloaded,
        org.mockito.Mockito.mock(com.onlineexam.service.SystemLogService.class));
    var response = service.buildExamSnapshot(data, data.exams.getFirst(), false, null, null);
    assertEquals(30, response.get("durationMinutes"));
    assertEquals(5, response.get("totalScore"));
    var question = (Map<?, ?>) ((List<?>) response.get("questions")).getFirst();
    assertEquals("Original", question.get("title"));
    assertEquals(List.of("A"), question.get("answer"));
  }

  @Test
  void concurrentDuplicatePublicationCreatesExactlyOneVersion() throws Exception {
    store.saveRecord("exams", exam("e1", false));
    var ready = new java.util.concurrent.CountDownLatch(2);
    var go = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      List<java.util.concurrent.Future<?>> results = new java.util.ArrayList<>();
      for (int i = 0; i < 2; i++) {
        results.add(executor.submit(() -> {
          ready.countDown();
          try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
          store.saveRecord("exams", exam("e1", true));
        }));
      }
      assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
      go.countDown();
      for (var result : results) result.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertEquals(1, jdbc.queryForObject("select count(*) from exam_snapshot", Integer.class));
  }

  @Test
  void snapshotInsertFailureRollsBackExamPublication() {
    jdbc.execute("alter table exam_snapshot add constraint reject_snapshot check(exam_id <> 'blocked')");
    store.saveRecord("exams", exam("blocked", false));
    assertThrows(RuntimeException.class, () -> store.saveRecord("exams", exam("blocked", true)));
    assertFalse(jdbc.queryForObject("select published from exam where id='blocked'", Boolean.class));
    assertEquals(0, jdbc.queryForObject("select count(*) from exam_snapshot", Integer.class));
  }

  @Test
  void withdrawnHistoricalExamWithSubmissionsCannotBeReSnapshottedFromCurrentBank() {
    store.saveRecord("exams", exam("legacy", false));
    jdbc.update("insert into submission(id,exam_id) values('s1','legacy')");
    assertThrows(RuntimeException.class, () -> store.saveRecord("exams", exam("legacy", true)));
    assertFalse(jdbc.queryForObject("select published from exam where id='legacy'", Boolean.class));
    assertEquals(0, jdbc.queryForObject("select count(*) from exam_snapshot", Integer.class));
  }

  @Test
  void importedExplanationSurvivesDatabaseAndFrozenVersionButNotStudentResponse() {
    jdbc.update("insert into user_account(id,role,username,name) values('t1','teacher','teacher','Teacher')");
    var ai = new com.onlineexam.service.AiService(store,
        org.mockito.Mockito.mock(com.onlineexam.service.SystemLogService.class),
        org.mockito.Mockito.mock(org.springframework.web.client.RestTemplate.class), new ObjectMapper(), Runnable::run,
        org.mockito.Mockito.mock(com.onlineexam.service.AiCircuitBreaker.class));
    String explanation = "【答案】B 【解析】This is private until grading.";
    var candidate = Map.<String, Object>of("title", "Generated", "type", "single", "subject", "Math",
        "options", List.of("A. one", "B. two"), "answer", List.of("B"), "score", 5, "explanation", explanation);
    var imported = ai.importQuestions("t1", Map.of("questions", List.of(candidate)));
    assertEquals(1, ((Map<?, ?>) imported.getBody()).get("importedCount"));
    String id = jdbc.queryForObject("select id from question where id<>'q1'", String.class);
    assertEquals(explanation, jdbc.queryForObject("select explanation from question where id=?", String.class, id));
    jdbc.update("update paper set question_ids_json=? where id='p1'", "[\"" + id + "\"]");
    store.saveRecord("exams", exam("e1", true));
    var data = new StoreService(jdbc, new ObjectMapper()).readStore();
    var reader = new com.onlineexam.service.ExamService(store,
        org.mockito.Mockito.mock(com.onlineexam.service.SystemLogService.class));
    var teacher = reader.buildExamSnapshot(data, data.exams.getFirst(), false, null, null);
    var teacherQuestion = (Map<?, ?>) ((List<?>) teacher.get("questions")).getFirst();
    assertEquals(explanation, teacherQuestion.get("explanation"));
    var student = reader.buildExamSnapshot(data, data.exams.getFirst(), true, null, null);
    var studentQuestion = (Map<?, ?>) ((List<?>) student.get("questions")).getFirst();
    assertFalse(studentQuestion.containsKey("explanation"));
    assertFalse(studentQuestion.containsKey("answer"));
  }

  @Test
  void missingQuestionLeavesDraftUnpublished() {
    store.saveRecord("exams", exam("e1", false));
    jdbc.update("update question set deleted=1 where id='q1'");
    assertThrows(RuntimeException.class, () -> store.saveRecord("exams", exam("e1", true)));
    assertFalse(jdbc.queryForObject("select published from exam where id='e1'", Boolean.class));
    assertEquals(0, jdbc.queryForObject("select count(*) from exam_snapshot", Integer.class));
  }
}
