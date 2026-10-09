package com.onlineexam.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reconcile explicit original-option labels with the actual option values sent by the student UI. */
final class ChoiceAnswers {
  private static final Pattern PRACTICE_KEY = Pattern.compile("^([A-D])(?:\\.\\s*|$)");
  private ChoiceAnswers() {}

  static boolean hasUniquePracticeKeys(List<String> options) {
    HashSet<String> keys = new HashSet<>();
    for (String option : options) {
      var matcher = PRACTICE_KEY.matcher(option);
      if (!keys.add(matcher.find() ? matcher.group(1) : option)) return false;
    }
    return true;
  }

  static List<String> validate(List<String> options, List<String> answers, String type) {
    if (answers.isEmpty()) throw new IllegalArgumentException("参考答案不能为空");
    if (("single".equals(type) || "judge".equals(type)) && answers.size() != 1) {
      throw new IllegalArgumentException("单选题或判断题必须只有一个答案");
    }
    List<String> values = new ArrayList<>();
    for (String answer : answers) {
      int exact = options.indexOf(answer);
      int label = labelIndex(answer, options.size());
      if (exact >= 0 && label >= 0 && exact != label) {
        throw new IllegalArgumentException("答案与选项字母存在歧义，请明确选项内容");
      }
      if (exact >= 0) values.add(options.get(exact));
      else if (label >= 0) values.add(options.get(label));
      else {
        // Also match content without option prefix
        int contentMatch = -1;
        String cleanAns = choiceContent(answer);
        for (int i = 0; i < options.size(); i++) {
          if (choiceContent(options.get(i)).equalsIgnoreCase(cleanAns)) {
            contentMatch = i;
            break;
          }
        }
        if (contentMatch >= 0) {
          values.add(options.get(contentMatch));
        } else if ("judge".equals(type)) {
          if (isJudgeTrue(answer) && options.size() >= 2) {
            values.add(options.get(0));
          } else if (isJudgeFalse(answer) && options.size() >= 2) {
            values.add(options.get(1));
          } else {
            throw new IllegalArgumentException("参考答案必须对应已有选项");
          }
        } else {
          throw new IllegalArgumentException("参考答案必须对应已有选项");
        }
      }
    }
    if (new HashSet<>(values).size() != values.size()) {
      throw new IllegalArgumentException("参考答案不能重复");
    }
    return values;
  }

  static List<String> forScoring(Object rawOptions, List<String> answers) {
    List<String> options = new ArrayList<>();
    if (rawOptions instanceof List<?> list) {
      for (Object option : list) options.add(String.valueOf(option).trim());
    }
    List<String> values = new ArrayList<>();
    for (String answer : answers) {
      // Exact text retains the existing semantics of historical questions.
      if (options.contains(answer)) values.add(answer);
      else {
        int index = labelIndex(answer, options.size());
        if (index >= 0) {
          values.add(options.get(index));
        } else {
          int contentMatch = -1;
          String cleanAns = choiceContent(answer);
          for (int i = 0; i < options.size(); i++) {
            if (choiceContent(options.get(i)).equalsIgnoreCase(cleanAns)) {
              contentMatch = i;
              break;
            }
          }
          if (contentMatch >= 0) {
            values.add(options.get(contentMatch));
          } else if (isJudgeTrue(answer) && options.size() >= 2) {
            values.add(options.get(0));
          } else if (isJudgeFalse(answer) && options.size() >= 2) {
            values.add(options.get(1));
          } else {
            values.add(answer);
          }
        }
      }
    }
    return values;
  }

  static String choiceContent(String option) {
    if (option == null) return "";
    String text = option.trim();
    String prev = "";
    while (!text.equals(prev)) {
      prev = text;
      text = text.replaceFirst("^[\\(\\[（【][A-Za-z0-9][\\)\\]）】]\\s*", "")
                 .replaceFirst("^[A-Za-z0-9][.、:：)）]\\s*", "")
                 .trim();
    }
    return text;
  }

  private static boolean isJudgeTrue(String text) {
    String s = choiceContent(text).toLowerCase(Locale.ROOT);
    return s.matches("^(对|正确|true|t|√|1)$");
  }

  private static boolean isJudgeFalse(String text) {
    String s = choiceContent(text).toLowerCase(Locale.ROOT);
    return s.matches("^(错|错误|false|f|×|0)$");
  }

  private static int labelIndex(String answer, int count) {
    String label = answer.toUpperCase(Locale.ROOT);
    if (!label.matches("[A-D][.、:：)）]?")) return -1;
    int index = label.charAt(0) - 'A';
    return index < count ? index : -1;
  }
}
