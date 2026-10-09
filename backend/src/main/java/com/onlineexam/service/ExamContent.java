package com.onlineexam.service;

import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Server-owned publication content. Never attach the raw version to a public exam map. */
public final class ExamContent {
  private ExamContent() {}

  /** Read-only history lookup; active delivery/mutation must continue to use store.exams. */
  public static Map<String,Object> examForHistory(Store store,String examId) {
    Map<String,Object> active = find(store.exams,examId);
    Map<String,Object> exam = active != null ? active : store.archivedExams.get(examId);
    return exam == null ? null : copyMap(exam);
  }

  public static java.util.Set<String> ownedHistoryExamIds(Store store,String teacherId) {
    java.util.Set<String> ids = new java.util.HashSet<>();
    java.util.stream.Stream.concat(store.exams.stream(),store.archivedExams.values().stream())
        .filter(exam -> Objects.equals(text(exam,"teacherId"),teacherId)).forEach(exam -> ids.add(text(exam,"id")));
    return ids;
  }

  public static Map<String, Object> capture(Store store, Map<String, Object> exam) {
    Map<String, Object> paper = find(store.papers, text(exam, "paperId"));
    if (paper == null) throw unavailable("发布失败：试卷不存在或已删除");
    if (!Objects.equals(text(paper, "teacherId"), text(exam, "teacherId"))) {
      throw unavailable("发布失败：试卷不属于当前教师");
    }
    List<?> ids = list(paper.get("questionIds"));
    if (ids.isEmpty() || ids.stream().map(String::valueOf).distinct().count() != ids.size()) {
      throw unavailable("发布失败：试卷题目为空或存在重复引用");
    }
    List<Map<String, Object>> questions = new ArrayList<>();
    int totalScore = 0;
    for (Object id : ids) {
      Map<String, Object> question = find(store.questions, String.valueOf(id));
      if (question == null) throw unavailable("发布失败：试卷包含不存在或已删除的题目");
      if (!Objects.equals(text(question, "teacherId"), text(exam, "teacherId"))) {
        throw unavailable("发布失败：试卷包含其他教师的题目");
      }
      int score = number(question.get("score"));
      if (score <= 0) throw unavailable("发布失败：题目分值必须大于零");
      totalScore = Math.addExact(totalScore, score);
      questions.add(copyMap(question));
    }
    if (totalScore != number(paper.get("totalScore"))) {
      throw unavailable("发布失败：试卷总分与题目分值合计不一致");
    }
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("schemaVersion", 1);
    content.put("paper", copyMap(paper));
    content.put("questions", questions);
    return content;
  }

  public static String status(Store store, Map<String, Object> exam) {
    if (store.examSnapshots.containsKey(text(exam, "id"))) return "FROZEN";
    boolean hasSubmissions = store.submissions.stream()
        .anyMatch(submission -> Objects.equals(text(submission, "examId"), text(exam, "id")));
    return Boolean.TRUE.equals(exam.get("published")) || hasSubmissions ? "MISSING" : "DRAFT";
  }

  public static Map<String, Object> paper(Store store, Map<String, Object> exam) {
    Map<String, Object> version = version(store, exam);
    if (version != null) return copyMap(asMap(version.get("paper")));
    Map<String, Object> paper = find(store.papers, text(exam, "paperId"));
    return paper == null ? null : copyMap(paper);
  }

  /** Listing/history can still display saved scores without pretending missing content is current. */
  public static Map<String, Object> displayPaper(Store store, Map<String, Object> exam) {
    return "MISSING".equals(status(store, exam)) ? null : paper(store, exam);
  }

  public static List<Map<String, Object>> questions(Store store, Map<String, Object> exam) {
    Map<String, Object> version = version(store, exam);
    List<Map<String, Object>> result = new ArrayList<>();
    if (version != null) {
      for (Object raw : list(version.get("questions"))) result.add(copyMap(asMap(raw)));
      return result;
    }
    Map<String, Object> paper = paper(store, exam);
    for (Object id : list(paper == null ? null : paper.get("questionIds"))) {
      Map<String, Object> question = find(store.questions, String.valueOf(id));
      if (question != null) result.add(copyMap(question));
    }
    return result;
  }

  private static Map<String, Object> version(Store store, Map<String, Object> exam) {
    Map<String, Object> version = store.examSnapshots.get(text(exam, "id"));
    if (version == null) {
      if ("MISSING".equals(status(store, exam))) {
        throw unavailable("考试缺少发布版本，不能使用当前题库代替；请联系教师恢复原始版本");
      }
      return null;
    }
    if (number(version.get("schemaVersion")) != 1 || !(version.get("paper") instanceof Map<?, ?>)
        || !(version.get("questions") instanceof List<?>)) {
      throw unavailable("考试发布版本损坏，请联系管理员恢复");
    }
    Map<String, Object> paper = asMap(version.get("paper"));
    List<?> ids = list(paper.get("questionIds"));
    List<?> questions = list(version.get("questions"));
    if (!Objects.equals(text(paper, "id"), text(exam, "paperId")) || ids.isEmpty()
        || ids.size() != questions.size()) throw unavailable("考试发布版本不完整，请联系管理员恢复");
    for (int i = 0; i < ids.size(); i++) {
      if (!(questions.get(i) instanceof Map<?, ?>)
          || !Objects.equals(String.valueOf(ids.get(i)), text(asMap(questions.get(i)), "id"))) {
        throw unavailable("考试发布版本题目不完整，请联系管理员恢复");
      }
    }
    return version;
  }

  public static ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  public static Map<String, Object> copyMap(Map<String, Object> input) {
    Map<String, Object> copy = new LinkedHashMap<>();
    input.forEach((key, value) -> copy.put(key, copyValue(value)));
    return copy;
  }

  private static Object copyValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, item) -> result.put(String.valueOf(key), copyValue(item)));
      return result;
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      for (Object item : list) result.add(copyValue(item));
      return result;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }

  private static List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
  private static String text(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value == null ? "" : String.valueOf(value);
  }
  private static int number(Object value) {
    if (value instanceof Number number) return number.intValue();
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ignored) { return 0; }
  }
  private static Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    return rows.stream().filter(row -> Objects.equals(text(row, "id"), id)).findFirst().orElse(null);
  }
}
