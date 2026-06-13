package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 系统日志数据访问层
 */
@Repository
public class SystemLogRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public SystemLogRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询最近的系统日志（最多100条） */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select id,actor_id,action,detail,time from system_log order by time desc limit 100").stream().map(row -> compact(mapOf(
      "id", row.get("id"), "actorId", row.get("actor_id"), "action", row.get("action"), "detail", row.get("detail"), "time", json.asIso(row.get("time"))
    ))).toList();
  }

  /** 保存或更新系统日志 */
  public void save(Map<String, Object> r) {
    jdbc.update("""
      insert into system_log(id,actor_id,action,detail,time) values(?,?,?,?,?)
      on duplicate key update actor_id=values(actor_id),action=values(action),detail=values(detail),time=values(time)
      """, str(r, "id"), str(r, "actorId"), str(r, "action"), nullableStr(r, "detail"), json.timestamp(r.get("time")));
  }

  /** 根据 ID 删除系统日志 */
  public void delete(String id) {
    jdbc.update("delete from system_log where id=?", id);
  }

  private String str(Map<String, Object> r, String key) {
    Object v = r.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String nullableStr(Map<String, Object> r, String key) {
    String value = str(r, key);
    return value.isBlank() ? null : value;
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
