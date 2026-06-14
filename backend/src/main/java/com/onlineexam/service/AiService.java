package com.onlineexam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 服务 - 集成智谱 GLM-4-Flash 模型实现智能出题、评分和练习
 */
@Service
public class AiService {

  private static final Set<String> SUBJECTIVE_TYPES = Set.of("short", "coding");

  private final StoreService storeService;
  private final SystemLogService systemLogService;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${ai.api-url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
  private String apiUrl;

  @Value("${ai.api-key:}")
  private String apiKey;

  @Value("${ai.model:glm-4-flash}")
  private String model;

  @Value("${ai.rate-limit-per-minute:30}")
  private int rateLimitPerMinute;

  // Rate limiting: userId -> list of call timestamps
  private final ConcurrentHashMap<String, List<Long>> rateLimitMap = new ConcurrentHashMap<>();

  // Simple cache: prompt hash -> response
  private final ConcurrentHashMap<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
  private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

  public AiService(StoreService storeService, SystemLogService systemLogService,
                   RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.storeService = storeService;
    this.systemLogService = systemLogService;
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
  }

  private record CachedResponse(String content, long timestamp) {}

  /**
   * AI 生成题目 - 调用 GLM-4-Flash 生成真实题目
   */
  public ResponseEntity<?> generateQuestions(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    long existingCount = store.questions.stream().filter(q -> Objects.equals(str(q, "teacherId"), userId)).count();

    // Rate limit check
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

    if (!customPrompt.isBlank()) {
      // User provided a custom prompt — use it directly (like student practice-questions)
      systemPrompt = "出题专家。输出JSON数组:[{title,type:single|multiple|judge|fill|short|coding,options:[A.xx,B.xx,C.xx,D.xx],answer:[],score:5,explanation}]。无markdown包裹。";
      userPrompt = customPrompt;
      subject = str(body, "subject");
      knowledgePoint = str(body, "knowledgePoint");
      difficulty = str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty");
      type = str(body, "type").isBlank() ? "single" : str(body, "type");
    } else {
      subject = str(body, "subject");
      knowledgePoint = str(body, "knowledgePoint");
      difficulty = str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty");
      int count = Math.min(asInt(body.get("count")), 10);
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

      systemPrompt = "出题专家。输出JSON数组:[{title,type,options:[A.xx,B.xx,C.xx,D.xx],answer:[],score:5,explanation}]。无markdown包裹。";

      userPrompt = String.format(
        "请生成 %d 道关于「%s」的%s，知识点方向为「%s」，难度为「%s」。每题分值5分。请包含详细解析。",
        count,
        subject.isBlank() ? "计算机基础" : subject,
        typeChinese,
        knowledgePoint.isBlank() ? "综合" : knowledgePoint,
        difficultyChinese
      );
    }

    List<Map<String, Object>> generated;
    try {
      String aiResponse = callAiApi(systemPrompt, userPrompt);
      generated = parseAiQuestions(aiResponse, userId, subject, knowledgePoint, difficulty, type);
    } catch (Exception e) {
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

    List<Map<String, Object>> imported = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();

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

  /**
   * AI 评分提交 - 使用 GLM-4-Flash 进行智能评分
   */
  public ResponseEntity<?> gradeSubmission(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    Map<String, Object> submission = find(store.submissions, str(body, "submissionId"));
    if (submission == null) return error(HttpStatus.NOT_FOUND, "Submission not found.");

    Map<String, Object> exam = find(store.exams, str(submission, "examId"));
    if (exam == null || !Objects.equals(str(exam, "teacherId"), userId)) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    // Rate limit check
    if (!checkRateLimit(userId)) {
      return error(HttpStatus.TOO_MANY_REQUESTS, "AI 调用频率过高，请稍后再试");
    }

    int aiScore = 0;
    List<Object> details = new ArrayList<>(asList(submission.get("answerDetail")));
    List<Object> aiDetails = new ArrayList<>();

    for (Object raw : details) {
      Map<String, Object> detail = new LinkedHashMap<>(asMap(raw));
      String type = str(detail, "type");

      if (SUBJECTIVE_TYPES.contains(type)) {
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
            // Use AI to grade subjective questions
            Map<String, Object> aiResult = aiGradeAnswer(
              str(detail, "title"), givenText, expectedText, fullScore
            );
            int score = asInt(aiResult.get("score"));
            score = Math.max(0, Math.min(fullScore, score));
            String comment = str(aiResult, "comment");

            detail.put("aiScore", score);
            detail.put("aiComment", "AI评分：" + score + "/" + fullScore + "（" + comment + "）");
            aiScore += score;
          } catch (Exception e) {
            // Fallback to simple keyword matching if AI fails
            int score = fallbackKeywordScore(givenText, expectedText, fullScore);
            detail.put("aiScore", score);
            detail.put("aiComment", "AI评分：" + score + "/" + fullScore + "（关键词匹配，AI服务暂不可用）");
            aiScore += score;
          }
        }
      } else {
        // Objective questions: use auto-score
        aiScore += asInt(detail.get("score"));
      }
      aiDetails.add(detail);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("submissionId", str(submission, "id"));
    result.put("aiScore", aiScore);
    result.put("manualScore", asInt(submission.get("finalScore")));
    result.put("details", aiDetails);
    result.put("message", "AI评分完成（仅供参考），不影响教师手动阅卷分数。");

    systemLogService.log(user, "AI阅卷", str(submission, "id"));
    return ResponseEntity.ok(result);
  }

  /**
   * AI 助手 - 使用 GLM-4-Flash 根据用户自定义提示词或预设参数生成内容
   */
  public ResponseEntity<?> practiceQuestions(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    // Rate limit check
    if (!checkRateLimit(userId)) {
      return error(HttpStatus.TOO_MANY_REQUESTS, "AI 调用频率过高，请稍后再试（每分钟最多 " + rateLimitPerMinute + " 次）");
    }

    String customPrompt = str(body, "customPrompt");

    String systemPrompt;
    String userPrompt;

    if (!customPrompt.isBlank()) {
      // User provided a custom prompt — use it directly
      systemPrompt = "教学辅导。输出JSON数组:[{title,type:single|multiple|judge|fill|short|coding,options:[A.xx,B.xx,C.xx,D.xx],answer:[],score:5,explanation}]。无markdown包裹。";
      userPrompt = customPrompt;
    } else {
      // Fall back to structured form fields
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

      systemPrompt = "教学辅导。输出JSON数组:[{title,type,options:[A.xx,B.xx,C.xx,D.xx],answer:[],score:5,explanation}]。无markdown包裹。";

      userPrompt = String.format(
        "请生成 %d 道关于「%s」的%s练习题，难度为「%s」。每题都要包含详细解析。",
        count,
        subject.isBlank() ? "计算机基础" : subject,
        typeChinese,
        difficultyChinese
      );
    }

    List<Map<String, Object>> questions;
    try {
      String aiResponse = callAiApi(systemPrompt, userPrompt);
      String subject = str(body, "subject");
      questions = parseAiQuestions(aiResponse, "", subject.isBlank() ? "" : subject, "AI助手", str(body, "difficulty").isBlank() ? "medium" : str(body, "difficulty"), str(body, "type").isBlank() ? "single" : str(body, "type"));
      // Add IDs for practice
      for (Map<String, Object> q : questions) {
        if (str(q, "id").isBlank()) {
          q.put("id", createId("practice"));
        }
      }
    } catch (Exception e) {
      systemLogService.log(user, "AI助手失败", e.getMessage());
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用：" + e.getMessage());
    }

    systemLogService.log(user, "AI助手请求", "自定义=" + !customPrompt.isBlank());
    return ResponseEntity.ok(mapOf("questions", questions, "totalCount", questions.size()));
  }

  /**
   * AI 解析答案 - 使用 GLM-4-Flash 解释正确答案和解题思路
   */
  public ResponseEntity<?> explainAnswer(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    // Require a valid authenticated user (any role)
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    // Rate limit check
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

      systemLogService.log(user, "AI解析答案", "正确=" + isCorrect);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      boolean isCorrect = studentAnswer.equals(correctAnswer);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("isCorrect", isCorrect);
      result.put("explanation", "AI 解析暂不可用，正确答案为：" + String.join(", ", correctAnswer));
      result.put("tips", "请稍后重试获取 AI 解析");
      return ResponseEntity.ok(result);
    }
  }

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
   * Call the ZhiPu GLM-4-Flash API
   */
  /**
   * Call the ZhiPu GLM-4.7-Flash API with streaming support (SSE).
   * Runs in a separate thread and feeds chunks into an SseEmitter.
   * @param thinkingEnabled if true, enables deep-thinking mode (slower but more thorough)
   */
  public void callAiApiStream(String systemPrompt, String userPrompt, boolean thinkingEnabled,
                               org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
    callAiApiStream(systemPrompt, userPrompt, thinkingEnabled, 0.9, false, emitter);
  }

  public void callAiApiStream(String systemPrompt, String userPrompt, boolean thinkingEnabled,
                               double temperature,
                               org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
    callAiApiStream(systemPrompt, userPrompt, thinkingEnabled, temperature, false, emitter);
  }

  public void callAiApiStream(String systemPrompt, String userPrompt, boolean thinkingEnabled,
                               double temperature, boolean jsonMode,
                               org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
    if (apiKey == null || apiKey.isBlank()) {
      try { emitter.send(SseEmitter.event().name("error").data("{\"error\":\"AI API密钥未配置\"}")); emitter.complete(); }
      catch (Exception ignored) {}
      return;
    }

    // Run in a separate thread so the controller can return the emitter immediately
    new Thread(() -> {
      java.io.BufferedReader reader = null;
      int maxRetries = 3;
      for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        java.net.URL url = new java.net.URL(apiUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
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
        reqBody.put("max_tokens", jsonMode ? 16384 : 32768);  // practice: enough for 5-10 questions with explanations
        reqBody.put("stream", true);
        if (jsonMode) {
          reqBody.put("response_format", java.util.Map.of("type", "json_object"));
        }
        if (thinkingEnabled) {
          reqBody.put("thinking", java.util.Map.of("type", "enabled"));
        }

        String jsonBody = objectMapper.writeValueAsString(reqBody);
        try (java.io.OutputStream os = conn.getOutputStream()) {
          os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
          os.flush();
        }

        int responseCode = conn.getResponseCode();

        // Retry on 429 (rate limit) or 503 (service unavailable) with backoff
        if ((responseCode == 429 || responseCode == 503) && attempt < maxRetries) {
          conn.disconnect();
          try { Thread.sleep(1000L * attempt * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
          continue;
        }

        if (responseCode != 200) {
          java.io.InputStream errorStream = conn.getErrorStream();
          String errorBody = errorStream != null ? new String(errorStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) : "";
          String friendlyMsg = friendlyAiError(responseCode, errorBody);
          if (attempt >= maxRetries && responseCode == 429) {
            friendlyMsg = friendlyMsg + "（已自动重试 " + maxRetries + " 次）";
          }
          emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + escapeJson(friendlyMsg) + "\"}"));
          emitter.complete();
          return;
        }

        // Read SSE stream line by line
        reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        String line;
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();

        while ((line = reader.readLine()) != null) {
          String data = null;
          if (line.startsWith("data: ")) {
            data = line.substring(6).trim();
          } else if (line.startsWith("data:")) {
            data = line.substring(5).trim();
          }
          if (data != null) {
            if ("[DONE]".equals(data)) {
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

                  // When deep thinking is disabled, redirect reasoning_content to content
                  // (GLM models sometimes send reasoning_content even without thinking enabled)
                  if (!thinkingEnabled && content.isEmpty() && !reasoningContent.isEmpty()) {
                    content = reasoningContent;
                    reasoningContent = "";
                  }

                  if (!reasoningContent.isEmpty()) {
                    reasoningBuilder.append(reasoningContent);
                    java.util.Map<String, Object> chunk = new java.util.LinkedHashMap<>();
                    chunk.put("type", "reasoning");
                    chunk.put("text", reasoningContent);
                    emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
                  }
                  if (!content.isEmpty()) {
                    contentBuilder.append(content);
                    java.util.Map<String, Object> chunk = new java.util.LinkedHashMap<>();
                    chunk.put("type", "content");
                    chunk.put("text", content);
                    emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
                  }
                }
              }
            } catch (Exception parseEx) {
              System.err.println("[AiService SSE] Failed to parse chunk: " + data.substring(0, Math.min(200, data.length())));
            }
          }
        }
        // If we exit the loop without [DONE], send complete anyway
        java.util.Map<String, Object> donePayload = new java.util.LinkedHashMap<>();
        donePayload.put("content", contentBuilder.toString());
        donePayload.put("reasoning", reasoningBuilder.toString());
        donePayload.put("done", true);
        emitter.send(SseEmitter.event().name("complete").data(objectMapper.writeValueAsString(donePayload)));
        emitter.complete();
        return; // success — exit retry loop

      } catch (Exception e) {
        // On last attempt, send error to client
        if (attempt >= maxRetries) {
          try {
            String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            emitter.send(SseEmitter.event().name("error")
              .data("{\"error\":\"" + escapeJson(errMsg) + "\"}"));
            emitter.complete();
          } catch (Exception ignored) {}
          return;
        }
        // Retry on transient errors
        try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
      } finally {
        if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        reader = null;
      }
      } // end retry loop
    }).start();
  }

  /** Escape a string for embedding in JSON. */
  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  /**
   * Streaming version for practice questions (student)
   */
  public void practiceQuestionsStream(String userId, Map<String, Object> body,
                                       org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) {
      try {
        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
          .name("error").data("{\"error\":\"Forbidden\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    String customPrompt = str(body, "customPrompt");
    String systemPrompt;
    String userPrompt;

    if (!customPrompt.isBlank()) {
      systemPrompt = """
你是出题专家。严格按步骤执行：

第1步 分析：理解用户要求的学科、知识点、难度。
第2步 出题：生成指定数量的高质量题目，全部使用中文，内容准确。
第3步 自检：确认答案正确、干扰项合理、解析有教学价值。
第4步 输出：输出一个JSON数组，每道题一个对象。不要markdown、不要废话、不要尾随逗号。

输出格式示例：
[{"title":"栈遵循什么原则？","type":"single","options":["A. 先进先出","B. 后进先出","C. 随机存取","D. 仅栈顶操作"],"answer":["B"],"score":5,"explanation":"【答案】B\\n【解析】栈是后进先出（LIFO）结构，最后放入的最先取出。"}]

字段说明：
- title: 题目文字（中文）
- type: single/multiple/judge/fill/short/coding
- options: 选项数组，单选/判断4个以"A. "开头，多选4个，填空/简答/编程用空数组[]
- answer: 答案数组，单选["A"]，多选["A","C"]，填空["答案文字"]
- score: 整数分值
- explanation: 【答案】X\\n【解析】详细解析，\\n表示换行""";
      userPrompt = customPrompt;
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

      systemPrompt = """
你是出题专家。严格按步骤执行：

第1步 分析：理解学科、知识点、难度。
第2步 出题：生成指定数量的题目，全部中文，内容准确。
第3步 自检：验证答案、干扰项、解析质量。
第4步 输出：JSON数组。不要markdown、不要废话。

格式：[{"title":"题目","type":"single","options":["A. xx","B. xx","C. xx","D. xx"],"answer":["A"],"score":5,"explanation":"【答案】A\\n【解析】..."}]

字段：title/type/options/answer/score/explanation。type取single/multiple/judge/fill/short/coding。\\n表示换行。选项以"A. "开头。全部中文。""";

      userPrompt = String.format(
        "生成 %d 道「%s」%s题，难度%s。输出JSON。",
        count, subject.isBlank() ? "计算机基础" : subject, typeChinese, difficultyChinese
      );
    }

    boolean deepThinking = Boolean.TRUE.equals(body.get("deepThinking"));
    // Practice mode: glm-4.7-flash supports json_object in streaming
    callAiApiStream(systemPrompt, userPrompt, deepThinking, 0.3, true, emitter);
  }

  /**
   * Streaming version for question generation (teacher)
   */
  public void generateQuestionsStream(String userId, Map<String, Object> body,
                                       org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) {
      try {
        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
          .name("error").data("{\"error\":\"Forbidden\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    String customPrompt = str(body, "customPrompt");
    String systemPrompt;
    String userPrompt;

    if (!customPrompt.isBlank()) {
      systemPrompt = "出题专家。输出JSON:[{title,type:single|multiple|judge|fill|short|coding,options:[A.xx,B.xx,C.xx,D.xx],answer:[],score:5,explanation}]。无markdown包裹。";
      userPrompt = customPrompt;
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

      systemPrompt = "出题专家。输出JSON:[{title,type,options:[A.xx,B.xx,C.xx,D.xx],answer:[],score:5,explanation}]。无markdown包裹。";

      userPrompt = String.format(
        "请生成 %d 道关于「%s」的%s，知识点方向为「%s」，难度为「%s」。每题分值5分。请包含详细解析。",
        count, subject.isBlank() ? "计算机基础" : subject, typeChinese,
        knowledgePoint.isBlank() ? "综合" : knowledgePoint, difficultyChinese
      );
    }

    boolean deepThinking = Boolean.TRUE.equals(body.get("deepThinking"));
    callAiApiStream(systemPrompt, userPrompt, deepThinking, emitter);
  }

  // ========== General AI Chat ==========

  /**
   * AI 自由对话 - 普通模式（任何已认证用户可用）
   */
  public ResponseEntity<?> chat(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    // Rate limit check
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

    String systemPrompt = buildChatSystemPrompt();
    String userPrompt = buildChatUserPrompt(userMessage, body);

    try {
      String aiResponse = callAiApi(systemPrompt, userPrompt);
      systemLogService.log(user, "AI对话", "完成");
      return ResponseEntity.ok(mapOf("content", aiResponse, "role", "assistant"));
    } catch (Exception e) {
      systemLogService.log(user, "AI对话失败", e.getMessage());
      return error(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用：" + e.getMessage());
    }
  }

  /**
   * AI 自由对话 - 流式 SSE（任何已认证用户可用）
   */
  public void chatStream(String userId, Map<String, Object> body,
                          org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) {
      try {
        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
          .name("error").data("{\"error\":\"Not authenticated\"}"));
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
        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
          .name("error").data("{\"error\":\"请输入消息内容\"}"));
        emitter.complete();
      } catch (Exception ex) {}
      return;
    }

    String systemPrompt = buildChatSystemPrompt();
    String userPrompt = buildChatUserPrompt(userMessage, body);

    boolean deepThinking = Boolean.TRUE.equals(body.get("deepThinking"));
    systemLogService.log(user, "AI对话流", "深度思考=" + deepThinking);
    callAiApiStream(systemPrompt, userPrompt, deepThinking, emitter);
  }

  /** Build system prompt for general chat — conversational only, no JSON format */
  private String buildChatSystemPrompt() {
    return """
      你是在线考试系统的AI学习助手，名叫"智学"。你的职责是帮助学生学习和解答问题。
      
      核心原则：
      1. 直接输出你认为最合适的回答内容，不要刻意遵循固定格式，用你认为最自然、最清晰的方式表达
      2. 回答要有深度和广度：对概念给出准确定义，对原理给出清晰推导，对应用给出实际例子
      3. 善用 Markdown 格式：合理使用标题（##、###）、加粗（**）、列表、代码块（```）等让内容层次分明
      4. 对数学公式使用 LaTeX 语法（用 $ 包裹行内公式，用 $$ 包裹独立公式）
      5. 提供学习建议：回答完问题后，可以推荐相关的延伸学习方向或练习题
      6. 使用中文回答，语言风格友好专业，像一位耐心且博学的导师
      
      注意：直接给出高质量的回答内容即可，不需要任何前缀说明或格式约束。""";
  }

  /** Build user prompt for chat, optionally including conversation history.
   *  Implements sliding window: keeps only the last N messages to avoid context bloat. */
  private static final int MAX_HISTORY_MESSAGES = 6; // 3 Q&A rounds

  private String buildChatUserPrompt(String currentMessage, Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> history = (List<Map<String, Object>>) body.get("messages");
    if (history == null || history.isEmpty()) {
      return currentMessage;
    }

    // Sliding window: keep only the most recent messages
    int startIndex = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
    int skipped = startIndex;

    StringBuilder sb = new StringBuilder();
    if (skipped > 0) {
      sb.append("[已省略前 ").append(skipped).append(" 条历史消息]\n");
    }
    sb.append("以下是最近对话：\n");
    for (int i = startIndex; i < history.size(); i++) {
      Map<String, Object> msg = history.get(i);
      String role = str(msg, "role");
      String content = str(msg, "content");
      if (!content.isBlank()) {
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

  private String callAiApi(String systemPrompt, String userPrompt) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new RuntimeException("AI API 密钥未配置，请联系管理员设置 AI_API_KEY 环境变量");
    }

    // Check cache first
    String cacheKey = String.valueOf((systemPrompt + userPrompt).hashCode());
    CachedResponse cached = responseCache.get(cacheKey);
    if (cached != null && (System.currentTimeMillis() - cached.timestamp()) < CACHE_TTL_MS) {
      return cached.content();
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", model);
    requestBody.put("messages", List.of(
      Map.of("role", "system", "content", systemPrompt),
      Map.of("role", "user", "content", userPrompt)
    ));
    requestBody.put("temperature", 0.9);
    requestBody.put("max_tokens", 32768);

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

            // Cache the response
            responseCache.put(cacheKey, new CachedResponse(content, System.currentTimeMillis()));
            cleanCache();

            return content;
          }
        }
        throw new RuntimeException("AI API 返回无效响应");

      } catch (RestClientException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";

        // Retry on 429 (rate limit) with exponential backoff
        if (msg.contains("429") && attempt < maxRetries) {
          try { Thread.sleep(1000L * attempt * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
          continue;
        }
        // Retry on 503 (service unavailable) with shorter backoff
        if (msg.contains("503") && attempt < maxRetries) {
          try { Thread.sleep(800L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
          continue;
        }

        // Non-retryable errors or all retries exhausted
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

  /**
   * Use AI to grade a subjective answer
   */
  private Map<String, Object> aiGradeAnswer(String questionTitle, String studentAnswer,
                                              String expectedAnswer, int fullScore) {
    String systemPrompt = "阅卷评分。返回JSON:{score:int(0到满分),comment}。无markdown包裹。";

    String userPrompt = String.format(
      "题目：%s\n满分：%d分\n学生作答：%s\n参考答案：%s\n请评分。",
      questionTitle, fullScore, studentAnswer, expectedAnswer
    );

    try {
      String response = callAiApi(systemPrompt, userPrompt);
      return parseAiJson(response, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      // Return fallback score
      Map<String, Object> result = new LinkedHashMap<>();
      int score = fallbackKeywordScore(studentAnswer, expectedAnswer, fullScore);
      result.put("score", score);
      result.put("comment", "关键词匹配评分");
      return result;
    }
  }

  /**
   * Parse AI response into question objects
   */
  private List<Map<String, Object>> parseAiQuestions(String aiResponse, String teacherId,
                                                       String subject, String knowledgePoint,
                                                       String difficulty, String type) {
    String cleaned = aiResponse.trim();
    // Remove markdown code blocks if present
    if (cleaned.startsWith("```")) {
      int firstNewline = cleaned.indexOf('\n');
      if (firstNewline > 0) cleaned = cleaned.substring(firstNewline + 1);
      if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
      cleaned = cleaned.trim();
    }

    List<Map<String, Object>> questions = new ArrayList<>();
    try {
      List<Map<String, Object>> parsed = objectMapper.readValue(cleaned, new TypeReference<>() {});
      for (Map<String, Object> q : parsed) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", createId("ai-question"));
        question.put("teacherId", teacherId);
        question.put("subject", str(q, "subject").isBlank() ? (subject.isBlank() ? "AI生成" : subject) : str(q, "subject"));
        question.put("knowledgePoint", str(q, "knowledgePoint").isBlank() ? (knowledgePoint.isBlank() ? "综合" : knowledgePoint) : str(q, "knowledgePoint"));
        question.put("difficulty", str(q, "difficulty").isBlank() ? difficulty : str(q, "difficulty"));
        question.put("type", str(q, "type").isBlank() ? type : str(q, "type"));
        question.put("title", str(q, "title"));
        question.put("options", normalizeList(q.get("options")));
        question.put("answer", normalizeList(q.get("answer")));
        question.put("score", asInt(q.get("score")) > 0 ? asInt(q.get("score")) : 5);
        question.put("explanation", str(q, "explanation").isBlank() ? "暂无解析" : str(q, "explanation"));
        question.put("sourceTag", "ai-generated");
        questions.add(question);
      }
    } catch (Exception e) {
      // If JSON parsing fails, try to extract questions from the text
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
    // Remove markdown code blocks if present
    if (cleaned.startsWith("```")) {
      int firstNewline = cleaned.indexOf('\n');
      if (firstNewline > 0) cleaned = cleaned.substring(firstNewline + 1);
      if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
      cleaned = cleaned.trim();
    }

    try {
      return objectMapper.readValue(cleaned, typeRef);
    } catch (Exception e) {
      // Try to extract JSON from the response
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
    long windowMs = 60_000; // 1 minute

    List<Long> timestamps = rateLimitMap.computeIfAbsent(userId, k -> new ArrayList<>());
    synchronized (timestamps) {
      // Remove timestamps older than 1 minute
      timestamps.removeIf(t -> (now - t) > windowMs);
      if (timestamps.size() >= rateLimitPerMinute) {
        return false;
      }
      timestamps.add(now);
      return true;
    }
  }

  // ========== Cache Management ==========

  private void cleanCache() {
    long now = System.currentTimeMillis();
    responseCache.entrySet().removeIf(e -> (now - e.getValue().timestamp()) > CACHE_TTL_MS);
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
}
