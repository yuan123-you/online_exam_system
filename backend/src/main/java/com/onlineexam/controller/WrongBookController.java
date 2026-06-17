package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.service.SubmissionService;
import com.onlineexam.service.SystemLogService;
import com.onlineexam.service.WrongBookService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题本控制器 - 处理错题本重做、移除和批量移除
 */
@RestController
@RequestMapping("/api")
public class WrongBookController {

  private final StoreService storeService;
  private final WrongBookService wrongBookService;
  private final SubmissionService submissionService;
  private final SystemLogService systemLogService;

  public WrongBookController(StoreService storeService, WrongBookService wrongBookService,
                             SubmissionService submissionService, SystemLogService systemLogService) {
    this.storeService = storeService;
    this.wrongBookService = wrongBookService;
    this.submissionService = submissionService;
    this.systemLogService = systemLogService;
  }

  @PostMapping("/wrongbook/{entryId}/retry")
  public ResponseEntity<?> retryWrongBook(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @PathVariable String entryId,
                                          @RequestBody Map<String, Object> body) {
    try {
      Store store = storeService.readStore();
      Map<String, Object> user = find(store.users, userId);
      if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
      Map<String, Object> entry = store.wrongBookEntries.stream().filter(e -> Objects.equals(str(e, "id"), entryId) && Objects.equals(str(e, "studentId"), userId) && !e.containsKey("removedAt")).findFirst().orElse(null);
      if (entry == null) return error(HttpStatus.NOT_FOUND, "Wrong-book entry not found.");
      // AI 练习产生的错题条目引用的 questionId 未持久化到 questions 表，
      // 此时用条目自身保存的 expectedAnswer/type 构造伪题目以完成重做判定。
      Map<String, Object> question = find(store.questions, str(entry, "questionId"));
      if (question == null) {
        question = new LinkedHashMap<>();
        question.put("id", str(entry, "questionId"));
        question.put("type", str(entry, "type"));
        question.put("answer", entry.get("expectedAnswer") != null ? entry.get("expectedAnswer") : entry.get("latestAnswer"));
      }
      SubmissionService.CompareResult result = submissionService.compare(question, body.get("answer"), true);
      // 复制一份再修改，避免直接修改缓存中的引用导致并发问题
      Map<String, Object> updated = new LinkedHashMap<>(entry);
      updated.put("retryCount", asInt(entry.get("retryCount")) + 1);
      updated.put("lastRetryAt", Instant.now().toString());
      updated.put("lastRetryAnswer", result.answer());
      updated.put("lastRetryCorrect", Boolean.TRUE.equals(result.correct()));
      updated.put("removable", Boolean.TRUE.equals(result.correct()));
      updated.put("status", "active");
      if (!Boolean.TRUE.equals(result.correct())) {
        updated.put("latestAnswer", result.answer());
        updated.put("wrongCount", asInt(entry.get("wrongCount")) + 1);
        updated.put("lastWrongAt", Instant.now().toString());
      }
      try {
        storeService.saveRecord("wrongBookEntries", updated);
      } catch (Exception saveErr) {
        // saveRecord 可能因外键约束失败（AI 练习的 questionId 不在 question 表），
        // 尝试直接 JDBC 更新已存在的记录来绕过外键检查
        try {
          wrongBookService.updateRetryDirect(updated);
        } catch (Exception jdbcErr) {
          throw new RuntimeException("保存重做结果失败: " + jdbcErr.getMessage(), jdbcErr);
        }
      }
      systemLogService.log(user, "retry wrong book", entryId);
      return ResponseEntity.ok(mapOf("entry", wrongBookService.buildWrongBookEntry(store, updated)));
    } catch (Exception e) {
      e.printStackTrace();
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "重做失败：" + e.getMessage());
    }
  }

  @PostMapping("/wrongbook/{entryId}/remove")
  public ResponseEntity<?> removeWrongBook(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                           @PathVariable String entryId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> entry = find(store.wrongBookEntries, entryId);
    if (entry == null || !Objects.equals(str(entry, "studentId"), userId)) return error(HttpStatus.NOT_FOUND, "Wrong-book entry not found.");
    if (!Boolean.TRUE.equals(entry.get("lastRetryCorrect"))) return error(HttpStatus.BAD_REQUEST, "Retry correctly before removing.");
    entry.put("removedAt", Instant.now().toString());
    entry.put("status", "removed");
    entry.put("removable", false);
    storeService.saveRecord("wrongBookEntries", entry);
    systemLogService.log(user, "remove wrong book", entryId);
    return ResponseEntity.ok(mapOf("success", true));
  }

  @PostMapping("/wrongbook/batch-remove")
  public ResponseEntity<?> batchRemoveWrongBook(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<String> removed = new ArrayList<>();
    for (Object raw : asList(body.get("ids"))) {
      String id = String.valueOf(raw);
      Map<String, Object> entry = find(store.wrongBookEntries, id);
      if (entry == null || !Objects.equals(str(entry, "studentId"), userId)) continue;
      entry.put("removedAt", Instant.now().toString());
      entry.put("status", "removed");
      entry.put("removable", false);
      storeService.saveRecord("wrongBookEntries", entry);
      removed.add(id);
    }
    if (!removed.isEmpty()) systemLogService.log(user, "batch remove wrong book", String.valueOf(removed.size()));
    return ResponseEntity.ok(mapOf("removedCount", removed.size(), "removedIds", removed));
  }

  /**
   * 错题本分页查询（学生）
   */
  @GetMapping("/wrongbook/page")
  public ResponseEntity<?> wrongBookPage(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         @RequestParam(defaultValue = "") String subject,
                                         @RequestParam(defaultValue = "") String status) {
    Map<String, Object> user = find(storeService.readStore().users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    return ResponseEntity.ok(storeService.queryWrongBookPage(userId, page, pageSize, subject, status));
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
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
    if (pairs.length % 2 != 0) { throw new IllegalArgumentException("mapOf 参数个数必须为偶数，当前为 " + pairs.length); }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
