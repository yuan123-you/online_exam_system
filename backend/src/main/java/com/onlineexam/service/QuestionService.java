package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.repository.QuestionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 题目服务 - 处理题目分页、批量导入/删除
 */
@Service
public class QuestionService {

  private final StoreService storeService;
  private final EntityCrudService entityCrudService;
  private final SystemLogService systemLogService;
  private final QuestionRepository questionRepository;

  public QuestionService(StoreService storeService, EntityCrudService entityCrudService,
                         SystemLogService systemLogService, QuestionRepository questionRepository) {
    this.storeService = storeService;
    this.entityCrudService = entityCrudService;
    this.systemLogService = systemLogService;
    this.questionRepository = questionRepository;
  }

  /**
   * 批量导入题目
   */
  public ResponseEntity<?> importQuestions(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Object> records = asList(body.get("records"));
    List<Map<String, Object>> created = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    long existingCount = store.questions.stream().filter(q -> Objects.equals(str(q, "teacherId"), userId)).count();
    int importIndex = 0;
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> record = new LinkedHashMap<>(asMap(records.get(i)));
      record.put("id", createId("question"));
      record.put("teacherId", userId);
      if (existingCount + importIndex >= 5000) {
        errors.add(mapOf("index", i, "title", str(record, "title"), "message", "题库数量已达上限（5000题）"));
        continue;
      }
      String validation = entityCrudService.validate(store, "questions", record, null);
      if (!validation.isBlank()) {
        errors.add(mapOf("index", i, "title", str(record, "title"), "message", validation));
      } else {
        storeService.saveRecord("questions", record);
        store.questions.add(record);
        created.add(record);
        importIndex++;
      }
    }
    if (!created.isEmpty()) systemLogService.log(user, "batch import questions", String.valueOf(created.size()));
    return ResponseEntity.ok(mapOf("importedCount", created.size(), "failedCount", errors.size(), "created", created, "errors", errors));
  }

  /**
   * 批量删除题目 (soft delete - sets deleted=1)
   */
  public ResponseEntity<?> deleteQuestions(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<String> deleted = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    for (Object raw : asList(body.get("ids"))) {
      String id = String.valueOf(raw);
      Map<String, Object> q = find(store.questions, id);
      if (q == null || !Objects.equals(str(q, "teacherId"), userId)) {
        errors.add(mapOf("id", id, "message", "Question not found."));
        continue;
      }
      String block = entityCrudService.deleteBlocker(store, "questions", id);
      if (!block.isBlank()) {
        errors.add(mapOf("id", id, "message", block));
        continue;
      }
      storeService.deleteRecord("questions", id);
      deleted.add(id);
    }
    if (!deleted.isEmpty()) systemLogService.log(user, "batch delete questions", String.valueOf(deleted.size()));
    return ResponseEntity.ok(mapOf("deletedCount", deleted.size(), "failedCount", errors.size(), "deletedIds", deleted, "errors", errors));
  }

  /**
   * 批量恢复已删除的题目 (restore soft-deleted questions)
   */
  public ResponseEntity<?> restoreQuestions(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    // Read all questions including deleted ones to find the ones to restore
    List<String> restored = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    for (Object raw : asList(body.get("ids"))) {
      String id = String.valueOf(raw);
      // Check ownership: first check active questions in store, then check DB for soft-deleted
      Map<String, Object> q = store.questions.stream()
        .filter(row -> Objects.equals(str(row, "id"), id))
        .findFirst().orElse(null);
      if (q != null) {
        // Found in active questions - verify ownership
        if (!Objects.equals(str(q, "teacherId"), userId)) {
          errors.add(mapOf("id", id, "message", "Question not owned by you."));
          continue;
        }
      } else {
        // Not in active questions - check DB for soft-deleted question ownership
        String teacherId = questionRepository.findTeacherIdIncludingDeleted(id);
        if (teacherId == null) {
          errors.add(mapOf("id", id, "message", "Question not found."));
          continue;
        }
        if (!Objects.equals(teacherId, userId)) {
          errors.add(mapOf("id", id, "message", "Question not owned by you."));
          continue;
        }
      }
      storeService.restoreRecord("questions", id);
      restored.add(id);
    }
    if (!restored.isEmpty()) systemLogService.log(user, "batch restore questions", String.valueOf(restored.size()));
    return ResponseEntity.ok(mapOf("restoredCount", restored.size(), "failedCount", errors.size(), "restoredIds", restored, "errors", errors));
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object raw) {
    return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private List<Object> asList(Object raw) {
    return raw instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }

  private String createId(String prefix) {
    return prefix + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
  }
}
