package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 提交记录数据访问层
 */
@Repository
public class SubmissionRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public SubmissionRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询所有提交记录 */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select * from submission order by updated_at desc,id").stream().map(row -> compact(mapOf(
      "id", row.get("id"), "examId", row.get("exam_id"), "studentId", row.get("student_id"), "studentName", row.get("student_name"),
      "answers", json.readList(row.get("answers_json")), "answerDetail", json.readList(row.get("answer_detail_json")),
      "switchCount", asInt(row.get("switch_count")), "suspicious", asBool(row.get("suspicious")),
      "suspiciousReasons", json.readList(row.get("suspicious_reasons_json")), "autoScore", asInt(row.get("auto_score")),
      "finalScore", asInt(row.get("final_score")), "status", normalizeStatus(str(row.get("status"))),
      "revision", SubmissionWrites.readRevision(row.get("revision")),
      "startedAt", json.asIso(row.get("started_at")), "deadlineAt", json.asIso(row.get("deadline_at")),
      "submittedAt", json.asIso(row.get("submitted_at")), "updatedAt", json.asIso(row.get("updated_at")),
      "manualExtendedMinutes", asInt(row.get("manual_extended_minutes")), "gradedBy", row.get("graded_by"),
      "questionOrder", json.readList(row.get("question_order_json")),
      "optionOrder", json.readMap(row.get("option_order_json"))
    ))).toList();
  }

  /** Creation cannot overwrite another session; existing writes require a matching server-read revision. */
  public void save(Map<String, Object> record) {
    SubmissionWrites.save(jdbc, json, record);
  }

  /** 根据 ID 删除提交记录 */
  public void delete(String id) {
    jdbc.update("delete from submission where id=?", id);
  }

  private String str(Map<String, Object> r, String key) {
    Object v = r.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
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

  private boolean asBool(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    // TINYINT(1) may arrive as Integer — handle gracefully
    if (value != null) {
      try { return Integer.parseInt(String.valueOf(value)) != 0; } catch (NumberFormatException ignored) {}
    }
    return false;
  }

  private String normalizeStatus(String status) {
    if (status == null) return "";
    return switch (status) {
      case "已完成", "完成" -> "已完成";
      case "待阅卷" -> "待阅卷";
      case "进行中" -> "进行中";
      case "已结束" -> "已结束";
      default -> status;
    };
  }

  private Map<String, Object> compact(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>(source);
    result.entrySet().removeIf(e -> e.getValue() == null || "".equals(e.getValue()));
    return result;
  }

  private Map<String, Object> mapOf(Object... pairs) {
    if (pairs.length % 2 != 0) {
      throw new IllegalArgumentException("mapOf 参数个数必须为偶数，当前为 " + pairs.length);
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
