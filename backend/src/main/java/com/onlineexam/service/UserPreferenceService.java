package com.onlineexam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户偏好与画像服务 — 基于多源数据（对话、考试、错题、练习、行为日志）
 * 构建用户画像，集成AI大模型进行智能分析与预测，提供个性化推荐
 */
@Service
public class UserPreferenceService {

  private static final Logger log = LoggerFactory.getLogger(UserPreferenceService.class);

  private final JdbcTemplate jdbc;
  private final AiService aiService;
  private final ObjectMapper objectMapper;

  // 用户画像缓存: userId -> profile data (避免频繁AI调用)
  private final ConcurrentHashMap<String, CachedProfile> profileCache = new ConcurrentHashMap<>();
  private static final long PROFILE_CACHE_TTL_MS = 10 * 60 * 1000; // 10分钟

  // 推荐缓存: userId -> recommendations (实时更新)
  private final ConcurrentHashMap<String, CachedRecommendations> recommendationCache = new ConcurrentHashMap<>();
  private static final long RECOMMENDATION_CACHE_TTL_MS = 5 * 60 * 1000; // 5分钟

  // 科目关键词映射
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

  // 兴趣分类模式
  private static final Map<String, Pattern> INTEREST_PATTERNS = new LinkedHashMap<>();
  static {
    INTEREST_PATTERNS.put("理科", Pattern.compile("数学|物理|化学|生物|统计|计算|工程|力|电|磁|光|热"));
    INTEREST_PATTERNS.put("文科", Pattern.compile("语文|历史|政治|哲学|文学|写作|法律|经济|社会"));
    INTEREST_PATTERNS.put("工科", Pattern.compile("编程|算法|数据|网络|系统|设计|架构|开发|软件|硬件"));
    INTEREST_PATTERNS.put("考研", Pattern.compile("考研|复习|真题|模拟|冲刺|备考|初试|复试"));
    INTEREST_PATTERNS.put("英语", Pattern.compile("英语|四六级|雅思|托福|词汇|语法|翻译|阅读"));
  }

  private record CachedProfile(Map<String, Object> profile, long timestamp) {}
  private record CachedRecommendations(List<Map<String, Object>> recommendations, long timestamp) {}

  public UserPreferenceService(JdbcTemplate jdbc, AiService aiService, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.aiService = aiService;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void initTables() {
    try {
      jdbc.execute("CREATE TABLE IF NOT EXISTS user_behavior_log (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, action VARCHAR(50) NOT NULL, target_type VARCHAR(50), target_id VARCHAR(64), detail JSON, duration_ms INT DEFAULT 0, created_at DATETIME(3) NOT NULL, INDEX idx_behavior_user (user_id), INDEX idx_behavior_action (action), INDEX idx_behavior_time (created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception e) { log.warn("创建user_behavior_log表失败: {}", e.getMessage()); }
    try {
      jdbc.execute("CREATE TABLE IF NOT EXISTS user_profile (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL UNIQUE, learning_style VARCHAR(20) DEFAULT 'balanced', difficulty_preference VARCHAR(20) DEFAULT 'medium', activity_level VARCHAR(20) DEFAULT 'moderate', subject_affinity JSON, knowledge_gaps JSON, study_patterns JSON, ai_summary TEXT, profile_version INT DEFAULT 1, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, INDEX idx_profile_user (user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception e) { log.warn("创建user_profile表失败: {}", e.getMessage()); }
    try {
      jdbc.execute("CREATE TABLE IF NOT EXISTS recommendation_feedback (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, recommendation_type VARCHAR(50) NOT NULL, recommendation_content JSON, feedback_type VARCHAR(20) NOT NULL, feedback_detail TEXT, created_at DATETIME(3) NOT NULL, INDEX idx_feedback_user (user_id), INDEX idx_feedback_type (recommendation_type), INDEX idx_feedback_time (created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception e) { log.warn("创建recommendation_feedback表失败: {}", e.getMessage()); }
    // 修复旧表中 recommendation_content NOT NULL 约束
    try {
      jdbc.execute("ALTER TABLE recommendation_feedback MODIFY COLUMN recommendation_content JSON");
    } catch (Exception e) { /* 列已是可空或表不存在，忽略 */ }
  }

  // ========== 用户行为记录 ==========

  /**
   * 记录用户行为日志
   */
  public ResponseEntity<?> logBehavior(String userId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated."));
    }

    String action = str(body, "action");
    if (action.isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "缺少action参数"));
    }

    String id = "beh-" + System.currentTimeMillis() + "-" + Integer.toHexString((int)(Math.random() * 0xffffff));
    String targetType = str(body, "targetType");
    String targetId = str(body, "targetId");
    String detailJson = null;
    try {
      Object detail = body.get("detail");
      if (detail != null) {
        detailJson = objectMapper.writeValueAsString(detail);
      }
    } catch (Exception e) { /* ignore */ }
    int durationMs = asInt(body.get("durationMs"));

    jdbc.update(
      "INSERT INTO user_behavior_log (id, user_id, action, target_type, target_id, detail, duration_ms, created_at) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
      id, userId, action, targetType.isBlank() ? null : targetType, targetId.isBlank() ? null : targetId,
      detailJson, durationMs, LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
    );

    // 行为记录后，使推荐缓存失效（实现实时更新）
    recommendationCache.remove(userId);

    return ResponseEntity.ok(Map.of("logged", true));
  }

  // ========== 用户画像构建 ==========

  /**
   * 获取或构建用户画像 — 综合多源数据分析
   */
  public ResponseEntity<?> getUserProfile(String userId) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated."));
    }

    // 检查缓存
    CachedProfile cached = profileCache.get(userId);
    if (cached != null && (System.currentTimeMillis() - cached.timestamp()) < PROFILE_CACHE_TTL_MS) {
      return ResponseEntity.ok(cached.profile());
    }

    try {
      Map<String, Object> profile = buildUserProfile(userId);

      // 尝试持久化画像到数据库
      saveProfileToDb(userId, profile);

      // 更新缓存
      profileCache.put(userId, new CachedProfile(profile, System.currentTimeMillis()));

      return ResponseEntity.ok(profile);
    } catch (Exception e) {
      // 降级：返回基础画像
      Map<String, Object> fallback = buildBasicProfile(userId);
      return ResponseEntity.ok(fallback);
    }
  }

  /**
   * 构建完整用户画像 — 从多源数据采集并分析
   */
  private Map<String, Object> buildUserProfile(String userId) {
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("userId", userId);
    profile.put("generatedAt", Instant.now().toString());

    // 1. 从对话历史分析学习偏好
    Map<String, Object> chatAnalysis = analyzeChatHistory(userId);
    profile.put("chatAnalysis", chatAnalysis);

    // 2. 从考试提交分析学科能力
    Map<String, Object> examAnalysis = analyzeExamPerformance(userId);
    profile.put("examAnalysis", examAnalysis);

    // 3. 从错题本分析知识薄弱点
    Map<String, Object> wrongBookAnalysis = analyzeWrongBook(userId);
    profile.put("wrongBookAnalysis", wrongBookAnalysis);

    // 4. 从练习记录分析学习模式
    Map<String, Object> practiceAnalysis = analyzePracticeRecords(userId);
    profile.put("practiceAnalysis", practiceAnalysis);

    // 5. 从行为日志分析使用习惯
    Map<String, Object> behaviorAnalysis = analyzeBehaviorLogs(userId);
    profile.put("behaviorAnalysis", behaviorAnalysis);

    // 6. 综合分析：科目亲和度
    List<Map<String, Object>> subjectAffinity = computeSubjectAffinity(
      chatAnalysis, examAnalysis, wrongBookAnalysis, practiceAnalysis
    );
    profile.put("subjectAffinity", subjectAffinity);

    // 7. 综合分析：知识薄弱点
    List<Map<String, Object>> knowledgeGaps = computeKnowledgeGaps(
      examAnalysis, wrongBookAnalysis, practiceAnalysis
    );
    profile.put("knowledgeGaps", knowledgeGaps);

    // 8. 学习风格与难度偏好
    Map<String, Object> learningPreferences = computeLearningPreferences(
      behaviorAnalysis, practiceAnalysis, examAnalysis
    );
    profile.put("learningPreferences", learningPreferences);

    // 9. 使用AI大模型生成画像摘要（异步降级）
    String aiSummary = generateAiProfileSummary(profile);
    profile.put("aiSummary", aiSummary);

    return profile;
  }

  /**
   * 分析对话历史 — 提取学习主题和兴趣
   */
  private Map<String, Object> analyzeChatHistory(String userId) {
    Map<String, Object> result = new LinkedHashMap<>();
    try {
      List<Map<String, Object>> userMessages = jdbc.queryForList(
        "SELECT m.content, m.created_at FROM chat_message m " +
        "JOIN chat_conversation c ON m.conversation_id = c.id " +
        "WHERE c.user_id = ? AND m.role = 'user' " +
        "ORDER BY m.created_at DESC LIMIT 200",
        userId);

      StringBuilder allText = new StringBuilder();
      for (Map<String, Object> msg : userMessages) {
        String content = msg.get("content") != null ? String.valueOf(msg.get("content")) : "";
        if (!content.isBlank()) allText.append(content).append(" ");
      }
      String text = allText.toString();

      // 科目偏好评分
      Map<String, Integer> subjectScores = new LinkedHashMap<>();
      for (Map.Entry<String, List<String>> entry : SUBJECT_KEYWORDS.entrySet()) {
        int score = 0;
        for (String keyword : entry.getValue()) {
          score += countOccurrences(text, keyword);
        }
        if (score > 0) subjectScores.put(entry.getKey(), score);
      }

      // 兴趣分类
      List<String> interests = new ArrayList<>();
      for (Map.Entry<String, Pattern> entry : INTEREST_PATTERNS.entrySet()) {
        if (entry.getValue().matcher(text).find()) interests.add(entry.getKey());
      }

      // 最近对话主题
      List<String> recentThemes = extractRecentThemes(userMessages, 20);

      result.put("messageCount", userMessages.size());
      result.put("subjectScores", subjectScores.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .limit(5)
        .collect(Collectors.toList()));
      result.put("interests", interests);
      result.put("recentThemes", recentThemes);
    } catch (Exception e) {
      result.put("messageCount", 0);
      result.put("subjectScores", List.of());
      result.put("interests", List.of());
      result.put("recentThemes", List.of());
    }
    return result;
  }

  /**
   * 分析考试表现 — 学科能力评估
   */
  private Map<String, Object> analyzeExamPerformance(String userId) {
    Map<String, Object> result = new LinkedHashMap<>();
    try {
      // 获取学生的考试提交记录
      List<Map<String, Object>> submissions = jdbc.queryForList(
        "SELECT s.exam_id, s.final_score, s.status, s.submitted_at, " +
        "e.name as exam_name FROM submission s " +
        "JOIN exam e ON s.exam_id = e.id " +
        "WHERE s.student_id = ? AND s.status = 'submitted' " +
        "ORDER BY s.submitted_at DESC LIMIT 50",
        userId);

      // 按科目统计正确率
      Map<String, int[]> subjectStats = new LinkedHashMap<>(); // subject -> [total, correct]
      List<Map<String, Object>> examResults = new ArrayList<>();

      for (Map<String, Object> sub : submissions) {
        Map<String, Object> examResult = new LinkedHashMap<>();
        examResult.put("examId", sub.get("exam_id"));
        examResult.put("examName", sub.get("exam_name"));
        examResult.put("score", sub.get("final_score"));
        examResult.put("submittedAt", sub.get("submitted_at"));
        examResults.add(examResult);
      }

      // 通过answer_detail分析各科目表现
      List<Map<String, Object>> detailedSubmissions = jdbc.queryForList(
        "SELECT s.answer_detail_json, s.final_score FROM submission s " +
        "WHERE s.student_id = ? AND s.answer_detail_json IS NOT NULL " +
        "ORDER BY s.submitted_at DESC LIMIT 20",
        userId);

      Map<String, int[]> knowledgeStats = new LinkedHashMap<>(); // knowledgePoint -> [total, correct]

      for (Map<String, Object> sub : detailedSubmissions) {
        try {
          String detailJson = String.valueOf(sub.get("answer_detail_json"));
          if (detailJson == null || "null".equals(detailJson)) continue;
          List<Map<String, Object>> details = objectMapper.readValue(detailJson,
            new TypeReference<List<Map<String, Object>>>() {});
          for (Map<String, Object> detail : details) {
            String subject = str(detail, "subject");
            String kp = str(detail, "knowledgePoint");
            boolean correct = asInt(detail.get("score")) >= asInt(detail.get("fullScore"));

            if (!subject.isBlank()) {
              subjectStats.computeIfAbsent(subject, k -> new int[]{0, 0});
              subjectStats.get(subject)[0]++;
              if (correct) subjectStats.get(subject)[1]++;
            }
            if (!kp.isBlank()) {
              knowledgeStats.computeIfAbsent(kp, k -> new int[]{0, 0});
              knowledgeStats.get(kp)[0]++;
              if (correct) knowledgeStats.get(kp)[1]++;
            }
          }
        } catch (Exception e) { /* skip malformed */ }
      }

      // 科目能力评估
      List<Map<String, Object>> subjectAbilities = new ArrayList<>();
      for (Map.Entry<String, int[]> entry : subjectStats.entrySet()) {
        Map<String, Object> ability = new LinkedHashMap<>();
        ability.put("subject", entry.getKey());
        ability.put("totalQuestions", entry.getValue()[0]);
        ability.put("correctCount", entry.getValue()[1]);
        ability.put("accuracy", entry.getValue()[0] > 0
          ? Math.round((double) entry.getValue()[1] / entry.getValue()[0] * 100) : 0);
        subjectAbilities.add(ability);
      }
      subjectAbilities.sort((a, b) -> (int) b.get("accuracy") - (int) a.get("accuracy"));

      // 知识点掌握度
      List<Map<String, Object>> knowledgeMastery = new ArrayList<>();
      for (Map.Entry<String, int[]> entry : knowledgeStats.entrySet()) {
        Map<String, Object> mastery = new LinkedHashMap<>();
        mastery.put("knowledgePoint", entry.getKey());
        mastery.put("totalQuestions", entry.getValue()[0]);
        mastery.put("correctCount", entry.getValue()[1]);
        mastery.put("accuracy", entry.getValue()[0] > 0
          ? Math.round((double) entry.getValue()[1] / entry.getValue()[0] * 100) : 0);
        knowledgeMastery.add(mastery);
      }
      knowledgeMastery.sort((a, b) -> (int) a.get("accuracy") - (int) b.get("accuracy"));

      result.put("examCount", submissions.size());
      result.put("examResults", examResults);
      result.put("subjectAbilities", subjectAbilities);
      result.put("knowledgeMastery", knowledgeMastery.stream().limit(10).collect(Collectors.toList()));
    } catch (Exception e) {
      result.put("examCount", 0);
      result.put("examResults", List.of());
      result.put("subjectAbilities", List.of());
      result.put("knowledgeMastery", List.of());
    }
    return result;
  }

  /**
   * 分析错题本 — 识别知识薄弱点
   */
  private Map<String, Object> analyzeWrongBook(String userId) {
    Map<String, Object> result = new LinkedHashMap<>();
    try {
      List<Map<String, Object>> wrongEntries = jdbc.queryForList(
        "SELECT subject, knowledge_point, type, wrong_count, retry_count, " +
        "last_retry_correct, last_wrong_at FROM wrong_book_entry " +
        "WHERE student_id = ? AND status = 'active' " +
        "ORDER BY last_wrong_at DESC LIMIT 100",
        userId);

      // 按科目统计错题
      Map<String, int[]> subjectWrong = new LinkedHashMap<>(); // subject -> [total, stillWrong]
      Map<String, int[]> kpWrong = new LinkedHashMap<>(); // knowledgePoint -> [total, stillWrong]
      Map<String, int[]> typeWrong = new LinkedHashMap<>(); // type -> [total, stillWrong]

      for (Map<String, Object> entry : wrongEntries) {
        String subject = String.valueOf(entry.getOrDefault("subject", ""));
        String kp = String.valueOf(entry.getOrDefault("knowledge_point", ""));
        String type = String.valueOf(entry.getOrDefault("type", ""));
        boolean stillWrong = !Boolean.TRUE.equals(entry.get("last_retry_correct"));

        if (!"null".equals(subject) && !subject.isBlank()) {
          subjectWrong.computeIfAbsent(subject, k -> new int[]{0, 0});
          subjectWrong.get(subject)[0]++;
          if (stillWrong) subjectWrong.get(subject)[1]++;
        }
        if (!"null".equals(kp) && !kp.isBlank()) {
          kpWrong.computeIfAbsent(kp, k -> new int[]{0, 0});
          kpWrong.get(kp)[0]++;
          if (stillWrong) kpWrong.get(kp)[1]++;
        }
        if (!"null".equals(type) && !type.isBlank()) {
          typeWrong.computeIfAbsent(type, k -> new int[]{0, 0});
          typeWrong.get(type)[0]++;
          if (stillWrong) typeWrong.get(type)[1]++;
        }
      }

      // 科目错题分布
      List<Map<String, Object>> subjectWrongList = new ArrayList<>();
      for (Map.Entry<String, int[]> entry : subjectWrong.entrySet()) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("subject", entry.getKey());
        item.put("totalWrong", entry.getValue()[0]);
        item.put("stillWrong", entry.getValue()[1]);
        item.put("resolvedRate", entry.getValue()[0] > 0
          ? Math.round((1 - (double) entry.getValue()[1] / entry.getValue()[0]) * 100) : 100);
        subjectWrongList.add(item);
      }
      subjectWrongList.sort((a, b) -> (int) b.get("totalWrong") - (int) a.get("totalWrong"));

      // 知识点错题分布（薄弱点）
      List<Map<String, Object>> kpWrongList = new ArrayList<>();
      for (Map.Entry<String, int[]> entry : kpWrong.entrySet()) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("knowledgePoint", entry.getKey());
        item.put("totalWrong", entry.getValue()[0]);
        item.put("stillWrong", entry.getValue()[1]);
        item.put("severity", entry.getValue()[1] >= 3 ? "high" : entry.getValue()[1] >= 2 ? "medium" : "low");
        kpWrongList.add(item);
      }
      kpWrongList.sort((a, b) -> (int) b.get("stillWrong") - (int) a.get("stillWrong"));

      result.put("totalWrongCount", wrongEntries.size());
      result.put("subjectWrongDistribution", subjectWrongList);
      result.put("knowledgeWeakPoints", kpWrongList.stream().limit(10).collect(Collectors.toList()));
      result.put("typeWrongDistribution", typeWrong.entrySet().stream()
        .map(e -> Map.of("type", e.getKey(), "count", e.getValue()[0], "stillWrong", e.getValue()[1]))
        .sorted((a, b) -> asInt(b.get("count")) - asInt(a.get("count")))
        .collect(Collectors.toList()));
    } catch (Exception e) {
      result.put("totalWrongCount", 0);
      result.put("subjectWrongDistribution", List.of());
      result.put("knowledgeWeakPoints", List.of());
      result.put("typeWrongDistribution", List.of());
    }
    return result;
  }

  /**
   * 分析练习记录 — 学习模式与频率
   */
  private Map<String, Object> analyzePracticeRecords(String userId) {
    Map<String, Object> result = new LinkedHashMap<>();
    try {
      // 练习记录（存在wrong_book_entry中status='practice'）
      List<Map<String, Object>> practices = jdbc.queryForList(
        "SELECT subject, knowledge_point, type, retry_count, last_retry_correct, last_retry_at " +
        "FROM wrong_book_entry WHERE student_id = ? AND status = 'practice' " +
        "ORDER BY last_retry_at DESC LIMIT 200",
        userId);

      // 练习频率统计
      Map<String, Integer> subjectPractice = new LinkedHashMap<>();
      Map<String, Integer> typePractice = new LinkedHashMap<>();
      int correctCount = 0;

      for (Map<String, Object> p : practices) {
        String subject = String.valueOf(p.getOrDefault("subject", ""));
        String type = String.valueOf(p.getOrDefault("type", ""));
        if (Boolean.TRUE.equals(p.get("last_retry_correct"))) correctCount++;

        if (!"null".equals(subject) && !subject.isBlank()) {
          subjectPractice.merge(subject, 1, Integer::sum);
        }
        if (!"null".equals(type) && !type.isBlank()) {
          typePractice.merge(type, 1, Integer::sum);
        }
      }

      result.put("totalPracticeCount", practices.size());
      result.put("correctRate", practices.size() > 0
        ? Math.round((double) correctCount / practices.size() * 100) : 0);
      result.put("subjectDistribution", subjectPractice.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .map(e -> Map.of("subject", e.getKey(), "count", e.getValue()))
        .collect(Collectors.toList()));
      result.put("typeDistribution", typePractice.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .map(e -> Map.of("type", e.getKey(), "count", e.getValue()))
        .collect(Collectors.toList()));
    } catch (Exception e) {
      result.put("totalPracticeCount", 0);
      result.put("correctRate", 0);
      result.put("subjectDistribution", List.of());
      result.put("typeDistribution", List.of());
    }
    return result;
  }

  /**
   * 分析行为日志 — 使用习惯与活跃度
   */
  private Map<String, Object> analyzeBehaviorLogs(String userId) {
    Map<String, Object> result = new LinkedHashMap<>();
    try {
      // 最近30天的行为日志
      List<Map<String, Object>> logs = jdbc.queryForList(
        "SELECT action, target_type, duration_ms, created_at FROM user_behavior_log " +
        "WHERE user_id = ? AND created_at > DATE_SUB(NOW(), INTERVAL 30 DAY) " +
        "ORDER BY created_at DESC LIMIT 500",
        userId);

      // 行为频率统计
      Map<String, Integer> actionCounts = new LinkedHashMap<>();
      Map<String, Integer> targetCounts = new LinkedHashMap<>();
      long totalDuration = 0;
      int durationCount = 0;

      for (Map<String, Object> log : logs) {
        String action = String.valueOf(log.getOrDefault("action", ""));
        String targetType = String.valueOf(log.getOrDefault("target_type", ""));
        int durationMs = asInt(log.get("duration_ms"));

        if (!"null".equals(action) && !action.isBlank()) {
          actionCounts.merge(action, 1, Integer::sum);
        }
        if (!"null".equals(targetType) && !targetType.isBlank()) {
          targetCounts.merge(targetType, 1, Integer::sum);
        }
        if (durationMs > 0) {
          totalDuration += durationMs;
          durationCount++;
        }
      }

      // 活跃度评估
      String activityLevel = logs.size() >= 100 ? "high" : logs.size() >= 30 ? "moderate" : "low";
      long avgSessionMs = durationCount > 0 ? totalDuration / durationCount : 0;

      result.put("totalActions", logs.size());
      result.put("activityLevel", activityLevel);
      result.put("avgSessionMinutes", Math.round(avgSessionMs / 60000.0));
      result.put("actionDistribution", actionCounts.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .map(e -> Map.of("action", e.getKey(), "count", e.getValue()))
        .collect(Collectors.toList()));
      result.put("targetDistribution", targetCounts.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .map(e -> Map.of("targetType", e.getKey(), "count", e.getValue()))
        .collect(Collectors.toList()));
    } catch (Exception e) {
      result.put("totalActions", 0);
      result.put("activityLevel", "low");
      result.put("avgSessionMinutes", 0);
      result.put("actionDistribution", List.of());
      result.put("targetDistribution", List.of());
    }
    return result;
  }

  // ========== 综合计算方法 ==========

  /**
   * 综合计算科目亲和度 — 融合多源数据
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> computeSubjectAffinity(
      Map<String, Object> chatAnalysis, Map<String, Object> examAnalysis,
      Map<String, Object> wrongBookAnalysis, Map<String, Object> practiceAnalysis) {

    Map<String, double[]> affinityMap = new LinkedHashMap<>(); // subject -> [chatScore, examScore, wrongScore, practiceScore]

    // 从对话分析获取科目偏好
    List<Map<String, Object>> chatSubjects = (List<Map<String, Object>>) chatAnalysis.getOrDefault("subjectScores", List.of());
    for (Map<String, Object> s : chatSubjects) {
      String subject = s.containsKey("subject") ? str(s, "subject") : str(s, "key");
      // 兼容两种格式
      if (subject.isBlank() && s.containsKey("key")) subject = str(s, "key");
      if (subject.isBlank()) {
        // 可能是 Map.Entry 格式
        for (String key : s.keySet()) {
          if (!"value".equals(key)) { subject = key; break; }
        }
      }
      double score = s.containsKey("value") ? asInt(s.get("value")) :
                     s.containsKey("score") ? asInt(s.get("score")) : 1;
      affinityMap.computeIfAbsent(subject, k -> new double[4])[0] = score;
    }

    // 从考试分析获取科目能力
    List<Map<String, Object>> examAbilities = (List<Map<String, Object>>) examAnalysis.getOrDefault("subjectAbilities", List.of());
    for (Map<String, Object> a : examAbilities) {
      String subject = str(a, "subject");
      double accuracy = asInt(a.get("accuracy"));
      affinityMap.computeIfAbsent(subject, k -> new double[4])[1] = accuracy;
    }

    // 从错题分析获取科目关注度（错题多说明关注多但掌握差）
    List<Map<String, Object>> wrongSubjects = (List<Map<String, Object>>) wrongBookAnalysis.getOrDefault("subjectWrongDistribution", List.of());
    for (Map<String, Object> w : wrongSubjects) {
      String subject = str(w, "subject");
      double totalWrong = asInt(w.get("totalWrong"));
      double resolvedRate = asInt(w.get("resolvedRate"));
      // 关注度 = 错题数 * (1 - 解决率/100) + 错题数 * 0.3
      double score = totalWrong * (1 - resolvedRate / 100.0) * 2 + totalWrong * 0.3;
      affinityMap.computeIfAbsent(subject, k -> new double[4])[2] = score;
    }

    // 从练习分析获取科目练习频率
    List<Map<String, Object>> practiceSubjects = (List<Map<String, Object>>) practiceAnalysis.getOrDefault("subjectDistribution", List.of());
    for (Map<String, Object> p : practiceSubjects) {
      String subject = str(p, "subject");
      double count = asInt(p.get("count"));
      affinityMap.computeIfAbsent(subject, k -> new double[4])[3] = count;
    }

    // 综合评分：加权求和
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map.Entry<String, double[]> entry : affinityMap.entrySet()) {
      double[] scores = entry.getValue();
      // 权重：对话0.2, 考试0.3, 错题0.3, 练习0.2
      double composite = scores[0] * 0.2 + scores[1] * 0.3 + scores[2] * 0.3 + scores[3] * 0.2;

      // 趋势判断
      String trend = "stable";
      if (scores[3] > scores[2]) trend = "rising"; // 练习多于错题 → 上升
      else if (scores[2] > scores[3] * 2) trend = "declining"; // 错题远多于练习 → 下降

      Map<String, Object> affinity = new LinkedHashMap<>();
      affinity.put("subject", entry.getKey());
      affinity.put("score", Math.round(composite * 10) / 10.0);
      affinity.put("trend", trend);
      affinity.put("chatInterest", Math.round(scores[0] * 10) / 10.0);
      affinity.put("examAccuracy", Math.round(scores[1] * 10) / 10.0);
      affinity.put("weaknessLevel", Math.round(scores[2] * 10) / 10.0);
      affinity.put("practiceFrequency", Math.round(scores[3] * 10) / 10.0);
      result.add(affinity);
    }

    result.sort((a, b) -> Double.compare(asDouble(b.get("score")), asDouble(a.get("score"))));
    return result.stream().limit(8).collect(Collectors.toList());
  }

  /**
   * 综合计算知识薄弱点
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> computeKnowledgeGaps(
      Map<String, Object> examAnalysis, Map<String, Object> wrongBookAnalysis,
      Map<String, Object> practiceAnalysis) {

    Map<String, double[]> gapMap = new LinkedHashMap<>(); // knowledgePoint -> [wrongScore, examScore, severity]

    // 从错题本获取薄弱知识点
    List<Map<String, Object>> weakPoints = (List<Map<String, Object>>) wrongBookAnalysis.getOrDefault("knowledgeWeakPoints", List.of());
    for (Map<String, Object> wp : weakPoints) {
      String kp = str(wp, "knowledgePoint");
      double stillWrong = asInt(wp.get("stillWrong"));
      String severity = str(wp, "severity");
      double severityScore = "high".equals(severity) ? 3 : "medium".equals(severity) ? 2 : 1;
      gapMap.computeIfAbsent(kp, k -> new double[3]);
      gapMap.get(kp)[0] = stillWrong;
      gapMap.get(kp)[2] = Math.max(gapMap.get(kp)[2], severityScore);
    }

    // 从考试分析获取低掌握度知识点
    List<Map<String, Object>> knowledgeMastery = (List<Map<String, Object>>) examAnalysis.getOrDefault("knowledgeMastery", List.of());
    for (Map<String, Object> km : knowledgeMastery) {
      String kp = str(km, "knowledgePoint");
      double accuracy = asInt(km.get("accuracy"));
      if (accuracy < 60) { // 低于60%视为薄弱
        gapMap.computeIfAbsent(kp, k -> new double[3]);
        gapMap.get(kp)[1] = 100 - accuracy; // 差距越大分数越高
        gapMap.get(kp)[2] = Math.max(gapMap.get(kp)[2], accuracy < 30 ? 3 : accuracy < 50 ? 2 : 1);
      }
    }

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map.Entry<String, double[]> entry : gapMap.entrySet()) {
      double[] scores = entry.getValue();
      Map<String, Object> gap = new LinkedHashMap<>();
      gap.put("knowledgePoint", entry.getKey());
      gap.put("wrongCount", (int) scores[0]);
      gap.put("examWeakness", Math.round(scores[1]));
      gap.put("severity", scores[2] >= 3 ? "high" : scores[2] >= 2 ? "medium" : "low");
      result.add(gap);
    }

    result.sort((a, b) -> {
      int sa = "high".equals(a.get("severity")) ? 3 : "medium".equals(a.get("severity")) ? 2 : 1;
      int sb = "high".equals(b.get("severity")) ? 3 : "medium".equals(b.get("severity")) ? 2 : 1;
      return sb - sa;
    });

    return result.stream().limit(10).collect(Collectors.toList());
  }

  /**
   * 计算学习偏好 — 难度偏好、学习风格
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> computeLearningPreferences(
      Map<String, Object> behaviorAnalysis, Map<String, Object> practiceAnalysis,
      Map<String, Object> examAnalysis) {

    Map<String, Object> prefs = new LinkedHashMap<>();

    // 难度偏好：基于练习正确率
    int practiceCorrectRate = asInt(practiceAnalysis.get("correctRate"));
    String difficultyPreference;
    if (practiceCorrectRate >= 80) difficultyPreference = "hard";
    else if (practiceCorrectRate >= 50) difficultyPreference = "medium";
    else if (practiceCorrectRate > 0) difficultyPreference = "easy";
    else difficultyPreference = "adaptive";
    prefs.put("difficultyPreference", difficultyPreference);

    // 学习风格：基于行为分布
    List<Map<String, Object>> actionDist = (List<Map<String, Object>>) behaviorAnalysis.getOrDefault("actionDistribution", List.of());
    String learningStyle = "balanced";
    if (!actionDist.isEmpty()) {
      String topAction = str(actionDist.get(0), "action");
      if ("chat".equals(topAction)) learningStyle = "reading";
      else if ("practice".equals(topAction)) learningStyle = "kinesthetic";
      else if ("explain_answer".equals(topAction)) learningStyle = "auditory";
      else if ("page_view".equals(topAction)) learningStyle = "visual";
    }
    prefs.put("learningStyle", learningStyle);

    // 活跃度
    prefs.put("activityLevel", behaviorAnalysis.getOrDefault("activityLevel", "moderate"));

    // 偏好题型
    List<Map<String, Object>> typeDist = (List<Map<String, Object>>) practiceAnalysis.getOrDefault("typeDistribution", List.of());
    prefs.put("preferredTypes", typeDist.stream()
      .limit(3)
      .map(t -> str(t, "type"))
      .collect(Collectors.toList()));

    return prefs;
  }

  // ========== AI 画像摘要生成 ==========

  /**
   * 使用AI大模型生成用户画像摘要 — 智能分析与预测
   */
  private String generateAiProfileSummary(Map<String, Object> profile) {
    try {
      String systemPrompt = """
        你是学习分析专家。根据用户的学习数据生成简洁的画像摘要。
        输出JSON格式：{summary:string, strengths:string[], weaknesses:string[], suggestions:string[], predictedNeeds:string[]}
        不要markdown包裹，不要多余文字。""";

      // 精简数据传给AI（避免token过多）
      Map<String, Object> condensedProfile = new LinkedHashMap<>();
      condensedProfile.put("subjectAffinity", profile.get("subjectAffinity"));
      condensedProfile.put("knowledgeGaps", profile.get("knowledgeGaps"));
      condensedProfile.put("learningPreferences", profile.get("learningPreferences"));

      @SuppressWarnings("unchecked")
      Map<String, Object> examAnalysis = (Map<String, Object>) profile.getOrDefault("examAnalysis", Map.of());
      condensedProfile.put("examCount", examAnalysis.getOrDefault("examCount", 0));
      condensedProfile.put("subjectAbilities", examAnalysis.getOrDefault("subjectAbilities", List.of()));

      @SuppressWarnings("unchecked")
      Map<String, Object> wrongAnalysis = (Map<String, Object>) profile.getOrDefault("wrongBookAnalysis", Map.of());
      condensedProfile.put("totalWrongCount", wrongAnalysis.getOrDefault("totalWrongCount", 0));
      condensedProfile.put("knowledgeWeakPoints", wrongAnalysis.getOrDefault("knowledgeWeakPoints", List.of()));

      String profileJson = objectMapper.writeValueAsString(condensedProfile);
      // 限制长度
      if (profileJson.length() > 3000) {
        profileJson = profileJson.substring(0, 3000) + "...(truncated)";
      }

      String userPrompt = "请分析以下用户学习数据，生成画像摘要：\n" + profileJson;
      String aiResponse = aiService.callAiApi(systemPrompt, userPrompt);

      // 解析AI响应
      Map<String, Object> parsed = parseAiJson(aiResponse);
      if (parsed.containsKey("summary")) {
        return objectMapper.writeValueAsString(parsed);
      }
      return aiResponse;
    } catch (Exception e) {
      // AI不可用时生成基础摘要
      return generateBasicSummary(profile);
    }
  }

  /**
   * 基础摘要生成（AI不可用时的降级方案）
   */
  @SuppressWarnings("unchecked")
  private String generateBasicSummary(Map<String, Object> profile) {
    try {
      Map<String, Object> summary = new LinkedHashMap<>();
      List<Map<String, Object>> affinity = (List<Map<String, Object>>) profile.getOrDefault("subjectAffinity", List.of());
      List<Map<String, Object>> gaps = (List<Map<String, Object>>) profile.getOrDefault("knowledgeGaps", List.of());

      if (!affinity.isEmpty()) {
        summary.put("summary", "用户最关注的科目是" + affinity.get(0).get("subject")
          + "，共涉及" + affinity.size() + "个学科领域。");
        summary.put("strengths", affinity.stream()
          .filter(a -> "rising".equals(a.get("trend")))
          .map(a -> a.get("subject") + "（上升趋势）")
          .limit(3).collect(Collectors.toList()));
      } else {
        summary.put("summary", "暂无足够数据生成画像，请继续使用系统积累学习记录。");
        summary.put("strengths", List.of());
      }

      summary.put("weaknesses", gaps.stream()
        .filter(g -> "high".equals(g.get("severity")))
        .map(g -> g.get("knowledgePoint"))
        .limit(3).collect(Collectors.toList()));
      summary.put("suggestions", List.of("建议多做错题重练", "关注薄弱知识点的专项练习"));
      summary.put("predictedNeeds", List.of());

      return objectMapper.writeValueAsString(summary);
    } catch (Exception e) {
      return "{\"summary\":\"数据不足\",\"strengths\":[],\"weaknesses\":[],\"suggestions\":[],\"predictedNeeds\":[]}";
    }
  }

  // ========== 个性化推荐生成 ==========

  /**
   * 获取个性化推荐 — 基于用户画像实时生成
   */
  public ResponseEntity<?> getRecommendations(String userId) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated."));
    }

    // 检查缓存
    CachedRecommendations cached = recommendationCache.get(userId);
    if (cached != null && (System.currentTimeMillis() - cached.timestamp()) < RECOMMENDATION_CACHE_TTL_MS) {
      return ResponseEntity.ok(Map.of("recommendations", cached.recommendations(), "cached", true));
    }

    try {
      Map<String, Object> profile = buildUserProfile(userId);
      List<Map<String, Object>> recommendations = generateRecommendations(profile);

      // 更新缓存
      recommendationCache.put(userId, new CachedRecommendations(recommendations, System.currentTimeMillis()));

      // 使画像缓存也更新
      profileCache.put(userId, new CachedProfile(profile, System.currentTimeMillis()));

      return ResponseEntity.ok(Map.of("recommendations", recommendations, "cached", false));
    } catch (Exception e) {
      // 降级：返回基础推荐
      List<Map<String, Object>> fallback = generateFallbackRecommendations();
      return ResponseEntity.ok(Map.of("recommendations", fallback, "cached", false));
    }
  }

  /**
   * 生成个性化推荐 — 核心推荐算法
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> generateRecommendations(Map<String, Object> profile) {
    List<Map<String, Object>> recommendations = new ArrayList<>();

    List<Map<String, Object>> subjectAffinity = (List<Map<String, Object>>) profile.getOrDefault("subjectAffinity", List.of());
    List<Map<String, Object>> knowledgeGaps = (List<Map<String, Object>>) profile.getOrDefault("knowledgeGaps", List.of());
    Map<String, Object> learningPrefs = (Map<String, Object>) profile.getOrDefault("learningPreferences", Map.of());

    String difficulty = String.valueOf(learningPrefs.getOrDefault("difficultyPreference", "medium"));
    String learningStyle = String.valueOf(learningPrefs.getOrDefault("learningStyle", "balanced"));

    // 1. 薄弱知识点专项练习推荐（优先级最高）
    for (Map<String, Object> gap : knowledgeGaps.stream()
        .filter(g -> "high".equals(g.get("severity")) || "medium".equals(g.get("severity")))
        .limit(3).collect(Collectors.toList())) {
      String kp = str(gap, "knowledgePoint");
      Map<String, Object> rec = new LinkedHashMap<>();
      rec.put("type", "knowledge_gap");
      rec.put("priority", "high");
      rec.put("title", "薄弱知识点强化：" + kp);
      rec.put("description", "检测到你在「" + kp + "」上存在薄弱点，建议进行专项练习");
      rec.put("action", "practice");
      rec.put("prompt", buildGapPracticePrompt(kp, difficulty));
      rec.put("knowledgePoint", kp);
      rec.put("severity", gap.get("severity"));
      recommendations.add(rec);
    }

    // 2. 科目提升推荐（基于亲和度趋势）
    for (Map<String, Object> affinity : subjectAffinity.stream()
        .filter(a -> "declining".equals(a.get("trend")) || "stable".equals(a.get("trend")))
        .limit(2).collect(Collectors.toList())) {
      String subject = str(affinity, "subject");
      Map<String, Object> rec = new LinkedHashMap<>();
      rec.put("type", "subject_review");
      rec.put("priority", "medium");
      rec.put("title", subject + " 巩固提升");
      rec.put("description", "你在「" + subject + "」的表现趋于平稳/下降，建议复习巩固");
      rec.put("action", "practice");
      rec.put("prompt", buildSubjectReviewPrompt(subject, difficulty));
      rec.put("subject", subject);
      rec.put("trend", affinity.get("trend"));
      recommendations.add(rec);
    }

    // 3. 学习建议推荐（基于学习风格）
    Map<String, Object> studyRec = new LinkedHashMap<>();
    studyRec.put("type", "study_plan");
    studyRec.put("priority", "medium");
    studyRec.put("title", "个性化学习建议");
    studyRec.put("description", getStyleBasedAdvice(learningStyle, subjectAffinity));
    studyRec.put("action", "chat");
    studyRec.put("prompt", buildStudyPlanPrompt(subjectAffinity, knowledgeGaps, difficulty));
    recommendations.add(studyRec);

    // 4. 对话建议芯片（基于最近主题和兴趣）
    List<Map<String, Object>> chatSuggestions = generateChatSuggestions(subjectAffinity, knowledgeGaps);
    for (Map<String, Object> suggestion : chatSuggestions) {
      Map<String, Object> rec = new LinkedHashMap<>();
      rec.put("type", "chat");
      rec.put("priority", "low");
      rec.put("title", suggestion.get("text"));
      rec.put("description", "基于你的学习兴趣推荐");
      rec.put("action", "chat");
      rec.put("prompt", suggestion.get("text"));
      rec.put("category", suggestion.get("category"));
      recommendations.add(rec);
    }

    // 5. 错题重练推荐
    if (!knowledgeGaps.isEmpty()) {
      Map<String, Object> rec = new LinkedHashMap<>();
      rec.put("type", "wrongbook_retry");
      rec.put("priority", "high");
      rec.put("title", "错题重练");
      rec.put("description", "你有" + knowledgeGaps.size() + "个薄弱知识点，建议重练相关错题");
      rec.put("action", "wrongbook");
      recommendations.add(rec);
    }

    // 6. 多样性推荐：探索新科目（确保推荐多样性）
    Set<String> coveredSubjects = subjectAffinity.stream()
      .map(a -> str(a, "subject")).collect(Collectors.toSet());
    for (String subject : SUBJECT_KEYWORDS.keySet()) {
      if (!coveredSubjects.contains(subject) && recommendations.size() < 12) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", "explore");
        rec.put("priority", "low");
        rec.put("title", "探索新领域：" + subject);
        rec.put("description", "尝试学习「" + subject + "」，拓展知识面");
        rec.put("action", "practice");
        rec.put("prompt", getSubjectPracticePrompt(subject));
        rec.put("subject", subject);
        recommendations.add(rec);
        break; // 只推荐一个新科目
      }
    }

    // 使用AI优化推荐排序和内容（异步降级）
    try {
      recommendations = aiOptimizeRecommendations(recommendations, profile);
    } catch (Exception e) {
      // AI不可用时保持原始推荐
    }

    return recommendations;
  }

  /**
   * 使用AI大模型优化推荐 — 智能排序与内容增强
   */
  private List<Map<String, Object>> aiOptimizeRecommendations(
      List<Map<String, Object>> recommendations, Map<String, Object> profile) {
    try {
      String systemPrompt = """
        你是推荐系统专家。根据用户画像优化推荐列表的排序和内容。
        返回JSON数组，每个元素包含：type, priority, title, description, action, prompt(如有)。
        确保推荐内容准确、多样、有时效性。不要markdown包裹。""";

      // 精简数据
      Map<String, Object> condensed = new LinkedHashMap<>();
      condensed.put("subjectAffinity", profile.get("subjectAffinity"));
      condensed.put("knowledgeGaps", profile.get("knowledgeGaps"));
      condensed.put("learningPreferences", profile.get("learningPreferences"));
      condensed.put("currentRecommendations", recommendations.stream().limit(6).collect(Collectors.toList()));

      String dataJson = objectMapper.writeValueAsString(condensed);
      if (dataJson.length() > 4000) {
        dataJson = dataJson.substring(0, 4000) + "...(truncated)";
      }

      String userPrompt = "请优化以下推荐列表，调整排序使其更符合用户需求，并增强推荐描述的吸引力：\n" + dataJson;
      String aiResponse = aiService.callAiApi(systemPrompt, userPrompt);

      // 尝试解析AI返回的推荐列表
      String cleaned = aiResponse.trim();
      if (cleaned.startsWith("```")) {
        int idx = cleaned.indexOf('\n');
        if (idx > 0) cleaned = cleaned.substring(idx + 1);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        cleaned = cleaned.trim();
      }
      int arrStart = cleaned.indexOf('[');
      int arrEnd = cleaned.lastIndexOf(']');
      if (arrStart >= 0 && arrEnd > arrStart) {
        List<Map<String, Object>> optimized = objectMapper.readValue(
          cleaned.substring(arrStart, arrEnd + 1),
          new TypeReference<List<Map<String, Object>>>() {});
        if (!optimized.isEmpty()) return optimized;
      }
    } catch (Exception e) { /* 降级 */ }
    return recommendations;
  }

  // ========== 推荐反馈处理 ==========

  /**
   * 记录用户对推荐的反馈
   */
  public ResponseEntity<?> submitFeedback(String userId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated."));
    }

    String recommendationType = str(body, "recommendationType");
    String feedbackType = str(body, "feedbackType");
    if (recommendationType.isBlank() || feedbackType.isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "缺少必要参数"));
    }

    String id = "fb-" + System.currentTimeMillis() + "-" + Integer.toHexString((int)(Math.random() * 0xffffff));
    String contentJson = null;
    try {
      Object content = body.get("recommendationContent");
      if (content != null) contentJson = objectMapper.writeValueAsString(content);
    } catch (Exception e) { /* ignore */ }

    try {
      jdbc.update(
        "INSERT INTO recommendation_feedback (id, user_id, recommendation_type, recommendation_content, " +
        "feedback_type, feedback_detail, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        id, userId, recommendationType, contentJson, feedbackType,
        str(body, "feedbackDetail"),
        LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
      );
    } catch (Exception e) {
      log.error("[UserPreferenceService] 推荐反馈写入失败(表可能不存在): {}", e.getMessage());
      return ResponseEntity.ok(Map.of("submitted", true, "message", "感谢你的反馈，我们将持续优化推荐内容"));
    }

    // 反馈后使推荐和画像缓存失效，下次请求时重新生成
    recommendationCache.remove(userId);
    profileCache.remove(userId);

    // 根据反馈类型调整后续推荐策略
    adjustRecommendationStrategy(userId, feedbackType, recommendationType);

    return ResponseEntity.ok(Map.of("submitted", true, "message", "感谢你的反馈，我们将持续优化推荐内容"));
  }

  /**
   * 根据反馈调整推荐策略
   */
  private void adjustRecommendationStrategy(String userId, String feedbackType, String recommendationType) {
    // 记录行为日志用于后续分析
    String id = "beh-" + System.currentTimeMillis() + "-" + Integer.toHexString((int)(Math.random() * 0xffffff));
    try {
      jdbc.update(
        "INSERT INTO user_behavior_log (id, user_id, action, target_type, detail, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        id, userId, "recommendation_feedback", recommendationType,
        objectMapper.writeValueAsString(Map.of("feedbackType", feedbackType)),
        LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
      );
    } catch (Exception e) {
      log.warn("[UserPreferenceService] 行为日志写入失败: {}", e.getMessage());
    }
  }

  // ========== 兼容旧接口 ==========

  /**
   * 分析用户偏好 — 兼容旧接口，同时返回增强数据
   */
  public ResponseEntity<?> analyzePreferences(String userId) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated."));
    }

    try {
      Map<String, Object> profile = buildUserProfile(userId);

      @SuppressWarnings("unchecked")
      Map<String, Object> chatAnalysis = (Map<String, Object>) profile.getOrDefault("chatAnalysis", Map.of());
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> subjectAffinity = (List<Map<String, Object>>) profile.getOrDefault("subjectAffinity", List.of());

      // 转换为旧接口格式
      List<Map<String, Object>> subjects = subjectAffinity.stream()
        .map(a -> Map.of("name", a.get("subject"), "score", a.get("score")))
        .limit(5).collect(Collectors.toList());

      @SuppressWarnings("unchecked")
      List<String> interests = (List<String>) chatAnalysis.getOrDefault("interests", List.of());
      @SuppressWarnings("unchecked")
      List<String> recentThemes = (List<String>) chatAnalysis.getOrDefault("recentThemes", List.of());

      List<Map<String, Object>> suggestions = generateSuggestions(subjectAffinity, interests,
        asInt(chatAnalysis.get("messageCount")));
      List<Map<String, Object>> practiceTopics = generatePracticeTopics(subjectAffinity, interests);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("subjects", subjects);
      result.put("interests", interests);
      result.put("suggestions", suggestions);
      result.put("practiceTopics", practiceTopics);
      result.put("recentThemes", recentThemes);
      result.put("messageCount", chatAnalysis.getOrDefault("messageCount", 0));
      // 新增：增强数据
      result.put("knowledgeGaps", profile.get("knowledgeGaps"));
      result.put("learningPreferences", profile.get("learningPreferences"));
      result.put("aiSummary", profile.get("aiSummary"));

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("subjects", List.of());
      empty.put("interests", List.of());
      empty.put("suggestions", generateDefaultSuggestions());
      empty.put("practiceTopics", generateDefaultPracticeTopics());
      empty.put("recentThemes", List.of());
      empty.put("messageCount", 0);
      empty.put("knowledgeGaps", List.of());
      empty.put("learningPreferences", Map.of());
      empty.put("aiSummary", "");
      return ResponseEntity.ok(empty);
    }
  }

  // ========== 辅助方法 ==========

  private String buildGapPracticePrompt(String knowledgePoint, String difficulty) {
    String diffChinese = switch (difficulty) {
      case "easy" -> "简单";
      case "hard" -> "困难";
      default -> "中等";
    };
    return "帮我出5道关于「" + knowledgePoint + "」的练习题，难度" + diffChinese + "，附详细解析。重点考察这个知识点的易错之处。";
  }

  private String buildSubjectReviewPrompt(String subject, String difficulty) {
    String diffChinese = switch (difficulty) {
      case "easy" -> "简单偏基础";
      case "hard" -> "较难";
      default -> "中等";
    };
    return "帮我出5道" + subject + "复习题，难度" + diffChinese + "，涵盖核心知识点，附详细解析。";
  }

  private String buildStudyPlanPrompt(List<Map<String, Object>> subjectAffinity,
                                       List<Map<String, Object>> knowledgeGaps, String difficulty) {
    StringBuilder sb = new StringBuilder("请根据我的学习情况制定个性化学习计划：");
    if (!subjectAffinity.isEmpty()) {
      sb.append("我主要学习").append(subjectAffinity.get(0).get("subject"));
      if (subjectAffinity.size() > 1) sb.append("和").append(subjectAffinity.get(1).get("subject"));
    }
    if (!knowledgeGaps.isEmpty()) {
      sb.append("，薄弱知识点有");
      knowledgeGaps.stream().limit(3).forEach(g -> sb.append("「").append(g.get("knowledgePoint")).append("」"));
    }
    return sb.toString();
  }

  private String getStyleBasedAdvice(String learningStyle, List<Map<String, Object>> subjectAffinity) {
    return switch (learningStyle) {
      case "visual" -> "你偏好视觉化学习，建议多使用图表和思维导图整理知识点";
      case "auditory" -> "你偏好听觉学习，建议通过AI对话深入理解概念";
      case "reading" -> "你偏好阅读学习，建议多阅读教材和AI生成的详细解析";
      case "kinesthetic" -> "你偏好实践学习，建议多做练习题和错题重练";
      default -> "建议结合多种学习方式，均衡提升各科能力";
    };
  }

  private List<Map<String, Object>> generateChatSuggestions(
      List<Map<String, Object>> subjectAffinity, List<Map<String, Object>> knowledgeGaps) {
    List<Map<String, Object>> suggestions = new ArrayList<>();

    // 基于薄弱知识点
    for (Map<String, Object> gap : knowledgeGaps.stream().limit(2).collect(Collectors.toList())) {
      String kp = str(gap, "knowledgePoint");
      suggestions.add(Map.of("text", "请详细讲解「" + kp + "」的核心概念和易错点", "category", kp));
    }

    // 基于科目亲和度
    for (Map<String, Object> affinity : subjectAffinity.stream().limit(2).collect(Collectors.toList())) {
      String subject = str(affinity, "subject");
      suggestions.add(Map.of("text", getSubjectQuestion(subject), "category", subject));
    }

    return suggestions.stream().limit(4).collect(Collectors.toList());
  }

  private List<Map<String, Object>> generateSuggestions(List<Map<String, Object>> subjectAffinity,
                                                          List<String> interests, int messageCount) {
    List<Map<String, Object>> suggestions = new ArrayList<>();
    if (subjectAffinity.isEmpty()) return generateDefaultSuggestions();

    for (int i = 0; i < Math.min(3, subjectAffinity.size()); i++) {
      String subject = subjectAffinity.get(i).containsKey("subject") ? String.valueOf(subjectAffinity.get(i).get("subject")) : String.valueOf(subjectAffinity.get(i).get("name"));
      if (subjectAffinity.get(i).containsKey("name")) subject = String.valueOf(subjectAffinity.get(i).get("name"));
      Map<String, Object> chip = new LinkedHashMap<>();
      chip.put("text", getSubjectQuestion(subject));
      chip.put("category", subject);
      chip.put("type", "chat");
      suggestions.add(chip);
    }

    if (interests.contains("考研")) {
      suggestions.add(Map.of("text", "考研复习有什么好的学习方法？", "category", "考研", "type", "chat"));
    }
    if (suggestions.size() < 6) {
      suggestions.add(Map.of("text", "帮我总结之前学过的重点知识", "category", "复习", "type", "chat"));
    }
    return suggestions.subList(0, Math.min(6, suggestions.size()));
  }

  private List<Map<String, Object>> generatePracticeTopics(List<Map<String, Object>> subjectAffinity,
                                                             List<String> interests) {
    List<Map<String, Object>> topics = new ArrayList<>();
    if (subjectAffinity.isEmpty()) return generateDefaultPracticeTopics();

    for (int i = 0; i < Math.min(4, subjectAffinity.size()); i++) {
      String subject = subjectAffinity.get(i).containsKey("subject") ? String.valueOf(subjectAffinity.get(i).get("subject")) : String.valueOf(subjectAffinity.get(i).get("name"));
      if (subjectAffinity.get(i).containsKey("name")) subject = String.valueOf(subjectAffinity.get(i).get("name"));
      Map<String, Object> topic = new LinkedHashMap<>();
      topic.put("icon", getSubjectIcon(subject));
      topic.put("label", subject);
      topic.put("prompt", getSubjectPracticePrompt(subject));
      topics.add(topic);
    }

    if (subjectAffinity.size() >= 2) {
      String s1 = String.valueOf(subjectAffinity.get(0).get("subject" != null ? "subject" : "name"));
      if (subjectAffinity.get(0).containsKey("name")) s1 = String.valueOf(subjectAffinity.get(0).get("name"));
      String s2 = String.valueOf(subjectAffinity.get(1).get("subject" != null ? "subject" : "name"));
      if (subjectAffinity.get(1).containsKey("name")) s2 = String.valueOf(subjectAffinity.get(1).get("name"));
      topics.add(Map.of("icon", "🔀", "label", s1 + " + " + s2 + " 综合",
        "prompt", "帮我出5道" + s1 + "和" + s2 + "综合练习题，单选题为主，附详细解析"));
    }
    return topics.subList(0, Math.min(8, topics.size()));
  }

  private List<Map<String, Object>> generateFallbackRecommendations() {
    List<Map<String, Object>> fallback = new ArrayList<>();
    fallback.add(Map.of("type", "practice", "priority", "medium", "title", "开始练习",
      "description", "选择你感兴趣的科目开始练习", "action", "practice",
      "prompt", "帮我出5道计算机基础单选题，附详细解析"));
    fallback.add(Map.of("type", "chat", "priority", "low", "title", "向AI助手提问",
      "description", "有任何学习问题都可以问我", "action", "chat",
      "prompt", "如何制定高效的学习计划？"));
    return fallback;
  }

  private List<String> extractRecentThemes(List<Map<String, Object>> messages, int limit) {
    Set<String> themes = new java.util.LinkedHashSet<>();
    int count = 0;
    for (Map<String, Object> msg : messages) {
      if (count >= limit) break;
      String content = msg.get("content") != null ? String.valueOf(msg.get("content")) : "";
      if (!content.isBlank()) {
        java.util.regex.Matcher m = Pattern.compile("[「《]([^」》]+)[」》]").matcher(content);
        while (m.find()) themes.add(m.group(1));
        for (String subject : SUBJECT_KEYWORDS.keySet()) {
          if (content.contains(subject)) themes.add(subject);
        }
      }
      count++;
    }
    return new ArrayList<>(themes).subList(0, Math.min(10, themes.size()));
  }

  private void saveProfileToDb(String userId, Map<String, Object> profile) {
    try {
      // 检查是否已存在
      Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM user_profile WHERE user_id = ?", Integer.class, userId);

      @SuppressWarnings("unchecked")
      Map<String, Object> prefs = (Map<String, Object>) profile.getOrDefault("learningPreferences", Map.of());

      String subjectAffinityJson = objectMapper.writeValueAsString(profile.get("subjectAffinity"));
      String knowledgeGapsJson = objectMapper.writeValueAsString(profile.get("knowledgeGaps"));
      String studyPatternsJson = objectMapper.writeValueAsString(Map.of(
        "learningStyle", prefs.getOrDefault("learningStyle", "balanced"),
        "difficultyPreference", prefs.getOrDefault("difficultyPreference", "medium"),
        "activityLevel", prefs.getOrDefault("activityLevel", "moderate")
      ));
      String aiSummary = String.valueOf(profile.getOrDefault("aiSummary", ""));
      LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());

      if (count != null && count > 0) {
        jdbc.update(
          "UPDATE user_profile SET subject_affinity=?, knowledge_gaps=?, study_patterns=?, " +
          "ai_summary=?, difficulty_preference=?, learning_style=?, activity_level=?, " +
          "updated_at=? WHERE user_id=?",
          subjectAffinityJson, knowledgeGapsJson, studyPatternsJson, aiSummary,
          String.valueOf(prefs.getOrDefault("difficultyPreference", "medium")),
          String.valueOf(prefs.getOrDefault("learningStyle", "balanced")),
          String.valueOf(prefs.getOrDefault("activityLevel", "moderate")),
          now, userId);
      } else {
        String id = "prof-" + System.currentTimeMillis() + "-" + Integer.toHexString((int)(Math.random() * 0xffffff));
        jdbc.update(
          "INSERT INTO user_profile (id, user_id, learning_style, difficulty_preference, activity_level, " +
          "subject_affinity, knowledge_gaps, study_patterns, ai_summary, profile_version, created_at, updated_at) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
          id, userId,
          String.valueOf(prefs.getOrDefault("learningStyle", "balanced")),
          String.valueOf(prefs.getOrDefault("difficultyPreference", "medium")),
          String.valueOf(prefs.getOrDefault("activityLevel", "moderate")),
          subjectAffinityJson, knowledgeGapsJson, studyPatternsJson, aiSummary,
          now, now);
      }
    } catch (Exception e) {
      // 持久化失败不影响服务
      log.error("画像持久化失败", e);
    }
  }

  private Map<String, Object> buildBasicProfile(String userId) {
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("userId", userId);
    profile.put("generatedAt", Instant.now().toString());
    profile.put("subjectAffinity", List.of());
    profile.put("knowledgeGaps", List.of());
    profile.put("learningPreferences", Map.of(
      "difficultyPreference", "adaptive",
      "learningStyle", "balanced",
      "activityLevel", "low"
    ));
    profile.put("aiSummary", "{\"summary\":\"数据不足，请继续使用系统积累学习记录\"}");
    return profile;
  }

  private Map<String, Object> parseAiJson(String aiResponse) {
    String cleaned = aiResponse.trim();
    if (cleaned.startsWith("```")) {
      int idx = cleaned.indexOf('\n');
      if (idx > 0) cleaned = cleaned.substring(idx + 1);
      if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
      cleaned = cleaned.trim();
    }
    try {
      int start = cleaned.indexOf('{');
      int end = cleaned.lastIndexOf('}');
      if (start >= 0 && end > start) {
        return objectMapper.readValue(cleaned.substring(start, end + 1), new TypeReference<Map<String, Object>>() {});
      }
    } catch (Exception e) { /* ignore */ }
    return Map.of();
  }

  private int countOccurrences(String text, String keyword) {
    int count = 0, idx = 0;
    while ((idx = text.indexOf(keyword, idx)) != -1) { count++; idx += keyword.length(); }
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

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null || "null".equals(String.valueOf(value)) || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }

  private double asDouble(Object value) {
    if (value instanceof Number n) return n.doubleValue();
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }
}
