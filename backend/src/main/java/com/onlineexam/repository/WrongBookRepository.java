package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 错题本数据访问层
 */
@Repository
public class WrongBookRepository {
  private final JdbcTemplate jdbc;
  private final JsonHelper json;

  public WrongBookRepository(JdbcTemplate jdbc, JsonHelper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** 查询所有错题本条目 */
  public List<Map<String, Object>> findAll() {
    return jdbc.queryForList("select * from wrong_book_entry order by last_wrong_at desc,id").stream().map(row -> compact(mapOf(
      "id", row.get("id"), "studentId", row.get("student_id"), "studentName", row.get("student_name"),
      "questionId", row.get("question_id"), "subject", row.get("subject"), "knowledgePoint", row.get("knowledge_point"),
      "type", row.get("type"), "title", row.get("title"), "latestAnswer", json.readList(row.get("latest_answer_json")),
      "expectedAnswer", json.readList(row.get("expected_answer_json")), "lastRetryAnswer", json.readList(row.get("last_retry_answer_json")),
      "retryCount", asInt(row.get("retry_count")), "wrongCount", asInt(row.get("wrong_count")),
      "fullScore", asInt(row.get("full_score")), "lastScore", asInt(row.get("last_score")),
      "lastWrongAt", json.asIso(row.get("last_wrong_at")), "lastRetryAt", json.asIso(row.get("last_retry_at")),
      "lastSourceSubmissionId", row.get("last_source_submission_id"), "lastSourceExamId", row.get("last_source_exam_id"),
      "lastRetryCorrect", asBool(row.get("last_retry_correct")), "removable", asBool(row.get("removable")),
      "removedAt", json.asIso(row.get("removed_at")), "status", row.get("status")
    ))).toList();
  }

  /** 保存或更新错题本条目 */
  public void save(Map<String, Object> r) {
    jdbc.update("""
      insert into wrong_book_entry(id,student_id,student_name,question_id,subject,knowledge_point,type,title,latest_answer_json,
      expected_answer_json,last_retry_answer_json,retry_count,wrong_count,full_score,last_score,last_wrong_at,last_retry_at,
      last_source_submission_id,last_source_exam_id,last_retry_correct,removable,removed_at,status)
      values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
      on duplicate key update student_name=values(student_name),subject=values(subject),knowledge_point=values(knowledge_point),
      type=values(type),title=values(title),latest_answer_json=values(latest_answer_json),expected_answer_json=values(expected_answer_json),
      last_retry_answer_json=values(last_retry_answer_json),retry_count=values(retry_count),wrong_count=values(wrong_count),
      full_score=values(full_score),last_score=values(last_score),last_wrong_at=values(last_wrong_at),last_retry_at=values(last_retry_at),
      last_source_submission_id=values(last_source_submission_id),last_source_exam_id=values(last_source_exam_id),
      last_retry_correct=values(last_retry_correct),removable=values(removable),removed_at=values(removed_at),status=values(status)
      """, str(r, "id"), str(r, "studentId"), nullableStr(r, "studentName"), str(r, "questionId"),
      nullableStr(r, "subject"), nullableStr(r, "knowledgePoint"), nullableStr(r, "type"), nullableStr(r, "title"),
      json.json(r.get("latestAnswer")), json.json(r.get("expectedAnswer")), json.json(r.get("lastRetryAnswer")), asInt(r.get("retryCount")),
      asInt(r.get("wrongCount")), asInt(r.get("fullScore")), asInt(r.get("lastScore")), json.timestamp(r.get("lastWrongAt")),
      json.timestamp(r.get("lastRetryAt")), nullableStr(r, "lastSourceSubmissionId"), nullableStr(r, "lastSourceExamId"),
      asBool(r, "lastRetryCorrect"), asBool(r, "removable"), json.timestamp(r.get("removedAt")), nullableStr(r, "status"));
  }

  /** 根据 ID 删除错题本条目 */
  public void delete(String id) {
    jdbc.update("delete from wrong_book_entry where id=?", id);
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
