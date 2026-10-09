package com.onlineexam.repository;

import com.onlineexam.common.JsonHelper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Shared database write authority: creation is insert-only; updates use server-read revisions. */
public final class SubmissionWrites {
  private SubmissionWrites() {}

  public static void save(JdbcTemplate jdbc, JsonHelper json, Map<String,Object> record) {
    String status = normalizeStatus(text(record, "status"));
    List<String> from = switch (status) {
      case "进行中" -> List.of("进行中");
      case "已结束" -> List.of("进行中", "已结束");
      case "待阅卷" -> List.of("进行中", "待阅卷");
      case "已完成" -> List.of("进行中", "待阅卷", "已完成", "完成");
      default -> throw conflict();
    };
    Object[] values = {
        text(record,"id"), text(record,"examId"), text(record,"studentId"), text(record,"studentName"),
        json.json(record.get("answers")), json.json(record.get("answerDetail")), integer(record.get("switchCount")),
        truth(record.get("suspicious")), json.json(record.get("suspiciousReasons")), integer(record.get("autoScore")),
        integer(record.get("finalScore")), status, json.timestamp(record.get("startedAt")), json.timestamp(record.get("deadlineAt")),
        json.timestamp(record.get("submittedAt")), json.timestamp(record.get("updatedAt")), integer(record.get("manualExtendedMinutes")),
        nullable(record,"gradedBy"), json.json(record.get("questionOrder")), json.json(record.get("optionOrder"))
    };
    if (!record.containsKey("revision")) {
      try {
        int affected = jdbc.update("""
            insert into submission(id,exam_id,student_id,student_name,answers_json,answer_detail_json,switch_count,suspicious,
            suspicious_reasons_json,auto_score,final_score,status,started_at,deadline_at,submitted_at,updated_at,manual_extended_minutes,
            graded_by,question_order_json,option_order_json) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, values);
        if (affected != 1) throw conflict();
      } catch (DuplicateKeyException e) {
        throw conflict();
      }
      return;
    }
    long revision = expectedRevision(record.get("revision"));
    List<Object> arguments = new ArrayList<>(Arrays.asList(values).subList(3, values.length));
    arguments.add(text(record,"id")); arguments.add(text(record,"examId")); arguments.add(text(record,"studentId")); arguments.add(revision);
    arguments.addAll(from);
    String placeholders = String.join(",", java.util.Collections.nCopies(from.size(), "?"));
    int affected = jdbc.update("""
        update submission set student_name=?,answers_json=?,answer_detail_json=?,switch_count=?,suspicious=?,suspicious_reasons_json=?,
        auto_score=?,final_score=?,status=?,started_at=?,deadline_at=?,submitted_at=?,updated_at=?,manual_extended_minutes=?,graded_by=?,
        question_order_json=?,option_order_json=?,revision=revision+1
        where id=? and exam_id=? and student_id=? and revision=? and status in (
        """ + placeholders + ")", arguments.toArray());
    if (affected != 1) throw conflict();
  }

  /** BIGINT metadata comes only from persisted records, not from a student request body. */
  public static long readRevision(Object raw) {
    return raw == null ? 0L : new BigDecimal(raw.toString()).longValueExact();
  }
  private static long expectedRevision(Object raw) {
    if (!(raw instanceof Number)) throw conflict();
    try {
      long revision = new BigDecimal(raw.toString()).longValueExact();
      if (revision < 0 || revision == Long.MAX_VALUE) throw conflict();
      return revision;
    } catch (NumberFormatException | ArithmeticException e) { throw conflict(); }
  }
  private static ResponseStatusException conflict() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "答题记录已变化或已提交，请刷新后重试；本次未覆盖已有记录。");
  }
  private static String normalizeStatus(String status) { return "完成".equals(status) ? "已完成" : status; }
  private static String text(Map<String,Object> record,String key) { return record.get(key)==null ? "" : String.valueOf(record.get(key)); }
  private static String nullable(Map<String,Object> record,String key) { String value=text(record,key); return value.isBlank()?null:value; }
  private static int integer(Object raw) {
    if(raw instanceof Number n) return n.intValue();
    if(raw==null || String.valueOf(raw).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(raw)); } catch(NumberFormatException e) { return 0; }
  }
  private static boolean truth(Object raw) {
    if(raw instanceof Boolean b) return b;
    if(raw instanceof Number n) return n.intValue()!=0;
    try { return raw!=null && Integer.parseInt(String.valueOf(raw))!=0; } catch(NumberFormatException e) { return false; }
  }
}
