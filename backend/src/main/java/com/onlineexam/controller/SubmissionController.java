package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.service.AnalysisService;
import com.onlineexam.service.ExamService;
import com.onlineexam.service.SubmissionService;
import com.onlineexam.service.SystemLogService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 提交控制器 - 处理提交保存、提交和手动评分
 */
@RestController
@RequestMapping("/api")
public class SubmissionController {

  private static final String RUNNING = "\u8fdb\u884c\u4e2d";
  private static final String ENDED = "\u5df2\u7ed3\u675f";
  private static final String PENDING = "\u5f85\u9605\u5377";
  private static final String COMPLETED = "\u5df2\u5b8c\u6210";

  private final StoreService storeService;
  private final ExamService examService;
  private final SubmissionService submissionService;
  private final SystemLogService systemLogService;

  public SubmissionController(StoreService storeService, ExamService examService,
                              SubmissionService submissionService, SystemLogService systemLogService) {
    this.storeService = storeService;
    this.examService = examService;
    this.submissionService = submissionService;
    this.systemLogService = systemLogService;
  }

  @PostMapping("/submissions/save")
  public ResponseEntity<?> saveSubmission(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, str(body, "examId"));
    if (exam == null || !examService.canStudentAccess(user, exam)) return error(HttpStatus.NOT_FOUND, "Exam not found.");
    Map<String, Object> submission = examService.ensureStudentSession(store, exam, user);
    if (submission.containsKey("error")) return error(HttpStatus.BAD_REQUEST, str(submission, "error"));
    if (examService.remainingMs(submission) <= 0) return error(HttpStatus.BAD_REQUEST, "Exam time is over.");
    submission.put("answers", asList(body.get("answers")));
    submission.put("switchCount", asInt(body.get("switchCount")));
    submission.put("updatedAt", Instant.now().toString());
    storeService.saveRecord("submissions", submission);
    systemLogService.log(user, "save submission", str(exam, "id"));
    return ResponseEntity.ok(mapOf("success", true, "submission", submission));
  }

  @PostMapping("/submissions/submit")
  public ResponseEntity<?> submitSubmission(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                            @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, str(body, "examId"));
    if (exam == null || !examService.canStudentAccess(user, exam)) return error(HttpStatus.NOT_FOUND, "Exam not found.");
    Map<String, Object> existing = examService.studentSubmission(store, str(exam, "id"), userId);
    if (existing != null && (PENDING.equals(str(existing, "status")) || COMPLETED.equals(str(existing, "status")))) {
      return ResponseEntity.ok(mapOf("submission", submissionService.buildSubmissionReview(store, existing)));
    }
    Map<String, Object> submission = examService.ensureStudentSession(store, exam, user);
    if (submission.containsKey("error") && !ENDED.equals(examService.examStatus(exam))) return error(HttpStatus.BAD_REQUEST, str(submission, "error"));
    submission.put("answers", asList(body.get("answers")));
    submission.put("switchCount", asInt(body.get("switchCount")));
    List<String> reasons = new ArrayList<>();
    if (asInt(submission.get("switchCount")) > asInt(exam.get("antiCheatLimit"))) reasons.add("\u5207\u5c4f\u6b21\u6570\u8d85\u9650");
    Instant submitStartedAt = parseInstant(str(submission, "startedAt")).orElse(null);
    Instant submitDeadline = parseInstant(str(submission, "deadlineAt")).orElse(null);
    if (submitStartedAt != null && submitDeadline != null) {
      long allowedMs = submitDeadline.toEpochMilli() - submitStartedAt.toEpochMilli();
      long usedMs = System.currentTimeMillis() - submitStartedAt.toEpochMilli();
      if (allowedMs > 60000 && usedMs < allowedMs / 10) {
        reasons.add("\u7b54\u9898\u65f6\u95f4\u5f02\u5e38\uff08\u4ec5\u7528\u65f6" + submissionService.formatUsedMs(usedMs) + "\uff0c\u5141\u8bb8" + submissionService.formatUsedMs(allowedMs) + "\uff09");
      }
    }
    submission.put("suspicious", !reasons.isEmpty());
    submission.put("suspiciousReasons", reasons);
    submissionService.gradeSubmission(store, submission);
    storeService.saveRecord("submissions", submission);
    if (COMPLETED.equals(str(submission, "status"))) submissionService.syncWrongBook(store, submission);
    systemLogService.log(user, "submit submission", str(exam, "id"));
    return ResponseEntity.ok(mapOf("submission", submissionService.buildSubmissionReview(store, submission)));
  }

  @PostMapping("/submissions/manual-grade")
  public ResponseEntity<?> manualGrade(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> submission = find(store.submissions, str(body, "submissionId"));
    if (submission == null) return error(HttpStatus.NOT_FOUND, "Submission not found.");
    Map<String, Object> exam = find(store.exams, str(submission, "examId"));
    if (exam == null || !Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> scores = asMap(body.get("scores"));
    int finalScore = 0;
    List<Object> details = new ArrayList<>();
    for (Object raw : asList(submission.get("answerDetail"))) {
      Map<String, Object> detail = new LinkedHashMap<>(asMap(raw));
      int full = asInt(detail.get("fullScore"));
      if (scores.containsKey(str(detail, "questionId"))) {
        int score = Math.max(0, Math.min(full, asInt(scores.get(str(detail, "questionId")))));
        detail.put("score", score);
        detail.put("correct", full > 0 && score >= full);
      }
      finalScore += asInt(detail.get("score"));
      details.add(detail);
    }
    submission.put("answerDetail", details);
    submission.put("finalScore", finalScore);
    submission.put("status", COMPLETED);
    submission.put("gradedBy", str(user, "name"));
    submission.put("updatedAt", Instant.now().toString());
    storeService.saveRecord("submissions", submission);
    submissionService.syncWrongBook(store, submission);
    systemLogService.log(user, "manual grade", str(submission, "id"));
    return ResponseEntity.ok(mapOf("submission", submissionService.buildSubmissionReview(store, submission)));
  }

  private java.util.Optional<Instant> parseInstant(String value) {
    if (value == null || value.isBlank()) return java.util.Optional.empty();
    try { return java.util.Optional.of(Instant.parse(value)); } catch (Exception e) { return java.util.Optional.empty(); }
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
}
