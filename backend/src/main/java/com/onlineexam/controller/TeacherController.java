package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.repository.QuestionRepository;
import com.onlineexam.service.EntityCrudService;
import com.onlineexam.service.PaperService;
import com.onlineexam.service.QuestionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师控制器 - 处理题目、试卷和考试的 CRUD
 */
@RestController
@RequestMapping("/api")
public class TeacherController {

  private final StoreService storeService;
  private final EntityCrudService entityCrudService;
  private final QuestionService questionService;
  private final PaperService paperService;
  private final QuestionRepository questionRepository;

  public TeacherController(StoreService storeService, EntityCrudService entityCrudService,
                           QuestionService questionService, PaperService paperService,
                           QuestionRepository questionRepository) {
    this.storeService = storeService;
    this.entityCrudService = entityCrudService;
    this.questionService = questionService;
    this.paperService = paperService;
    this.questionRepository = questionRepository;
  }

  @PutMapping("/entities/{entity}")
  public ResponseEntity<?> updateEntity(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                        @PathVariable String entity,
                                        @RequestBody Map<String, Object> body) {
    return entityCrudService.updateEntity(userId, entity, body);
  }

  @DeleteMapping("/entities/{entity}/{id}")
  public ResponseEntity<?> deleteEntity(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                        @PathVariable String entity,
                                        @PathVariable String id) {
    return entityCrudService.deleteEntity(userId, entity, id);
  }

  @PostMapping("/questions/batch-import")
  public ResponseEntity<?> importQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                           @RequestBody Map<String, Object> body) {
    return questionService.importQuestions(userId, body);
  }

  @PostMapping("/questions/batch-delete")
  public ResponseEntity<?> deleteQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                           @RequestBody Map<String, Object> body) {
    return questionService.deleteQuestions(userId, body);
  }

  @PostMapping("/questions/batch-restore")
  public ResponseEntity<?> restoreQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                            @RequestBody Map<String, Object> body) {
    return questionService.restoreQuestions(userId, body);
  }

  @GetMapping("/questions/page")
  public ResponseEntity<?> questionsPage(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         @RequestParam(defaultValue = "") String keyword,
                                         @RequestParam(defaultValue = "all") String type,
                                         @RequestParam(defaultValue = "all") String subject) {
    Map<String, Object> user = find(storeService.readStore().users, userId);
    if (!isRole(user, "teacher") && !isRole(user, "admin")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    String teacherId = isRole(user, "admin") ? null : userId;
    return ResponseEntity.ok(questionRepository.queryPage(teacherId, page, pageSize, keyword, type, subject));
  }

  @GetMapping("/questions/subjects")
  public ResponseEntity<?> questionSubjects(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    Map<String, Object> user = find(storeService.readStore().users, userId);
    if (!isRole(user, "teacher") && !isRole(user, "admin")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    return ResponseEntity.ok(mapOf("subjects", questionRepository.querySubjects(userId)));
  }

  @PostMapping("/papers/auto-generate")
  public ResponseEntity<?> autoGeneratePaper(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                             @RequestBody Map<String, Object> body) {
    return paperService.autoGeneratePaper(userId, body);
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    if (pairs.length % 2 != 0) { throw new IllegalArgumentException("mapOf 参数个数必须为偶数，当前为 " + pairs.length); }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
