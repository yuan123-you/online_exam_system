package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 试卷服务 - 处理试卷 CRUD 和自动生成
 */
@Service
public class PaperService {

  private final StoreService storeService;
  private final EntityCrudService entityCrudService;
  private final SystemLogService systemLogService;

  public PaperService(StoreService storeService, EntityCrudService entityCrudService, SystemLogService systemLogService) {
    this.storeService = storeService;
    this.entityCrudService = entityCrudService;
    this.systemLogService = systemLogService;
  }

  /**
   * 自动生成试卷
   */
  public ResponseEntity<?> autoGeneratePaper(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "teacher")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    String name = str(body, "name");
    int durationMinutes = asInt(body.get("durationMinutes"));
    int passScore = asInt(body.get("passScore"));
    List<Object> rules = asList(body.get("rules"));
    List<Map<String, Object>> available = store.questions.stream()
      .filter(q -> Objects.equals(str(q, "teacherId"), userId)).toList();
    Set<String> occupiedIds = new HashSet<>();
    for (Map<String, Object> p : store.papers) occupiedIds.addAll(asList(p.get("questionIds")).stream().map(String::valueOf).toList());
    List<String> selectedIds = new ArrayList<>();
    for (Object ruleRaw : rules) {
      Map<String, Object> rule = asMap(ruleRaw);
      String type = str(rule, "type");
      int count = asInt(rule.get("count"));
      String subjectFilter = str(rule, "subject");
      String kpFilter = str(rule, "knowledgePoint");
      String diffFilter = str(rule, "difficulty");
      List<Map<String, Object>> pool = new ArrayList<>(available.stream()
        .filter(q -> Objects.equals(str(q, "type"), type) && !occupiedIds.contains(str(q, "id"))
          && (subjectFilter.isEmpty() || Objects.equals(str(q, "subject"), subjectFilter))
          && (kpFilter.isEmpty() || Objects.equals(str(q, "knowledgePoint"), kpFilter))
          && (diffFilter.isEmpty() || Objects.equals(str(q, "difficulty"), diffFilter)))
        .toList());
      Collections.shuffle(pool);
      for (int i = 0; i < Math.min(count, pool.size()); i++) {
        selectedIds.add(str(pool.get(i), "id"));
        occupiedIds.add(str(pool.get(i), "id"));
      }
    }
    int totalScore = selectedIds.stream().mapToInt(id -> asInt(Optional.ofNullable(find(store.questions, id)).map(q -> q.get("score")).orElse(0))).sum();
    Map<String, Object> paper = new LinkedHashMap<>();
    paper.put("id", createId("paper"));
    paper.put("teacherId", userId);
    paper.put("name", name);
    paper.put("durationMinutes", durationMinutes > 0 ? durationMinutes : 60);
    paper.put("passScore", passScore);
    paper.put("totalScore", totalScore);
    paper.put("questionIds", selectedIds);
    paper.put("paperType", "auto");
    String validation = entityCrudService.validate(store, "papers", paper, null);
    if (!validation.isBlank()) return error(HttpStatus.BAD_REQUEST, validation);
    storeService.saveRecord("papers", paper);
    systemLogService.log(user, "auto generate paper", str(paper, "id"));
    return ResponseEntity.ok(mapOf("record", paper));
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
