package com.onlineexam.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户偏好分析服务 — 根据历史对话推理用户学习偏好，提供个性化推荐
 */
@Service
public class UserPreferenceService {

  private final JdbcTemplate jdbc;

  // Subject keyword mapping for preference detection
  private static final Map<String, List<String>> SUBJECT_KEYWORDS = new LinkedHashMap<>();
  static {
    SUBJECT_KEYWORDS.put("高等数学", List.of("微积分", "线性代数", "概率论", "矩阵", "极限", "导数", "积分", "级数", "拉普拉斯", "泰勒", "洛必达", "行列式"));
    SUBJECT_KEYWORDS.put("大学物理", List.of("力学", "电磁学", "热学", "光学", "量子", "相对论", "牛顿", "电场", "磁场", "波动"));
    SUBJECT_KEYWORDS.put("大学语文", List.of("诗词", "文言文", "文学", "写作", "作文", "阅读", "鉴赏", "古文", "散文", "诗歌"));
    SUBJECT_KEYWORDS.put("大学英语", List.of("英语", "词汇", "语法", "阅读", "翻译", "听力", "口语", "English", "grammar", "vocabulary"));
    SUBJECT_KEYWORDS.put("计算机基础", List.of("数据结构", "操作系统", "计算机网络", "编程", "算法", "数据库", "C语言", "Java", "Python", "TCP", "HTTP"));
    SUBJECT_KEYWORDS.put("马克思主义", List.of("唯物辩证法", "剩余价值", "科学社会主义", "马克思", "恩格斯", "辩证法", "历史唯物主义", "资本论"));
    SUBJECT_KEYWORDS.put("政治", List.of("中国特色社会主义", "时政", "思想道德", "政治", "社会主义", "改革开放", "新时代"));
    SUBJECT_KEYWORDS.put("中国近现代史", List.of("鸦片战争", "辛亥革命", "五四运动", "抗日战争", "改革开放", "近代史", "现代史", "新中国成立"));
    SUBJECT_KEYWORDS.put("线性代数", List.of("矩阵", "向量", "行列式", "特征值", "线性方程", "秩", "空间"));
    SUBJECT_KEYWORDS.put("概率统计", List.of("概率", "统计", "分布", "期望", "方差", "假设检验", "回归"));
  }

  // General interest patterns (broader categories)
  private static final Map<String, Pattern> INTEREST_PATTERNS = new LinkedHashMap<>();
  static {
    INTEREST_PATTERNS.put("理科", Pattern.compile("数学|物理|化学|生物|统计|计算|工程|力|电|磁|光|热"));
    INTEREST_PATTERNS.put("文科", Pattern.compile("语文|历史|政治|哲学|文学|写作|法律|经济|社会"));
    INTEREST_PATTERNS.put("工科", Pattern.compile("编程|算法|数据|网络|系统|设计|架构|开发|软件|硬件"));
    INTEREST_PATTERNS.put("考研", Pattern.compile("考研|复习|真题|模拟|冲刺|备考|初试|复试"));
    INTEREST_PATTERNS.put("英语", Pattern.compile("英语|四六级|雅思|托福|词汇|语法|翻译|阅读"));
  }

  public UserPreferenceService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 分析用户偏好 — 根据历史对话内容推理用户学习方向和兴趣点
   */
  public ResponseEntity<?> analyzePreferences(String userId) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated."));
    }

    try {
      // Fetch recent user messages (last 100) to analyze preferences
      List<Map<String, Object>> userMessages = jdbc.queryForList(
        "SELECT m.content FROM chat_message m " +
        "JOIN chat_conversation c ON m.conversation_id = c.id " +
        "WHERE c.user_id = ? AND m.role = 'user' " +
        "ORDER BY m.created_at DESC LIMIT 100",
        userId);

      // Collect all user text
      StringBuilder allText = new StringBuilder();
      for (Map<String, Object> msg : userMessages) {
        String content = msg.get("content") != null ? String.valueOf(msg.get("content")) : "";
        if (!content.isBlank()) {
          allText.append(content).append(" ");
        }
      }

      String text = allText.toString();

      // Detect subject preferences
      Map<String, Integer> subjectScores = new LinkedHashMap<>();
      for (Map.Entry<String, List<String>> entry : SUBJECT_KEYWORDS.entrySet()) {
        String subject = entry.getKey();
        int score = 0;
        for (String keyword : entry.getValue()) {
          score += countOccurrences(text, keyword);
        }
        if (score > 0) {
          subjectScores.put(subject, score);
        }
      }

      // Sort by score descending, take top 5
      List<Map<String, Object>> subjects = new ArrayList<>();
      subjectScores.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .limit(5)
        .forEach(e -> {
          Map<String, Object> s = new LinkedHashMap<>();
          s.put("name", e.getKey());
          s.put("score", e.getValue());
          subjects.add(s);
        });

      // Detect interest categories
      List<String> interests = new ArrayList<>();
      for (Map.Entry<String, Pattern> entry : INTEREST_PATTERNS.entrySet()) {
        if (entry.getValue().matcher(text).find()) {
          interests.add(entry.getKey());
        }
      }

      // Generate personalized suggestion chips
      List<Map<String, Object>> suggestions = generateSuggestions(subjects, interests, userMessages.size());

      // Generate practice topic suggestions
      List<Map<String, Object>> practiceTopics = generatePracticeTopics(subjects, interests);

      // Extract recent question themes (last 20 messages)
      List<String> recentThemes = extractRecentThemes(userMessages, 20);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("subjects", subjects);
      result.put("interests", interests);
      result.put("suggestions", suggestions);
      result.put("practiceTopics", practiceTopics);
      result.put("recentThemes", recentThemes);
      result.put("messageCount", userMessages.size());

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      // Return empty preferences on error — frontend falls back to defaults
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("subjects", List.of());
      empty.put("interests", List.of());
      empty.put("suggestions", generateDefaultSuggestions());
      empty.put("practiceTopics", generateDefaultPracticeTopics());
      empty.put("recentThemes", List.of());
      empty.put("messageCount", 0);
      return ResponseEntity.ok(empty);
    }
  }

  /**
   * 生成个性化对话建议芯片
   */
  private List<Map<String, Object>> generateSuggestions(List<Map<String, Object>> subjects,
                                                          List<String> interests,
                                                          int messageCount) {
    List<Map<String, Object>> suggestions = new ArrayList<>();

    if (subjects.isEmpty()) {
      // No history — return defaults
      return generateDefaultSuggestions();
    }

    // Based on top subjects
    for (int i = 0; i < Math.min(3, subjects.size()); i++) {
      String subject = (String) subjects.get(i).get("name");
      Map<String, Object> chip = new LinkedHashMap<>();
      chip.put("text", getSubjectQuestion(subject));
      chip.put("category", subject);
      chip.put("type", "chat");
      suggestions.add(chip);
    }

    // Based on interests
    if (interests.contains("考研")) {
      suggestions.add(Map.of("text", "考研复习有什么好的学习方法？", "category", "考研", "type", "chat"));
    }
    if (interests.contains("英语")) {
      suggestions.add(Map.of("text", "如何提高英语阅读理解能力？", "category", "英语", "type", "chat"));
    }

    // Follow-up on recent themes
    if (suggestions.size() < 6) {
      suggestions.add(Map.of("text", "帮我总结之前学过的重点知识", "category", "复习", "type", "chat"));
    }

    return suggestions.subList(0, Math.min(6, suggestions.size()));
  }

  /**
   * 生成个性化练题推荐
   */
  private List<Map<String, Object>> generatePracticeTopics(List<Map<String, Object>> subjects,
                                                             List<String> interests) {
    List<Map<String, Object>> topics = new ArrayList<>();

    if (subjects.isEmpty()) {
      return generateDefaultPracticeTopics();
    }

    for (int i = 0; i < Math.min(4, subjects.size()); i++) {
      String subject = (String) subjects.get(i).get("name");
      Map<String, Object> topic = new LinkedHashMap<>();
      topic.put("icon", getSubjectIcon(subject));
      topic.put("label", subject);
      topic.put("prompt", getSubjectPracticePrompt(subject));
      topics.add(topic);
    }

    // Add a mixed review if user has multiple subjects
    if (subjects.size() >= 2) {
      String s1 = (String) subjects.get(0).get("name");
      String s2 = (String) subjects.get(1).get("name");
      topics.add(Map.of(
        "icon", "🔀",
        "label", s1 + " + " + s2 + " 综合",
        "prompt", "帮我出5道" + s1 + "和" + s2 + "综合练习题，单选题为主，附详细解析"
      ));
    }

    return topics.subList(0, Math.min(8, topics.size()));
  }

  /**
   * 提取最近对话的主题关键词
   */
  private List<String> extractRecentThemes(List<Map<String, Object>> messages, int limit) {
    Set<String> themes = new LinkedHashSet<>(); // deduplicate
    int count = 0;
    for (Map<String, Object> msg : messages) {
      if (count >= limit) break;
      String content = msg.get("content") != null ? String.valueOf(msg.get("content")) : "";
      if (!content.isBlank()) {
        // Extract meaningful phrases (simple heuristic: look for quoted or emphasized terms)
        Matcher m = Pattern.compile("[「《]([^」》]+)[」》]").matcher(content);
        while (m.find()) {
          themes.add(m.group(1));
        }
        // Also look for subject-like keywords
        for (String subject : SUBJECT_KEYWORDS.keySet()) {
          if (content.contains(subject)) {
            themes.add(subject);
          }
        }
      }
      count++;
    }
    return new ArrayList<>(themes).subList(0, Math.min(10, themes.size()));
  }

  private int countOccurrences(String text, String keyword) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(keyword, idx)) != -1) {
      count++;
      idx += keyword.length();
    }
    return count;
  }

  private String getSubjectQuestion(String subject) {
    return switch (subject) {
      case "高等数学" -> "帮我讲解微积分中的极限与连续的概念";
      case "大学物理" -> "电磁学中麦克斯韦方程组的物理含义是什么？";
      case "大学语文" -> "如何鉴赏古诗词中的意境与情感表达？";
      case "大学英语" -> "英语写作中如何避免常见的语法错误？";
      case "计算机基础" -> "数据结构中二叉树的遍历算法有哪些？";
      case "马克思主义" -> "唯物辩证法的核心观点是什么？";
      case "政治" -> "新时代中国特色社会主义的基本方略是什么？";
      case "中国近现代史" -> "五四运动的历史意义是什么？";
      case "线性代数" -> "矩阵的特征值和特征向量如何计算？";
      case "概率统计" -> "正态分布的特点和应用场景是什么？";
      default -> "帮我讲解" + subject + "中的核心知识点";
    };
  }

  private String getSubjectIcon(String subject) {
    return switch (subject) {
      case "高等数学" -> "📐";
      case "大学物理" -> "⚛️";
      case "大学语文" -> "📖";
      case "大学英语" -> "🇬🇧";
      case "计算机基础" -> "💻";
      case "马克思主义" -> "🏛️";
      case "政治" -> "🗳️";
      case "中国近现代史" -> "📜";
      case "线性代数" -> "📊";
      case "概率统计" -> "📈";
      default -> "📚";
    };
  }

  private String getSubjectPracticePrompt(String subject) {
    return switch (subject) {
      case "高等数学" -> "帮我出5道高等数学单选题，涵盖微积分、极限、导数，难度中等偏难，附详细解析";
      case "大学物理" -> "帮我出5道大学物理单选题，涵盖力学、电磁学，附详细解析";
      case "大学语文" -> "帮我出5道大学语文单选题，涵盖诗词鉴赏、文学常识，附详细解析";
      case "大学英语" -> "帮我出5道大学英语单选题，涵盖词汇辨析、语法结构，附详细解析";
      case "计算机基础" -> "帮我出5道计算机基础单选题，涵盖数据结构、操作系统，附详细解析";
      case "马克思主义" -> "帮我出5道马克思主义基本原理单选题，附详细解析";
      case "政治" -> "帮我出5道政治理论单选题，附详细解析";
      case "中国近现代史" -> "帮我出5道中国近现代史单选题，附详细解析";
      case "线性代数" -> "帮我出5道线性代数单选题，涵盖矩阵、向量、行列式，附详细解析";
      case "概率统计" -> "帮我出5道概率统计单选题，涵盖概率分布、期望方差，附详细解析";
      default -> "帮我出5道" + subject + "单选题，附详细解析";
    };
  }

  private List<Map<String, Object>> generateDefaultSuggestions() {
    return List.of(
      Map.of("text", "光合作用的基本原理是什么？", "category", "生物", "type", "chat"),
      Map.of("text", "如何提高学习效率？", "category", "学习方法", "type", "chat"),
      Map.of("text", "什么是勾股定理？", "category", "数学", "type", "chat"),
      Map.of("text", "太阳系有哪些行星？", "category", "天文", "type", "chat"),
      Map.of("text", "怎样写好一篇作文？", "category", "语文", "type", "chat"),
      Map.of("text", "帮我制定一个学习计划", "category", "学习规划", "type", "chat")
    );
  }

  private List<Map<String, Object>> generateDefaultPracticeTopics() {
    return List.of(
      Map.of("icon", "📐", "label", "高等数学", "prompt", "帮我出5道高等数学单选题，涵盖微积分、线性代数，难度较难，附详细解析"),
      Map.of("icon", "📖", "label", "大学语文", "prompt", "帮我出5道大学语文单选题，涵盖诗词鉴赏、文言文阅读、文学常识，附详细解析"),
      Map.of("icon", "💻", "label", "计算机基础", "prompt", "帮我出5道计算机基础单选题，涵盖数据结构、操作系统、计算机网络，附详细解析"),
      Map.of("icon", "🇬🇧", "label", "大学英语", "prompt", "帮我出5道大学英语单选题，涵盖词汇辨析、语法结构、阅读理解，附详细解析"),
      Map.of("icon", "🏛️", "label", "马克思主义", "prompt", "帮我出5道马克思主义基本原理单选题，附详细解析"),
      Map.of("icon", "⚛️", "label", "大学物理", "prompt", "帮我出5道大学物理单选题，涵盖力学、电磁学，附详细解析"),
      Map.of("icon", "🗳️", "label", "政治", "prompt", "帮我出5道政治理论单选题，附详细解析"),
      Map.of("icon", "📜", "label", "中国近现代史", "prompt", "帮我出5道中国近现代史单选题，附详细解析")
    );
  }
}
