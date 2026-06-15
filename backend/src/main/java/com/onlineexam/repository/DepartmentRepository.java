package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 学院/系部数据访问层
 */
@Repository
public class DepartmentRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public DepartmentRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询所有学院 */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select id,name,created_by from department order by id").stream().map(row -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", row.get("id"));
      m.put("name", row.get("name"));
      m.put("createdBy", row.get("created_by"));
      return compact(m);
    }).toList();
  }

  /** 保存或更新学院记录 */
  public void save(Map<String, Object> r) {
    jdbc.update("insert into department(id,name,created_by) values(?,?,?) on duplicate key update name=values(name),created_by=values(created_by)",
        str(r, "id"), str(r, "name"), nullableStr(r, "createdBy"));
  }

  private String nullableStr(Map<String, Object> r, String key) {
    String value = str(r, key);
    return value.isBlank() ? null : value;
  }

  /** 根据 ID 删除学院 */
  public void delete(String id) {
    jdbc.update("delete from department where id=?", id);
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
