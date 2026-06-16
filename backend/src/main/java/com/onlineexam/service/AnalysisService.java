package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
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
import org.springframework.stereotype.Service;

/**
 * 分析服务 - 处理成绩趋势、知识点雷达和题目分析
 */
@Service
public class AnalysisService {

  private static final String COMPLETED = "\u5df2\u5b8c\u6210";

  private final StoreService storeService;

  public AnalysisService(StoreService storeService) {
    this.storeService = storeService;
  }

  /**
   * 成绩趋势
   */
  public ResponseEntity<?> scoreTrend(String userId, Store store) {
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Map<String, Object>> mySubmissions = store.submissions.stream()
      .filter(s -> Objects.equals(str(s, "studentId"), userId) && COMPLETED.equals(str(s, "status")))
      .sorted(Comparator.comparing(s -> str(s, "submittedAt"))).toList();
    List<Map<String, Object>> trend = new ArrayList<>();
    for (Map<String, Object> s : mySubmissions) {
      Map<String, Object> exam = find(store.exams, str(s, "examId"));
      Map<String, Object> paper = exam == null ? null : find(store.papers, str(exam, "paperId"));
      trend.add(mapOf("examName", exam == null ? "-" : str(exam, "name"),
        "score", asInt(s.get("finalScore")), "totalScore", asInt(paper == null ? 0 : paper.get("totalScore")),
        "passScore", asInt(paper == null ? 0 : paper.get("passScore")), "submittedAt", s.get("submittedAt")));
    }
    return ResponseEntity.ok(mapOf("trend", trend));
  }

  /**
   * 知识点雷达
   */
  public ResponseEntity<?> knowledgeRadar(String userId, Store store) {
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Map<String, Object>> mySubmissions = store.submissions.stream()
      .filter(s -> Objects.equals(str(s, "studentId"), userId) && COMPLETED.equals(str(s, "status"))).toList();
    Map<String, int[]> subjectStats = new LinkedHashMap<>();
    Map<String, Map<String, int[]>> kpBySubject = new LinkedHashMap<>();
    for (Map<String, Object> s : mySubmissions) {
      for (Object detailRaw : asList(s.get("answerDetail"))) {
        Map<String, Object> detail = asMap(detailRaw);
        String subject = str(detail, "subject");
        String kp = str(detail, "knowledgePoint");
        subjectStats.computeIfAbsent(subject, k -> new int[2]);
        subjectStats.get(subject)[0]++;
        if (Boolean.TRUE.equals(detail.get("correct"))) subjectStats.get(subject)[1]++;
        kpBySubject.computeIfAbsent(subject, k -> new LinkedHashMap<>()).computeIfAbsent(kp, k -> new int[2]);
        kpBySubject.get(subject).get(kp)[0]++;
        if (Boolean.TRUE.equals(detail.get("correct"))) kpBySubject.get(subject).get(kp)[1]++;
      }
    }
    List<Map<String, Object>> subjectMastery = new ArrayList<>();
    subjectStats.forEach((subject, counts) -> subjectMastery.add(mapOf("subject", subject,
      "totalQuestions", counts[0], "correctQuestions", counts[1],
      "mastery", counts[0] > 0 ? Math.round(counts[1] * 100.0 / counts[0]) : 0)));
    List<Map<String, Object>> kpMastery = new ArrayList<>();
    kpBySubject.forEach((subject, kps) -> kps.forEach((kp, counts) -> kpMastery.add(mapOf("subject", subject,
      "knowledgePoint", kp, "totalQuestions", counts[0], "correctQuestions", counts[1],
      "mastery", counts[0] > 0 ? Math.round(counts[1] * 100.0 / counts[0]) : 0))));
    return ResponseEntity.ok(mapOf("subjectMastery", subjectMastery, "knowledgePointMastery", kpMastery));
  }

  /**
   * 考试题目分析
   */
  public ResponseEntity<?> questionAnalysis(String userId, String examId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = find(store.exams, examId);
    if (exam == null || !Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> paper = find(store.papers, str(exam, "paperId"));
    List<Map<String, Object>> examSubmissions = store.submissions.stream()
      .filter(s -> Objects.equals(str(s, "examId"), examId) && COMPLETED.equals(str(s, "status"))).toList();
    List<Map<String, Object>> results = new ArrayList<>();
    for (Object rawId : asList(paper == null ? null : paper.get("questionIds"))) {
      Map<String, Object> q = find(store.questions, String.valueOf(rawId));
      if (q == null) continue;
      int totalAttempts = 0;
      int correctCount = 0;
      for (Map<String, Object> submission : examSubmissions) {
        for (Object detailRaw : asList(submission.get("answerDetail"))) {
          Map<String, Object> detail = asMap(detailRaw);
          if (Objects.equals(str(detail, "questionId"), str(q, "id"))) {
            totalAttempts++;
            if (Boolean.TRUE.equals(detail.get("correct"))) correctCount++;
          }
        }
      }
      double correctRate = totalAttempts > 0 ? Math.round(correctCount * 1000.0 / totalAttempts) / 10.0 : 0;
      results.add(mapOf("questionId", str(q, "id"), "title", str(q, "title"), "type", str(q, "type"),
        "subject", str(q, "subject"), "knowledgePoint", str(q, "knowledgePoint"),
        "totalAttempts", totalAttempts, "correctCount", correctCount, "correctRate", correctRate));
    }
    Map<String, int[]> kpStats = new LinkedHashMap<>();
    for (Map<String, Object> r : results) {
      String kp = str(r, "knowledgePoint");
      kpStats.computeIfAbsent(kp, k -> new int[2]);
      kpStats.get(kp)[0] += asInt(r.get("totalAttempts"));
      kpStats.get(kp)[1] += asInt(r.get("correctCount"));
    }
    List<Map<String, Object>> kpAnalysis = new ArrayList<>();
    kpStats.forEach((kp, counts) -> kpAnalysis.add(mapOf("knowledgePoint", kp, "totalAttempts", counts[0],
      "correctCount", counts[1], "correctRate", counts[0] > 0 ? Math.round(counts[1] * 1000.0 / counts[0]) / 10.0 : 0)));
    kpAnalysis.sort(Comparator.comparingDouble(a -> (double) asInt(a.get("correctRate"))));
    return ResponseEntity.ok(mapOf("questions", results, "knowledgePointAnalysis", kpAnalysis));
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
