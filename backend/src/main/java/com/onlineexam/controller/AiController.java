package com.onlineexam.controller;

import com.onlineexam.service.AiService;
import com.onlineexam.service.AnalysisService;
import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 控制器 - 处理 AI 题目生成、评分和练习
 */
@RestController
@RequestMapping("/api")
public class AiController {

  private final AiService aiService;
  private final AnalysisService analysisService;
  private final StoreService storeService;

  public AiController(AiService aiService, AnalysisService analysisService, StoreService storeService) {
    this.aiService = aiService;
    this.analysisService = analysisService;
    this.storeService = storeService;
  }

  /**
   * AI 生成题目（教师端）
   */
  @PostMapping("/ai/generate-questions")
  public ResponseEntity<?> aiGenerateQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                               @RequestBody Map<String, Object> body) {
    return aiService.generateQuestions(userId, body);
  }

  /**
   * 导入 AI 生成的题目到题库（教师端）
   */
  @PostMapping("/ai/import-questions")
  public ResponseEntity<?> aiImportQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                             @RequestBody Map<String, Object> body) {
    return aiService.importQuestions(userId, body);
  }

  /**
   * AI 辅助阅卷评分（教师端）
   */
  @PostMapping("/ai/grade-submission")
  public ResponseEntity<?> aiGradeSubmission(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                             @RequestBody Map<String, Object> body) {
    return aiService.gradeSubmission(userId, body);
  }

  /**
   * AI 练习题生成（学生端）
   */
  @PostMapping("/ai/practice-questions")
  public ResponseEntity<?> aiPracticeQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                               @RequestBody Map<String, Object> body) {
    return aiService.practiceQuestions(userId, body);
  }

  /**
   * AI 解析答案
   */
  @PostMapping("/ai/explain-answer")
  public ResponseEntity<?> aiExplainAnswer(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                           @RequestBody Map<String, Object> body) {
    return aiService.explainAnswer(userId, body);
  }

  /**
   * AI 练习题生成 - 流式 SSE（学生端，使用 glm-4.7-flash + 深度思考）
   * 客户端通过 EventSource 或 fetch ReadableStream 消费
   */
  @PostMapping(value = "/ai/practice-questions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter aiPracticeQuestionsStream(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                              @RequestBody Map<String, Object> body) {
    SseEmitter emitter = new SseEmitter(180_000L); // 3 min timeout
    aiService.practiceQuestionsStream(userId, body, emitter);
    return emitter;
  }

  /**
   * AI 生成题目 - 流式 SSE（教师端，使用 glm-4.7-flash + 深度思考）
   */
  @PostMapping(value = "/ai/generate-questions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter aiGenerateQuestionsStream(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                              @RequestBody Map<String, Object> body) {
    SseEmitter emitter = new SseEmitter(180_000L);
    aiService.generateQuestionsStream(userId, body, emitter);
    return emitter;
  }

  /**
   * AI 结构化出题（教师端，增强版参数验证）
   */
  @PostMapping("/ai/generate-questions-structured")
  public ResponseEntity<?> aiGenerateQuestionsStructured(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                         @RequestBody Map<String, Object> body) {
    return aiService.generateQuestions(userId, body);
  }

  /**
   * AI 结构化出题 - 流式 SSE（教师端，增强版参数验证）
   */
  @PostMapping(value = "/ai/generate-questions-structured/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter aiGenerateQuestionsStructuredStream(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                         @RequestBody Map<String, Object> body) {
    SseEmitter emitter = new SseEmitter(180_000L);
    aiService.generateQuestionsStream(userId, body, emitter);
    return emitter;
  }

  /**
   * 题库备份（教师端）
   */
  @PostMapping("/ai/backup-question-bank")
  public ResponseEntity<?> aiBackupQuestionBank(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录"));
    }
    Store store = storeService.readStore();
    List<Map<String, Object>> questions = store.questions.stream()
        .filter(q -> userId.equals(String.valueOf(q.get("teacherId"))))
        .filter(q -> !Boolean.TRUE.equals(q.get("deleted")))
        .toList();
    if (questions.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("message", "没有可备份的题目"));
    }
    String backupId = UUID.randomUUID().toString();
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", backupId);
    record.put("teacherId", userId);
    record.put("questions", new ArrayList<>(questions));
    record.put("questionCount", questions.size());
    record.put("createdAt", Instant.now().toString());
    storeService.saveRecord("backups", record);
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("id", backupId);
    info.put("teacherId", userId);
    info.put("questionCount", questions.size());
    info.put("createdAt", record.get("createdAt"));
    return ResponseEntity.ok(info);
  }

  /**
   * 列出题库备份（教师端）
   */
  @GetMapping("/ai/backups")
  public ResponseEntity<?> aiListBackups(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录"));
    }
    Store store = storeService.readStore();
    List<Map<String, Object>> backups = store.backups.stream()
        .filter(b -> userId.equals(String.valueOf(b.get("teacherId"))))
        .map(b -> {
          Map<String, Object> meta = new LinkedHashMap<>();
          meta.put("id", b.get("id"));
          meta.put("teacherId", b.get("teacherId"));
          meta.put("questionCount", b.get("questionCount"));
          meta.put("createdAt", b.get("createdAt"));
          return meta;
        })
        .toList();
    return ResponseEntity.ok(Map.of("backups", backups));
  }

  /**
   * 从备份恢复题库（教师端）
   */
  @PostMapping("/ai/restore-backup")
  public ResponseEntity<?> aiRestoreBackup(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                            @RequestBody Map<String, Object> body) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录"));
    }
    String backupId = body.get("backupId") != null ? String.valueOf(body.get("backupId")) : null;
    if (backupId == null || backupId.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "缺少 backupId"));
    }
    Store store = storeService.readStore();
    Map<String, Object> backup = store.backups.stream()
        .filter(b -> backupId.equals(String.valueOf(b.get("id"))))
        .findFirst()
        .orElse(null);
    if (backup == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "备份不存在"));
    }
    if (!userId.equals(String.valueOf(backup.get("teacherId")))) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "无权操作此备份"));
    }
    // 软删除当前所有题目
    store.questions.stream()
        .filter(q -> userId.equals(String.valueOf(q.get("teacherId"))))
        .filter(q -> !Boolean.TRUE.equals(q.get("deleted")))
        .forEach(q -> {
          q.put("deleted", true);
          storeService.saveRecord("questions", q);
        });
    // 恢复备份中的题目
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> backupQuestions = (List<Map<String, Object>>) backup.get("questions");
    if (backupQuestions != null) {
      for (Map<String, Object> q : backupQuestions) {
        q.put("deleted", false);
        storeService.saveRecord("questions", q);
      }
    }
    int restoredCount = backupQuestions != null ? backupQuestions.size() : 0;
    return ResponseEntity.ok(Map.of("message", "恢复成功", "restoredCount", restoredCount));
  }

  /**
   * 删除题库备份（教师端）
   */
  @DeleteMapping("/ai/backups/{backupId}")
  public ResponseEntity<?> aiDeleteBackup(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @PathVariable String backupId) {
    if (userId == null || userId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录"));
    }
    Store store = storeService.readStore();
    Map<String, Object> backup = store.backups.stream()
        .filter(b -> backupId.equals(String.valueOf(b.get("id"))))
        .findFirst()
        .orElse(null);
    if (backup == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "备份不存在"));
    }
    if (!userId.equals(String.valueOf(backup.get("teacherId")))) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "无权操作此备份"));
    }
    storeService.deleteRecord("backups", backupId);
    return ResponseEntity.ok(Map.of("message", "备份已删除"));
  }

  /**
   * AI 自由对话 - 普通模式（学生端，任何已认证用户）
   */
  @PostMapping("/ai/chat")
  public ResponseEntity<?> aiChat(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                  @RequestBody Map<String, Object> body) {
    return aiService.chat(userId, body);
  }

  /**
   * AI 自由对话 - 流式 SSE（学生端，任何已认证用户，使用 glm-4.7-flash + 深度思考）
   */
  @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter aiChatStream(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                 @RequestBody Map<String, Object> body) {
    SseEmitter emitter = new SseEmitter(180_000L); // 3 min timeout
    aiService.chatStream(userId, body, emitter);
    return emitter;
  }

  /**
   * 保存单条练习记录
   */
  @PostMapping("/practice/record")
  public ResponseEntity<?> savePracticeRecord(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                              @RequestBody Map<String, Object> body) {
    return aiService.savePracticeRecord(userId, body);
  }

  /**
   * 批量保存练习记录
   */
  @PostMapping("/practice/records")
  public ResponseEntity<?> savePracticeRecords(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                               @RequestBody Map<String, Object> body) {
    return aiService.savePracticeRecords(userId, body);
  }

  @org.springframework.web.bind.annotation.GetMapping("/exams/{examId}/question-analysis")
  public ResponseEntity<?> questionAnalysis(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                            @PathVariable String examId) {
    return analysisService.questionAnalysis(userId, examId);
  }

  /**
   * AI 服务健康检查（含熔断器状态、活跃连接数等）
   */
  @GetMapping("/ai/health")
  public ResponseEntity<?> aiHealth() {
    return aiService.getHealthStatus();
  }
}
