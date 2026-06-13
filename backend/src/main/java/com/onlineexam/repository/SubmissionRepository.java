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
      "startedAt", json.asIso(row.get("started_at")), "deadlineAt", json.asIso(row.get("deadline_at")),
      "submittedAt", json.asIso(row.get("submitted_at")), "updatedAt", json.asIso(row.get("updated_at")),
      "manualExtendedMinutes", asInt(row.get("manual_extended_minutes")), "gradedBy", row.get("graded_by"),
      "questionOrder", json.readList(row.get("question_order_json")),
      "optionOrder", json.readMap(row.get("option_order_json"))
    ))).toList();
  }

  /** 保存或更新提交记录 */
  public void save(Map<String, Object> r) {
    jdbc.update("""
      insert into submission(id,exam_id,student_id,student_name,answers_json,answer_detail_json,switch_count,suspicious,
      suspicious_reasons_json,auto_score,final_score,status,started_at,deadline_at,submitted_at,updated_at,manual_extended_minutes,graded_by,question_order_json,option_order_json)
      values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
      on duplicate key update student_name=values(student_name),answers_json=values(answers_json),
      answer_detail_json=values(answer_detail_json),switch_count=values(switch_count),suspicious=values(suspicious),
      suspicious_reasons_json=values(suspicious_reasons_json),auto_score=values(auto_score),final_score=values(final_score),
      status=values(status),started_at=values(started_at),deadline_at=values(deadline_at),submitted_at=values(submitted_at),
      updated_at=values(updated_at),manual_extended_minutes=values(manual_extended_minutes),graded_by=values(graded_by),
      question_order_json=values(question_order_json),option_order_json=values(option_order_json)
      """, str(r, "id"), str(r, "examId"), str(r, "studentId"), str(r, "studentName"), json.json(r.get("answers")),
      json.json(r.get("answerDetail")), asInt(r.get("switchCount")), asBool(r.get("suspicious")), json.json(r.get("suspiciousReasons")),
      asInt(r.get("autoScore")), asInt(r.get("finalScore")), normalizeStatus(str(r, "status")), json.timestamp(r.get("startedAt")),
      json.timestamp(r.get("deadlineAt")), json.timestamp(r.get("submittedAt")), json.timestamp(r.get("updatedAt")),
      asInt(r.get("manualExtendedMinutes")), nullableStr(r, "gradedBy"), json.json(r.get("questionOrder")), json.json(r.get("optionOrder")));
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
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  private String normalizeStatus(String status) {
    if (status == null) return "";
    if (status.contains("\u5df2\u5b8c\u6210") || status.contains("\u5b8c\u6210")) return "\u5df2\u5b8c\u6210";
    if (status.contains("\u5f85\u9605\u5377")) return "\u5f85\u9605\u5377";
    if (status.contains("\u8fdb\u884c\u4e2d")) return "\u8fdb\u884c\u4e2d";
    return status;
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
