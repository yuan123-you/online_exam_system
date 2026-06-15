package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.service.ExamService;
import com.onlineexam.service.SubmissionService;
import com.onlineexam.service.SystemLogService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * 考试控制器 - 处理考试详情、监控、成绩导出和题目分析
 */
@RestController
@RequestMapping("/api")
public class ExamController {

  private static final String RUNNING = "进行中";
  private static final String PENDING = "待阅卷";
  private static final String COMPLETED = "已完成";

  private final StoreService storeService;
  private final ExamService examService;
  private final SubmissionService submissionService;
  private final SystemLogService systemLogService;

  public ExamController(StoreService storeService, ExamService examService,
                        SubmissionService submissionService, SystemLogService systemLogService) {
    this.storeService = storeService;
    this.examService = examService;
    this.submissionService = submissionService;
    this.systemLogService = systemLogService;
  }

  @GetMapping("/exams/{examId}/detail")
  public ResponseEntity<?> examDetail(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                      @PathVariable String examId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!hasRole(user, "student", "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, examId);
    if (exam == null) return error(HttpStatus.NOT_FOUND, "Exam not found.");
    if (isRole(user, "teacher")) {
      if (!Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
      return ResponseEntity.ok(examService.buildExamSnapshot(store, exam, false, null, null));
    }
    if (!examService.canStudentAccess(user, exam)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> submission = examService.ensureStudentSession(store, exam, user);
    if (submission.containsKey("error")) return error(HttpStatus.BAD_REQUEST, str(submission, "error"));
    storeService.saveRecord("submissions", submission);
    List<String> questionOrder = asList(submission.get("questionOrder")).stream().map(String::valueOf).toList();
    Map<String, Object> optionOrder = submission.get("optionOrder") instanceof Map ? asMap(submission.get("optionOrder")) : Map.of();
    Map<String, Object> snapshot = examService.buildExamSnapshot(store, exam, true, questionOrder, optionOrder);
    snapshot.put("session", mapOf(
      "submissionId", str(submission, "id"), "startedAt", submission.get("startedAt"), "deadlineAt", submission.get("deadlineAt"),
      "switchCount", asInt(submission.get("switchCount")), "answers", asList(submission.get("answers")), "remainingMs", examService.remainingMs(submission)
    ));
    return ResponseEntity.ok(snapshot);
  }

  @GetMapping("/exams/{examId}/monitor")
  public ResponseEntity<?> monitorExam(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @PathVariable String examId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, examId);
    if (exam == null || !Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Set<String> targetClassIds = new HashSet<>(asList(exam.get("targetClassIds")).stream().map(String::valueOf).toList());
    List<Map<String, Object>> targetStudents = store.users.stream()
      .filter(u -> isRole(u, "student") && targetClassIds.contains(str(u, "classId"))).toList();
    List<Map<String, Object>> examSubmissions = store.submissions.stream()
      .filter(s -> Objects.equals(str(s, "examId"), examId)).toList();
    List<Map<String, Object>> students = new ArrayList<>();
    for (Map<String, Object> student : targetStudents) {
      Map<String, Object> submission = examSubmissions.stream()
        .filter(s -> Objects.equals(str(s, "studentId"), str(student, "id"))).findFirst().orElse(null);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("studentId", str(student, "id"));
      item.put("studentName", str(student, "name"));
      item.put("className", str(find(store.classes, str(student, "classId")), "name"));
      if (submission == null) {
        item.put("status", "未开始");
        item.put("score", null);
        item.put("switchCount", 0);
        item.put("suspicious", false);
      } else {
        item.put("status", str(submission, "status"));
        item.put("score", submission.get("finalScore"));
        item.put("switchCount", asInt(submission.get("switchCount")));
        item.put("suspicious", asBool(submission.get("suspicious")));
        item.put("suspiciousReasons", asList(submission.get("suspiciousReasons")));
        item.put("startedAt", submission.get("startedAt"));
        item.put("submittedAt", submission.get("submittedAt"));
        item.put("usedTimeText", submissionService.buildUsedTimeText(submission));
      }
      students.add(item);
    }
    long notStarted = students.stream().filter(s -> "未开始".equals(str(s, "status"))).count();
    long running = students.stream().filter(s -> RUNNING.equals(str(s, "status"))).count();
    long submitted = students.stream().filter(s -> PENDING.equals(str(s, "status")) || COMPLETED.equals(str(s, "status"))).count();
    List<Integer> scores = students.stream()
      .filter(s -> s.get("score") != null).map(s -> asInt(s.get("score"))).toList();
    return ResponseEntity.ok(mapOf(
      "students", students, "totalCount", targetStudents.size(),
      "notStartedCount", notStarted, "runningCount", running, "submittedCount", submitted,
      "maxScore", scores.isEmpty() ? null : Collections.max(scores),
      "minScore", scores.isEmpty() ? null : Collections.min(scores),
      "avgScore", scores.isEmpty() ? null : Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0) * 10.0) / 10.0
    ));
  }

  @GetMapping("/exams/{examId}/export-scores")
  public ResponseEntity<?> exportScores(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                        @PathVariable String examId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, examId);
    if (exam == null || !Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> paper = find(store.papers, str(exam, "paperId"));
    Set<String> targetClassIds = new HashSet<>(asList(exam.get("targetClassIds")).stream().map(String::valueOf).toList());
    List<Map<String, Object>> targetStudents = store.users.stream()
      .filter(u -> isRole(u, "student") && targetClassIds.contains(str(u, "classId"))).toList();
    List<Map<String, Object>> examSubmissions = store.submissions.stream()
      .filter(s -> Objects.equals(str(s, "examId"), examId)).toList();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, Object> student : targetStudents) {
      Map<String, Object> submission = examSubmissions.stream()
        .filter(s -> Objects.equals(str(s, "studentId"), str(student, "id"))).findFirst().orElse(null);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("username", str(student, "username"));
      row.put("studentName", str(student, "name"));
      row.put("className", str(find(store.classes, str(student, "classId")), "name"));
      row.put("status", submission == null ? "未开始" : str(submission, "status"));
      row.put("score", submission == null ? null : asInt(submission.get("finalScore")));
      row.put("totalScore", asInt(paper == null ? 0 : paper.get("totalScore")));
      row.put("passScore", asInt(paper == null ? 0 : paper.get("passScore")));
      row.put("rank", null);
      rows.add(row);
    }
    List<Map<String, Object>> sorted = rows.stream()
      .filter(r -> r.get("score") != null)
      .sorted(Comparator.comparingInt((Map<String, Object> r) -> asInt(r.get("score"))).reversed()).toList();
    for (int i = 0; i < sorted.size(); i++) sorted.get(i).put("rank", i + 1);
    systemLogService.log(user, "export scores", examId);
    return ResponseEntity.ok(mapOf("examName", str(exam, "name"), "rows", rows));
  }

  @PostMapping("/exams/{examId}/extend-student")
  public ResponseEntity<?> extendStudent(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                         @PathVariable String examId,
                                         @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    Map<String, Object> exam = find(store.exams, examId);
    if (!isRole(user, "teacher") || exam == null || !Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> submission = examService.studentSubmission(store, examId, str(body, "studentId"));
    if (submission == null || !RUNNING.equals(str(submission, "status"))) return error(HttpStatus.BAD_REQUEST, "Only running submissions can be extended.");
    int minutes = asInt(body.get("extraMinutes"));
    if (minutes <= 0) return error(HttpStatus.BAD_REQUEST, "Extra minutes must be greater than zero.");
    Instant base = Instant.parse(str(submission, "deadlineAt"));
    submission.put("deadlineAt", base.plusSeconds(minutes * 60L).toString());
    submission.put("manualExtendedMinutes", asInt(submission.get("manualExtendedMinutes")) + minutes);
    submission.put("updatedAt", Instant.now().toString());
    storeService.saveRecord("submissions", submission);
    systemLogService.log(user, "extend student", examId + ":" + str(body, "studentId"));
    return ResponseEntity.ok(mapOf("submission", submissionService.buildSubmissionReview(store, submission)));
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

  private boolean asBool(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    return value != null && Boolean.parseBoolean(String.valueOf(value));
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
