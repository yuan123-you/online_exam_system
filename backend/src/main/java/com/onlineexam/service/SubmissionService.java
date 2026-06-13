package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 提交服务 - 处理提交保存、提交、评分和错题本同步
 */
@Service
public class SubmissionService {

  private static final String RUNNING = "\u8fdb\u884c\u4e2d";
  private static final String ENDED = "\u5df2\u7ed3\u675f";
  private static final String PENDING = "\u5f85\u9605\u5377";
  private static final String COMPLETED = "\u5df2\u5b8c\u6210";
  private static final Set<String> SUBJECTIVE_TYPES = Set.of("short", "coding");

  private final StoreService storeService;
  private final ExamService examService;
  private final SystemLogService systemLogService;
  private final WrongBookService wrongBookService;

  public SubmissionService(StoreService storeService, ExamService examService,
                           SystemLogService systemLogService, WrongBookService wrongBookService) {
    this.storeService = storeService;
    this.examService = examService;
    this.systemLogService = systemLogService;
    this.wrongBookService = wrongBookService;
  }

  /**
   * 自动评分提交
   */
  public void gradeSubmission(Store store, Map<String, Object> submission) {
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

  /**
   * 构建提交审查信息
   */
  public Map<String, Object> buildSubmissionReview(Store store, Map<String, Object> submission) {
    Map<String, Object> exam = find(store.exams, str(submission, "examId"));
    Map<String, Object> paper = exam == null ? null : find(store.papers, str(exam, "paperId"));
    int score = asInt(submission.get("finalScore"));
    int passScore = asInt(paper == null ? 0 : paper.get("passScore"));
    List<Map<String, Object>> finished = store.submissions.stream()
      .filter(s -> Objects.equals(str(s, "examId"), str(submission, "examId")) && COMPLETED.equals(str(s, "status")))
      .sorted(java.util.Comparator.comparingInt((Map<String, Object> s) -> asInt(s.get("finalScore"))).reversed())
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
    review.put("usedTimeText", buildUsedTimeText(submission));
    Instant startedAt = parseInstant(str(submission, "startedAt")).orElse(null);
    Instant submittedAt = parseInstant(str(submission, "submittedAt")).orElse(null);
    if (startedAt != null && submittedAt != null) {
      long usedMs = submittedAt.toEpochMilli() - startedAt.toEpochMilli();
      int durationMin = asInt(paper == null ? 0 : paper.get("durationMinutes"));
      if (durationMin > 0) review.put("timeUsageRate", Math.round(usedMs / 1000.0 / (durationMin * 60.0) * 100));
    }
    return review;
  }

  /**
   * 同步错题本
   */
  public void syncWrongBook(Store store, Map<String, Object> submission) {
    wrongBookService.syncFromSubmission(store, submission);
  }

  /**
   * 比较答案
   */
  public CompareResult compare(Map<String, Object> question, Object rawAnswer, boolean allowSubjectiveAuto) {
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

  public List<String> normalizeAnswer(Object raw) {
    List<String> values = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object value : list) if (value != null && !String.valueOf(value).trim().isBlank()) values.add(String.valueOf(value).trim());
    } else if (raw != null && !String.valueOf(raw).trim().isBlank()) {
      values.add(String.valueOf(raw).trim());
    }
    return values;
  }

  public String norm(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
  }

  public String buildUsedTimeText(Map<String, Object> submission) {
    Instant startedAt = parseInstant(str(submission, "startedAt")).orElse(null);
    Instant submittedAt = parseInstant(str(submission, "submittedAt")).orElse(null);
    if (startedAt == null || submittedAt == null) return null;
    long usedMs = submittedAt.toEpochMilli() - startedAt.toEpochMilli();
    return formatUsedMs(usedMs);
  }

  public String formatUsedMs(long ms) {
    long totalSeconds = ms / 1000;
    long hours = totalSeconds / 3600;
    long minutes = (totalSeconds % 3600) / 60;
    long seconds = totalSeconds % 60;
    if (hours > 0) return hours + "\u5c0f\u65f6" + minutes + "\u5206" + seconds + "\u79d2";
    if (minutes > 0) return minutes + "\u5206" + seconds + "\u79d2";
    return seconds + "\u79d2";
  }

  public record CompareResult(List<String> answer, Boolean correct) {}

  private Optional<Instant> parseInstant(String value) {
    if (value == null || value.isBlank()) return Optional.empty();
    try { return Optional.of(Instant.parse(value)); } catch (Exception e) { return Optional.empty(); }
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
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

  private String now() {
    return Instant.now().toString();
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
