package com.onlineexam.service;

import java.math.BigDecimal;
import java.util.*;

/** Pure final-grade construction: request errors are distinct from inconsistent server-owned grading data. */
public final class ManualGradePolicy {
  private ManualGradePolicy() { }

  public static Map<String,Object> apply(Map<String,Object> submission,Object requestedScores,List<Map<String,Object>> frozenQuestions) {
    if (!(requestedScores instanceof Map<?,?> scores)) throw new IllegalArgumentException("Scores must be an explicit object.");
    Map<String,Integer> bounds = new LinkedHashMap<>();
    for (Map<String,Object> question : frozenQuestions) {
      String id = text(question.get("id"));
      Integer full = integer(question.get("score"));
      if (id.isBlank() || full == null || full <= 0 || bounds.putIfAbsent(id,full) != null) throw corrupt();
    }
    if (bounds.isEmpty()) throw corrupt();
    for (Object key : scores.keySet()) {
      if (!(key instanceof String id) || !bounds.containsKey(id)) throw new IllegalArgumentException("Scores contain an unknown question.");
    }
    Object rawDetails = submission.get("answerDetail");
    if (!(rawDetails instanceof List<?> saved) || saved.size() != bounds.size()) throw corrupt();
    Set<String> seen = new HashSet<>();
    List<Object> details = new ArrayList<>();
    int total = 0;
    for (Object raw : saved) {
      if (!(raw instanceof Map<?,?> item)) throw corrupt();
      String id = text(item.get("questionId"));
      Integer full = integer(item.get("fullScore"));
      if (id.isBlank() || !seen.add(id) || full == null || !Objects.equals(full,bounds.get(id))) throw corrupt();
      Integer score;
      if (scores.containsKey(id)) {
        score = integer(scores.get(id));
        if (score == null || score < 0 || score > full) throw new IllegalArgumentException("Scores must be exact integers between zero and the frozen full score.");
      } else {
        score = integer(item.get("score"));
        if (score == null || score < 0 || score > full) throw corrupt();
      }
      Map<String,Object> detail = new LinkedHashMap<>();
      for (var entry : item.entrySet()) {
        if (!(entry.getKey() instanceof String key)) throw corrupt();
        detail.put(key,entry.getValue());
      }
      if (scores.containsKey(id)) {
        detail.put("score",score);
        detail.put("correct",score.equals(full));
      }
      try { total = Math.addExact(total,score); }
      catch (ArithmeticException overflow) { throw corrupt(); }
      details.add(detail);
    }
    Map<String,Object> result = new LinkedHashMap<>(submission);
    result.put("answerDetail",details);
    result.put("finalScore",total);
    result.put("status","已完成");
    return result;
  }

  private static Integer integer(Object raw) {
    try {
      if (raw instanceof Number) return new BigDecimal(raw.toString()).intValueExact();
      if (raw instanceof String value) return Integer.parseInt(value);
      return null;
    } catch (ArithmeticException | NumberFormatException invalid) { return null; }
  }
  private static String text(Object raw) { return raw instanceof String value ? value : ""; }
  private static org.springframework.web.server.ResponseStatusException corrupt() {
    return ExamContent.unavailable("提交的评分明细与冻结试题不一致或无法校验，请恢复原始明细后重试；本次未改动成绩。");
  }
}
