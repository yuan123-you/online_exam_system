package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 用户数据访问层
 */
@Repository
public class UserRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public UserRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询所有用户（不返回密码字段） */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select id,role,username,name,department_id,class_id,major from user_account order by id").stream().map(row -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", row.get("id"));
      m.put("role", row.get("role"));
      m.put("username", row.get("username"));
      m.put("name", row.get("name"));
      m.put("departmentId", row.get("department_id"));
      m.put("classId", row.get("class_id"));
      m.put("major", row.get("major"));
      return compact(m);
    }).toList();
  }

  /** 保存或更新用户记录 */
  public void save(Map<String, Object> r) {
    jdbc.update("""
      insert into user_account(id,role,username,password,name,department_id,class_id,major) values(?,?,?,?,?,?,?,?)
      on duplicate key update role=values(role),username=values(username),password=values(password),name=values(name),
      department_id=values(department_id),class_id=values(class_id),major=values(major)
      """, str(r, "id"), str(r, "role"), str(r, "username"), str(r, "password"), str(r, "name"),
      nullableStr(r, "departmentId"), nullableStr(r, "classId"), nullableStr(r, "major"));
  }

  /** 根据 ID 删除用户 */
  public void delete(String id) {
    jdbc.update("delete from user_account where id=?", id);
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
    Map<String, Object> result = new LinkedHashMap<>(source);
    result.entrySet().removeIf(e -> e.getValue() == null || "".equals(e.getValue()));
    return result;
  }
}
