package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PracticeSessionIntegrityTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private PracticeSessionService service;

  @Configuration
  @EnableTransactionManagement
  static class Config {
    @Bean DataSource dataSource() {
      return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
          + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }
    @Bean JdbcTemplate jdbc(DataSource dataSource) {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      jdbc.execute("CREATE TABLE practice_session (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64), conversation_id VARCHAR(64), subject VARCHAR(100), question_count INT DEFAULT 0, correct_count INT DEFAULT 0, total_score INT DEFAULT 0, earned_score INT DEFAULT 0, status VARCHAR(20) DEFAULT 'active', created_at VARCHAR(40), updated_at VARCHAR(40), submitted_at VARCHAR(40))");
      jdbc.execute("CREATE TABLE practice_question (id VARCHAR(64) PRIMARY KEY, session_id VARCHAR(64), user_id VARCHAR(64), question_index INT, question_data VARCHAR(16000), user_answer_json VARCHAR(16000), is_correct INT, is_submitted INT DEFAULT 0, created_at VARCHAR(40), updated_at VARCHAR(40), submitted_at VARCHAR(40))");
      return spy(jdbc);
    }
    @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }
    @Bean PracticeSessionService service(JdbcTemplate jdbc) {
      return new PracticeSessionService(jdbc, new ObjectMapper());
    }
  }

  @BeforeEach void setUp() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = context.getBean(JdbcTemplate.class);
    service = context.getBean(PracticeSessionService.class);
    jdbc.update("INSERT INTO practice_session (id,user_id,question_count,status) VALUES ('s1','u1',1,'active')");
    jdbc.update("INSERT INTO practice_question (id,session_id,user_id,question_index,question_data,user_answer_json) VALUES ('q0','s1','u1',0,?,?)",
        "{\"title\":\"Original\",\"type\":\"fill\",\"answer\":[\"correct\"],\"score\":5}", "[]");
  }

  @AfterEach void tearDown() { if (context != null) context.close(); }

  @Test void savesStringAnswerAsAJsonArray() {
    assertEquals(200, service.saveAnswer("u1", "q0", Map.of("answer", "correct")).getStatusCode().value());
    assertEquals("[\"correct\"]", savedAnswer());
  }

  @Test void batchSavesStringAnswerAsAJsonArray() {
    assertEquals(200, service.saveAnswers("u1", "s1", Map.of("answers",
        List.of(Map.of("questionIndex", 0, "answer", "correct")))).getStatusCode().value());
    assertEquals("[\"correct\"]", savedAnswer());
  }

  @Test void submissionGradesTheLatestStringAnswer() {
    var response = service.submitSession("u1", "s1", Map.of("answers",
        List.of(Map.of("questionIndex", 0, "answer", "correct"))));
    assertEquals(200, response.getStatusCode().value());
    assertEquals(5, ((Map<?, ?>) response.getBody()).get("earnedScore"));
    assertEquals("[\"correct\"]", savedAnswer());
  }

  @Test void failedQuestionReplacementRestoresOriginalQuestions() {
    var inserts = new AtomicInteger();
    doAnswer(invocation -> {
      if (inserts.incrementAndGet() == 2) throw new DataIntegrityViolationException("injected insert failure");
      return invocation.callRealMethod();
    }).when(jdbc).update(startsWith("INSERT INTO practice_question"), any(Object[].class));
    assertThrows(DataAccessException.class, () -> service.saveQuestions("u1", "s1", Map.of("questions",
        List.of(Map.of("title", "New first"), Map.of("title", "New second")))));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM practice_question WHERE session_id='s1'", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM practice_question WHERE id='q0'", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT question_count FROM practice_session WHERE id='s1'", Integer.class));
  }

  @Test void failedSessionUpdateRollsBackSavedAnswer() {
    failSessionUpdate();
    assertThrows(DataAccessException.class, () -> service.saveAnswer("u1", "q0", Map.of("answer", List.of("correct"))));
    assertEquals("[]", savedAnswer());
  }

  @Test void failedSessionUpdateRollsBackBatchAnswers() {
    failSessionUpdate();
    assertThrows(DataAccessException.class, () -> service.saveAnswers("u1", "s1", Map.of("answers",
        List.of(Map.of("questionIndex", 0, "answer", List.of("correct"))))));
    assertEquals("[]", savedAnswer());
  }

  @Test void failedSessionSubmissionRollsBackQuestionGrade() {
    failSessionUpdate();
    assertThrows(DataAccessException.class, () -> service.submitSession("u1", "s1", Map.of("answers",
        List.of(Map.of("questionIndex", 0, "answer", List.of("correct"))))));
    assertEquals("[]", savedAnswer());
    assertEquals(0, jdbc.queryForObject("SELECT is_submitted FROM practice_question WHERE id='q0'", Integer.class));
    assertEquals("active", jdbc.queryForObject("SELECT status FROM practice_session WHERE id='s1'", String.class));
  }

  @Test void failedQuestionDeletionRestoresTheSession() {
    doThrow(new DataIntegrityViolationException("injected delete failure")).when(jdbc)
        .update(startsWith("DELETE FROM practice_question"), any(Object[].class));
    assertThrows(DataAccessException.class, () -> service.deleteSession("u1", "s1"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM practice_session WHERE id='s1'", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM practice_question WHERE id='q0'", Integer.class));
  }

  @Test void failedReplacementMetadataUpdateRestoresOriginalQuestions() {
    failSessionUpdate();
    assertThrows(DataAccessException.class, () -> service.saveQuestions("u1", "s1", Map.of("questions",
        List.of(Map.of("title", "New first"), Map.of("title", "New second")))));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM practice_question WHERE session_id='s1'", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM practice_question WHERE id='q0'", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT question_count FROM practice_session WHERE id='s1'", Integer.class));
  }

  @Test void failedSecondBatchAnswerUpdateRestoresBothAnswers() {
    jdbc.update("INSERT INTO practice_question (id,session_id,user_id,question_index,question_data,user_answer_json) VALUES ('q1','s1','u1',1,'{}','[]')");
    var updates = new AtomicInteger();
    doAnswer(invocation -> {
      if (updates.incrementAndGet() == 2) throw new DataIntegrityViolationException("injected second answer failure");
      return invocation.callRealMethod();
    }).when(jdbc).update(startsWith("UPDATE practice_question SET user_answer_json"), any(Object[].class));
    assertThrows(DataAccessException.class, () -> service.saveAnswers("u1", "s1", Map.of("answers", List.of(
        Map.of("questionIndex", 0, "answer", List.of("first")),
        Map.of("questionIndex", 1, "answer", List.of("second"))))));
    assertEquals("[]", savedAnswer());
    assertEquals("[]", jdbc.queryForObject("SELECT user_answer_json FROM practice_question WHERE id='q1'", String.class));
  }

  @Test void otherUsersCannotChangeOrDeleteTheSession() {
    assertEquals(404, service.saveAnswer("other", "q0", Map.of("answer", "correct")).getStatusCode().value());
    assertEquals(404, service.deleteSession("other", "s1").getStatusCode().value());
    assertEquals("[]", savedAnswer());
  }

  private void failSessionUpdate() {
    doThrow(new DataIntegrityViolationException("injected session update failure")).when(jdbc)
        .update(startsWith("UPDATE practice_session"), any(Object[].class));
  }

  private String savedAnswer() {
    return jdbc.queryForObject("SELECT user_answer_json FROM practice_question WHERE id='q0'", String.class);
  }
}
