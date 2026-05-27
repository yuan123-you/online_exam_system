package com.onlineexam;

import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {
  private static final String RUNNING = "\u8fdb\u884c\u4e2d";
  private static final String UPCOMING = "\u672a\u5f00\u59cb";
  private static final String ENDED = "\u5df2\u7ed3\u675f";
  private static final String PENDING = "\u5f85\u9605\u5377";
  private static final String COMPLETED = "\u5df2\u5b8c\u6210";
  private static final Set<String> SUBJECTIVE_TYPES = Set.of("short", "coding");
  private static final Set<String> OBJECTIVE_TYPES = Set.of("single", "multiple", "judge", "fill");

  private final StoreService storeService;

  public ApiController(StoreService storeService) {
    this.storeService = storeService;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return mapOf("status", "ok", "time", now(), "storage", "mysql");
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Optional<Map<String, Object>> found = store.users.stream()
      .filter(user -> Objects.equals(str(user, "username"), str(body, "username")) && Objects.equals(str(user, "password"), str(body, "password")))
      .findFirst();
    if (found.isEmpty()) {
      return error(HttpStatus.UNAUTHORIZED, "Username or password is incorrect.");
    }
    log(found.get(), "login", str(found.get(), "role") + ":" + str(found.get(), "name"));
    return ResponseEntity.ok(mapOf("user", sanitizeUser(found.get())));
  }

  @GetMapping("/bootstrap")
  public ResponseEntity<?> bootstrap(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not logged in.");
    return ResponseEntity.ok(buildBootstrap(store, user));
  }

  @GetMapping("/stats")
  public ResponseEntity<?> stats(@RequestHeader(value = "X-User-Id", required = false) String userId) {
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

  @PostMapping("/entities")
  public ResponseEntity<?> createEntity(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    String entity = str(body, "entity");
    if (!canManage(user, entity)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> record = asMap(body.get("record"));
    if (record.isEmpty()) return error(HttpStatus.BAD_REQUEST, "Record is required.");
    record = new LinkedHashMap<>(record);
    record.putIfAbsent("id", createId(entity.endsWith("s") ? entity.substring(0, entity.length() - 1) : entity));
    if (isRole(user, "teacher") && Set.of("questions", "papers", "exams").contains(entity)) {
      record.put("teacherId", userId);
    }
    String validation = validate(store, entity, record, null);
    if (!validation.isBlank()) return error(HttpStatus.BAD_REQUEST, validation);
    storeService.saveRecord(entity, record);
    log(user, "create " + entity, str(record, "id"));
    return ResponseEntity.ok(mapOf("record", record));
  }

  @PutMapping("/entities/{entity}")
  public ResponseEntity<?> updateEntity(@RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String entity, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!canManage(user, entity)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Map<String, Object>> records = store.entity(entity);
    if (records == null) return error(HttpStatus.BAD_REQUEST, "Unknown entity.");
    Map<String, Object> existing = find(records, str(body, "id"));
    if (existing == null) return error(HttpStatus.NOT_FOUND, "Record not found.");
    if (isRole(user, "teacher") && !Objects.equals(str(existing, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> next = new LinkedHashMap<>(existing);
    next.putAll(body);
    String validation = validate(store, entity, next, str(existing, "id"));
    if (!validation.isBlank()) return error(HttpStatus.BAD_REQUEST, validation);
    storeService.saveRecord(entity, next);
    log(user, "update " + entity, str(next, "id"));
    return ResponseEntity.ok(mapOf("record", next));
  }

  @DeleteMapping("/entities/{entity}/{id}")
  public ResponseEntity<?> deleteEntity(@RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String entity, @PathVariable String id) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!canManage(user, entity)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Map<String, Object>> records = store.entity(entity);
    if (records == null || find(records, id) == null) return error(HttpStatus.NOT_FOUND, "Record not found.");
    String block = deleteBlocker(store, entity, id);
    if (!block.isBlank()) return error(HttpStatus.BAD_REQUEST, block);
    storeService.deleteRecord(entity, id);
    log(user, "delete " + entity, id);
    return ResponseEntity.ok(mapOf("success", true));
  }

  @PostMapping("/questions/batch-import")
  public ResponseEntity<?> importQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Object> records = asList(body.get("records"));
    List<Map<String, Object>> created = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> record = new LinkedHashMap<>(asMap(records.get(i)));
      record.put("id", createId("question"));
      record.put("teacherId", userId);
      String validation = validate(store, "questions", record, null);
      if (!validation.isBlank()) {
        errors.add(mapOf("index", i, "title", str(record, "title"), "message", validation));
      } else {
        storeService.saveRecord("questions", record);
        store.questions.add(record);
        created.add(record);
      }
    }
    if (!created.isEmpty()) log(user, "batch import questions", String.valueOf(created.size()));
    return ResponseEntity.ok(mapOf("importedCount", created.size(), "failedCount", errors.size(), "created", created, "errors", errors));
  }

  @PostMapping("/questions/batch-delete")
  public ResponseEntity<?> deleteQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
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
      String block = deleteBlocker(store, "questions", id);
      if (!block.isBlank()) {
        errors.add(mapOf("id", id, "message", block));
        continue;
      }
      storeService.deleteRecord("questions", id);
      deleted.add(id);
    }
    if (!deleted.isEmpty()) log(user, "batch delete questions", String.valueOf(deleted.size()));
    return ResponseEntity.ok(mapOf("deletedCount", deleted.size(), "failedCount", errors.size(), "deletedIds", deleted, "errors", errors));
  }

  @PostMapping("/users/batch-import")
  public ResponseEntity<?> importUsers(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "admin")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Map<String, Object>> created = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    for (int i = 0; i < asList(body.get("records")).size(); i++) {
      Map<String, Object> record = new LinkedHashMap<>(asMap(asList(body.get("records")).get(i)));
      record.put("id", createId(str(record, "role").isBlank() ? "user" : str(record, "role")));
      String validation = validate(store, "users", record, null);
      if (!validation.isBlank()) {
        errors.add(mapOf("index", i, "title", str(record, "username"), "message", validation));
      } else {
        storeService.saveRecord("users", record);
        store.users = new ArrayList<>(store.users);
        store.users.add(record);
        created.add(record);
      }
    }
    if (!created.isEmpty()) log(user, "batch import users", String.valueOf(created.size()));
    return ResponseEntity.ok(mapOf("importedCount", created.size(), "failedCount", errors.size(), "created", created, "errors", errors));
  }

  @GetMapping("/exams/{examId}/detail")
  public ResponseEntity<?> examDetail(@RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String examId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!hasRole(user, "student", "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, examId);
    if (exam == null) return error(HttpStatus.NOT_FOUND, "Exam not found.");
    if (isRole(user, "teacher")) {
      if (!Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
      return ResponseEntity.ok(buildExamSnapshot(store, exam, false));
    }
    if (!canStudentAccess(user, exam)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> submission = ensureStudentSession(store, exam, user);
    if (submission.containsKey("error")) return error(HttpStatus.BAD_REQUEST, str(submission, "error"));
    storeService.saveRecord("submissions", submission);
    Map<String, Object> snapshot = buildExamSnapshot(store, exam, true);
    snapshot.put("session", mapOf(
      "submissionId", str(submission, "id"), "startedAt", submission.get("startedAt"), "deadlineAt", submission.get("deadlineAt"),
      "switchCount", asInt(submission.get("switchCount")), "answers", asList(submission.get("answers")), "remainingMs", remainingMs(submission)
    ));
    return ResponseEntity.ok(snapshot);
  }

  @PostMapping("/submissions/save")
  public ResponseEntity<?> saveSubmission(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, str(body, "examId"));
    if (exam == null || !canStudentAccess(user, exam)) return error(HttpStatus.NOT_FOUND, "Exam not found.");
    Map<String, Object> submission = ensureStudentSession(store, exam, user);
    if (submission.containsKey("error")) return error(HttpStatus.BAD_REQUEST, str(submission, "error"));
    if (remainingMs(submission) <= 0) return error(HttpStatus.BAD_REQUEST, "Exam time is over.");
    submission.put("answers", asList(body.get("answers")));
    submission.put("switchCount", asInt(body.get("switchCount")));
    submission.put("updatedAt", now());
    storeService.saveRecord("submissions", submission);
    log(user, "save submission", str(exam, "id"));
    return ResponseEntity.ok(mapOf("success", true, "submission", submission));
  }

  @PostMapping("/submissions/submit")
  public ResponseEntity<?> submitSubmission(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, str(body, "examId"));
    if (exam == null || !canStudentAccess(user, exam)) return error(HttpStatus.NOT_FOUND, "Exam not found.");
    Map<String, Object> existing = studentSubmission(store, str(exam, "id"), userId);
    if (existing != null && (PENDING.equals(str(existing, "status")) || COMPLETED.equals(str(existing, "status")))) {
      return ResponseEntity.ok(mapOf("submission", buildSubmissionReview(store, existing)));
    }
    Map<String, Object> submission = ensureStudentSession(store, exam, user);
    if (submission.containsKey("error") && !ENDED.equals(examStatus(exam))) return error(HttpStatus.BAD_REQUEST, str(submission, "error"));
    submission.put("answers", asList(body.get("answers")));
    submission.put("switchCount", asInt(body.get("switchCount")));
    List<String> reasons = new ArrayList<>();
    if (asInt(submission.get("switchCount")) > asInt(exam.get("antiCheatLimit"))) reasons.add("\u5207\u5c4f\u6b21\u6570\u8d85\u9650");
    submission.put("suspicious", !reasons.isEmpty());
    submission.put("suspiciousReasons", reasons);
    gradeSubmission(store, submission);
    storeService.saveRecord("submissions", submission);
    if (COMPLETED.equals(str(submission, "status"))) syncWrongBook(store, submission);
    log(user, "submit submission", str(exam, "id"));
    return ResponseEntity.ok(mapOf("submission", buildSubmissionReview(store, submission)));
  }

  @PostMapping("/submissions/manual-grade")
  public ResponseEntity<?> manualGrade(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
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
    submission.put("updatedAt", now());
    storeService.saveRecord("submissions", submission);
    syncWrongBook(store, submission);
    log(user, "manual grade", str(submission, "id"));
    return ResponseEntity.ok(mapOf("submission", buildSubmissionReview(store, submission)));
  }

  @PostMapping("/exams/{examId}/extend-student")
  public ResponseEntity<?> extendStudent(@RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String examId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    Map<String, Object> exam = find(store.exams, examId);
    if (!isRole(user, "teacher") || exam == null || !Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> submission = studentSubmission(store, examId, str(body, "studentId"));
    if (submission == null || !RUNNING.equals(str(submission, "status"))) return error(HttpStatus.BAD_REQUEST, "Only running submissions can be extended.");
    int minutes = asInt(body.get("extraMinutes"));
    if (minutes <= 0) return error(HttpStatus.BAD_REQUEST, "Extra minutes must be greater than zero.");
    Instant base = parseInstant(str(submission, "deadlineAt")).orElse(Instant.now());
    submission.put("deadlineAt", base.plusSeconds(minutes * 60L).toString());
    submission.put("manualExtendedMinutes", asInt(submission.get("manualExtendedMinutes")) + minutes);
    submission.put("updatedAt", now());
    storeService.saveRecord("submissions", submission);
    log(user, "extend student", examId + ":" + str(body, "studentId"));
    return ResponseEntity.ok(mapOf("submission", buildSubmissionReview(store, submission)));
  }

  @PostMapping("/wrongbook/{entryId}/retry")
  public ResponseEntity<?> retryWrongBook(@RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String entryId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> entry = store.wrongBookEntries.stream().filter(e -> Objects.equals(str(e, "id"), entryId) && Objects.equals(str(e, "studentId"), userId) && !e.containsKey("removedAt")).findFirst().orElse(null);
    if (entry == null) return error(HttpStatus.NOT_FOUND, "Wrong-book entry not found.");
    Map<String, Object> question = find(store.questions, str(entry, "questionId"));
    if (question == null) return error(HttpStatus.NOT_FOUND, "Question not found.");
    CompareResult result = compare(question, body.get("answer"), true);
    entry.put("retryCount", asInt(entry.get("retryCount")) + 1);
    entry.put("lastRetryAt", now());
    entry.put("lastRetryAnswer", result.answer());
    entry.put("lastRetryCorrect", Boolean.TRUE.equals(result.correct()));
    entry.put("removable", Boolean.TRUE.equals(result.correct()));
    entry.put("status", Boolean.TRUE.equals(result.correct()) ? "mastered" : "active");
    if (!Boolean.TRUE.equals(result.correct())) {
      entry.put("latestAnswer", result.answer());
      entry.put("wrongCount", asInt(entry.get("wrongCount")) + 1);
      entry.put("lastWrongAt", now());
    }
    storeService.saveRecord("wrongBookEntries", entry);
    log(user, "retry wrong book", entryId);
    return ResponseEntity.ok(mapOf("entry", buildWrongBookEntry(store, entry)));
  }

  @PostMapping("/wrongbook/{entryId}/remove")
  public ResponseEntity<?> removeWrongBook(@RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String entryId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> entry = find(store.wrongBookEntries, entryId);
    if (entry == null || !Objects.equals(str(entry, "studentId"), userId)) return error(HttpStatus.NOT_FOUND, "Wrong-book entry not found.");
    if (!Boolean.TRUE.equals(entry.get("lastRetryCorrect"))) return error(HttpStatus.BAD_REQUEST, "Retry correctly before removing.");
    entry.put("removedAt", now());
    entry.put("status", "removed");
    entry.put("removable", false);
    storeService.saveRecord("wrongBookEntries", entry);
    log(user, "remove wrong book", entryId);
    return ResponseEntity.ok(mapOf("success", true));
  }

  @PostMapping("/user/password")
  public ResponseEntity<?> changePassword(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not logged in.");
    if (!Objects.equals(str(user, "password"), str(body, "oldPassword"))) return error(HttpStatus.BAD_REQUEST, "Old password is incorrect.");
    if (str(body, "newPassword").length() < 6) return error(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters.");
    user.put("password", str(body, "newPassword"));
    storeService.saveRecord("users", user);
    log(user, "change password", str(user, "username"));
    return ResponseEntity.ok(mapOf("success", true));
  }

  @PostMapping("/admin/reset-password")
  public ResponseEntity<?> resetPassword(@RequestHeader(value = "X-User-Id", required = false) String userId, @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "admin")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> target = find(store.users, str(body, "userId"));
    if (target == null) return error(HttpStatus.NOT_FOUND, "User not found.");
    String password = str(body, "newPassword").isBlank() ? "123456" : str(body, "newPassword");
    if (password.length() < 6) return error(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters.");
    target.put("password", password);
    storeService.saveRecord("users", target);
    log(user, "reset password", str(target, "username"));
    return ResponseEntity.ok(mapOf("success", true));
  }

  private Map<String, Object> buildBootstrap(Store store, Map<String, Object> user) {
    List<Map<String, Object>> safeUsers = store.users.stream().map(this::sanitizeUser).toList();
    if (isRole(user, "admin")) {
      return mapOf("currentUser", sanitizeUser(user), "departments", store.departments, "classes", store.classes, "users", safeUsers,
        "questions", store.questions, "papers", store.papers, "exams", store.exams.stream().map(e -> decorateExam(store, e)).toList(),
        "submissions", store.submissions.stream().map(s -> buildSubmissionReview(store, s)).toList(),
        "wrongBookEntries", List.of(), "logs", store.logs.stream().limit(50).toList());
    }
    if (isRole(user, "teacher")) {
      Set<String> ownExamIds = ids(store.exams.stream().filter(e -> Objects.equals(str(e, "teacherId"), str(user, "id"))).toList());
      return mapOf("currentUser", sanitizeUser(user), "departments", store.departments, "classes", store.classes, "users", safeUsers,
        "questions", store.questions.stream().filter(q -> Objects.equals(str(q, "teacherId"), str(user, "id"))).toList(),
        "papers", store.papers.stream().filter(p -> Objects.equals(str(p, "teacherId"), str(user, "id"))).toList(),
        "exams", store.exams.stream().filter(e -> ownExamIds.contains(str(e, "id"))).map(e -> decorateExam(store, e)).toList(),
        "submissions", store.submissions.stream().filter(s -> ownExamIds.contains(str(s, "examId"))).map(s -> buildSubmissionReview(store, s)).toList(),
        "wrongBookEntries", List.of(), "logs", List.of());
    }
    return mapOf("currentUser", sanitizeUser(user), "departments", store.departments, "classes", store.classes, "users", List.of(sanitizeUser(user)),
      "questions", List.of(), "papers", List.of(),
      "exams", store.exams.stream().filter(e -> canStudentAccess(user, e)).map(e -> decorateExam(store, e)).toList(),
      "submissions", store.submissions.stream().filter(s -> Objects.equals(str(s, "studentId"), str(user, "id"))).map(s -> buildSubmissionReview(store, s)).toList(),
      "wrongBookEntries", store.wrongBookEntries.stream().filter(e -> Objects.equals(str(e, "studentId"), str(user, "id")) && !e.containsKey("removedAt")).map(e -> buildWrongBookEntry(store, e)).toList(),
      "logs", List.of());
  }

  private Map<String, Object> buildExamSnapshot(Store store, Map<String, Object> exam, boolean hideAnswers) {
    Map<String, Object> paper = find(store.papers, str(exam, "paperId"));
    List<Object> questionIds = asList(paper == null ? null : paper.get("questionIds"));
    List<Map<String, Object>> questions = new ArrayList<>();
    int order = 1;
    for (Object id : questionIds) {
      Map<String, Object> q = find(store.questions, String.valueOf(id));
      if (q != null) {
        Map<String, Object> next = hideAnswers ? sanitizeQuestion(q) : new LinkedHashMap<>(q);
        next.put("order", order++);
        questions.add(next);
      }
    }
    Map<String, Object> snapshot = new LinkedHashMap<>(decorateExam(store, exam));
    snapshot.put("paper", paper == null ? Map.of() : paper);
    snapshot.put("questions", questions);
    return snapshot;
  }

  private Map<String, Object> ensureStudentSession(Store store, Map<String, Object> exam, Map<String, Object> user) {
    String status = examStatus(exam);
    if (UPCOMING.equals(status)) return mapOf("error", "Exam has not started.");
    if (ENDED.equals(status)) return mapOf("error", ENDED);
    Map<String, Object> paper = find(store.papers, str(exam, "paperId"));
    if (paper == null) return mapOf("error", "Paper not found.");
    Map<String, Object> existing = studentSubmission(store, str(exam, "id"), str(user, "id"));
    if (existing != null && (PENDING.equals(str(existing, "status")) || COMPLETED.equals(str(existing, "status")))) {
      return mapOf("error", "Submission already finished.");
    }
    Map<String, Object> session = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
    session.putIfAbsent("id", createId("submission"));
    session.put("examId", str(exam, "id"));
    session.put("studentId", str(user, "id"));
    session.put("studentName", str(user, "name"));
    session.putIfAbsent("answers", List.of());
    session.putIfAbsent("switchCount", 0);
    session.put("status", RUNNING);
    session.putIfAbsent("startedAt", now());
    session.put("deadlineAt", computeDeadline(exam, paper, str(session, "startedAt")));
    session.put("updatedAt", now());
    return session;
  }

  private void gradeSubmission(Store store, Map<String, Object> submission) {
    Map<String, Object> exam = find(store.exams, str(submission, "examId"));
    Map<String, Object> paper = exam == null ? null : find(store.papers, str(exam, "paperId"));
    Map<String, Object> answerMap = new HashMap<>();
    for (Object raw : asList(submission.get("answers"))) {
      Map<String, Object> item = asMap(raw);
      answerMap.put(str(item, "questionId"), asList(item.get("answer")));
    }
    int autoScore = 0;
    boolean needsManual = false;
    List<Object> details = new ArrayList<>();
    for (Object rawId : asList(paper == null ? null : paper.get("questionIds"))) {
      Map<String, Object> q = find(store.questions, String.valueOf(rawId));
      if (q == null) continue;
      CompareResult result = compare(q, answerMap.get(str(q, "id")), false);
      int score = 0;
      if (SUBJECTIVE_TYPES.contains(str(q, "type"))) {
        needsManual = true;
      } else if (Boolean.TRUE.equals(result.correct())) {
        score = asInt(q.get("score"));
        autoScore += score;
      }
      details.add(mapOf("questionId", str(q, "id"), "answer", result.answer(), "title", str(q, "title"), "subject", str(q, "subject"),
        "knowledgePoint", str(q, "knowledgePoint"), "type", str(q, "type"), "score", score, "fullScore", asInt(q.get("score")),
        "correct", result.correct(), "expectedAnswer", asList(q.get("answer"))));
    }
    submission.put("answerDetail", details);
    submission.put("autoScore", autoScore);
    submission.put("finalScore", autoScore);
    submission.put("status", needsManual ? PENDING : COMPLETED);
    submission.put("submittedAt", now());
    submission.put("updatedAt", now());
  }

  private Map<String, Object> buildSubmissionReview(Store store, Map<String, Object> submission) {
    Map<String, Object> exam = find(store.exams, str(submission, "examId"));
    Map<String, Object> paper = exam == null ? null : find(store.papers, str(exam, "paperId"));
    int score = asInt(submission.get("finalScore"));
    int passScore = asInt(paper == null ? 0 : paper.get("passScore"));
    List<Map<String, Object>> finished = store.submissions.stream()
      .filter(s -> Objects.equals(str(s, "examId"), str(submission, "examId")) && COMPLETED.equals(str(s, "status")))
      .sorted(Comparator.comparingInt((Map<String, Object> s) -> asInt(s.get("finalScore"))).reversed())
      .toList();
    int rank = -1;
    for (int i = 0; i < finished.size(); i++) if (Objects.equals(str(finished.get(i), "id"), str(submission, "id"))) rank = i + 1;
    Map<String, Object> review = new LinkedHashMap<>(submission);
    review.put("examName", exam == null ? str(submission, "examId") : str(exam, "name"));
    review.put("paperName", paper == null ? "-" : str(paper, "name"));
    review.put("totalScore", asInt(paper == null ? 0 : paper.get("totalScore")));
    review.put("durationMinutes", asInt(paper == null ? 0 : paper.get("durationMinutes")));
    review.put("passScore", passScore);
    review.put("passStatus", COMPLETED.equals(str(submission, "status")) ? (score >= passScore ? "\u5df2\u53ca\u683c" : "\u672a\u53ca\u683c") : "\u5f85\u5b9a");
    review.put("scoreRate", asInt(paper == null ? 0 : paper.get("totalScore")) > 0 ? Math.round(score * 1000.0 / asInt(paper.get("totalScore"))) / 10.0 : null);
    review.put("rank", rank > 0 ? rank : null);
    review.put("finishedCount", finished.size());
    review.put("participantCount", store.submissions.stream().filter(s -> Objects.equals(str(s, "examId"), str(submission, "examId"))).count());
    review.put("targetStudentCount", exam == null ? 0 : store.users.stream().filter(u -> isRole(u, "student") && asList(exam.get("targetClassIds")).contains(str(u, "classId"))).count());
    return review;
  }

  private void syncWrongBook(Store store, Map<String, Object> submission) {
    for (Object raw : asList(submission.get("answerDetail"))) {
      Map<String, Object> detail = asMap(raw);
      int full = asInt(detail.get("fullScore"));
      if (full <= 0 || asInt(detail.get("score")) >= full) continue;
      Map<String, Object> existing = store.wrongBookEntries.stream()
        .filter(e -> Objects.equals(str(e, "studentId"), str(submission, "studentId")) && Objects.equals(str(e, "questionId"), str(detail, "questionId")))
        .findFirst().orElse(null);
      Map<String, Object> question = find(store.questions, str(detail, "questionId"));
      Map<String, Object> entry = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
      entry.putIfAbsent("id", createId("wrong"));
      entry.put("studentId", str(submission, "studentId"));
      entry.put("studentName", str(submission, "studentName"));
      entry.put("questionId", str(detail, "questionId"));
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
      storeService.saveRecord("wrongBookEntries", entry);
    }
  }

  private Map<String, Object> buildWrongBookEntry(Store store, Map<String, Object> entry) {
    Map<String, Object> question = find(store.questions, str(entry, "questionId"));
    Map<String, Object> next = new LinkedHashMap<>(entry);
    if (question != null) {
      next.put("subject", str(question, "subject"));
      next.put("knowledgePoint", str(question, "knowledgePoint"));
      next.put("type", str(question, "type"));
      next.put("title", str(question, "title"));
      next.put("expectedAnswer", asList(question.get("answer")));
      next.put("question", sanitizeQuestion(question));
    }
    next.put("statusText", Boolean.TRUE.equals(entry.get("lastRetryCorrect")) ? "\u5df2\u91cd\u505a\u901a\u8fc7" : "\u5f85\u91cd\u505a");
    next.put("removable", Boolean.TRUE.equals(entry.get("lastRetryCorrect")) && !entry.containsKey("removedAt"));
    return next;
  }

  private CompareResult compare(Map<String, Object> question, Object rawAnswer, boolean allowSubjectiveAuto) {
    List<String> given = normalizeAnswer(rawAnswer);
    List<String> expected = normalizeAnswer(question.get("answer"));
    String type = str(question, "type");
    Boolean correct = null;
    if ("single".equals(type) || "judge".equals(type)) correct = !given.isEmpty() && !expected.isEmpty() && Objects.equals(given.get(0), expected.get(0));
    else if ("multiple".equals(type)) correct = new HashSet<>(given).equals(new HashSet<>(expected)) && given.size() == expected.size();
    else if ("fill".equals(type)) correct = !given.isEmpty() && !expected.isEmpty() && norm(given.get(0)).equals(norm(expected.get(0)));
    else if (allowSubjectiveAuto) correct = norm(String.join("\n", given)).equals(norm(String.join("\n", expected))) && !given.isEmpty();
    return new CompareResult(given, correct);
  }

  private String validate(Store store, String entity, Map<String, Object> record, String currentId) {
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
      int total = asList(record.get("questionIds")).stream().mapToInt(id -> asInt(Optional.ofNullable(find(store.questions, String.valueOf(id))).map(q -> q.get("score")).orElse(0))).sum();
      record.put("totalScore", total);
    }
    if ("exams".equals(entity)) {
      if (str(record, "name").isBlank() || str(record, "paperId").isBlank() || asList(record.get("targetClassIds")).isEmpty()) return "Exam fields are incomplete.";
    }
    return "";
  }

  private String deleteBlocker(Store store, String entity, String id) {
    if ("departments".equals(entity) && (store.classes.stream().anyMatch(c -> Objects.equals(str(c, "departmentId"), id)) || store.users.stream().anyMatch(u -> Objects.equals(str(u, "departmentId"), id)))) return "Department is in use.";
    if ("classes".equals(entity) && (store.users.stream().anyMatch(u -> Objects.equals(str(u, "classId"), id)) || store.exams.stream().anyMatch(e -> asList(e.get("targetClassIds")).contains(id)))) return "Class is in use.";
    if ("questions".equals(entity) && store.papers.stream().anyMatch(p -> asList(p.get("questionIds")).contains(id))) return "Question is in use.";
    if ("papers".equals(entity) && store.exams.stream().anyMatch(e -> Objects.equals(str(e, "paperId"), id))) return "Paper is in use.";
    if ("exams".equals(entity) && store.submissions.stream().anyMatch(s -> Objects.equals(str(s, "examId"), id))) return "Exam has submissions.";
    return "";
  }

  private Map<String, Object> decorateExam(Store store, Map<String, Object> exam) {
    Map<String, Object> paper = find(store.papers, str(exam, "paperId"));
    Map<String, Object> next = new LinkedHashMap<>(exam);
    next.put("statusText", examStatus(exam));
    next.put("durationMinutes", paper == null ? 0 : asInt(paper.get("durationMinutes")));
    next.put("totalScore", paper == null ? 0 : asInt(paper.get("totalScore")));
    next.put("passScore", paper == null ? 0 : asInt(paper.get("passScore")));
    next.put("paperName", paper == null ? "-" : str(paper, "name"));
    return next;
  }

  private String examStatus(Map<String, Object> exam) {
    Instant now = Instant.now();
    Instant start = parseInstant(str(exam, "startTime")).orElse(now);
    Instant end = parseInstant(str(exam, "endTime")).orElse(now);
    if (now.isBefore(start)) return UPCOMING;
    if (now.isAfter(end)) return ENDED;
    return RUNNING;
  }

  private String computeDeadline(Map<String, Object> exam, Map<String, Object> paper, String startedAt) {
    Instant end = parseInstant(str(exam, "endTime")).orElse(Instant.now());
    Instant started = parseInstant(startedAt).orElse(Instant.now());
    Instant deadline = started.plusSeconds(asInt(paper.get("durationMinutes")) * 60L);
    return deadline.isBefore(end) ? deadline.toString() : end.toString();
  }

  private long remainingMs(Map<String, Object> submission) {
    return parseInstant(str(submission, "deadlineAt")).map(i -> Math.max(0L, i.toEpochMilli() - System.currentTimeMillis())).orElse(0L);
  }

  private boolean canStudentAccess(Map<String, Object> user, Map<String, Object> exam) {
    return isRole(user, "student") && Boolean.TRUE.equals(exam.get("published")) && asList(exam.get("targetClassIds")).contains(str(user, "classId"));
  }

  private boolean canManage(Map<String, Object> user, String entity) {
    if (isRole(user, "admin")) return Set.of("users", "departments", "classes").contains(entity);
    if (isRole(user, "teacher")) return Set.of("questions", "papers", "exams").contains(entity);
    return false;
  }

  private Map<String, Object> studentSubmission(Store store, String examId, String studentId) {
    return store.submissions.stream().filter(s -> Objects.equals(str(s, "examId"), examId) && Objects.equals(str(s, "studentId"), studentId)).findFirst().orElse(null);
  }

  private Map<String, Object> sanitizeUser(Map<String, Object> user) {
    Map<String, Object> safe = new LinkedHashMap<>(user);
    safe.remove("password");
    return safe;
  }

  private Map<String, Object> sanitizeQuestion(Map<String, Object> question) {
    Map<String, Object> safe = new LinkedHashMap<>(question);
    safe.remove("answer");
    return safe;
  }

  private void log(Map<String, Object> user, String action, String detail) {
    storeService.saveRecord("logs", mapOf("id", createId("log"), "actorId", user == null ? "system" : str(user, "id"), "action", action, "detail", detail, "time", now()));
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private boolean hasRole(Map<String, Object> user, String... roles) {
    if (user == null) return false;
    for (String role : roles) if (isRole(user, role)) return true;
    return false;
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  private Set<String> ids(List<Map<String, Object>> rows) {
    Set<String> ids = new HashSet<>();
    for (Map<String, Object> row : rows) ids.add(str(row, "id"));
    return ids;
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

  private List<String> normalizeAnswer(Object raw) {
    List<String> values = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object value : list) if (value != null && !String.valueOf(value).trim().isBlank()) values.add(String.valueOf(value).trim());
    } else if (raw != null && !String.valueOf(raw).trim().isBlank()) {
      values.add(String.valueOf(raw).trim());
    }
    return values;
  }

  private String norm(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private String str(Map<String, Object> map, String key) {
    return str(map == null ? null : map.get(key));
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private Optional<Instant> parseInstant(String value) {
    if (value == null || value.isBlank()) return Optional.empty();
    try {
      return Optional.of(Instant.parse(value));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private String now() {
    return Instant.now().toString();
  }

  private String createId(String prefix) {
    return prefix + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }

  private record CompareResult(List<String> answer, Boolean correct) {}
}
