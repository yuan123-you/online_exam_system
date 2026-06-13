package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 试卷数据访问层
 */
@Repository
public class PaperRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public PaperRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询所有试卷 */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select * from paper where deleted=0 order by id").stream().map(row -> compact(mapOf(
      "id", row.get("id"), "teacherId", row.get("teacher_id"), "name", row.get("name"),
      "durationMinutes", asInt(row.get("duration_minutes")), "totalScore", asInt(row.get("total_score")),
      "passScore", asInt(row.get("pass_score")), "questionIds", json.readList(row.get("question_ids_json")),
      "paperType", row.get("paper_type"), "sourceTag", row.get("source_tag"), "deleted", asBool(row.get("deleted"))
    ))).toList();
  }

  /** 保存或更新试卷记录 */
  public void save(Map<String, Object> r) {
    jdbc.update("""
      insert into paper(id,teacher_id,name,duration_minutes,total_score,pass_score,question_ids_json,paper_type,source_tag,deleted)
      values(?,?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),name=values(name),duration_minutes=values(duration_minutes),
      total_score=values(total_score),pass_score=values(pass_score),question_ids_json=values(question_ids_json),
      paper_type=values(paper_type),source_tag=values(source_tag),deleted=values(deleted)
      """, str(r, "id"), str(r, "teacherId"), str(r, "name"), asInt(r.get("durationMinutes")), asInt(r.get("totalScore")),
      asInt(r.get("passScore")), json.json(r.get("questionIds")), nullableStr(r, "paperType"), nullableStr(r, "sourceTag"),
      asBool(r, "deleted") ? 1 : 0);
  }

  /** 软删除试卷 */
  public void delete(String id) {
    jdbc.update("update paper set deleted=1 where id=?", id);
  }

  private boolean asBool(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  private boolean asBool(Map<String, Object> r, String key) {
    return asBool(r.get(key));
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
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }

  private Map<String, Object> compact(Map<String, Object> source) {
    source.entrySet().removeIf(e -> e.getValue() == null || "".equals(e.getValue()));
    return source;
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
