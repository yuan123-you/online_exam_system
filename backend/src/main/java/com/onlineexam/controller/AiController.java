package com.onlineexam.controller;

import com.onlineexam.service.AiService;
import com.onlineexam.service.AnalysisService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  public AiController(AiService aiService, AnalysisService analysisService) {
    this.aiService = aiService;
    this.analysisService = analysisService;
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
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("message", "题库备份功能暂未实现"));
  }

  /**
   * 列出题库备份（教师端）
   */
  @GetMapping("/ai/backups")
  public ResponseEntity<?> aiListBackups(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("message", "题库备份功能暂未实现"));
  }

  /**
   * 从备份恢复题库（教师端）
   */
  @PostMapping("/ai/restore-backup")
  public ResponseEntity<?> aiRestoreBackup(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                            @RequestBody Map<String, Object> body) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("message", "题库备份功能暂未实现"));
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
