package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ClassAnalysisController {

  private final StoreService storeService;

  public ClassAnalysisController(StoreService storeService) {
    this.storeService = storeService;
  }

  /**
   * 获取指定考试的班级维度分析数据
   * 返回每个班级的: 人数、平均分、最高分、最低分、通过率、各分数段人数分布
   */
  @GetMapping("/exams/{examId}/class-analysis")
  public ResponseEntity<?> classAnalysis(
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @PathVariable String examId) {
    Store store = storeService.readStore();
    Map<String, Object> user = findUser(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> exam = findById(store.exams, examId);
    if (exam == null || !Objects.equals(str(exam, "teacherId"), userId))
      return error(HttpStatus.FORBIDDEN, "Forbidden.");

    Map<String, Object> paper = findById(store.papers, str(exam, "paperId"));
    int passScore = paper != null ? asInt(paper.get("passScore")) : 60;
    int totalScore = paper != null ? asInt(paper.get("totalScore")) : 100;

    // 获取目标班级
    Set<String> targetClassIds = new HashSet<>(asList(exam.get("targetClassIds")).stream().map(String::valueOf).toList());

    // 获取该考试的所有提交
    List<Map<String, Object>> examSubmissions = store.submissions.stream()
        .filter(s -> Objects.equals(str(s, "examId"), examId)).toList();

    // 按班级分组
    Map<String, List<Map<String, Object>>> classStudentMap = new LinkedHashMap<>();
    for (Map<String, Object> student : store.users) {
      if (!isRole(student, "student")) continue;
      String classId = str(student, "classId");
      if (!targetClassIds.contains(classId)) continue;
      classStudentMap.computeIfAbsent(classId, k -> new ArrayList<>()).add(student);
    }

    List<Map<String, Object>> classResults = new ArrayList<>();
    for (Map.Entry<String, List<Map<String, Object>>> entry : classStudentMap.entrySet()) {
      String classId = entry.getKey();
      List<Map<String, Object>> students = entry.getValue();
      Map<String, Object> classInfo = findById(store.classes, classId);
      String className = classInfo != null ? str(classInfo, "name") : "未知班级";

      List<Integer> scores = new ArrayList<>();
      int notStartedCount = 0;
      int runningCount = 0;
      int passedCount = 0;

      // 分数段分布: 0-59, 60-69, 70-79, 80-89, 90-100
      int[] distribution = new int[5];

      for (Map<String, Object> student : students) {
        Map<String, Object> submission = examSubmissions.stream()
            .filter(s -> Objects.equals(str(s, "studentId"), str(student, "id")))
            .findFirst().orElse(null);

        if (submission == null) {
          notStartedCount++;
          continue;
        }
        String status = str(submission, "status");
        if ("进行中".equals(status)) {
          runningCount++;
          continue;
        }
        if (submission.get("finalScore") == null && submission.get("autoScore") == null) continue;

        int score = asInt(submission.get("finalScore") != null ? submission.get("finalScore") : submission.get("autoScore"));
        scores.add(score);

        if (score >= passScore) passedCount++;

        // 分数段
        int pct = totalScore > 0 ? (int) ((score * 100.0) / totalScore) : score;
        if (pct < 60) distribution[0]++;
        else if (pct < 70) distribution[1]++;
        else if (pct < 80) distribution[2]++;
        else if (pct < 90) distribution[3]++;
        else distribution[4]++;
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("classId", classId);
      result.put("className", className);
      result.put("totalStudents", students.size());
      result.put("submittedCount", scores.size());
      result.put("notStartedCount", notStartedCount);
      result.put("runningCount", runningCount);
      result.put("avgScore", scores.isEmpty() ? null : Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0) * 10.0) / 10.0);
      result.put("maxScore", scores.isEmpty() ? null : Collections.max(scores));
      result.put("minScore", scores.isEmpty() ? null : Collections.min(scores));
      result.put("passRate", scores.isEmpty() ? 0 : Math.round(passedCount * 1000.0 / scores.size()) / 10.0);
      result.put("distribution", mapOf(
          "fail", distribution[0],
          "d60", distribution[1],
          "d70", distribution[2],
          "d80", distribution[3],
          "d90", distribution[4]
      ));

      // 题目正确率（找出该考试试卷中的题目，统计各班级的正确率）
      if (paper != null) {
        List<Object> questionIds = asList(paper.get("questionIds"));
        List<Map<String, Object>> questionStats = new ArrayList<>();
        for (Object qid : questionIds) {
          String questionId = String.valueOf(qid);
          Map<String, Object> question = findById(store.questions, questionId);
          if (question == null) continue;

          int correctCount = 0;
          int attemptCount = 0;
          for (Map<String, Object> student : students) {
            Map<String, Object> sub = examSubmissions.stream()
                .filter(s -> Objects.equals(str(s, "studentId"), str(student, "id")))
                .findFirst().orElse(null);
            if (sub == null) continue;
            List<Map<String, Object>> details = asListOfMaps(sub.get("answerDetail"));
            for (Map<String, Object> detail : details) {
              if (Objects.equals(str(detail, "questionId"), questionId)) {
                attemptCount++;
                if (asBool(detail.get("correct"))) correctCount++;
                break;
              }
            }
          }
          Map<String, Object> qs = new LinkedHashMap<>();
          qs.put("questionId", questionId);
          qs.put("title", str(question, "title").length() > 30 ? str(question, "title").substring(0, 30) + "..." : str(question, "title"));
          qs.put("type", str(question, "type"));
          qs.put("knowledgePoint", str(question, "knowledgePoint"));
          qs.put("correctRate", attemptCount > 0 ? Math.round(correctCount * 1000.0 / attemptCount) / 10.0 : 0);
          qs.put("attemptCount", attemptCount);
          questionStats.add(qs);
        }
        result.put("questionStats", questionStats);
      }

      classResults.add(result);
    }

    return ResponseEntity.ok(mapOf(
        "examName", str(exam, "name"),
        "totalScore", totalScore,
        "passScore", passScore,
        "classes", classResults
    ));
  }

  // Helper methods
  private Map<String, Object> findUser(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(r -> Objects.equals(str(r, "id"), id)).findFirst().orElse(null);
  }

  private Map<String, Object> findById(List<Map<String, Object>> rows, String id) {
    if (id == null || id.isEmpty()) return null;
    return rows.stream().filter(r -> Objects.equals(str(r, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object raw) {
    return raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
  }

  private List<Object> asList(Object raw) {
    return raw instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> asListOfMaps(Object raw) {
    if (raw instanceof List<?> list) {
      return list.stream().filter(Map.class::isInstance).map(m -> (Map<String, Object>) m).toList();
    }
    return List.of();
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

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
