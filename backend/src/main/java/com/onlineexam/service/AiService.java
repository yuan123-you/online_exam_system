package com.onlineexam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 服务 - 集成智谱 GLM-4.7-Flash 模型实现智能出题、评分和练习
 *
 * 并发与线程安全设计：
 * - 使用受管理的线程池(aiTaskExecutor)替代 new Thread()，避免资源耗尽
 * - Semaphore 控制并发 AI API 调用数，防止 429 限流
 * - ConcurrentHashMap + 同步块保证速率限制和缓存的线程安全
 * - CompletableFuture 并行评分主观题，提升阅卷效率
 * - 会话隔离：每个用户的对话上下文独立存储，互不干扰
 * - SSE 连接生命周期管理：onCompletion/onTimeout/onError 自动释放信号量
 * - 定时任务清理过期缓存和速率限制数据，防止内存泄漏
 */
@Service
public class AiService {

  private static final Logger log = LoggerFactory.getLogger(AiService.class);

  private static final Set<String> SUBJECTIVE_TYPES = Set.of("short", "coding");

  /** 出题系统提示词（动态学科识别版 - AI自主识别学科，无需预定义映射） */
  private static final String QUESTION_SYSTEM_PROMPT =
    "你是出题专家。严格按用户要求的数量和题型出题，一道不少一道不多。\n"
    + "输出纯JSON数组，不要markdown包裹，不要多余文字：\n"
    + "[{\"subject\":\"学科\",\"title\":\"题干\",\"type\":\"题型\",\"options\":[...],\"answer\":[...],\"score\":5,\"explanation\":\"解析\"}]\n\n"
    + "字段规则：\n"
    + "- subject: 【必填】你必须从用户需求中识别出所属学科/科目，填入标准学科名称（如\"数学\"、\"物理\"、\"哲学\"、\"体育学\"、\"美术学\"、\"科目一\"、\"科目四\"、\"科学\"等）。即使用户表述模糊，也必须根据上下文推断最合理的学科分类。\n"
    + "- type: single(单选) | multiple(多选) | judge(判断) | fill(填空) | short(简答) | coding(编程)\n"
    + "- options: 单选/多选必须4个选项[\"A.选项1\",\"B.选项2\",\"C.选项3\",\"D.选项4\"]；判断2个选项[\"A.正确\",\"B.错误\"]；填空/简答/编程为[]\n"
    + "- answer: 单选[\"A\"]，多选[\"A\",\"C\"]，判断[\"A\"]，填空[\"答案文本\"]\n"
    + "- explanation: 必须包含【答案】和【解析】两部分\n"
    + "- score: 固定为5\n\n"
    + "重要：单选/多选/判断题的options绝不能为空数组，必须包含完整选项！严禁输出思考过程。\n\n"
    + "【学科约束 - 最高优先级】\n"
    + "1. 你必须首先识别用户需求所属的学科/科目，并在每道题的subject字段中准确填写。\n"
    + "2. 题目内容（题干、选项、答案、解析）必须严格属于该学科范畴，严禁跨学科混淆！\n"
    + "3. 例如：用户要求\"数学题\"→subject=\"数学\"，题干必须是数学问题（方程求解、函数分析、几何证明等），绝不能生成物理题、地理题等。\n"
    + "4. 例如：用户要求\"哲学题\"→subject=\"哲学\"，必须是哲学专业问题，不能混入政治或历史内容。\n"
    + "5. 例如：用户要求\"科目一\"→subject=\"科目一\"，必须是驾照科目一考试内容。\n"
    + "6. 即使题目涉及数字计算，也不等于属于数学学科。物理题中的计算仍属于物理。\n"
    + "7. 你能识别任何学科领域，包括但不限于：数学、物理、化学、生物、地理、历史、哲学、体育学、美术学、音乐、科学、法学、经济学、医学、心理学、教育学、科目一、科目四等。\n\n"
    + "示例（数学单选题）：\n"
    + "[{\"subject\":\"数学\",\"title\":\"函数f(x)=x²-4x+3的零点为？\",\"type\":\"single\",\"options\":[\"A.x=1和x=3\",\"B.x=-1和x=-3\",\"C.x=1和x=-3\",\"D.x=-1和x=3\"],\"answer\":[\"A\"],\"score\":5,\"explanation\":\"【答案】A 【解析】令f(x)=0，即x²-4x+3=0，分解因式(x-1)(x-3)=0，解得x=1或x=3。\"}]";

  private final StoreService storeService;
  private final SystemLogService systemLogService;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final Executor aiTaskExecutor;
  private final AiCircuitBreaker circuitBreaker;

  @Value("${ai.api-url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
  private String apiUrl;

  @Value("${ai.api-key:}")
  private String apiKey;

  @Value("${ai.model:glm-4.7-flash}")
  private String model;

  @Value("${ai.rate-limit-per-minute:30}")
  private int rateLimitPerMinute;

  @Value("${ai.concurrent-limit:4}")
  private int concurrentLimit;

  // ========== 并发控制 ==========

  /** 并发 AI API 调用信号量，防止过多请求触发 429 */
  private volatile Semaphore aiApiSemaphore;

  /** 速率限制：userId -> 时间戳队列（线程安全） */
  private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> rateLimitMap = new ConcurrentHashMap<>();

  /** 响应缓存：prompt hash -> 缓存响应 */
  private final ConcurrentHashMap<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
  private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 分钟

  /** 活跃 SSE 连接计数 */
  private final AtomicInteger activeSseConnections = new AtomicInteger(0);

  /** 会话上下文：userId -> 最近对话历史（线程安全隔离） */
  private final ConcurrentHashMap<String, ConversationSession> conversationSessions = new ConcurrentHashMap<>();
  private static final int MAX_SESSIONS = 500;
  private static final int MAX_SESSION_HISTORY = 20;

  public AiService(StoreService storeService, SystemLogService systemLogService,
                   RestTemplate restTemplate, ObjectMapper objectMapper,
                   @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
                   AiCircuitBreaker circuitBreaker) {
    this.storeService = storeService;
    this.systemLogService = systemLogService;
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.aiTaskExecutor = aiTaskExecutor;
    this.circuitBreaker = circuitBreaker;
  }

  private record CachedResponse(String content, long timestamp) {}

  /** 会话上下文 - 每个用户独立，线程安全 */
  private static class ConversationSession {
    private final ConcurrentLinkedDeque<Map<String, String>> history = new ConcurrentLinkedDeque<>();
    private volatile long lastAccessTime = System.currentTimeMillis();

    void addMessage(String role, String content) {
      // 截断过长消息节省内存
      if (content.length() > 500) {
        content = content.substring(0, 500) + "...(已省略)";
      }
      history.addLast(Map.of("role", role, "content", content));
      // 滑动窗口：保留最近 N 条
      while (history.size() > MAX_SESSION_HISTORY) {
        history.pollFirst();
      }
      lastAccessTime = System.currentTimeMillis();
    }

    List<Map<String, String>> getRecentHistory(int maxCount) {
      List<Map<String, String>> all = new ArrayList<>(history);
      if (all.size() <= maxCount) return all;
      return all.subList(all.size() - maxCount, all.size());
    }

    void touch() {
      lastAccessTime = System.currentTimeMillis();
    }
  }

  /** 延迟初始化信号量（@Value 注入后才能确定大小） */
  private Semaphore getSemaphore() {
    if (aiApiSemaphore == null) {
      synchronized (this) {
        if (aiApiSemaphore == null) {
          aiApiSemaphore = new Semaphore(concurrentLimit);
        }
      }
    }
    return aiApiSemaphore;
  }

  // ========== 定时清理任务 ==========

  /** 每分钟清理过期的速率限制数据 */
  @Scheduled(fixedRate = 60_000)
  public void cleanupRateLimits() {
    long now = System.currentTimeMillis();
    long windowMs = 60_000;
    rateLimitMap.entrySet().removeIf(entry -> {
      ConcurrentLinkedDeque<Long> ts = entry.getValue();
      ts.removeIf(t -> (now - t) > windowMs);
      return ts.isEmpty();
    });
  }

  /** 每5分钟清理过期缓存 */
  @Scheduled(fixedRate = 300_000)
  public void cleanupCache() {
    long now = System.currentTimeMillis();
    responseCache.entrySet().removeIf(e -> (now - e.getValue().timestamp()) > CACHE_TTL_MS);
    log.debug("[AiService] 缓存清理完成，当前缓存条目: {}", responseCache.size());
  }

  /** 每10分钟清理过期会话 */
  @Scheduled(fixedRate = 600_000)
  public void cleanupSessions() {
    long now = System.currentTimeMillis();
    long sessionTimeoutMs = 30 * 60 * 1000; // 30 分钟无访问则过期
    conversationSessions.entrySet().removeIf(entry ->
      (now - entry.getValue().lastAccessTime) > sessionTimeoutMs
    );
    // 如果会话数超限，移除最旧的
    if (conversationSessions.size() > MAX_SESSIONS) {
      List<String> keys = new ArrayList<>(conversationSessions.keySet());
      keys.sort((a, b) -> Long.compare(
        conversationSessions.get(a).lastAccessTime,
        conversationSessions.get(b).lastAccessTime
      ));
      int toRemove = conversationSessions.size() - MAX_SESSIONS;
      for (int i = 0; i < toRemove; i++) {
        conversationSessions.remove(keys.get(i));
      }
    }
  }

  // ========== 安全缓存键生成 ==========

  /** 使用 SHA-256 生成缓存键，避免 hashCode 冲突导致错误缓存命中 */
  private String computeCacheKey(String systemPrompt, String userPrompt) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest((systemPrompt + "|" + userPrompt).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) hex.append(String.format("%02x", b));
      return hex.toString();
    } catch (Exception e) {
      // 回退到 hashCode（不应发生）
      return String.valueOf((systemPrompt + userPrompt).hashCode());
    }
  }

  // ========== AI 生成题目 ==========

  /**
   * AI 生成题目 - 调用 GLM-4.7-Flash 生成真实题目
   */
  public ResponseEntity<?> generateQuestions(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    // 熔断器检查
    if (!circuitBreaker.allowRequest("generate")) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用（熔断保护中），请稍后重试");
    }

    long existingCount = store.questions.stream().filter(q -> Objects.equals(str(q, "teacherId"), userId)).count();

    if (!checkRateLimit(userId)) {
      return error(HttpStatus.TOO_MANY_REQUESTS, "AI 调用频率过高，请稍后再试（每分钟最多 " + rateLimitPerMinute + " 次）");
    }

    String customPrompt = str(body, "customPrompt");
    String systemPrompt;
    String userPrompt;
    String subject;
    String knowledgePoint;
    String difficulty;
    String type;
    int count;

    if (!customPrompt.isBlank()) {
      // 通过AI语义理解分析用户意图，替代正则匹配
      UserIntent intent = analyzeUserIntent(customPrompt);
      systemPrompt = QUESTION_SYSTEM_PROMPT;
      userPrompt = buildSmartUserPrompt(customPrompt, body, 10, intent);
      // 使用AI分析结果作为元数据
      count = intent.count > 0 ? intent.count : Math.min(asInt(body.get("count")), 10);
      if (count <= 0) count = 5;
      type = !intent.type.isBlank() ? intent.type : (str(body, "type").isBlank() ? "single" : str(body, "type"));
      subject = !intent.subject.isBlank() ? intent.subject : str(body, "subject");
      knowledgePoint = !intent.knowledgePoint.isBlank() ? intent.knowledgePoint : str(body, "knowledgePoint");
      difficulty = !intent.difficulty.isBlank() ? intent.difficulty : (str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty"));
    } else {
      subject = str(body, "subject");
      knowledgePoint = str(body, "knowledgePoint");
      difficulty = str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty");
      count = Math.min(asInt(body.get("count")), 10);
      if (count <= 0) count = 5;
      type = str(body, "type").isBlank() ? "single" : str(body, "type");

      String typeChinese = switch (type) {
        case "single" -> "单选题";
        case "multiple" -> "多选题";
        case "judge" -> "判断题";
        case "fill" -> "填空题";
        case "short" -> "简答题";
        case "coding" -> "编程题";
        default -> "题目";
      };

      String difficultyChinese = switch (difficulty) {
        case "easy" -> "简单";
        case "medium" -> "中等";
        case "hard" -> "困难";
        default -> difficulty;
      };

      systemPrompt = QUESTION_SYSTEM_PROMPT;

      userPrompt = String.format(
        "严格生成恰好 %d 道关于「%s」的%s，知识点方向为「%s」，难度为「%s」。每题分值5分。请包含详细解析。不要多也不要少。\n【学科约束】你必须识别所属学科并在每题subject字段中填写，所有题目内容必须严格属于该学科范畴，严禁跨学科混淆！",
        count,
        subject.isBlank() ? "综合" : subject,
        typeChinese,
        knowledgePoint.isBlank() ? "综合" : knowledgePoint,
        difficultyChinese
      );
    }

    List<Map<String, Object>> generated;
    try {
      String aiResponse = callAiApi(systemPrompt, userPrompt);
      generated = parseAiQuestions(aiResponse, userId, subject, knowledgePoint, difficulty, type);
      if (generated.size() > count) {
        generated = generated.subList(0, count);
      }
      circuitBreaker.recordSuccess("generate");
    } catch (Exception e) {
      circuitBreaker.recordFailure("generate");
      systemLogService.log(user, "AI出题失败", e.getMessage());
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用：" + e.getMessage());
    }

    systemLogService.log(user, "AI出题", "自定义=" + !customPrompt.isBlank() + ", 数量=" + generated.size());
    return ResponseEntity.ok(mapOf(
      "questions", generated,
      "aiUsed", true,
      "totalCount", generated.size(),
      "existingCount", existingCount,
      "remainingQuota", Math.max(0, 5000 - existingCount)
    ));
  }

  // ========== 导入题目 ==========

  /**
   * 导入 AI 生成的题目到题库
   */
  public ResponseEntity<?> importQuestions(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    long existingCount = store.questions.stream().filter(q -> Objects.equals(str(q, "teacherId"), userId)).count();
    long remaining = 5000 - existingCount;

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> questions = (List<Map<String, Object>>) body.get("questions");
    if (questions == null || questions.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "没有需要导入的题目");
    }

    List<Map<String, Object>> imported = Collections.synchronizedList(new ArrayList<>());
    List<Map<String, Object>> errors = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < questions.size(); i++) {
      Map<String, Object> q = questions.get(i);
      if (existingCount + imported.size() >= 5000) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("index", i);
        err.put("title", str(q, "title"));
        err.put("message", "题库已满（5000题上限），无法继续导入");
        errors.add(err);
        continue;
      }

      String id = str(q, "id");
      if (id.isBlank()) id = createId("ai-question");

      Map<String, Object> record = new LinkedHashMap<>();
      record.put("id", id);
      record.put("teacherId", userId);
      record.put("subject", str(q, "subject").isBlank() ? "AI生成" : str(q, "subject"));
      record.put("knowledgePoint", str(q, "knowledgePoint").isBlank() ? "综合" : str(q, "knowledgePoint"));
      record.put("difficulty", str(q, "difficulty").isBlank() ? "medium" : str(q, "difficulty"));
      record.put("type", str(q, "type").isBlank() ? "single" : str(q, "type"));
      record.put("title", str(q, "title"));
      record.put("options", asList(q.get("options")));
      record.put("answer", asList(q.get("answer")));
      record.put("score", asInt(q.get("score")) > 0 ? asInt(q.get("score")) : 5);
      record.put("sourceTag", "ai-generated");

      try {
        storeService.saveRecord("questions", record);
        imported.add(record);
      } catch (Exception e) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("index", i);
        err.put("title", str(q, "title"));
        err.put("message", "导入失败：" + e.getMessage());
        errors.add(err);
      }
    }

    systemLogService.log(user, "AI导入题目", "导入=" + imported.size() + ", 失败=" + errors.size());
    return ResponseEntity.ok(mapOf(
      "importedCount", imported.size(),
      "errors", errors,
      "totalCount", existingCount + imported.size(),
      "remainingQuota", Math.max(0, 5000 - existingCount - imported.size())
    ));
  }

  // ========== AI 评分（并行化主观题评分） ==========

  /**
   * AI 评分提交 - 使用 GLM-4.7-Flash 进行智能评分
   * 主观题评分使用 CompletableFuture 并行处理，提升效率
   */
  public ResponseEntity<?> gradeSubmission(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    // 熔断器检查
    if (!circuitBreaker.allowRequest("grade")) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 评分服务暂时不可用（熔断保护中），请稍后重试");
    }

    Map<String, Object> submission = find(store.submissions, str(body, "submissionId"));
    if (submission == null) return error(HttpStatus.NOT_FOUND, "Submission not found.");

    Map<String, Object> exam = find(store.exams, str(submission, "examId"));
    if (exam == null || !Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    if (!checkRateLimit(userId)) {
      return error(HttpStatus.TOO_MANY_REQUESTS, "AI 调用频率过高，请稍后再试");
    }

    List<Object> details = new ArrayList<>(asList(submission.get("answerDetail")));

    // 分离主观题和客观题
    List<Map<String, Object>> subjectiveDetails = new ArrayList<>();
    List<Integer> subjectiveIndices = new ArrayList<>();
    for (int i = 0; i < details.size(); i++) {
      Map<String, Object> detail = new LinkedHashMap<>(asMap(details.get(i)));
      String type = str(detail, "type");
      if (SUBJECTIVE_TYPES.contains(type)) {
        subjectiveDetails.add(detail);
        subjectiveIndices.add(i);
      }
    }

    // 并行评分所有主观题
    AtomicInteger aiScore = new AtomicInteger(0);
    if (!subjectiveDetails.isEmpty()) {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (int si = 0; si < subjectiveDetails.size(); si++) {
        Map<String, Object> detail = subjectiveDetails.get(si);
        final int detailIndex = si;
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
          int fullScore = asInt(detail.get("fullScore"));
          List<String> given = normalizeAnswer(detail.get("answer"));
          List<String> expected = normalizeAnswer(detail.get("expectedAnswer"));
          String givenText = String.join(" ", given);
          String expectedText = String.join(" ", expected);

          if (givenText.isBlank()) {
            detail.put("aiScore", 0);
            detail.put("aiComment", "未作答");
          } else {
            try {
              Map<String, Object> aiResult = aiGradeAnswer(
                str(detail, "title"), givenText, expectedText, fullScore
              );
              int score = asInt(aiResult.get("score"));
              score = Math.max(0, Math.min(fullScore, score));
              String comment = str(aiResult, "comment");

              detail.put("aiScore", score);
              detail.put("aiComment", "AI评分：" + score + "/" + fullScore + "（" + comment + "）");
              aiScore.addAndGet(score);
            } catch (Exception e) {
              int score = fallbackKeywordScore(givenText, expectedText, fullScore);
              detail.put("aiScore", score);
              detail.put("aiComment", "AI评分：" + score + "/" + fullScore + "（关键词匹配，AI服务暂不可用）");
              aiScore.addAndGet(score);
            }
          }
        }, aiTaskExecutor);
        futures.add(future);
      }

      // 等待所有主观题评分完成（最多等待 60 秒）
      try {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
          .get(60, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.warn("[AiService] 并行评分超时或异常: {}", e.getMessage());
        // 对未完成的主观题使用关键词匹配兜底
        for (Map<String, Object> detail : subjectiveDetails) {
          if (!detail.containsKey("aiScore")) {
            int fullScore = asInt(detail.get("fullScore"));
            String givenText = String.join(" ", normalizeAnswer(detail.get("answer")));
            String expectedText = String.join(" ", normalizeAnswer(detail.get("expectedAnswer")));
            int score = fallbackKeywordScore(givenText, expectedText, fullScore);
            detail.put("aiScore", score);
            detail.put("aiComment", "AI评分：" + score + "/" + fullScore + "（超时，关键词匹配）");
            aiScore.addAndGet(score);
          }
        }
      }
    }

    // 合并结果：客观题直接计分，主观题使用 AI 评分
    List<Object> aiDetails = new ArrayList<>();
    int subjectiveIdx = 0;
    for (int i = 0; i < details.size(); i++) {
      Map<String, Object> original = asMap(details.get(i));
      String type = str(original, "type");
      if (SUBJECTIVE_TYPES.contains(type)) {
        aiDetails.add(subjectiveDetails.get(subjectiveIdx));
        subjectiveIdx++;
      } else {
        Map<String, Object> detail = new LinkedHashMap<>(original);
        aiScore.addAndGet(asInt(detail.get("score")));
        aiDetails.add(detail);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("submissionId", str(submission, "id"));
    result.put("aiScore", aiScore.get());
    result.put("manualScore", asInt(submission.get("finalScore")));
    result.put("details", aiDetails);
    result.put("message", "AI评分完成（仅供参考），不影响教师手动阅卷分数。");

    systemLogService.log(user, "AI阅卷", str(submission, "id"));
    return ResponseEntity.ok(result);
  }

  // ========== AI 助手练习 ==========

  /**
   * AI 助手 - 使用 GLM-4.7-Flash 根据用户自定义提示词或预设参数生成内容
   */
  public ResponseEntity<?> practiceQuestions(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    // 熔断器检查
    if (!circuitBreaker.allowRequest("practice")) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 练习服务暂时不可用（熔断保护中），请稍后重试");
    }

    if (!checkRateLimit(userId)) {
      return error(HttpStatus.TOO_MANY_REQUESTS, "AI 调用频率过高，请稍后再试（每分钟最多 " + rateLimitPerMinute + " 次）");
    }

    String customPrompt = str(body, "customPrompt");

    String systemPrompt;
    String userPrompt;

    if (!customPrompt.isBlank()) {
      systemPrompt = QUESTION_SYSTEM_PROMPT;
      userPrompt = buildSmartUserPrompt(customPrompt, body, 20);
    } else {
      String subject = str(body, "subject");
      String type = str(body, "type").isBlank() ? "single" : str(body, "type");
      String difficulty = str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty");
      int count = Math.min(asInt(body.get("count")), 20);
      if (count <= 0) count = 5;

      String typeChinese = switch (type) {
        case "single" -> "单选题";
        case "multiple" -> "多选题";
        case "judge" -> "判断题";
        case "fill" -> "填空题";
        default -> "题目";
      };

      String difficultyChinese = switch (difficulty) {
        case "easy" -> "简单";
        case "medium" -> "中等";
        case "hard" -> "困难";
        default -> difficulty;
      };

      systemPrompt = QUESTION_SYSTEM_PROMPT;

      userPrompt = String.format(
        "请生成 %d 道关于「%s」的%s练习题，难度为「%s」。每题都要包含详细解析。\n【学科约束】你必须识别所属学科并在每题subject字段中填写，所有题目内容必须严格属于该学科范畴，严禁跨学科混淆！",
        count,
        subject.isBlank() ? "综合" : subject,
        typeChinese,
        difficultyChinese
      );
    }

    List<Map<String, Object>> questions;
    try {
      String aiResponse = callAiApi(systemPrompt, userPrompt);
      String subject = str(body, "subject");
      questions = parseAiQuestions(aiResponse, "", subject.isBlank() ? "" : subject, "AI助手", str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty"), str(body, "type").isBlank() ? "single" : str(body, "type"));
      for (Map<String, Object> q : questions) {
        if (str(q, "id").isBlank()) {
          q.put("id", createId("practice"));
        }
      }
      circuitBreaker.recordSuccess("practice");
    } catch (Exception e) {
      circuitBreaker.recordFailure("practice");
      systemLogService.log(user, "AI助手失败", e.getMessage());
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用：" + e.getMessage());
    }

    systemLogService.log(user, "AI助手请求", "自定义=" + !customPrompt.isBlank());
    return ResponseEntity.ok(mapOf("questions", questions, "totalCount", questions.size()));
  }

  // ========== AI 解析答案 ==========

  /**
   * AI 解析答案 - 使用 GLM-4.7-Flash 解释正确答案和解题思路
   */
  public ResponseEntity<?> explainAnswer(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    if (!checkRateLimit(userId)) {
      return error(HttpStatus.TOO_MANY_REQUESTS, "AI 调用频率过高，请稍后再试");
    }

    String questionTitle = str(body, "question");
    if (questionTitle.isBlank() && body.get("question") instanceof Map) {
      questionTitle = str(asMap(body.get("question")), "title");
    }
    List<String> studentAnswer = normalizeAnswer(body.get("studentAnswer"));
    List<String> correctAnswer = normalizeAnswer(body.get("correctAnswer"));

    String systemPrompt = "教学辅导。分析学生作答，返回JSON:{isCorrect:bool,explanation,tips}。无markdown包裹。";

    String userPrompt = String.format(
      "题目：%s\n学生作答：%s\n正确答案：%s\n请分析并给出解析。",
      questionTitle,
      String.join(", ", studentAnswer),
      String.join(", ", correctAnswer)
    );

    try {
      String aiResponse = callAiApi(systemPrompt, userPrompt);
      Map<String, Object> parsed = parseAiJson(aiResponse, new TypeReference<Map<String, Object>>() {});

      boolean isCorrect = Boolean.TRUE.equals(parsed.get("isCorrect"));
      if (!parsed.containsKey("isCorrect")) {
        isCorrect = studentAnswer.equals(correctAnswer);
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("isCorrect", isCorrect);
      result.put("explanation", str(parsed, "explanation").isBlank() ? "解析暂时无法生成" : str(parsed, "explanation"));
      result.put("tips", str(parsed, "tips").isBlank() ? "请认真复习相关知识点" : str(parsed, "tips"));

      circuitBreaker.recordSuccess("explain");
      systemLogService.log(user, "AI解析答案", "正确=" + isCorrect);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      circuitBreaker.recordFailure("explain");
      boolean isCorrect = studentAnswer.equals(correctAnswer);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("isCorrect", isCorrect);
      result.put("explanation", "AI 解析暂不可用，正确答案为：" + String.join(", ", correctAnswer));
      result.put("tips", "请稍后重试获取 AI 解析");
      return ResponseEntity.ok(result);
    }
  }

  // ========== 保存练习记录 ==========

  /**
   * 保存练习记录
   */
  public ResponseEntity<?> savePracticeRecord(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    long practiceCount = store.wrongBookEntries.stream()
      .filter(e -> Objects.equals(str(e, "studentId"), userId) && "practice".equals(str(e, "status"))).count();
    if (practiceCount >= 1000) {
      return error(HttpStatus.BAD_REQUEST, "练题记录已达上限（1000条）");
    }

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", createId("practice"));
    record.put("studentId", userId);
    record.put("studentName", str(user, "name"));
    record.put("questionId", str(body, "questionId"));
    record.put("subject", str(body, "subject"));
    record.put("knowledgePoint", str(body, "knowledgePoint"));
    record.put("type", str(body, "type"));
    record.put("title", str(body, "title"));
    record.put("latestAnswer", asList(body.get("answer")));
    record.put("expectedAnswer", asList(body.get("expectedAnswer")));
    record.put("wrongCount", 0);
    record.put("retryCount", 1);
    record.put("lastRetryCorrect", asList(body.get("answer")).equals(asList(body.get("expectedAnswer"))));
    record.put("removable", false);
    record.put("status", "practice");
    record.put("lastWrongAt", Instant.now().toString());

    storeService.saveRecord("wrongBookEntries", record);
    systemLogService.log(user, "保存练习记录", str(record, "id"));
    return ResponseEntity.ok(mapOf("record", record));
  }

  /**
   * 批量保存练习记录
   */
  public ResponseEntity<?> savePracticeRecords(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    long practiceCount = store.wrongBookEntries.stream()
      .filter(e -> Objects.equals(str(e, "studentId"), userId) && "practice".equals(str(e, "status"))).count();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> records = (List<Map<String, Object>>) body.get("records");
    if (records == null || records.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "没有需要保存的练习记录");
    }

    int savedCount = 0;
    for (Map<String, Object> item : records) {
      if (practiceCount + savedCount >= 1000) break;

      Map<String, Object> record = new LinkedHashMap<>();
      record.put("id", createId("practice"));
      record.put("studentId", userId);
      record.put("studentName", str(user, "name"));
      record.put("questionId", str(item, "questionId"));
      record.put("subject", str(item, "subject"));
      record.put("knowledgePoint", str(item, "knowledgePoint"));
      record.put("type", str(item, "type"));
      record.put("title", str(item, "title"));
      record.put("latestAnswer", asList(item.get("answer")));
      record.put("expectedAnswer", asList(item.get("expectedAnswer")));
      record.put("wrongCount", 0);
      record.put("retryCount", 1);
      record.put("lastRetryCorrect", asList(item.get("answer")).equals(asList(item.get("expectedAnswer"))));
      record.put("removable", false);
      record.put("status", "practice");
      record.put("lastWrongAt", Instant.now().toString());

      try {
        storeService.saveRecord("wrongBookEntries", record);
        savedCount++;
      } catch (Exception e) {
        // skip failed records
      }
    }

    systemLogService.log(user, "批量保存练习", "保存=" + savedCount);
    return ResponseEntity.ok(mapOf("savedCount", savedCount));
  }

  // ========== AI API Integration Methods ==========

  /**
   * Call the ZhiPu GLM-4.7-Flash API with streaming support (SSE).
   * 使用受管理的线程池执行，支持并发安全。
   * @param thinkingEnabled if true, enables deep-thinking mode
   */
  public void callAiApiStream(String systemPrompt, String userPrompt, boolean thinkingEnabled,
                               SseEmitter emitter) {
    callAiApiStream(systemPrompt, userPrompt, thinkingEnabled, 0.7, false, "chat", emitter);
  }

  public void callAiApiStream(String systemPrompt, String userPrompt, boolean thinkingEnabled,
                               double temperature,
                               SseEmitter emitter) {
    callAiApiStream(systemPrompt, userPrompt, thinkingEnabled, temperature, false, "chat", emitter);
  }

  public void callAiApiStream(String systemPrompt, String userPrompt, boolean thinkingEnabled,
                               double temperature, boolean jsonMode,
                               SseEmitter emitter) {
    callAiApiStream(systemPrompt, userPrompt, thinkingEnabled, temperature, jsonMode, "chat", emitter);
  }

  public void callAiApiStream(String systemPrompt, String userPrompt, boolean thinkingEnabled,
                               double temperature, boolean jsonMode, String circuitType,
                               SseEmitter emitter) {
    if (apiKey == null || apiKey.isBlank()) {
      log.error("[AiService SSE] API密钥未配置，无法发起流式请求");
      try { emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI API密钥未配置\"}")); emitter.complete(); }
      catch (Exception ignored) {}
      return;
    }

    String threadId = Long.toHexString(Thread.currentThread().getId());
    log.info("[AiService SSE] [{}] 开始流式请求: model={}, temp={}, thinking={}, jsonMode={}, 活跃SSE连接={}",
      threadId, model, temperature, thinkingEnabled, jsonMode, activeSseConnections.get());

    // 使用 AtomicBoolean 确保连接计数只减一次（修复竞态条件）
    AtomicInteger connectionCounter = new AtomicInteger(1);
    activeSseConnections.incrementAndGet();

    Runnable decrementConnection = () -> {
      if (connectionCounter.getAndSet(0) == 1) {
        activeSseConnections.decrementAndGet();
        log.debug("[AiService SSE] [{}] 连接结束，活跃SSE连接={}", threadId, activeSseConnections.get());
      }
    };

    // 注册 SSE 生命周期回调，确保计数只减一次
    emitter.onCompletion(decrementConnection);
    emitter.onTimeout(() -> {
      log.warn("[AiService SSE] [{}] 连接超时", threadId);
      decrementConnection.run();
    });
    emitter.onError(e -> {
      log.warn("[AiService SSE] [{}] 连接错误: {}", threadId, e.getMessage());
      decrementConnection.run();
    });

    // 使用受管理的线程池替代 new Thread()
    CompletableFuture.runAsync(() -> {
      // 获取信号量限制并发 AI API 调用
      Semaphore semaphore = getSemaphore();
      try {
        if (!semaphore.tryAcquire(60, TimeUnit.SECONDS)) {
          log.error("[AiService SSE] [{}] 等待并发槽超时(60s)，拒绝请求", threadId);
          try {
            emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI 服务繁忙，请稍后再试\"}"));
            emitter.complete();
          } catch (Exception ignored) {}
          return;
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return;
      }

      try { // 信号量守卫：finally 中释放
      java.io.BufferedReader reader = null;
      java.net.HttpURLConnection conn = null;
      int maxRetries = 3;
      long startTime = System.currentTimeMillis();
      for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        log.info("[AiService SSE] [{}] 第{}次尝试连接API: {}", threadId, attempt, apiUrl);

        java.net.URL url = new java.net.URL(apiUrl);
        conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        // Build request body
        java.util.LinkedHashMap<String, Object> reqBody = new java.util.LinkedHashMap<>();
        reqBody.put("model", model);
        reqBody.put("messages", java.util.List.of(
          java.util.Map.of("role", "system", "content", systemPrompt),
          java.util.Map.of("role", "user", "content", userPrompt)
        ));
        reqBody.put("temperature", temperature);
        reqBody.put("max_tokens", 16384);
        reqBody.put("stream", true);
        if (jsonMode) {
          reqBody.put("response_format", java.util.Map.of("type", "json_object"));
        }
        if (thinkingEnabled) {
          reqBody.put("thinking", java.util.Map.of("type", "enabled"));
        }

        String jsonBody = objectMapper.writeValueAsString(reqBody);
        log.debug("[AiService SSE] [{}] 请求体大小: {} bytes", threadId, jsonBody.length());

        try (java.io.OutputStream os = conn.getOutputStream()) {
          os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
          os.flush();
        }

        int responseCode = conn.getResponseCode();
        log.info("[AiService SSE] [{}] API响应码: {}", threadId, responseCode);

        // 429/503 重试
        if ((responseCode == 429 || responseCode == 503) && attempt < maxRetries) {
          long waitMs = responseCode == 429
            ? (long) (3000 * Math.pow(3, attempt - 1))
            : (long) (2000 * Math.pow(2.5, attempt - 1));
          log.info("[AiService SSE] [{}] 收到{}，{}ms后重试", threadId, responseCode, waitMs);
          conn.disconnect(); conn = null;
          try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
          continue;
        }

        if (responseCode != 200) {
          java.io.InputStream errorStream = conn.getErrorStream();
          String errorBody = errorStream != null ? new String(errorStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) : "";
          log.error("[AiService SSE] [{}] API错误: HTTP {}, body={}", threadId, responseCode,
            errorBody.substring(0, Math.min(500, errorBody.length())));
          circuitBreaker.recordFailure(circuitType);
          String friendlyMsg = friendlyAiError(responseCode, errorBody);
          if (attempt >= maxRetries && responseCode == 429) {
            friendlyMsg = friendlyMsg + "（已自动重试 " + maxRetries + " 次）";
          }
          emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + escapeJson(friendlyMsg) + "\"}"));
          emitter.complete();
          return;
        }

        // 读取 SSE 流
        reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        String line;
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        int chunkCount = 0;
        int errorChunkCount = 0;

        while ((line = reader.readLine()) != null) {
          String data = null;
          if (line.startsWith("data: ")) {
            data = line.substring(6).trim();
          } else if (line.startsWith("data:")) {
            data = line.substring(5).trim();
          }
          if (data != null) {
            if ("[DONE]".equals(data)) {
              long elapsed = System.currentTimeMillis() - startTime;
              log.info("[AiService SSE] [{}] 收到[DONE], 共{}个chunk, 内容长度={}, 推理长度={}, 耗时={}ms",
                threadId, chunkCount, contentBuilder.length(), reasoningBuilder.length(), elapsed);
              circuitBreaker.recordSuccess(circuitType);
              java.util.Map<String, Object> donePayload = new java.util.LinkedHashMap<>();
              donePayload.put("content", contentBuilder.toString());
              donePayload.put("reasoning", reasoningBuilder.toString());
              donePayload.put("done", true);
              emitter.send(SseEmitter.event().name("complete").data(objectMapper.writeValueAsString(donePayload)));
              emitter.complete();
              return;
            }
            try {
              @SuppressWarnings("unchecked")
              java.util.Map<String, Object> jsonMap = objectMapper.readValue(data, java.util.Map.class);
              @SuppressWarnings("unchecked")
              java.util.List<java.util.Map<String, Object>> choices =
                (java.util.List<java.util.Map<String, Object>>) jsonMap.get("choices");
              if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> delta = (java.util.Map<String, Object>) choices.get(0).get("delta");
                if (delta != null) {
                  String reasoningContent = delta.containsKey("reasoning_content") && delta.get("reasoning_content") != null
                    ? String.valueOf(delta.get("reasoning_content")) : "";
                  String content = delta.containsKey("content") && delta.get("content") != null
                    ? String.valueOf(delta.get("content")) : "";

                  if (!thinkingEnabled && !reasoningContent.isEmpty()) {
                    reasoningContent = "";
                  }

                  if (!reasoningContent.isEmpty()) {
                    reasoningBuilder.append(reasoningContent);
                    java.util.Map<String, Object> chunk = new java.util.LinkedHashMap<>();
                    chunk.put("type", "reasoning");
                    chunk.put("text", reasoningContent);
                    emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
                    chunkCount++;
                  }
                  if (!content.isEmpty()) {
                    contentBuilder.append(content);
                    java.util.Map<String, Object> chunk = new java.util.LinkedHashMap<>();
                    chunk.put("type", "content");
                    chunk.put("text", content);
                    emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
                    chunkCount++;
                  }
                }
              }
            } catch (Exception parseEx) {
              errorChunkCount++;
              if (errorChunkCount <= 3) {
                log.warn("[AiService SSE] [{}] 解析chunk失败(#{}): {}, 错误: {}",
                  threadId, errorChunkCount, data.substring(0, Math.min(200, data.length())), parseEx.getMessage());
              }
            }
          }
        }
        // 流结束但无 [DONE]
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[AiService SSE] [{}] 流读取结束(无[DONE]), 共{}个chunk, 内容长度={}, 耗时={}ms",
          threadId, chunkCount, contentBuilder.length(), elapsed);
        if (contentBuilder.length() > 0) {
          circuitBreaker.recordSuccess(circuitType);
        }
        java.util.Map<String, Object> donePayload = new java.util.LinkedHashMap<>();
        donePayload.put("content", contentBuilder.toString());
        donePayload.put("reasoning", reasoningBuilder.toString());
        donePayload.put("done", true);
        emitter.send(SseEmitter.event().name("complete").data(objectMapper.writeValueAsString(donePayload)));
        emitter.complete();
        return;

      } catch (Exception e) {
        long elapsed = System.currentTimeMillis() - startTime;
        log.error("[AiService SSE] [{}] 第{}次请求异常(耗时{}ms): {}: {}",
          threadId, attempt, elapsed, e.getClass().getSimpleName(), e.getMessage());
        if (attempt >= maxRetries) {
          circuitBreaker.recordFailure(circuitType);
          try {
            String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            emitter.send(SseEmitter.event().name("error")
              .data("{\"error\":\"" + escapeJson(errMsg) + "\"}"));
            emitter.complete();
          } catch (Exception sendErr) {
            log.error("[AiService SSE] [{}] 发送错误事件也失败: {}", threadId, sendErr.getMessage());
          }
          return;
        }
        long waitMs = (long) (2000 * Math.pow(2, attempt - 1));
        log.info("[AiService SSE] [{}] {}ms后重试...", threadId, waitMs);
        try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
      } finally {
        if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        reader = null;
        if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {}
        conn = null;
      }
      } // end retry loop
      } finally {
        semaphore.release();
      }
    }, aiTaskExecutor);
  }

  /** Escape a string for embedding in JSON. */
  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  // ========== 流式练习/出题 ==========

  /**
   * Streaming version for practice questions (student)
   */
  public void practiceQuestionsStream(String userId, Map<String, Object> body,
                                       SseEmitter emitter) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"Forbidden\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    // 熔断器检查
    if (!circuitBreaker.allowRequest("practice")) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI 练习服务暂时不可用（熔断保护中），请稍后重试\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    // 速率限制检查
    if (!checkRateLimit(userId)) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI 调用频率过高，请稍后再试\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    String customPrompt = str(body, "customPrompt");
    String systemPrompt;
    String userPrompt;

    if (!customPrompt.isBlank()) {
      systemPrompt = QUESTION_SYSTEM_PROMPT;
      userPrompt = buildSmartUserPrompt(customPrompt, body, 20);
    } else {
      String subject = str(body, "subject");
      String type = str(body, "type").isBlank() ? "single" : str(body, "type");
      String difficulty = str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty");
      int count = Math.min(asInt(body.get("count")), 20);
      if (count <= 0) count = 5;

      String typeChinese = switch (type) {
        case "single" -> "单选"; case "multiple" -> "多选";
        case "judge" -> "判断"; case "fill" -> "填空";
        default -> "题目";
      };
      String difficultyChinese = switch (difficulty) {
        case "easy" -> "简单"; case "medium" -> "中等";
        case "hard" -> "困难"; default -> difficulty;
      };

      systemPrompt = QUESTION_SYSTEM_PROMPT;

      userPrompt = String.format(
        "严格生成恰好 %d 道「%s」%s题，不要多也不要少。难度%s。输出JSON。\n【学科约束】你必须识别所属学科并在每题subject字段中填写，所有题目内容必须严格属于该学科范畴，严禁跨学科混淆！",
        count, subject.isBlank() ? "综合" : subject, typeChinese, difficultyChinese
      );
    }

    boolean deepThinking = Boolean.TRUE.equals(body.get("deepThinking"));
    callAiApiStream(systemPrompt, userPrompt, deepThinking, 0.5, false, "practice", emitter);
  }

  /**
   * Streaming version for question generation (teacher)
   */
  public void generateQuestionsStream(String userId, Map<String, Object> body,
                                       SseEmitter emitter) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"Forbidden\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    // 熔断器检查
    if (!circuitBreaker.allowRequest("generate")) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI 出题服务暂时不可用（熔断保护中），请稍后重试\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    // 速率限制检查
    if (!checkRateLimit(userId)) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI 调用频率过高，请稍后再试\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    String customPrompt = str(body, "customPrompt");
    String systemPrompt;
    String userPrompt;

    if (!customPrompt.isBlank()) {
      systemPrompt = QUESTION_SYSTEM_PROMPT;
      userPrompt = buildSmartUserPrompt(customPrompt, body, 10);
    } else {
      String subject = str(body, "subject");
      String knowledgePoint = str(body, "knowledgePoint");
      String difficulty = str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty");
      int count = Math.min(asInt(body.get("count")), 10);
      if (count <= 0) count = 5;
      String type = str(body, "type").isBlank() ? "single" : str(body, "type");

      String typeChinese = switch (type) {
        case "single" -> "单选"; case "multiple" -> "多选"; case "judge" -> "判断";
        case "fill" -> "填空"; case "short" -> "简答"; case "coding" -> "编程";
        default -> "题目";
      };
      String difficultyChinese = switch (difficulty) {
        case "easy" -> "简单"; case "medium" -> "中等";
        case "hard" -> "困难"; default -> difficulty;
      };

      systemPrompt = QUESTION_SYSTEM_PROMPT;

      userPrompt = String.format(
        "严格生成恰好 %d 道关于「%s」的%s，知识点方向为「%s」，难度为「%s」。每题分值5分。请包含详细解析。不要多也不要少。\n【学科约束】你必须识别所属学科并在每题subject字段中填写，所有题目内容必须严格属于该学科范畴，严禁跨学科混淆！",
        count, subject.isBlank() ? "综合" : subject, typeChinese,
        knowledgePoint.isBlank() ? "综合" : knowledgePoint, difficultyChinese
      );
    }

    boolean deepThinking = Boolean.TRUE.equals(body.get("deepThinking"));
    callAiApiStream(systemPrompt, userPrompt, deepThinking, 0.7, false, "generate", emitter);
  }

  // ========== General AI Chat ==========

  /**
   * AI 自由对话 - 普通模式（任何已认证用户可用）
   */
  public ResponseEntity<?> chat(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    // 熔断器检查
    if (!circuitBreaker.allowRequest("chat")) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 对话服务暂时不可用（熔断保护中），请稍后重试");
    }

    if (!checkRateLimit(userId)) {
      return error(HttpStatus.TOO_MANY_REQUESTS, "AI 调用频率过高，请稍后再试（每分钟最多 " + rateLimitPerMinute + " 次）");
    }

    String userMessage = str(body, "message");
    if (userMessage.isBlank()) {
      userMessage = str(body, "customPrompt");
    }
    if (userMessage.isBlank()) {
      return error(HttpStatus.BAD_REQUEST, "请输入消息内容");
    }

    // 更新会话上下文
    ConversationSession session = conversationSessions.computeIfAbsent(userId, k -> new ConversationSession());
    session.addMessage("user", userMessage);

    String systemPrompt = buildChatSystemPrompt();
    String userPrompt = buildChatUserPrompt(userMessage, body, session);

    try {
      String aiResponse = callAiApi(systemPrompt, userPrompt);
      session.addMessage("assistant", aiResponse);
      circuitBreaker.recordSuccess("chat");
      systemLogService.log(user, "AI对话", "完成");
      return ResponseEntity.ok(mapOf("content", aiResponse, "role", "assistant"));
    } catch (Exception e) {
      circuitBreaker.recordFailure("chat");
      systemLogService.log(user, "AI对话失败", e.getMessage());
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用：" + e.getMessage());
    }
  }

  /**
   * AI 自由对话 - 流式 SSE（任何已认证用户可用）
   */
  public void chatStream(String userId, Map<String, Object> body,
                          SseEmitter emitter) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"Not authenticated\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    // 熔断器检查
    if (!circuitBreaker.allowRequest("chat")) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI 对话服务暂时不可用（熔断保护中），请稍后重试\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    // 速率限制检查
    if (!checkRateLimit(userId)) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI 调用频率过高，请稍后再试\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    String userMessage = str(body, "message");
    if (userMessage.isBlank()) {
      userMessage = str(body, "customPrompt");
    }
    if (userMessage.isBlank()) {
      try {
        emitter.send(SseEmitter.event().name("error").data("{\"error\":\"请输入消息内容\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    // 更新会话上下文
    ConversationSession session = conversationSessions.computeIfAbsent(userId, k -> new ConversationSession());
    session.addMessage("user", userMessage);

    // 通过AI语义理解检测出题意图（三级：strong/weak/none），替代正则匹配
    UserIntent intent = analyzeUserIntent(userMessage);
    String intentLevel = intent.intent;
    String systemPrompt;
    String userPrompt;

    if ("strong".equals(intentLevel) || "weak".equals(intentLevel)) {
      // Strong or weak intent: generate questions with structured JSON format
      systemPrompt = QUESTION_SYSTEM_PROMPT;
      userPrompt = buildSmartUserPrompt(userMessage, body, 10, intent);
    } else {
      // No question intent: normal conversational chat
      systemPrompt = buildChatSystemPrompt();
      userPrompt = buildChatUserPrompt(userMessage, body, session);
    }

    boolean deepThinking = Boolean.TRUE.equals(body.get("deepThinking"));
    systemLogService.log(user, "AI对话流", "深度思考=" + deepThinking + ", 意图=" + intentLevel);

    // 流式对话完成后，将 AI 响应存入会话上下文
    // 通过 SSE 生命周期回调在 complete 事件时记录
    emitter.onCompletion(() -> {
      // 注意：此时无法获取完整 AI 响应内容，会话上下文由客户端下次请求时补充
      // 或通过流式累积的方式记录（已在 callAiApiStream 内部完成）
    });

    callAiApiStream(systemPrompt, userPrompt, deepThinking, 0.7, false, "chat", emitter);
  }

  /** Build system prompt for general chat */
  private String buildChatSystemPrompt() {
    return """
      你是在线考试系统的AI学习助手，名叫"智学"。你的职责是帮助学生学习和解答问题。

      核心原则：
      1. 直接给出最终回答，严禁输出你的思考过程、分析步骤、自我审查等内容（如"第1步分析"、"解构主题"、"起草内容"、"自我修正"等）
      2. 回答要有深度和广度：对概念给出准确定义，对原理给出清晰推导，对应用给出实际例子
      3. 善用 Markdown 格式：合理使用标题（##、###）、加粗（**）、列表、代码块（```）等让内容层次分明
      4. 对数学公式使用 LaTeX 语法（用 $ 包裹行内公式，用 $$ 包裹独立公式），确保 LaTeX 语法正确完整
      5. 提供学习建议：回答完问题后，可以推荐相关的延伸学习方向或练习题
      6. 使用中文回答，语言风格友好专业，像一位耐心且博学的导师

      注意：只输出给用户看的最终回答，不要输出任何中间过程。""";
  }

  /** Build user prompt for chat, including conversation history from session.
   *  Implements sliding window: keeps only the last N messages to avoid context bloat. */
  private static final int MAX_HISTORY_MESSAGES = 6; // 3 Q&A rounds

  private String buildChatUserPrompt(String currentMessage, Map<String, Object> body,
                                      ConversationSession session) {
    // 优先使用客户端传来的历史消息（前端维护的完整上下文）
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> history = (List<Map<String, Object>>) body.get("messages");
    if (history != null && !history.isEmpty()) {
      int startIndex = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
      StringBuilder sb = new StringBuilder();
      sb.append("以下是最近对话：\n");
      for (int i = startIndex; i < history.size(); i++) {
        Map<String, Object> msg = history.get(i);
        String role = str(msg, "role");
        String content = str(msg, "content");
        if (!content.isBlank()) {
          if (content.length() > 500) {
            content = content.substring(0, 500) + "...(已省略)";
          }
          if ("user".equals(role)) {
            sb.append("用户：").append(content).append("\n");
          } else if ("assistant".equals(role)) {
            sb.append("AI：").append(content).append("\n");
          }
        }
      }
      sb.append("\n用户新消息：").append(currentMessage);
      return sb.toString();
    }

    // 回退到服务端会话上下文
    List<Map<String, String>> sessionHistory = session.getRecentHistory(MAX_HISTORY_MESSAGES);
    if (sessionHistory.isEmpty()) {
      return currentMessage;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("以下是最近对话：\n");
    for (Map<String, String> msg : sessionHistory) {
      String role = msg.get("role");
      String content = msg.get("content");
      if ("user".equals(role)) {
        sb.append("用户：").append(content).append("\n");
      } else if ("assistant".equals(role)) {
        sb.append("AI：").append(content).append("\n");
      }
    }
    sb.append("\n用户新消息：").append(currentMessage);
    return sb.toString();
  }

  // ========== 非流式 AI API 调用 ==========

  String callAiApi(String systemPrompt, String userPrompt) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new RuntimeException("AI API 密钥未配置，请联系管理员设置 AI_API_KEY 环境变量");
    }

    // 检查缓存（使用 SHA-256 避免哈希冲突）
    String cacheKey = computeCacheKey(systemPrompt, userPrompt);
    CachedResponse cached = responseCache.get(cacheKey);
    if (cached != null && (System.currentTimeMillis() - cached.timestamp()) < CACHE_TTL_MS) {
      log.debug("[AiService] 命中缓存，跳过API调用");
      return cached.content();
    }

    // 获取信号量
    Semaphore semaphore = getSemaphore();
    try {
      if (!semaphore.tryAcquire(60, TimeUnit.SECONDS)) {
        throw new RuntimeException("AI 服务繁忙，请稍后再试");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("请求被中断");
    }

    try {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", model);
    requestBody.put("messages", List.of(
      Map.of("role", "system", "content", systemPrompt),
      Map.of("role", "user", "content", userPrompt)
    ));
    requestBody.put("temperature", 0.5);
    requestBody.put("max_tokens", 16384);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

    int maxRetries = 3;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
          @SuppressWarnings("unchecked")
          List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
          if (choices != null && !choices.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = str(message, "content");

            // 缓存响应
            responseCache.put(cacheKey, new CachedResponse(content, System.currentTimeMillis()));

            return content;
          }
        }
        throw new RuntimeException("AI API 返回无效响应");

      } catch (RestClientException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";

        if (msg.contains("429") && attempt < maxRetries) {
          long waitMs = (long) (3000 * Math.pow(3, attempt - 1));
          log.info("[AiService] 非流式请求429，{}ms后重试(第{}次)", waitMs, attempt);
          try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
          continue;
        }
        if (msg.contains("503") && attempt < maxRetries) {
          long waitMs = (long) (2000 * Math.pow(2.5, attempt - 1));
          log.info("[AiService] 非流式请求503，{}ms后重试(第{}次)", waitMs, attempt);
          try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
          continue;
        }

        if (msg.contains("401") || msg.contains("Unauthorized")) {
          throw new RuntimeException("AI API 密钥无效或已过期，请联系管理员更新 AI_API_KEY");
        }
        if (msg.contains("429")) {
          throw new RuntimeException("AI 服务当前访问繁忙，系统已自动重试 " + maxRetries + " 次仍然失败，请稍后再试");
        }
        if (msg.contains("503") || msg.contains("Service Unavailable")) {
          throw new RuntimeException("AI 服务暂时不可用，请稍后重试");
        }
        throw new RuntimeException("AI 请求失败：" + (msg.length() > 100 ? msg.substring(0, 100) : msg));
      }
    }
    throw new RuntimeException("AI 请求失败，请稍后重试");
    } finally {
      semaphore.release();
    }
  }

  /** Map raw AI API error codes to user-friendly Chinese messages */
  private String friendlyAiError(int httpCode, String errorBody) {
    return switch (httpCode) {
      case 429 -> "AI 服务当前访问繁忙，系统正在自动重试中，请稍候...";
      case 401 -> "AI API 密钥无效或已过期，请联系管理员更新";
      case 403 -> "AI 服务权限不足，请联系管理员";
      case 503 -> "AI 服务暂时不可用，系统正在自动重试...";
      case 500 -> "AI 服务内部错误，请稍后重试";
      case 502 -> "AI 服务网关错误，请稍后重试";
      case 504 -> "AI 服务响应超时，请稍后重试";
      default -> "AI 请求失败（HTTP " + httpCode + "），请稍后重试";
    };
  }

  // ========== AI 评分辅助 ==========

  /**
   * Use AI to grade a subjective answer
   */
  private Map<String, Object> aiGradeAnswer(String questionTitle, String studentAnswer,
                                              String expectedAnswer, int fullScore) {
    // 熔断器检查
    if (!circuitBreaker.allowRequest("grade")) {
      Map<String, Object> result = new LinkedHashMap<>();
      int score = fallbackKeywordScore(studentAnswer, expectedAnswer, fullScore);
      result.put("score", score);
      result.put("comment", "关键词匹配评分（AI服务熔断保护中）");
      return result;
    }

    String systemPrompt = "阅卷评分。返回JSON:{score:int(0到满分),comment}。无markdown包裹。";

    String userPrompt = String.format(
      "题目：%s\n满分：%d分\n学生作答：%s\n参考答案：%s\n请评分。",
      questionTitle, fullScore, studentAnswer, expectedAnswer
    );

    try {
      String response = callAiApi(systemPrompt, userPrompt);
      circuitBreaker.recordSuccess("grade");
      return parseAiJson(response, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      circuitBreaker.recordFailure("grade");
      Map<String, Object> result = new LinkedHashMap<>();
      int score = fallbackKeywordScore(studentAnswer, expectedAnswer, fullScore);
      result.put("score", score);
      result.put("comment", "关键词匹配评分");
      return result;
    }
  }

  // ========== AI 响应解析 ==========

  /**
   * Parse AI response into question objects
   */
  private List<Map<String, Object>> parseAiQuestions(String aiResponse, String teacherId,
                                                       String subject, String knowledgePoint,
                                                       String difficulty, String type) {
    String cleaned = aiResponse.trim();
    if (cleaned.startsWith("```")) {
      int firstNewline = cleaned.indexOf('\n');
      if (firstNewline > 0) cleaned = cleaned.substring(firstNewline + 1);
      if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
      cleaned = cleaned.trim();
    }

    String jsonArray = cleaned;
    int arrStart = cleaned.indexOf('[');
    int arrEnd = cleaned.lastIndexOf(']');
    if (arrStart >= 0 && arrEnd > arrStart) {
      jsonArray = cleaned.substring(arrStart, arrEnd + 1);
    }

    List<Map<String, Object>> questions = new ArrayList<>();
    try {
      List<Map<String, Object>> parsed = objectMapper.readValue(jsonArray, new TypeReference<>() {});
      for (Map<String, Object> q : parsed) {
        if (str(q, "title").isBlank()) continue;
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", createId("ai-question"));
        question.put("teacherId", teacherId);
        // Priority: AI-identified subject > caller-provided subject > "AI生成"
        String aiSubject = str(q, "subject");
        String effectiveSubject = !aiSubject.isBlank() ? aiSubject : (!subject.isBlank() ? subject : "AI生成");
        question.put("subject", effectiveSubject);
        question.put("knowledgePoint", str(q, "knowledgePoint").isBlank() ? (knowledgePoint.isBlank() ? "综合" : knowledgePoint) : str(q, "knowledgePoint"));
        question.put("difficulty", str(q, "difficulty").isBlank() ? difficulty : str(q, "difficulty"));
        String qType = str(q, "type").isBlank() ? type : str(q, "type");
        question.put("type", qType);
        question.put("title", str(q, "title"));
        List<Object> opts = normalizeList(q.get("options"));
        if (qType.equals("judge") && opts.size() > 2) {
          opts = new ArrayList<>(opts.subList(0, 2));
        } else if (opts.size() > 4 && (qType.equals("single") || qType.equals("multiple"))) {
          opts = new ArrayList<>(opts.subList(0, 4));
        }
        question.put("options", opts);
        List<Object> rawAnswer = normalizeList(q.get("answer"));
        if ("single".equals(qType) || "multiple".equals(qType) || "judge".equals(qType)) {
          List<Object> cleanAnswer = new ArrayList<>();
          for (Object a : rawAnswer) {
            String s = String.valueOf(a);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-D])").matcher(s);
            if (m.find()) cleanAnswer.add(m.group(1));
          }
          question.put("answer", cleanAnswer.isEmpty() ? rawAnswer : cleanAnswer);
        } else {
          question.put("answer", rawAnswer);
        }
        question.put("score", asInt(q.get("score")) > 0 ? asInt(q.get("score")) : 5);
        question.put("explanation", str(q, "explanation").isBlank() ? "暂无解析" : str(q, "explanation"));
        question.put("sourceTag", "ai-generated");
        questions.add(question);
      }
    } catch (Exception e) {
      Map<String, Object> fallback = new LinkedHashMap<>();
      fallback.put("id", createId("ai-question"));
      fallback.put("teacherId", teacherId);
      fallback.put("subject", subject.isBlank() ? "AI生成" : subject);
      fallback.put("knowledgePoint", knowledgePoint.isBlank() ? "综合" : knowledgePoint);
      fallback.put("difficulty", difficulty);
      fallback.put("type", type);
      fallback.put("title", "【AI原始响应 - 请手动编辑】" + aiResponse.substring(0, Math.min(200, aiResponse.length())));
      fallback.put("options", List.of());
      fallback.put("answer", List.of());
      fallback.put("score", 5);
      fallback.put("explanation", "AI 返回格式异常，请手动编辑题目内容");
      fallback.put("sourceTag", "ai-generated");
      questions.add(fallback);
    }

    return questions;
  }

  /**
   * Parse AI JSON response into a map
   */
  private <T> T parseAiJson(String aiResponse, TypeReference<T> typeRef) {
    String cleaned = aiResponse.trim();
    if (cleaned.startsWith("```")) {
      int firstNewline = cleaned.indexOf('\n');
      if (firstNewline > 0) cleaned = cleaned.substring(firstNewline + 1);
      if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
      cleaned = cleaned.trim();
    }

    try {
      return objectMapper.readValue(cleaned, typeRef);
    } catch (Exception e) {
      int start = cleaned.indexOf('{');
      int end = cleaned.lastIndexOf('}');
      if (start >= 0 && end > start) {
        try {
          return objectMapper.readValue(cleaned.substring(start, end + 1), typeRef);
        } catch (Exception e2) {
          throw new RuntimeException("无法解析 AI 响应：" + e.getMessage());
        }
      }
      throw new RuntimeException("无法解析 AI 响应：" + e.getMessage());
    }
  }

  /**
   * Fallback keyword matching score when AI is unavailable
   */
  private int fallbackKeywordScore(String givenText, String expectedText, int fullScore) {
    String givenLower = givenText.toLowerCase();
    String expectedLower = expectedText.toLowerCase();

    Set<String> givenWords = new HashSet<>(List.of(givenLower.split("\\s+")));
    Set<String> expectedWords = new HashSet<>(List.of(expectedLower.split("\\s+")));
    givenWords.removeIf(w -> w.length() < 2);
    expectedWords.removeIf(w -> w.length() < 2);

    Set<String> common = new HashSet<>(givenWords);
    common.retainAll(expectedWords);

    double similarity = expectedWords.isEmpty() ? 0 : (double) common.size() / expectedWords.size();
    return (int) Math.round(similarity * fullScore);
  }

  // ========== Rate Limiting ==========

  private boolean checkRateLimit(String userId) {
    long now = System.currentTimeMillis();
    long windowMs = 60_000;

    ConcurrentLinkedDeque<Long> timestamps = rateLimitMap.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());

    // 同步块保证同一用户的速率检查原子性
    synchronized (timestamps) {
      // 移除过期时间戳
      timestamps.removeIf(t -> (now - t) > windowMs);
      if (timestamps.size() >= rateLimitPerMinute) {
        return false;
      }
      timestamps.add(now);
    }

    return true;
  }

  // ========== 健康检查 ==========

  /** 获取 AI 服务健康状态（含熔断器状态、活跃连接数、缓存大小等） */
  public ResponseEntity<?> getHealthStatus() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank());
    status.put("model", model);
    status.put("activeSseConnections", activeSseConnections.get());
    status.put("cacheSize", responseCache.size());
    status.put("activeSessions", conversationSessions.size());
    status.put("concurrentLimit", concurrentLimit);
    status.put("rateLimitPerMinute", rateLimitPerMinute);

    // 各功能类型的熔断器状态
    Map<String, Object> circuitStatus = new LinkedHashMap<>();
    for (String type : List.of("chat", "practice", "generate", "grade", "explain")) {
      circuitStatus.put(type, circuitBreaker.getCircuitStatus(type));
    }
    status.put("circuitBreakers", circuitStatus);

    return ResponseEntity.ok(status);
  }

  // ========== Helper Methods ==========

  private List<Object> normalizeList(Object raw) {
    List<Object> values = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        if (value != null && !String.valueOf(value).trim().isBlank()) {
          values.add(String.valueOf(value).trim());
        }
      }
    } else if (raw != null && !String.valueOf(raw).trim().isBlank()) {
      values.add(String.valueOf(raw).trim());
    }
    return values;
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

  /** Extract the requested question count from user text */
  private int extractCountFromText(String text) {
    if (text == null || text.isBlank()) return -1;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*道").matcher(text);
    if (m.find()) {
      try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return -1; }
    }
    return -1;
  }

  /** Extract question type from user text */
  private String extractTypeFromText(String text) {
    if (text == null || text.isBlank()) return "";
    if (text.matches(".*多选.*")) return "multiple";
    if (text.matches(".*单选.*")) return "single";
    if (text.matches(".*判断.*")) return "judge";
    if (text.matches(".*填空.*")) return "fill";
    if (text.matches(".*简答.*")) return "short";
    if (text.matches(".*编程.*") || text.matches(".*代码.*")) return "coding";
    return "";
  }

  /** Map extracted type to Chinese label */
  private String typeToChinese(String type) {
    return switch (type) {
      case "single" -> "单选题";
      case "multiple" -> "多选题";
      case "judge" -> "判断题";
      case "fill" -> "填空题";
      case "short" -> "简答题";
      case "coding" -> "编程题";
      default -> "题目";
    };
  }

  /**
   * Dynamic subject extraction from user text - no predefined mapping required.
   * Uses multiple strategies with aggressive noise stripping to isolate the subject noun.
   * The AI model itself handles final subject identification via the prompt as a safety net.
   */
  private String extractSubjectFromText(String text) {
    if (text == null || text.isBlank()) return "";

    // Strategy 0: 科目X pattern (科目一, 科目二, 科目三, 科目四) - highest priority
    java.util.regex.Matcher m0 = java.util.regex.Pattern.compile(
      "(科目[一二三四五六七八九十])"
    ).matcher(text);
    if (m0.find()) return m0.group(1);

    // Strategy 1: "X题" or "X练习" pattern - capture up to 10 Chinese chars before 题/练习, then strip noise
    java.util.regex.Matcher m1 = java.util.regex.Pattern.compile(
      "([\\u4e00-\\u9fa5]{1,10})(?:题|练习)"
    ).matcher(text);
    if (m1.find()) {
      String cleaned = stripSubjectNoise(m1.group(1));
      if (!cleaned.isBlank()) return cleaned;
    }

    // Strategy 1b: English subject + 题/练习 (e.g., "Python练习", "JavaScript题")
    java.util.regex.Matcher m1b = java.util.regex.Pattern.compile(
      "([a-zA-Z]{2,})(?:题|练习)"
    ).matcher(text);
    if (m1b.find()) {
      String s = m1b.group(1);
      return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    // Strategy 2: "关于X的题", "X方面的题", "X相关的题"
    java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
      "关于([\\u4e00-\\u9fa5a-zA-Z0-9]+?)(?:的题|方面|相关|的|题)"
    ).matcher(text);
    if (m2.find()) {
      String s = m2.group(1).trim();
      while (s.endsWith("的") || s.endsWith("方") || s.endsWith("相")) {
        s = s.substring(0, s.length() - 1);
      }
      if (!s.isBlank()) return s;
    }

    // Strategy 3: "考我X", "出X的题", "练X", "练一练X"
    java.util.regex.Matcher m3 = java.util.regex.Pattern.compile(
      "(?:考我|考考我|出|练一练|练练|复习|学习|测试|练习|练)([\\u4e00-\\u9fa5a-zA-Z0-9]{1,10}?)(?:的题|的练习|方面|相关|知识点|的|题|$)"
    ).matcher(text);
    if (m3.find()) {
      String kp = m3.group(1).trim();
      if (!kp.matches(".*(几道|道|一些|点|个|那种|几|习).*")) return kp;
    }

    // Strategy 4: "X知识", "X基础", "X入门"
    java.util.regex.Matcher m4 = java.util.regex.Pattern.compile(
      "([\\u4e00-\\u9fa5a-zA-Z0-9]{1,10}?)(?:知识|基础|入门|概论|导论|原理)"
    ).matcher(text);
    if (m4.find()) return m4.group(1).trim();

    // Strategy 5: English subject names in free text
    java.util.regex.Matcher m5 = java.util.regex.Pattern.compile(
      "(?i)\\b(javascript|python|java|c\\+\\+|typescript|react|vue|rust|go|swift|kotlin|ruby|php|sql|r|matlab)\\b"
    ).matcher(text);
    if (m5.find()) return m5.group(1).substring(0, 1).toUpperCase() + m5.group(1).substring(1).toLowerCase();

    return "";
  }

  /** Strip action/quantity/modifier words from both ends of a raw subject extraction */
  private String stripSubjectNoise(String raw) {
    String cleaned = raw;
    for (int i = 0; i < 5; i++) {
      String prev = cleaned;
      // Strip known noise prefixes
      cleaned = cleaned.replaceAll("^(帮我|给我|请|关于|来|出|点|几道|\\d+道|几|道|练|考考|复习|测试|练习|学习|的|些|我|于|跟|和)", "");
      // Strip known noise suffixes
      cleaned = cleaned.replaceAll("(帮我|给我|请|关于|来|出|点|几道|\\d+道|几|道|练|考考|复习|测试|练习|学习|的|些|我|于|跟|和|方面|相关)$", "");
      if (cleaned.equals(prev)) break;
    }
    // Strip standalone "考" at beginning (preserve 考古学, 考研 etc.)
    if (cleaned.startsWith("考") && cleaned.length() > 1 && !cleaned.startsWith("考古") && !cleaned.startsWith("考研")) {
      cleaned = cleaned.substring(1);
    }
    // Reject if only noise remains
    if (!cleaned.isBlank() && !cleaned.matches("^(几|道|些|点|个|那种|什么|这|那|哪|多|少|出|来|练|考|给|帮|的|我|几道|点|于|习)$")) {
      return cleaned;
    }
    return "";
  }

  /** Extract difficulty from user text (e.g., "来点难的"→"hard", "出几道简单题"→"easy") */
  private String extractDifficultyFromText(String text) {
    if (text == null || text.isBlank()) return "";
    if (text.matches(".*(困难|较难|高难|挑战|难题|很难).*")) return "hard";
    if (text.matches(".*(简单|基础|入门|容易|简单点).*")) return "easy";
    return "";
  }

  /** Extract knowledge point from user text (e.g., "考我闭包"→"闭包", "出TCP协议的题"→"TCP协议") */
  private String extractKnowledgePointFromText(String text) {
    if (text == null || text.isBlank()) return "";
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
      "(?:关于|考我|考考我|出|练|复习|学习|测试|练习)([\\u4e00-\\u9fa5a-zA-Z0-9+.#]{2,20}?)(?:的题|的练习|方面|相关|知识点|的|题|$)"
    ).matcher(text);
    if (m.find()) {
      String kp = m.group(1).trim();
      if (kp.matches(".*(几道|道|一些|点|个|那种).*")) return "";
      return kp;
    }
    return "";
  }

  /**
   * Unified smart prompt builder for question generation.
   * Uses AI semantic understanding (UserIntent) instead of regex matching.
   */
  private String buildSmartUserPrompt(String userText, Map<String, Object> body, int maxCount) {
    return buildSmartUserPrompt(userText, body, maxCount, analyzeUserIntent(userText));
  }

  private String buildSmartUserPrompt(String userText, Map<String, Object> body, int maxCount, UserIntent intent) {

    int count = intent.count > 0 ? intent.count : Math.min(asInt(body.get("count")), maxCount);
    if (count <= 0) count = 5;

    String effectiveType = !intent.type.isBlank() ? intent.type : (str(body, "type").isBlank() ? "" : str(body, "type"));
    String effectiveSubject = !intent.subject.isBlank() ? intent.subject : str(body, "subject");
    String effectiveDifficulty = !intent.difficulty.isBlank() ? intent.difficulty : str(body, "difficulty");
    String effectiveKp = !intent.knowledgePoint.isBlank() ? intent.knowledgePoint : str(body, "knowledgePoint");

    StringBuilder sb = new StringBuilder();
    sb.append("严格生成恰好 ").append(count).append(" 道题目，不要多也不要少。\n");

    String typeVal = effectiveType.isBlank() ? "single" : effectiveType;
    sb.append("题型：").append(typeToChinese(typeVal))
      .append("（type字段必须为\"").append(typeVal).append("\"）\n");

    if (!effectiveSubject.isBlank()) {
      sb.append("科目（AI已识别）：").append(effectiveSubject).append("\n");
    }
    sb.append("【学科约束】你必须从用户需求中识别所属学科，在每道题的subject字段中填写，且所有题目内容必须严格属于该学科范畴，严禁跨学科混淆！\n");
    if (!effectiveDifficulty.isBlank()) {
      String diffChinese = switch (effectiveDifficulty) {
        case "easy" -> "简单"; case "hard" -> "困难"; default -> "中等";
      };
      sb.append("难度：").append(diffChinese).append("\n");
    }
    if (!effectiveKp.isBlank()) {
      sb.append("知识点方向：").append(effectiveKp).append("\n");
    }

    sb.append("用户需求：").append(userText);
    return sb.toString();
  }

  /**
   * Detect if user message indicates question-generation intent.
   * Three levels: strong (clear intent), weak (possible intent), none.
   */
  private String detectQuestionIntent(String text) {
    if (text == null || text.isBlank()) return "none";

    // Strong intent: explicit question-generation keywords
    if (text.matches(".*(出|生成|要|来|帮我出|给我出|给我来|给我生成)\\s*(\\d+|几)\\s*道.*")) return "strong";
    if (text.matches(".*\\d+\\s*道.*题.*")) return "strong";
    if (text.matches(".*\\d+\\s*道.*[\\u4e00-\\u9fa5a-zA-Z].*")) return "strong";
    if (text.matches(".*(单选|多选|判断|填空|简答|编程).*题.*")) return "strong";
    if (text.matches(".*题.*(单选|多选|判断|填空|简答|编程).*")) return "strong";

    // Weak intent: implicit question-generation signals
    if (text.matches(".*(考考我|练一练|练练|测试一下|测验|小测|随堂测|摸底).*")) return "weak";
    if (text.matches(".*(复习题|练习题|模拟题|真题|考题|试题).*")) return "weak";
    if (text.matches(".*(来点|来些|给我点|整点|整些).*(题|练习|测验|测试).*")) return "weak";
    if (text.matches(".*想(练|做|考|测试|练习).*")) return "weak";
    if (text.matches(".*[a-zA-Z\\u4e00-\\u9fa5]{2,}(题|练习|测验).*") && !text.matches(".*(问|答|解释|说明|什么是|为什么|怎么|如何|讲解).*")) return "weak";

    return "none";
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

  private String createId(String prefix) {
    return prefix + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
  }

  // ========== AI语义理解：用户意图分析 ==========

  /**
   * 用户意图分析结果 - 由AI大模型实时语义理解生成，而非正则匹配
   */
  static class UserIntent {
    final String subject;        // 识别的学科/科目
    final String difficulty;     // 难度: easy/medium/hard
    final String knowledgePoint; // 知识点方向
    final String intent;         // 出题意图: strong/weak/none
    final String type;           // 题型: single/multiple/judge/fill/short/coding
    final int count;             // 题目数量

    UserIntent(String subject, String difficulty, String knowledgePoint,
               String intent, String type, int count) {
      this.subject = subject == null ? "" : subject;
      this.difficulty = difficulty == null ? "" : difficulty;
      this.knowledgePoint = knowledgePoint == null ? "" : knowledgePoint;
      this.intent = intent == null ? "none" : intent;
      this.type = type == null ? "" : type;
      this.count = count;
    }

    static UserIntent empty() {
      return new UserIntent("", "", "", "none", "", 0);
    }
  }

  /** AI意图分析的系统提示词 - 轻量级，专注语义理解 */
  private static final String INTENT_ANALYSIS_SYSTEM_PROMPT =
    "你是语义分析专家。分析用户输入，识别其学习/出题意图。输出纯JSON，不要markdown包裹，不要多余文字：\n"
    + "{\"subject\":\"学科名\",\"difficulty\":\"easy|medium|hard\",\"knowledgePoint\":\"知识点\",\"intent\":\"strong|weak|none\",\"type\":\"single|multiple|judge|fill|short|coding\",\"count\":5}\n\n"
    + "规则：\n"
    + "- subject: 从用户需求中识别学科/科目（如数学、物理、哲学、体育学、美术学、科目一、科目四、科学等）。无法判断时留空。\n"
    + "- difficulty: 根据用户表述判断（简单/基础/入门→easy, 困难/挑战/高难→hard, 其余→medium）。\n"
    + "- knowledgePoint: 用户提到的具体知识点方向（如\"闭包\"、\"微积分\"、\"交通标志\"）。无法判断时留空。\n"
    + "- intent: strong=明确要求出题（含\"出题/来X道/给我X道\"等）, weak=隐含出题意图（含\"考考我/练一练/复习\"等）, none=普通对话/提问。\n"
    + "- type: 用户指定的题型。无法判断时留空。\n"
    + "- count: 用户要求的题目数量，未指定则为0。";

  /**
   * 通过AI大模型实时分析用户输入的语义和意图。
   * 这是核心方法，替代所有正则匹配提取，实现真正的智能语义理解。
   * 如果AI分析失败，降级到正则匹配作为兜底。
   */
  UserIntent analyzeUserIntent(String userText) {
    if (userText == null || userText.isBlank()) return UserIntent.empty();

    try {
      String aiResponse = callAiApiForIntentAnalysis(userText);
      return parseIntentResponse(aiResponse);
    } catch (Exception e) {
      log.warn("[AiService] AI意图分析失败，降级到正则匹配: {}", e.getMessage());
      return fallbackRegexIntent(userText);
    }
  }

  /** 调用AI API进行轻量级意图分析（低token、低延迟） */
  private String callAiApiForIntentAnalysis(String userText) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new RuntimeException("AI API 密钥未配置");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", model);
    requestBody.put("messages", List.of(
      Map.of("role", "system", "content", INTENT_ANALYSIS_SYSTEM_PROMPT),
      Map.of("role", "user", "content", "分析以下用户输入的意图：\n" + userText)
    ));
    requestBody.put("temperature", 0.1);  // 低温度，确保稳定输出
    requestBody.put("max_tokens", 256);    // 极少token即可

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

    // 意图分析API调用带重试（429/500时自动重试）
    int maxRetries = 2;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
          @SuppressWarnings("unchecked")
          List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
          if (choices != null && !choices.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return str(message, "content");
          }
        }
        throw new RuntimeException("AI意图分析返回无效响应");
      } catch (RestClientException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if ((msg.contains("429") || msg.contains("500") || msg.contains("503")) && attempt < maxRetries) {
          long waitMs = (long) (2000 * Math.pow(2, attempt));
          log.info("[AiService] 意图分析API {}，{}ms后重试(第{}次)", msg.substring(0, Math.min(30, msg.length())), waitMs, attempt + 1);
          try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
          continue;
        }
        throw e;
      }
    }
    throw new RuntimeException("AI意图分析重试耗尽");
  }

  /** 解析AI意图分析返回的JSON */
  private UserIntent parseIntentResponse(String aiResponse) {
    try {
      String json = aiResponse.trim();
      // 去除可能的markdown包裹
      if (json.startsWith("```")) {
        json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
      }
      Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

      String subject = str(map.get("subject"));
      String difficulty = str(map.get("difficulty"));
      String knowledgePoint = str(map.get("knowledgePoint"));
      String intent = str(map.get("intent"));
      String type = str(map.get("type"));
      int count = 0;
      Object countObj = map.get("count");
      if (countObj instanceof Number) {
        count = ((Number) countObj).intValue();
      } else if (countObj != null) {
        try { count = Integer.parseInt(str(countObj)); } catch (NumberFormatException ignored) {}
      }

      // 验证intent值
      if (!List.of("strong", "weak", "none").contains(intent)) {
        intent = "none";
      }
      // 验证difficulty值
      if (!List.of("easy", "medium", "hard").contains(difficulty)) {
        difficulty = "";
      }
      // 验证type值
      if (!List.of("single", "multiple", "judge", "fill", "short", "coding").contains(type)) {
        type = "";
      }

      return new UserIntent(subject, difficulty, knowledgePoint, intent, type, count);
    } catch (Exception e) {
      log.warn("[AiService] 解析AI意图分析结果失败: {}", e.getMessage());
      throw new RuntimeException("解析意图分析结果失败", e);
    }
  }

  /** 正则匹配降级方案 - 仅在AI分析失败时使用 */
  private UserIntent fallbackRegexIntent(String userText) {
    return new UserIntent(
      extractSubjectFromText(userText),
      extractDifficultyFromText(userText),
      extractKnowledgePointFromText(userText),
      detectQuestionIntent(userText),
      extractTypeFromText(userText),
      extractCountFromText(userText)
    );
  }
}
