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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> entry = store.wrongBookEntries.stream().filter(e -> Objects.equals(str(e, "id"), entryId) && Objects.equals(str(e, "studentId"), userId) && !e.containsKey("removedAt")).findFirst().orElse(null);
    if (entry == null) return error(HttpStatus.NOT_FOUND, "Wrong-book entry not found.");
    Map<String, Object> question = find(store.questions, str(entry, "questionId"));
    if (question == null) return error(HttpStatus.NOT_FOUND, "Question not found.");
    SubmissionService.CompareResult result = submissionService.compare(question, body.get("answer"), true);
    entry.put("retryCount", asInt(entry.get("retryCount")) + 1);
    entry.put("lastRetryAt", Instant.now().toString());
    entry.put("lastRetryAnswer", result.answer());
    entry.put("lastRetryCorrect", Boolean.TRUE.equals(result.correct()));
    entry.put("removable", Boolean.TRUE.equals(result.correct()));
    entry.put("status", Boolean.TRUE.equals(result.correct()) ? "mastered" : "active");
    if (!Boolean.TRUE.equals(result.correct())) {
      entry.put("latestAnswer", result.answer());
      entry.put("wrongCount", asInt(entry.get("wrongCount")) + 1);
      entry.put("lastWrongAt", Instant.now().toString());
    }
    storeService.saveRecord("wrongBookEntries", entry);
    systemLogService.log(user, "retry wrong book", entryId);
    return ResponseEntity.ok(mapOf("entry", wrongBookService.buildWrongBookEntry(store, entry)));
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
