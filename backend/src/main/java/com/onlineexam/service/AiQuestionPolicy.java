package com.onlineexam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An AI result is only a candidate until its structure and grading contract have been validated. */
final class AiQuestionPolicy {
  private static final Set<String> TYPES = Set.of("single", "multiple", "judge", "fill", "short", "coding");
  private static final Set<String> CHOICES = Set.of("single", "multiple", "judge");
  private AiQuestionPolicy() {}

  static List<Map<String, Object>> parsePreview(String response, ObjectMapper mapper, String subject,
      String knowledgePoint, String difficulty, String type) {
    if (response == null || response.isBlank()) throw new IllegalArgumentException("AI 未返回题目内容");
    String content = response.trim();
    if (content.startsWith("```")) {
      int newline = content.indexOf('\n');
      if (newline < 0 || !content.endsWith("```")) throw new IllegalArgumentException("AI 返回格式不是有效 JSON 数组");
      content = content.substring(newline + 1, content.length() - 3).trim();
    }
    List<?> entries;
    try {
      entries = mapper.readerFor(new TypeReference<List<Object>>() {})
          .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(content);
    } catch (Exception e) {
      throw new IllegalArgumentException("AI 返回格式不是有效 JSON 题目数组", e);
    }
    if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("AI 未生成有效题目");
    List<Map<String, Object>> questions = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      if (!(entries.get(i) instanceof Map<?, ?> candidate)) {
        throw new IllegalArgumentException("AI 第 " + (i + 1) + " 条题目格式错误");
      }
      try {
        Map<String, Object> preview = normalize(candidate, subject, knowledgePoint, difficulty, type);
        if (CHOICES.contains(preview.get("type"))) {
          List<?> options = (List<?>) preview.get("options");
          List<String> labels = new ArrayList<>();
          for (Object answer : (List<?>) preview.get("answer")) {
            labels.add(String.valueOf((char) ('A' + options.indexOf(answer))));
          }
          preview.put("answer", labels); // Existing preview/practice clients consume explicit letters.
        }
        questions.add(preview);
      }
      catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("AI 第 " + (i + 1) + " 条题目不合格：" + e.getMessage(), e);
      }
    }
    return questions;
  }

  static Map<String, Object> normalize(Map<?, ?> raw, String fallbackSubject, String fallbackPoint,
      String fallbackDifficulty, String fallbackType) {
    String title = text(raw.get("title"), "", "题干");
    if (title.isBlank()) throw new IllegalArgumentException("题干不能为空");
    String type = text(raw.get("type"), defaultText(fallbackType, "single"), "题型");
    if (!TYPES.contains(type)) throw new IllegalArgumentException("不支持的题型");
    String difficulty = text(raw.get("difficulty"), defaultText(fallbackDifficulty, "medium"), "难度");
    difficulty = switch (difficulty) {
      case "易", "简单" -> "easy";
      case "中", "中等" -> "medium";
      case "难", "困难" -> "hard";
      default -> difficulty;
    };
    if (!Set.of("easy", "medium", "hard").contains(difficulty)) throw new IllegalArgumentException("不支持的难度");
    List<String> options = options(raw.get("options"));
    List<String> answers = answers(raw.get("answer"));
    if (answers.isEmpty()) throw new IllegalArgumentException("参考答案不能为空");
    if (CHOICES.contains(type)) {
      if (options.size() < 2 || options.size() > 4 || ("judge".equals(type) && options.size() != 2)) {
        throw new IllegalArgumentException("选择题必须有 2 至 4 个选项，判断题必须有 2 个选项");
      }
      List<String> contents = options.stream().map(AiQuestionPolicy::choiceContent).toList();
      if (contents.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("选项内容不能为空");
      if (contents.stream().distinct().count() != options.size()) throw new IllegalArgumentException("选项内容不能重复");
      for (int i = 0; i < options.size(); i++) {
        String option = options.get(i);
        if (option.matches("^[A-Da-d][.、)）:].*")
            && Character.toUpperCase(option.charAt(0)) != 'A' + i) {
          throw new IllegalArgumentException("选项字母必须与选项顺序一致");
        }
      }
      List<String> resolvedAnswers = ChoiceAnswers.validate(options, answers, type);
      List<String> canonicalOptions = new ArrayList<>();
      for (int i = 0; i < contents.size(); i++) {
        canonicalOptions.add((char) ('A' + i) + ". " + contents.get(i));
      }
      List<String> canonicalAnswers = new ArrayList<>();
      for (String answer : resolvedAnswers) canonicalAnswers.add(canonicalOptions.get(options.indexOf(answer)));
      // One compatible encoding for both preview radio keys and bank option values; content/order stay intact.
      options = canonicalOptions;
      answers = canonicalAnswers;
    } else {
      if (!options.isEmpty()) throw new IllegalArgumentException("非选择题不能包含选项");
      if ("fill".equals(type) && answers.size() != 1) {
        throw new IllegalArgumentException("当前填空评分只支持一个参考答案");
      }
    }
    int score = score(raw.get("score"));
    Map<String, Object> question = new LinkedHashMap<>();
    question.put("subject", text(raw.get("subject"), defaultText(fallbackSubject, "AI生成"), "学科"));
    question.put("knowledgePoint", text(raw.get("knowledgePoint"), defaultText(fallbackPoint, "综合"), "知识点"));
    question.put("difficulty", difficulty);
    question.put("type", type);
    question.put("title", title);
    question.put("options", options);
    question.put("answer", answers);
    question.put("score", score);
    question.put("explanation", text(raw.get("explanation"), "暂无解析", "解析"));
    question.put("sourceTag", "ai-generated");
    return question;
  }

  private static String choiceContent(String option) {
    return ChoiceAnswers.choiceContent(option);
  }

  private static String defaultText(String text, String fallback) { return text == null || text.isBlank() ? fallback : text.trim(); }
  private static String text(Object raw, String fallback, String name) {
    if (raw == null) return fallback;
    if (!(raw instanceof String value)) throw new IllegalArgumentException(name + "必须是文本");
    return value.isBlank() ? fallback : value.trim();
  }
  private static List<String> options(Object raw) {
    if (raw == null) return new ArrayList<>();
    if (!(raw instanceof List<?> values)) throw new IllegalArgumentException("选项必须是数组");
    List<String> result = new ArrayList<>();
    for (Object value : values) {
      if (!(value instanceof String option) || option.isBlank()) throw new IllegalArgumentException("选项必须是非空文本");
      result.add(option.trim());
    }
    return result;
  }
  private static List<String> answers(Object raw) {
    List<?> values = raw == null ? List.of() : raw instanceof List<?> list ? list : List.of(raw);
    List<String> result = new ArrayList<>();
    for (Object value : values) {
      if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
        throw new IllegalArgumentException("参考答案必须是文本或标量");
      }
      String answer = String.valueOf(value).trim();
      if (answer.isBlank()) throw new IllegalArgumentException("参考答案不能为空");
      result.add(answer);
    }
    return result;
  }
  private static int score(Object raw) {
    if (raw == null) return 5;
    try {
      int value = new BigDecimal(String.valueOf(raw)).intValueExact();
      if (value > 0) return value;
    } catch (NumberFormatException | ArithmeticException ignored) { }
    throw new IllegalArgumentException("分值必须是正整数");
  }
}
