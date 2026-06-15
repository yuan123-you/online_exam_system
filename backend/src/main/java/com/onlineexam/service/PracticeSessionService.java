package com.onlineexam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 练习会话持久化服务 — 管理练习会话和题目答题状态的完整生命周期
 *
 * 核心功能：
 * 1. 自动保存带时间戳和唯一标识符的题目生成事件
 * 2. 实时记录用户答题提交情况
 * 3. 页面刷新时恢复完整的用户练习会话状态
 * 4. 并发会话期间保持数据完整性
 */
@Service
public class PracticeSessionService {

  private static final Logger log = LoggerFactory.getLogger(PracticeSessionService.class);
  private static final int MAX_SESSIONS_PER_USER = 50;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public PracticeSessionService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void initTables() {
    try {
      jdbc.execute(
        "CREATE TABLE IF NOT EXISTS practice_session (" +
        "id VARCHAR(64) PRIMARY KEY, " +
        "user_id VARCHAR(64) NOT NULL, " +
        "conversation_id VARCHAR(64), " +
        "subject VARCHAR(100), " +
        "question_count INT NOT NULL DEFAULT 0, " +
        "correct_count INT NOT NULL DEFAULT 0, " +
        "total_score INT NOT NULL DEFAULT 0, " +
        "earned_score INT NOT NULL DEFAULT 0, " +
        "status VARCHAR(20) NOT NULL DEFAULT 'active', " +
        "created_at DATETIME(3) NOT NULL, " +
        "updated_at DATETIME(3) NOT NULL, " +
        "submitted_at DATETIME(3), " +
        "INDEX idx_ps_user (user_id), " +
        "INDEX idx_ps_status (status), " +
        "INDEX idx_ps_updated (updated_at)" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
      );
      jdbc.execute(
        "CREATE TABLE IF NOT EXISTS practice_question (" +
        "id VARCHAR(64) PRIMARY KEY, " +
        "session_id VARCHAR(64) NOT NULL, " +
        "user_id VARCHAR(64) NOT NULL, " +
        "question_index INT NOT NULL DEFAULT 0, " +
        "question_data JSON NOT NULL, " +
        "user_answer_json JSON, " +
        "is_correct TINYINT(1), " +
        "is_submitted TINYINT(1) NOT NULL DEFAULT 0, " +
        "created_at DATETIME(3) NOT NULL, " +
        "updated_at DATETIME(3) NOT NULL, " +
        "submitted_at DATETIME(3), " +
        "INDEX idx_pq_session (session_id), " +
        "INDEX idx_pq_user (user_id)" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
      );
    } catch (Exception e) {
      log.error("Failed to initialize tables: {}", e.getMessage());
    }
  }

  // ========== 会话管理 ==========

  /** 创建新的练习会话 */
  @Transactional
  public ResponseEntity<?> createSession(String userId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    String subject = str(body, "subject");
    String conversationId = str(body, "conversationId");
    String id = "ps-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
    String nowStr = now();

    // 数据验证
    if (subject.length() > 100) subject = subject.substring(0, 100);

    try {
      jdbc.update(
        "INSERT INTO practice_session (id, user_id, conversation_id, subject, question_count, correct_count, total_score, earned_score, status, created_at, updated_at) VALUES (?,?,?,?,?,0,0,0,'active',?,?)",
        id, userId, conversationId.isBlank() ? null : conversationId, subject, 0, nowStr, nowStr
      );
      enforceSessionLimit(userId);
    } catch (Exception e) {
      log.error("Failed to create session: {}", e.getMessage());
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create practice session.");
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("userId", userId);
    result.put("conversationId", conversationId);
    result.put("subject", subject);
    result.put("questionCount", 0);
    result.put("status", "active");
    result.put("createdAt", nowStr);
    result.put("updatedAt", nowStr);
    return ResponseEntity.ok(result);
  }

  /** 获取用户的练习会话列表 */
  public ResponseEntity<?> listSessions(String userId) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, user_id, conversation_id, subject, question_count, correct_count, total_score, earned_score, status, created_at, updated_at, submitted_at FROM practice_session WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?",
        userId, MAX_SESSIONS_PER_USER
      );

      List<Map<String, Object>> sessions = new ArrayList<>();
      for (Map<String, Object> row : rows) {
        sessions.add(mapOf(
          "id", str(row.get("id")),
          "userId", str(row.get("user_id")),
          "conversationId", str(row.get("conversation_id")),
          "subject", str(row.get("subject")),
          "questionCount", asInt(row.get("question_count")),
          "correctCount", asInt(row.get("correct_count")),
          "totalScore", asInt(row.get("total_score")),
          "earnedScore", asInt(row.get("earned_score")),
          "status", str(row.get("status")),
          "createdAt", str(row.get("created_at")),
          "updatedAt", str(row.get("updated_at")),
          "submittedAt", str(row.get("submitted_at"))
        ));
      }
      return ResponseEntity.ok(mapOf("sessions", sessions));
    } catch (Exception e) {
      log.error("Failed to list sessions: {}", e.getMessage());
      return ResponseEntity.ok(mapOf("sessions", List.of()));
    }
  }

  /** 获取指定练习会话的完整状态（含所有题目和答题情况） */
  public ResponseEntity<?> getSession(String userId, String sessionId) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    try {
      List<Map<String, Object>> sessionRows = jdbc.queryForList(
        "SELECT id, user_id, conversation_id, subject, question_count, correct_count, total_score, earned_score, status, created_at, updated_at, submitted_at FROM practice_session WHERE id = ? AND user_id = ?",
        sessionId, userId
      );
      if (sessionRows.isEmpty()) return error(HttpStatus.NOT_FOUND, "Practice session not found.");

      Map<String, Object> sessionRow = sessionRows.get(0);
      Map<String, Object> session = new LinkedHashMap<>();
      session.put("id", str(sessionRow.get("id")));
      session.put("userId", str(sessionRow.get("user_id")));
      session.put("conversationId", str(sessionRow.get("conversation_id")));
      session.put("subject", str(sessionRow.get("subject")));
      session.put("questionCount", asInt(sessionRow.get("question_count")));
      session.put("correctCount", asInt(sessionRow.get("correct_count")));
      session.put("totalScore", asInt(sessionRow.get("total_score")));
      session.put("earnedScore", asInt(sessionRow.get("earned_score")));
      session.put("status", str(sessionRow.get("status")));
      session.put("createdAt", str(sessionRow.get("created_at")));
      session.put("updatedAt", str(sessionRow.get("updated_at")));
      session.put("submittedAt", str(sessionRow.get("submitted_at")));

      // 加载所有题目及答题状态
      List<Map<String, Object>> questionRows = jdbc.queryForList(
        "SELECT id, session_id, question_index, question_data, user_answer_json, is_correct, is_submitted, created_at, updated_at, submitted_at FROM practice_question WHERE session_id = ? ORDER BY question_index ASC",
        sessionId
      );

      List<Map<String, Object>> questions = new ArrayList<>();
      for (Map<String, Object> qRow : questionRows) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", str(qRow.get("id")));
        q.put("sessionId", str(qRow.get("session_id")));
        q.put("questionIndex", asInt(qRow.get("question_index")));
        // 解析 question_data JSON
        String questionDataJson = str(qRow.get("question_data"));
        try {
          q.put("questionData", objectMapper.readValue(questionDataJson, Map.class));
        } catch (JsonProcessingException e) {
          q.put("questionData", mapOf());
        }
        // 解析 user_answer_json
        String userAnswerJson = str(qRow.get("user_answer_json"));
        if (!userAnswerJson.isBlank()) {
          try {
            q.put("userAnswer", objectMapper.readValue(userAnswerJson, List.class));
          } catch (JsonProcessingException e) {
            q.put("userAnswer", List.of());
          }
        } else {
          q.put("userAnswer", null);
        }
        Object isCorrectObj = qRow.get("is_correct");
        q.put("isCorrect", isCorrectObj != null ? ((Number) isCorrectObj).intValue() == 1 : null);
        Object isSubmittedObj = qRow.get("is_submitted");
        q.put("isSubmitted", isSubmittedObj != null && ((Number) isSubmittedObj).intValue() == 1);
        q.put("createdAt", str(qRow.get("created_at")));
        q.put("updatedAt", str(qRow.get("updated_at")));
        q.put("submittedAt", str(qRow.get("submitted_at")));
        questions.add(q);
      }
      session.put("questions", questions);

      return ResponseEntity.ok(session);
    } catch (Exception e) {
      log.error("Failed to get session: {}", e.getMessage());
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load practice session.");
    }
  }

  /** 获取用户当前活跃的练习会话（用于页面刷新恢复） */
  public ResponseEntity<?> getActiveSession(String userId) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id FROM practice_session WHERE user_id = ? AND status = 'active' ORDER BY updated_at DESC LIMIT 1",
        userId
      );
      if (rows.isEmpty()) {
        return ResponseEntity.ok(mapOf("session", null));
      }
      // 返回完整的会话数据
      return getSession(userId, str(rows.get(0).get("id")));
    } catch (Exception e) {
      log.error("Failed to get active session: {}", e.getMessage());
      return ResponseEntity.ok(mapOf("session", null));
    }
  }

  // ========== 题目管理 ==========

  /** 保存/更新练习会话的题目列表（题目生成事件自动保存） */
  @Transactional
  public ResponseEntity<?> saveQuestions(String userId, String sessionId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    // 验证会话归属
    if (!verifySessionOwnership(userId, sessionId)) {
      return error(HttpStatus.NOT_FOUND, "Practice session not found.");
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> questions = (List<Map<String, Object>>) body.get("questions");
    if (questions == null || questions.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "No questions provided.");
    }

    String nowStr = now();
    int savedCount = 0;

    // 先删除该会话下已有的题目（支持重新生成）
    try {
      jdbc.update("DELETE FROM practice_question WHERE session_id = ?", sessionId);
    } catch (Exception e) {
      log.error("Failed to clear old questions: {}", e.getMessage());
    }

    for (int i = 0; i < questions.size(); i++) {
      Map<String, Object> q = questions.get(i);
      String qId = "pq-" + System.currentTimeMillis() + "-" + i + "-" + Integer.toHexString((int) (Math.random() * 0xffff));

      // 数据验证：确保必要字段存在
      Map<String, Object> questionData = new LinkedHashMap<>(q);
      if (!questionData.containsKey("title") || str(questionData, "title").isBlank()) {
        continue; // 跳过无标题的题目
      }
      if (!questionData.containsKey("type") || str(questionData, "type").isBlank()) {
        questionData.put("type", "single");
      }
      if (!questionData.containsKey("score")) {
        questionData.put("score", 5);
      }

      String questionDataJson;
      try {
        questionDataJson = objectMapper.writeValueAsString(questionData);
      } catch (JsonProcessingException e) {
        continue; // 跳过无法序列化的题目
      }

      try {
        jdbc.update(
          "INSERT INTO practice_question (id, session_id, user_id, question_index, question_data, is_submitted, created_at, updated_at) VALUES (?,?,?,?,?,0,?,?)",
          qId, sessionId, userId, i, questionDataJson, nowStr, nowStr
        );
        savedCount++;
      } catch (Exception e) {
        log.error("Failed to save question {}: {}", i, e.getMessage());
      }
    }

    // 更新会话的题目数量和时间戳
    try {
      jdbc.update(
        "UPDATE practice_session SET question_count = ?, updated_at = ? WHERE id = ?",
        savedCount, nowStr, sessionId
      );
    } catch (Exception e) {
      log.error("Failed to update session: {}", e.getMessage());
    }

    return ResponseEntity.ok(mapOf("savedCount", savedCount, "sessionId", sessionId));
  }

  /** 保存用户对某道题的答案（实时保存，支持增量更新） */
  @Transactional
  public ResponseEntity<?> saveAnswer(String userId, String questionId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    // 验证题目归属
    if (!verifyQuestionOwnership(userId, questionId)) {
      return error(HttpStatus.NOT_FOUND, "Practice question not found.");
    }

    @SuppressWarnings("unchecked")
    List<Object> answer = (List<Object>) body.get("answer");
    if (answer == null) {
      // 也支持字符串答案（填空题/简答题）
      Object answerObj = body.get("answer");
      if (answerObj instanceof String && !((String) answerObj).isBlank()) {
        answer = List.of(answerObj);
      } else {
        return error(HttpStatus.BAD_REQUEST, "No answer provided.");
      }
    }

    String answerJson;
    try {
      answerJson = objectMapper.writeValueAsString(answer);
    } catch (JsonProcessingException e) {
      return error(HttpStatus.BAD_REQUEST, "Invalid answer format.");
    }

    String nowStr = now();
    try {
      jdbc.update(
        "UPDATE practice_question SET user_answer_json = ?, updated_at = ? WHERE id = ? AND user_id = ?",
        answerJson, nowStr, questionId, userId
      );
      // 同时更新会话的 updated_at
      String sessionId = jdbc.queryForObject(
        "SELECT session_id FROM practice_question WHERE id = ? AND user_id = ?", String.class, questionId, userId
      );
      if (sessionId != null) {
        jdbc.update("UPDATE practice_session SET updated_at = ? WHERE id = ?", nowStr, sessionId);
      }
    } catch (Exception e) {
      log.error("Failed to save answer: {}", e.getMessage());
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save answer.");
    }

    return ResponseEntity.ok(mapOf("saved", true, "questionId", questionId));
  }

  /** 批量保存用户答案（减少请求次数） */
  @Transactional
  public ResponseEntity<?> saveAnswers(String userId, String sessionId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    if (!verifySessionOwnership(userId, sessionId)) {
      return error(HttpStatus.NOT_FOUND, "Practice session not found.");
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> answers = (List<Map<String, Object>>) body.get("answers");
    if (answers == null || answers.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "No answers provided.");
    }

    String nowStr = now();
    int savedCount = 0;

    for (Map<String, Object> item : answers) {
      int questionIndex = asInt(item.get("questionIndex"));
      @SuppressWarnings("unchecked")
      List<Object> answer = (List<Object>) item.get("answer");
      if (answer == null) {
        Object answerObj = item.get("answer");
        if (answerObj instanceof String && !((String) answerObj).isBlank()) {
          answer = List.of(answerObj);
        } else {
          continue;
        }
      }

      String answerJson;
      try {
        answerJson = objectMapper.writeValueAsString(answer);
      } catch (JsonProcessingException e) {
        continue;
      }

      try {
        int updated = jdbc.update(
          "UPDATE practice_question SET user_answer_json = ?, updated_at = ? WHERE session_id = ? AND user_id = ? AND question_index = ?",
          answerJson, nowStr, sessionId, userId, questionIndex
        );
        if (updated > 0) savedCount++;
      } catch (Exception e) {
        log.error("Failed to save answer for question {}: {}", questionIndex, e.getMessage());
      }
    }

    // 更新会话时间戳
    try {
      jdbc.update("UPDATE practice_session SET updated_at = ? WHERE id = ?", nowStr, sessionId);
    } catch (Exception e) {
      // non-critical
    }

    return ResponseEntity.ok(mapOf("savedCount", savedCount));
  }

  /** 提交整个练习会话的所有答案 */
  @Transactional
  public ResponseEntity<?> submitSession(String userId, String sessionId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    if (!verifySessionOwnership(userId, sessionId)) {
      return error(HttpStatus.NOT_FOUND, "Practice session not found.");
    }

    String nowStr = now();

    // 获取所有题目
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> answerEntries = (List<Map<String, Object>>) body.get("answers");
    int correctCount = 0;
    int totalScore = 0;
    int earnedScore = 0;

    try {
      List<Map<String, Object>> questionRows = jdbc.queryForList(
        "SELECT id, question_index, question_data, user_answer_json FROM practice_question WHERE session_id = ? AND user_id = ? ORDER BY question_index ASC",
        sessionId, userId
      );

      for (Map<String, Object> qRow : questionRows) {
        String questionDataJson = str(qRow.get("question_data"));
        String userAnswerJson = str(qRow.get("user_answer_json"));
        int qIndex = asInt(qRow.get("question_index"));

        Map<String, Object> questionData;
        try {
          questionData = objectMapper.readValue(questionDataJson, Map.class);
        } catch (JsonProcessingException e) {
          continue;
        }

        int score = asInt(questionData.get("score"));
        if (score <= 0) score = 5;
        totalScore += score;

        // 比对答案
        List<Object> correctAnswer = asList(questionData.get("answer"));
        List<Object> userAnswer = List.of();
        if (!userAnswerJson.isBlank()) {
          try {
            userAnswer = objectMapper.readValue(userAnswerJson, List.class);
          } catch (JsonProcessingException e) {
            // keep empty
          }
        }

        // 也从 body 中获取最新答案（可能尚未保存到数据库）
        if (answerEntries != null) {
          for (Map<String, Object> entry : answerEntries) {
            if (asInt(entry.get("questionIndex")) == qIndex) {
              Object ans = entry.get("answer");
              if (ans instanceof List) {
                userAnswer = (List<Object>) ans;
              }
              break;
            }
          }
        }

        boolean isCorrect = compareAnswers(userAnswer, correctAnswer, str(questionData, "type"));
        if (isCorrect) {
          correctCount++;
          earnedScore += score;
        }

        // 更新题目的提交状态
        String finalAnswerJson;
        try {
          finalAnswerJson = objectMapper.writeValueAsString(userAnswer);
        } catch (JsonProcessingException e) {
          finalAnswerJson = "[]";
        }

        jdbc.update(
          "UPDATE practice_question SET user_answer_json = ?, is_correct = ?, is_submitted = 1, submitted_at = ?, updated_at = ? WHERE id = ?",
          finalAnswerJson, isCorrect ? 1 : 0, nowStr, nowStr, str(qRow.get("id"))
        );
      }

      // 更新会话状态
      jdbc.update(
        "UPDATE practice_session SET status = 'submitted', correct_count = ?, total_score = ?, earned_score = ?, submitted_at = ?, updated_at = ? WHERE id = ?",
        correctCount, totalScore, earnedScore, nowStr, nowStr, sessionId
      );

    } catch (Exception e) {
      log.error("Failed to submit session: {}", e.getMessage());
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to submit practice session.");
    }

    return ResponseEntity.ok(mapOf(
      "sessionId", sessionId,
      "correctCount", correctCount,
      "totalScore", totalScore,
      "earnedScore", earnedScore,
      "status", "submitted"
    ));
  }

  /** 删除练习会话及其所有题目 */
  @Transactional
  public ResponseEntity<?> deleteSession(String userId, String sessionId) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    try {
      int deleted = jdbc.update("DELETE FROM practice_session WHERE id = ? AND user_id = ?", sessionId, userId);
      if (deleted == 0) return error(HttpStatus.NOT_FOUND, "Practice session not found.");
      jdbc.update("DELETE FROM practice_question WHERE session_id = ?", sessionId);
    } catch (Exception e) {
      // Table may not exist yet
    }
    return ResponseEntity.ok(mapOf("deleted", true));
  }

  // ========== 辅助方法 ==========

  private boolean verifySessionOwnership(String userId, String sessionId) {
    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id FROM practice_session WHERE id = ? AND user_id = ?", sessionId, userId
      );
      return !rows.isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  private boolean verifyQuestionOwnership(String userId, String questionId) {
    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id FROM practice_question WHERE id = ? AND user_id = ?", questionId, userId
      );
      return !rows.isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  /** 比较用户答案和正确答案 */
  private boolean compareAnswers(List<Object> userAnswer, List<Object> correctAnswer, String type) {
    if (userAnswer == null || correctAnswer == null) return false;
    if (userAnswer.isEmpty() && correctAnswer.isEmpty()) return true;
    if (userAnswer.isEmpty()) return false;

    // 选择题/判断题：忽略大小写和格式，只比较字母
    if ("single".equals(type) || "multiple".equals(type) || "judge".equals(type)) {
      List<String> userLetters = extractLetters(userAnswer);
      List<String> correctLetters = extractLetters(correctAnswer);
      if (userLetters.size() != correctLetters.size()) return false;
      userLetters.sort(String::compareTo);
      correctLetters.sort(String::compareTo);
      return userLetters.equals(correctLetters);
    }

    // 填空题/简答题：忽略首尾空白后比较
    List<String> userNorm = userAnswer.stream().map(a -> String.valueOf(a).trim()).filter(s -> !s.isEmpty()).sorted().toList();
    List<String> correctNorm = correctAnswer.stream().map(a -> String.valueOf(a).trim()).filter(s -> !s.isEmpty()).sorted().toList();
    return userNorm.equals(correctNorm);
  }

  private List<String> extractLetters(List<Object> answers) {
    List<String> letters = new ArrayList<>();
    for (Object a : answers) {
      String s = String.valueOf(a);
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-D])").matcher(s);
      if (m.find()) letters.add(m.group(1));
    }
    return letters;
  }

  private void enforceSessionLimit(String userId) {
    try {
      Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM practice_session WHERE user_id = ?", Integer.class, userId
      );
      if (count != null && count > MAX_SESSIONS_PER_USER) {
        int excess = count - MAX_SESSIONS_PER_USER;
        List<String> toDelete = jdbc.queryForList(
          "SELECT id FROM practice_session WHERE user_id = ? ORDER BY updated_at ASC LIMIT ?",
          String.class, userId, excess
        );
        for (String id : toDelete) {
          jdbc.update("DELETE FROM practice_question WHERE session_id = ?", id);
          jdbc.update("DELETE FROM practice_session WHERE id = ?", id);
        }
      }
    } catch (Exception e) {
      // non-critical
    }
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private String now() {
    return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }

  private List<Object> asList(Object raw) {
    if (raw instanceof List<?> list) return new ArrayList<>(list);
    return new ArrayList<>();
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
