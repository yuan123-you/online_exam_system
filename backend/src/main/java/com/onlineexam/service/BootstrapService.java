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
 * Bootstrap 服务 - 构建前端初始化数据和统计数据
 */
@Service
public class BootstrapService {

  private static final String COMPLETED = "\u5df2\u5b8c\u6210";

  private final StoreService storeService;
  private final ExamService examService;
  private final SubmissionService submissionService;
  private final WrongBookService wrongBookService;

  public BootstrapService(StoreService storeService, ExamService examService,
                          SubmissionService submissionService, WrongBookService wrongBookService) {
    this.storeService = storeService;
    this.examService = examService;
    this.submissionService = submissionService;
    this.wrongBookService = wrongBookService;
  }

  /**
   * 构建 Bootstrap 数据
   */
  public ResponseEntity<?> bootstrap(String userId) {
    Store store;
    try {
      store = storeService.readStore();
    } catch (Exception e) {
      // readStore 失败时（如表不存在），返回 200 + 空数据，避免浏览器控制台报 401 错误
      return ResponseEntity.ok(mapOf(
        "currentUser", "", "departments", List.of(), "classes", List.of(), "users", List.of(),
        "questions", List.of(), "papers", List.of(), "exams", List.of(),
        "submissions", List.of(), "wrongBookEntries", List.of(), "logs", List.of()
      ));
    }
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return ResponseEntity.ok(mapOf(
      "currentUser", "", "departments", List.of(), "classes", List.of(), "users", List.of(),
      "questions", List.of(), "papers", List.of(), "exams", List.of(),
      "submissions", List.of(), "wrongBookEntries", List.of(), "logs", List.of()
    ));
    try {
      return ResponseEntity.ok(buildBootstrap(store, user));
    } catch (Exception e) {
      // 如果构建失败，返回最小化数据避免前端完全不可用
      return ResponseEntity.ok(mapOf(
        "currentUser", sanitizeUser(user),
        "departments", List.of(), "classes", List.of(), "users", List.of(sanitizeUser(user)),
        "questions", List.of(), "papers", List.of(), "exams", List.of(),
        "submissions", List.of(), "wrongBookEntries", List.of(), "logs", List.of()
      ));
    }
  }

  /**
   * 获取统计数据
   */
  public ResponseEntity<?> stats(String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!hasRole(user, "admin", "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Set<String> teacherExamIds = ids(store.exams.stream().filter(e -> Objects.equals(str(e, "teacherId"), userId)).toList());
    List<Map<String, Object>> scope = isRole(user, "admin")
      ? store.submissions
      : store.submissions.stream().filter(s -> teacherExamIds.contains(str(s, "examId"))).toList();
    long finished = scope.stream().filter(s -> COMPLETED.equals(str(s, "status"))).count();
    int totalUsers = isRole(user, "admin") ? store.users.size() : (int) store.users.stream().filter(u -> isRole(u, "student")).count();
    int totalExams = isRole(user, "admin") ? store.exams.size() : teacherExamIds.size();
    return ResponseEntity.ok(mapOf("totalUsers", totalUsers, "totalExams", totalExams, "totalSubmissions", scope.size(), "finished", finished));
  }

  public Map<String, Object> buildBootstrap(Store store, Map<String, Object> user) {
    List<Map<String, Object>> safeUsers = store.users.stream().map(this::sanitizeUser).toList();
    if (isRole(user, "admin")) {
      return mapOf("currentUser", sanitizeUser(user), "departments", store.departments, "classes", store.classes, "users", safeUsers,
        "questions", store.questions, "papers", store.papers, "exams", store.exams.stream().map(e -> examService.decorateExam(store, e)).toList(),
        "submissions", store.submissions.stream().map(s -> submissionService.buildSubmissionReview(store, s)).toList(),
        "wrongBookEntries", List.of(), "logs", store.logs.stream().limit(50).toList());
    }
    if (isRole(user, "teacher")) {
      Set<String> ownExamIds = ids(store.exams.stream().filter(e -> Objects.equals(str(e, "teacherId"), str(user, "id"))).toList());
      return mapOf("currentUser", sanitizeUser(user), "departments", store.departments, "classes", store.classes, "users", safeUsers,
        "questions", store.questions.stream().filter(q -> Objects.equals(str(q, "teacherId"), str(user, "id"))).toList(),
        "papers", store.papers.stream().filter(p -> Objects.equals(str(p, "teacherId"), str(user, "id"))).toList(),
        "exams", store.exams.stream().filter(e -> ownExamIds.contains(str(e, "id"))).map(e -> examService.decorateExam(store, e)).toList(),
        "submissions", store.submissions.stream().filter(s -> ownExamIds.contains(str(s, "examId"))).map(s -> submissionService.buildSubmissionReview(store, s)).toList(),
        "wrongBookEntries", List.of(), "logs", List.of());
    }
    return mapOf("currentUser", sanitizeUser(user), "departments", store.departments, "classes", store.classes, "users", List.of(sanitizeUser(user)),
      "questions", List.of(), "papers", List.of(),
      "exams", store.exams.stream().filter(e -> examService.canStudentAccess(user, e)).map(e -> examService.decorateExam(store, e)).toList(),
      "submissions", store.submissions.stream().filter(s -> Objects.equals(str(s, "studentId"), str(user, "id"))).map(s -> submissionService.buildSubmissionReview(store, s)).toList(),
      "wrongBookEntries", store.wrongBookEntries.stream().filter(e -> Objects.equals(str(e, "studentId"), str(user, "id")) && !e.containsKey("removedAt")).map(e -> wrongBookService.buildWrongBookEntry(store, e)).toList(),
      "logs", List.of());
  }

  private Map<String, Object> sanitizeUser(Map<String, Object> user) {
    Map<String, Object> safe = new LinkedHashMap<>(user);
    safe.remove("password");
    return safe;
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  private boolean hasRole(Map<String, Object> user, String... roles) {
    if (user == null) return false;
    for (String role : roles) if (isRole(user, role)) return true;
    return false;
  }

  private Set<String> ids(List<Map<String, Object>> rows) {
    Set<String> ids = new HashSet<>();
    for (Map<String, Object> row : rows) ids.add(str(row, "id"));
    return ids;
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
