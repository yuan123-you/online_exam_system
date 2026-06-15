package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 考试数据访问层
 */
@Repository
public class ExamRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public ExamRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询所有考试 */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select * from exam where deleted=0 order by start_time desc,id").stream().map(row -> compact(mapOf(
      "id", row.get("id"), "teacherId", row.get("teacher_id"), "paperId", row.get("paper_id"), "name", row.get("name"),
      "targetClassIds", json.readList(row.get("target_class_ids_json")), "startTime", json.asIso(row.get("start_time")),
      "endTime", json.asIso(row.get("end_time")), "antiCheatLimit", asInt(row.get("anti_cheat_limit")),
      "published", asBool(row.get("published")), "deleted", asBool(row.get("deleted"))
    ))).toList();
  }

  /** 保存或更新考试记录 */
  public void save(Map<String, Object> r) {
    jdbc.update("""
      insert into exam(id,teacher_id,paper_id,name,target_class_ids_json,start_time,end_time,anti_cheat_limit,published,deleted)
      values(?,?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),paper_id=values(paper_id),name=values(name),
      target_class_ids_json=values(target_class_ids_json),start_time=values(start_time),end_time=values(end_time),
      anti_cheat_limit=values(anti_cheat_limit),published=values(published),deleted=values(deleted)
      """, str(r, "id"), str(r, "teacherId"), str(r, "paperId"), str(r, "name"), json.json(r.get("targetClassIds")),
      json.timestamp(r.get("startTime")), json.timestamp(r.get("endTime")), asInt(r.get("antiCheatLimit")), asBool(r.get("published")),
      asBool(r, "deleted") ? 1 : 0);
  }

  /** 软删除考试 */
  public void delete(String id) {
    jdbc.update("update exam set deleted=1 where id=?", id);
  }

  private String str(Map<String, Object> r, String key) {
    Object v = r.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }

  private boolean asBool(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    // TINYINT(1) may arrive as Integer — handle gracefully
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
