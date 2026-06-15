package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 题目数据访问层
 */
@Repository
public class QuestionRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public QuestionRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询所有题目 */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select * from question where deleted=0 order by id").stream().map(this::rowToMap).toList();
  }

  /** 查询题目的教师 ID（包括已软删除的题目） */
  public String findTeacherIdIncludingDeleted(String questionId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT teacher_id FROM question WHERE id = ?", questionId);
    if (rows.isEmpty()) return null;
    Object teacherId = rows.get(0).get("teacher_id");
    return teacherId == null ? "" : String.valueOf(teacherId);
  }

  /** 保存或更新题目记录 */
  public void save(Map<String, Object> r) {
    jdbc.update("""
      insert into question(id,teacher_id,subject,knowledge_point,difficulty,type,title,options_json,answer_json,score,source_tag,deleted)
      values(?,?,?,?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),subject=values(subject),knowledge_point=values(knowledge_point),
      difficulty=values(difficulty),type=values(type),title=values(title),options_json=values(options_json),
      answer_json=values(answer_json),score=values(score),source_tag=values(source_tag),deleted=values(deleted)
      """, str(r, "id"), str(r, "teacherId"), str(r, "subject"), str(r, "knowledgePoint"), str(r, "difficulty"),
      str(r, "type"), str(r, "title"), json.json(r.get("options")), json.json(r.get("answer")),
      asInt(r.get("score")), nullableStr(r, "sourceTag"), asBool(r, "deleted") ? 1 : 0);
  }

  /** 软删除题目 */
  public void delete(String id) {
    jdbc.update("update question set deleted=1 where id=?", id);
  }

  /** 分页查询题目 */
  public Map<String, Object> queryPage(String teacherId, int page, int pageSize, String keyword, String type, String subject) {
    StringBuilder where = new StringBuilder("WHERE deleted=0");
    List<Object> params = new ArrayList<>();
    if (teacherId != null && !teacherId.isBlank()) {
      where.append(" AND teacher_id = ?");
      params.add(teacherId);
    }
    if (keyword != null && !keyword.isBlank()) {
      String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      where.append(" AND (title LIKE ? ESCAPE '\\\\' OR knowledge_point LIKE ? ESCAPE '\\\\')");
      params.add("%" + escaped + "%");
      params.add("%" + escaped + "%");
    }
    if (type != null && !type.equals("all")) {
      where.append(" AND type = ?");
      params.add(type);
    }
    if (subject != null && !subject.equals("all")) {
      where.append(" AND subject = ?");
      params.add(subject);
    }
    int total = Optional.ofNullable(jdbc.queryForObject(
      "SELECT COUNT(*) FROM question " + where, Integer.class, params.toArray())).orElse(0);
    int offset = (Math.max(1, page) - 1) * pageSize;
    List<Object> fullParams = new ArrayList<>(params);
    fullParams.add(pageSize);
    fullParams.add(offset);
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT * FROM question " + where + " ORDER BY id LIMIT ? OFFSET ?", fullParams.toArray()).stream()
      .map(this::rowToMap).toList();
    return mapOf("rows", rows, "total", total, "page", page, "pageSize", pageSize);
  }

  /** 查询教师的所有科目 */
  public List<String> querySubjects(String teacherId) {
    return jdbc.queryForList("SELECT DISTINCT subject FROM question WHERE teacher_id = ? ORDER BY subject", String.class, teacherId);
  }

  private Map<String, Object> rowToMap(Map<String, Object> row) {
    return compact(mapOf(
      "id", row.get("id"), "teacherId", row.get("teacher_id"), "subject", row.get("subject"),
      "knowledgePoint", row.get("knowledge_point"), "difficulty", row.get("difficulty"), "type", row.get("type"),
      "title", row.get("title"), "options", json.readList(row.get("options_json")), "answer", json.readList(row.get("answer_json")),
      "score", asInt(row.get("score")), "sourceTag", row.get("source_tag"),
      "deleted", asBool(row.get("deleted"))
    ));
  }

  private String str(Map<String, Object> r, String key) {
    Object v = r.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String nullableStr(Map<String, Object> r, String key) {
    String value = str(r, key);
    return value.isBlank() ? null : value;
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value instanceof Boolean) return 0; // TINYINT(1) may arrive as Boolean without tinyInt1isBit=false
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }

  private boolean asBool(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    // TINYINT(1) may arrive as Integer even with instanceof checks — handle gracefully
    if (value != null) {
      try { return Integer.parseInt(String.valueOf(value)) != 0; } catch (NumberFormatException ignored) {}
    }
    return false;
  }

  private boolean asBool(Map<String, Object> r, String key) {
    return asBool(r.get(key));
  }

  private Map<String, Object> compact(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>(source);
    result.entrySet().removeIf(e -> e.getValue() == null || "".equals(e.getValue()));
    return result;
  }

  private Map<String, Object> mapOf(Object... pairs) {
    if (pairs.length % 2 != 0) { throw new IllegalArgumentException("mapOf 参数个数必须为偶数，当前为 " + pairs.length); }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
