package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 错题本服务 - 处理错题本 CRUD、重做和移除
 */
@Service
public class WrongBookService {

  private static final Logger log = LoggerFactory.getLogger(WrongBookService.class);
  private static final int WRONG_BOOK_LIMIT = 1000;
  private static final int ARCHIVE_BATCH_SIZE = 50;

  private final StoreService storeService;
  private final SystemLogService systemLogService;
  private final JdbcTemplate jdbc;

  public WrongBookService(StoreService storeService, SystemLogService systemLogService, JdbcTemplate jdbc) {
    this.storeService = storeService;
    this.systemLogService = systemLogService;
    this.jdbc = jdbc;
  }

  /**
   * 启动时移除 wrong_book_entry.question_id 上的所有外键约束。
   * AI 练习产生的错题条目 questionId 不存在于 question 表，保留外键会导致
   * 重做（retry）时 INSERT ... ON DUPLICATE KEY UPDATE 失败，返回 500。
   */
  @PostConstruct
  void dropQuestionForeignKeyIfNeeded() {
    try {
      // 查询 wrong_book_entry 上所有指向 question 表的外键约束
      List<Map<String, Object>> constraints = jdbc.queryForList(
        "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS " +
        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wrong_book_entry' " +
        "AND CONSTRAINT_TYPE = 'FOREIGN KEY'"
      );
      for (Map<String, Object> row : constraints) {
        String constraintName = String.valueOf(row.get("CONSTRAINT_NAME"));
        try {
          jdbc.execute("ALTER TABLE wrong_book_entry DROP FOREIGN KEY " + constraintName);
          log.info("Dropped foreign key {} from wrong_book_entry to allow AI practice records.", constraintName);
        } catch (Exception e) {
          log.warn("Failed to drop foreign key {}: {}", constraintName, e.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("Failed to check/drop foreign keys on wrong_book_entry: {}", e.getMessage());
    }
  }

  /**
   * Auto-archive the oldest entries when wrong book quota is exceeded.
   */
  public void autoArchiveIfNeeded(Store store, String studentId) {
    List<Map<String, Object>> activeEntries = store.wrongBookEntries.stream()
      .filter(e -> Objects.equals(str(e, "studentId"), studentId)
        && !e.containsKey("removedAt")
        && !"archived".equals(str(e, "status"))
        && !"practice".equals(str(e, "status")))
      .toList();
    if (activeEntries.size() < WRONG_BOOK_LIMIT) return;

    // Sort by lastWrongAt ascending (oldest first)
    List<Map<String, Object>> sorted = new ArrayList<>(activeEntries);
    sorted.sort(Comparator.comparing(e -> {
      String t = str(e, "lastWrongAt");
      return t.isEmpty() ? "" : t;
    }));

    int archiveCount = Math.min(ARCHIVE_BATCH_SIZE, sorted.size());
    for (int i = 0; i < archiveCount; i++) {
      Map<String, Object> entry = sorted.get(i);
      entry.put("status", "archived");
      entry.put("archivedAt", Instant.now().toString());
      storeService.saveRecord("wrongBookEntries", entry);
    }
    systemLogService.log(null, "auto-archive wrong book",
      "studentId=" + studentId + ", archived=" + archiveCount);
  }

  /**
   * From submission sync wrong book
   */
  public void syncFromSubmission(Store store, Map<String, Object> submission) {
    // Auto-archive if at quota limit before syncing
    autoArchiveIfNeeded(store, str(submission, "studentId"));

    for (Object raw : asList(submission.get("answerDetail"))) {
      long wrongBookCount = store.wrongBookEntries.stream()
        .filter(e -> Objects.equals(str(e, "studentId"), str(submission, "studentId"))
          && !e.containsKey("removedAt")
          && !"archived".equals(str(e, "status"))
          && !"practice".equals(str(e, "status")))
        .count();
      if (wrongBookCount >= WRONG_BOOK_LIMIT) {
        // Try archiving again in case we freed up space
        autoArchiveIfNeeded(store, str(submission, "studentId"));
        wrongBookCount = store.wrongBookEntries.stream()
          .filter(e -> Objects.equals(str(e, "studentId"), str(submission, "studentId"))
            && !e.containsKey("removedAt")
            && !"archived".equals(str(e, "status"))
            && !"practice".equals(str(e, "status")))
          .count();
        if (wrongBookCount >= WRONG_BOOK_LIMIT) return;
      }
      Map<String, Object> detail = asMap(raw);
      int full = asInt(detail.get("fullScore"));
      if (full <= 0 || asInt(detail.get("score")) >= full) continue;
      String questionId = str(detail, "questionId");
      if (questionId.isBlank()) continue;
      Map<String, Object> existing = store.wrongBookEntries.stream()
        .filter(e -> Objects.equals(str(e, "studentId"), str(submission, "studentId")) && Objects.equals(str(e, "questionId"), questionId))
        .findFirst().orElse(null);
      Map<String, Object> question = find(store.questions, questionId);
      Map<String, Object> entry = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
      entry.putIfAbsent("id", createId("wrong"));
      entry.put("studentId", str(submission, "studentId"));
      entry.put("studentName", str(submission, "studentName"));
      entry.put("questionId", questionId);
      entry.put("subject", question == null ? str(detail, "subject") : str(question, "subject"));
      entry.put("knowledgePoint", question == null ? str(detail, "knowledgePoint") : str(question, "knowledgePoint"));
      entry.put("type", question == null ? str(detail, "type") : str(question, "type"));
      entry.put("title", question == null ? str(detail, "title") : str(question, "title"));
      entry.put("latestAnswer", asList(detail.get("answer")));
      entry.put("expectedAnswer", question == null ? asList(detail.get("expectedAnswer")) : asList(question.get("answer")));
      entry.put("fullScore", full);
      entry.put("lastScore", asInt(detail.get("score")));
      entry.put("wrongCount", asInt(entry.get("wrongCount")) + 1);
      entry.put("lastWrongAt", str(submission, "submittedAt"));
      entry.put("lastSourceSubmissionId", str(submission, "id"));
      entry.put("lastSourceExamId", str(submission, "examId"));
      entry.put("lastRetryCorrect", false);
      entry.put("removable", false);
      entry.put("status", "active");
      try {
        storeService.saveRecord("wrongBookEntries", entry);
      } catch (Exception e) {
        // 单条错题本保存失败（如外键约束、唯一键冲突等）不应阻塞其他错题的同步
        System.err.println("[wrong-book] saveRecord failed for student=" + str(submission, "studentId") + " question=" + questionId + ": " + e.getMessage());
      }
    }
  }

  /**
   * 构建错题本条目（包含题目信息）
   */
  public Map<String, Object> buildWrongBookEntry(Store store, Map<String, Object> entry) {
    Map<String, Object> question = find(store.questions, str(entry, "questionId"));
    Map<String, Object> next = new LinkedHashMap<>(entry);
    if (question != null) {
      next.put("subject", str(question, "subject"));
      next.put("knowledgePoint", str(question, "knowledgePoint"));
      next.put("type", str(question, "type"));
      next.put("title", str(question, "title"));
      next.put("expectedAnswer", asList(question.get("answer")));
      Map<String, Object> sanitized = new LinkedHashMap<>(question);
      sanitized.remove("answer");
      next.put("question", sanitized);
    }
    next.put("statusText", Boolean.TRUE.equals(entry.get("lastRetryCorrect")) ? "\u5df2\u91cd\u505a\u901a\u8fc7" : "\u5f85\u91cd\u505a");
    next.put("removable", Boolean.TRUE.equals(entry.get("lastRetryCorrect")) && !entry.containsKey("removedAt"));
    return next;
  }

  /**
   * 直接通过 JDBC 更新错题本重做结果（绕过外键约束的 fallback）。
   * 当 saveRecord 因外键约束失败时（AI 练习的 questionId 不在 question 表），
   * 使用 UPDATE 语句只更新已有记录，避免 INSERT 触发外键检查。
   */
  public void updateRetryDirect(Map<String, Object> entry) {
    String id = str(entry, "id");
    jdbc.update(
      "UPDATE wrong_book_entry SET retry_count=?, last_retry_at=?, last_retry_answer_json=?, " +
      "last_retry_correct=?, removable=?, status=?, latest_answer_json=?, wrong_count=?, last_wrong_at=? " +
      "WHERE id=?",
      asInt(entry.get("retryCount")),
      toTimestamp(entry.get("lastRetryAt")),
      toJson(entry.get("lastRetryAnswer")),
      asBool(entry.get("lastRetryCorrect")) ? 1 : 0,
      asBool(entry.get("removable")) ? 1 : 0,
      nullableStr(entry, "status"),
      toJson(entry.get("latestAnswer")),
      asInt(entry.get("wrongCount")),
      toTimestamp(entry.get("lastWrongAt")),
      id
    );
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object raw) {
    return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private List<Object> asList(Object raw) {
    return raw instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private boolean asBool(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    if (value != null) {
      try { return Integer.parseInt(String.valueOf(value)) != 0; } catch (NumberFormatException ignored) {}
    }
    return false;
  }

  private String nullableStr(Map<String, Object> map, String key) {
    String value = str(map, key);
    return value.isBlank() ? null : value;
  }

  private String toJson(Object value) {
    try {
      Object next = value == null ? List.of() : value;
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(next);
    } catch (Exception e) {
      return "[]";
    }
  }

  private java.sql.Timestamp toTimestamp(Object value) {
    if (value == null || String.valueOf(value).isBlank()) return null;
    try {
      return java.sql.Timestamp.from(java.time.Instant.parse(String.valueOf(value)));
    } catch (Exception e) {
      try {
        return java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(String.valueOf(value).replace("Z", "")));
      } catch (Exception ex) {
        return null;
      }
    }
  }

  private String createId(String prefix) {
    return prefix + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
  }
}
