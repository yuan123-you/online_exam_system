package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 班级数据访问层
 */
@Repository
public class ClassRoomRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public ClassRoomRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询所有班级 */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select id,name,major,department_id,created_by from class_info order by id").stream().map(row -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", row.get("id"));
      m.put("name", row.get("name"));
      m.put("major", row.get("major"));
      m.put("departmentId", row.get("department_id"));
      m.put("createdBy", row.get("created_by"));
      return compact(m);
    }).toList();
  }

  /** 保存或更新班级记录 */
  public void save(Map<String, Object> r) {
    jdbc.update("""
      insert into class_info(id,name,major,department_id,created_by) values(?,?,?,?,?)
      on duplicate key update name=values(name),major=values(major),department_id=values(department_id),created_by=values(created_by)
      """, str(r, "id"), str(r, "name"), str(r, "major"), str(r, "departmentId"), nullableStr(r, "createdBy"));
  }

  private String nullableStr(Map<String, Object> r, String key) {
    String value = str(r, key);
    return value.isBlank() ? null : value;
  }

  /** 根据 ID 删除班级 */
  public void delete(String id) {
    jdbc.update("delete from class_info where id=?", id);
  }

  private String str(Map<String, Object> r, String key) {
    Object v = r.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private Map<String, Object> compact(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>(source);
    result.entrySet().removeIf(e -> e.getValue() == null || "".equals(e.getValue()));
    return result;
  }
}
