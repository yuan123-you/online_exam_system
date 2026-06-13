package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quota controller - returns current user's storage quota usage.
 *
 * GET /api/user/quotas
 *   teacher -> questionBank
 *   student -> wrongBook, practiceRecords
 *   admin   -> summary across all users
 */
@RestController
@RequestMapping("/api")
public class QuotaController {

  private static final int QUESTION_LIMIT = 5000;
  private static final int WRONG_BOOK_LIMIT = 1000;
  private static final int PRACTICE_LIMIT = 1000;

  private final StoreService storeService;

  public QuotaController(StoreService storeService) {
    this.storeService = storeService;
  }

  @GetMapping("/user/quotas")
  public ResponseEntity<?> quotas(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    String role = str(user, "role");
    Map<String, Object> result = new LinkedHashMap<>();

    if ("teacher".equals(role)) {
      long used = store.questions.stream()
        .filter(q -> Objects.equals(str(q, "teacherId"), userId))
        .count();
      result.put("questionBank", quotaMap(used, QUESTION_LIMIT));
    } else if ("student".equals(role)) {
      long wrongUsed = store.wrongBookEntries.stream()
        .filter(e -> Objects.equals(str(e, "studentId"), userId)
          && !e.containsKey("removedAt")
          && !"practice".equals(str(e, "status"))
          && !"archived".equals(str(e, "status")))
        .count();
      long practiceUsed = store.wrongBookEntries.stream()
        .filter(e -> Objects.equals(str(e, "studentId"), userId)
          && "practice".equals(str(e, "status"))
          && !"archived".equals(str(e, "status")))
        .count();
      result.put("wrongBook", quotaMap(wrongUsed, WRONG_BOOK_LIMIT));
      result.put("practiceRecords", quotaMap(practiceUsed, PRACTICE_LIMIT));
    } else if ("admin".equals(role)) {
      // Summary across all users
      long totalQuestions = store.questions.size();
      long totalWrongBook = store.wrongBookEntries.stream()
        .filter(e -> !e.containsKey("removedAt")
          && !"practice".equals(str(e, "status"))
          && !"archived".equals(str(e, "status")))
        .count();
      long totalPractice = store.wrongBookEntries.stream()
        .filter(e -> "practice".equals(str(e, "status"))
          && !"archived".equals(str(e, "status")))
        .count();
      long teacherCount = store.users.stream().filter(u -> "teacher".equals(str(u, "role"))).count();
      long studentCount = store.users.stream().filter(u -> "student".equals(str(u, "role"))).count();
      result.put("questionBank", quotaMap(totalQuestions, teacherCount * QUESTION_LIMIT));
      result.put("wrongBook", quotaMap(totalWrongBook, studentCount * WRONG_BOOK_LIMIT));
      result.put("practiceRecords", quotaMap(totalPractice, studentCount * PRACTICE_LIMIT));
    }

    return ResponseEntity.ok(result);
  }

  private Map<String, Object> quotaMap(long used, long limit) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("used", used);
    m.put("limit", limit);
    return m;
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
