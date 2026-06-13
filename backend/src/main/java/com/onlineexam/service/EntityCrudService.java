package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 实体 CRUD 服务 - 处理通用实体的创建、更新和删除
 */
@Service
public class EntityCrudService {

  private static final Set<String> SUBJECTIVE_TYPES = Set.of("short", "coding");
  private static final Set<String> OBJECTIVE_TYPES = Set.of("single", "multiple", "judge", "fill");

  private final StoreService storeService;
  private final AuthService authService;
  private final SystemLogService systemLogService;

  public EntityCrudService(StoreService storeService, AuthService authService, SystemLogService systemLogService) {
    this.storeService = storeService;
    this.authService = authService;
    this.systemLogService = systemLogService;
  }

  /**
   * 创建实体
   */
  public ResponseEntity<?> createEntity(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    String entity = str(body, "entity");
    if (!canManage(user, entity)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> record = asMap(body.get("record"));
    if (record.isEmpty()) return error(HttpStatus.BAD_REQUEST, "Record is required.");
    record = new LinkedHashMap<>(record);
    record.putIfAbsent("id", createId(entity.endsWith("s") ? entity.substring(0, entity.length() - 1) : entity));
    if ("users".equals(entity) && record.containsKey("password")) {
      String rawPw = str(record, "password");
      record.put("password", authService.hashPassword(rawPw.isBlank() ? "123456" : rawPw));
    }
    if (isRole(user, "teacher") && Set.of("questions", "papers", "exams").contains(entity)) {
      record.put("teacherId", userId);
    }
    record.put("createdBy", userId);
    if ("questions".equals(entity) && isRole(user, "teacher")) {
      long teacherQuestionCount = store.questions.stream()
        .filter(q -> Objects.equals(str(q, "teacherId"), userId)).count();
      if (teacherQuestionCount >= 5000) {
        return error(HttpStatus.BAD_REQUEST, "题库数量已达上限（5000题），无法继续添加。请删除部分题目后再操作。");
      }
    }
    String validation = validate(store, entity, record, null);
    if (!validation.isBlank()) return error(HttpStatus.BAD_REQUEST, validation);
    storeService.saveRecord(entity, record);
    systemLogService.log(user, "create " + entity, str(record, "id"));
    return ResponseEntity.ok(mapOf("record", record));
  }

  /**
   * 更新实体
   */
  public ResponseEntity<?> updateEntity(String userId, String entity, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!canManage(user, entity)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Map<String, Object>> records = store.entity(entity);
    if (records == null) return error(HttpStatus.BAD_REQUEST, "Unknown entity.");
    Map<String, Object> record = body.containsKey("record") ? asMap(body.get("record")) : body;
    Map<String, Object> existing = find(records, str(record, "id"));
    if (existing == null) return error(HttpStatus.NOT_FOUND, "Record not found.");
    if (isRole(user, "teacher") && !Objects.equals(str(existing, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> next = new LinkedHashMap<>(existing);
    next.putAll(record);
    // Prevent teachers from spoofing teacherId on their resources
    if (isRole(user, "teacher") && Set.of("questions", "papers", "exams").contains(entity)) {
      next.put("teacherId", userId);
    }
    if ("users".equals(entity)) {
      if (record.containsKey("password")) {
        String rawPw = str(record, "password");
        if (!rawPw.isBlank()) next.put("password", authService.hashPassword(rawPw));
        else next.put("password", str(existing, "password"));
      }
      if (!next.containsKey("password") || str(next, "password").isBlank()) {
        Map<String, Object> dbRecord = find(storeService.readStore().users, str(record, "id"));
        if (dbRecord != null && dbRecord.containsKey("password")) {
          next.put("password", str(dbRecord, "password"));
        }
      }
    }
    String validation = validate(store, entity, next, str(existing, "id"));
    if (!validation.isBlank()) return error(HttpStatus.BAD_REQUEST, validation);
    storeService.saveRecord(entity, next);
    systemLogService.log(user, "update " + entity, str(next, "id"));
    return ResponseEntity.ok(mapOf("record", next));
  }

  /**
   * 删除实体
   */
  public ResponseEntity<?> deleteEntity(String userId, String entity, String id) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!canManage(user, entity)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Map<String, Object>> records = store.entity(entity);
    if (records == null || find(records, id) == null) return error(HttpStatus.NOT_FOUND, "Record not found.");
    if ("classes".equals(entity)) {
      clearExamClassReferences(store, id);
    }
    String block = deleteBlocker(store, entity, id);
    if (!block.isBlank()) return error(HttpStatus.BAD_REQUEST, block);
    storeService.deleteRecord(entity, id);
    systemLogService.log(user, "delete " + entity, id);
    return ResponseEntity.ok(mapOf("success", true));
  }

  /**
   * 验证实体数据
   */
  public String validate(Store store, String entity, Map<String, Object> record, String currentId) {
    if ("users".equals(entity)) {
      if (str(record, "username").isBlank() || str(record, "password").isBlank() || str(record, "name").isBlank()) return "User fields are incomplete.";
      if (!Set.of("admin", "teacher", "student").contains(str(record, "role"))) return "Invalid role.";
      if (store.users.stream().anyMatch(u -> Objects.equals(str(u, "username"), str(record, "username")) && !Objects.equals(str(u, "id"), currentId))) return "Username already exists.";
      if ("student".equals(str(record, "role"))) {
        if (str(record, "classId").isBlank()) return "Student must bind a class.";
        if (find(store.classes, str(record, "classId")) == null) return "Class does not exist.";
      } else {
        if (str(record, "departmentId").isBlank()) return "Teacher or admin must bind a department.";
        if (find(store.departments, str(record, "departmentId")) == null) return "Department does not exist.";
      }
    }
    if ("questions".equals(entity)) {
      if (str(record, "title").isBlank() || str(record, "subject").isBlank() || str(record, "type").isBlank()) return "Question fields are incomplete.";
      if (!OBJECTIVE_TYPES.contains(str(record, "type")) && !SUBJECTIVE_TYPES.contains(str(record, "type"))) return "Invalid question type.";
      if (asInt(record.get("score")) <= 0) return "Question score must be greater than zero.";
    }
    if ("papers".equals(entity)) {
      if (str(record, "name").isBlank() || asList(record.get("questionIds")).isEmpty()) return "Paper needs questions.";
      int total = asList(record.get("questionIds")).stream().mapToInt(id -> asInt(java.util.Optional.ofNullable(find(store.questions, String.valueOf(id))).map(q -> q.get("score")).orElse(0))).sum();
      record.put("totalScore", total);
    }
    if ("exams".equals(entity)) {
      if (str(record, "name").isBlank() || str(record, "paperId").isBlank() || asList(record.get("targetClassIds")).isEmpty()) return "Exam fields are incomplete.";
    }
    return "";
  }

  /**
   * 检查删除阻止条件
   */
  public String deleteBlocker(Store store, String entity, String id) {
    if ("departments".equals(entity)) {
      long classCount = store.classes.stream().filter(c -> Objects.equals(str(c, "departmentId"), id)).count();
      long userCount = store.users.stream().filter(u -> Objects.equals(str(u, "departmentId"), id)).count();
      if (classCount > 0 || userCount > 0) {
        return "该学院下还有 " + classCount + " 个班级和 " + userCount + " 名师生，请先移除相关数据后再删除。";
      }
    }
    if ("classes".equals(entity)) {
      long studentCount = store.users.stream().filter(u -> Objects.equals(str(u, "classId"), id)).count();
      if (studentCount > 0) {
        return "该班级下还有 " + studentCount + " 名学生，请先移除或转移学生后再删除。";
      }
    }
    if ("questions".equals(entity) && store.papers.stream().anyMatch(p -> asList(p.get("questionIds")).contains(id))) return "该题目已被试卷引用，无法删除。";
    if ("papers".equals(entity) && store.exams.stream().anyMatch(e -> Objects.equals(str(e, "paperId"), id))) return "该试卷已被考试引用，无法删除。";
    if ("exams".equals(entity) && store.submissions.stream().anyMatch(s -> Objects.equals(str(s, "examId"), id))) return "该考试已有提交记录，无法删除。";
    return "";
  }

  /**
   * 清理考试中的班级引用
   */
  public void clearExamClassReferences(Store store, String classId) {
    for (Map<String, Object> exam : store.exams) {
      List<Object> targetClassIds = asList(exam.get("targetClassIds"));
      if (targetClassIds.contains(classId)) {
        List<Object> filtered = new ArrayList<>(targetClassIds);
        filtered.remove(classId);
        exam.put("targetClassIds", filtered);
        storeService.saveRecord("exams", exam);
      }
    }
  }

  private boolean canManage(Map<String, Object> user, String entity) {
    if (isRole(user, "admin")) return Set.of("users", "departments", "classes").contains(entity);
    if (isRole(user, "teacher")) return Set.of("questions", "papers", "exams").contains(entity);
    return false;
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
