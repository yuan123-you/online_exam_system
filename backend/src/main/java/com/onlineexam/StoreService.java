package com.onlineexam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {
  private static final Logger log = LoggerFactory.getLogger(StoreService.class);
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final ZoneId zone = ZoneId.systemDefault();

  public StoreService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public Store readStore() {
    Store store = new Store();
    try {
      store.departments = jdbc.queryForList("select id,name from department order by id").stream().map(row -> mapOf(
        "id", row.get("id"), "name", row.get("name"), "createdBy", null
      )).toList();
    } catch (Exception e) {
      log.error("Failed to load departments from database", e);
      store.departments = new ArrayList<>();
    }
    try {
      store.classes = jdbc.queryForList("select id,name,major,department_id from class_info order by id").stream().map(row -> mapOf(
        "id", row.get("id"), "name", row.get("name"), "major", row.get("major"), "departmentId", row.get("department_id"), "createdBy", null
      )).toList();
    } catch (Exception e) {
      log.error("Failed to load classes from database", e);
      store.classes = new ArrayList<>();
    }
    try {
      store.users = jdbc.queryForList("select id,role,username,name,department_id,class_id,major from user_account order by id").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "role", row.get("role"), "username", row.get("username"),
        "name", row.get("name"), "departmentId", row.get("department_id"), "classId", row.get("class_id"), "major", row.get("major")
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load users from database", e);
      store.users = new ArrayList<>();
    }
    try {
      store.questions = jdbc.queryForList("select * from question order by id").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "teacherId", row.get("teacher_id"), "subject", row.get("subject"),
        "knowledgePoint", row.get("knowledge_point"), "difficulty", row.get("difficulty"), "type", row.get("type"),
        "title", row.get("title"), "options", readList(row.get("options_json")), "answer", readList(row.get("answer_json")),
        "score", asInt(row.get("score")), "sourceTag", row.get("source_tag"), "deleted", asBool(row.get("deleted"))
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load questions from database", e);
      store.questions = new ArrayList<>();
    }
    try {
      store.papers = jdbc.queryForList("select * from paper order by id").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "teacherId", row.get("teacher_id"), "name", row.get("name"),
        "durationMinutes", asInt(row.get("duration_minutes")), "totalScore", asInt(row.get("total_score")),
        "passScore", asInt(row.get("pass_score")), "questionIds", readList(row.get("question_ids_json")),
        "paperType", row.get("paper_type"), "sourceTag", row.get("source_tag"), "deleted", false
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load papers from database", e);
      store.papers = new ArrayList<>();
    }
    try {
      store.exams = jdbc.queryForList("select * from exam order by start_time desc,id").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "teacherId", row.get("teacher_id"), "paperId", row.get("paper_id"), "name", row.get("name"),
        "targetClassIds", readList(row.get("target_class_ids_json")), "startTime", asIso(row.get("start_time")),
        "endTime", asIso(row.get("end_time")), "antiCheatLimit", asInt(row.get("anti_cheat_limit")),
        "published", asBool(row.get("published")), "deleted", false
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load exams from database", e);
      store.exams = new ArrayList<>();
    }
    try {
      store.submissions = jdbc.queryForList("select * from submission order by updated_at desc,id").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "examId", row.get("exam_id"), "studentId", row.get("student_id"), "studentName", row.get("student_name"),
        "answers", readList(row.get("answers_json")), "answerDetail", readList(row.get("answer_detail_json")),
        "switchCount", asInt(row.get("switch_count")), "suspicious", asBool(row.get("suspicious")),
        "suspiciousReasons", readList(row.get("suspicious_reasons_json")), "autoScore", asInt(row.get("auto_score")),
        "finalScore", asInt(row.get("final_score")), "status", normalizeStatus(str(row.get("status"))),
        "startedAt", asIso(row.get("started_at")), "deadlineAt", asIso(row.get("deadline_at")),
        "submittedAt", asIso(row.get("submitted_at")), "updatedAt", asIso(row.get("updated_at")),
        "manualExtendedMinutes", asInt(row.get("manual_extended_minutes")), "gradedBy", row.get("graded_by"),
        "questionOrder", readList(row.get("question_order_json")),
        "optionOrder", readMap(row.get("option_order_json"))
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load submissions from database", e);
      store.submissions = new ArrayList<>();
    }
    try {
      store.wrongBookEntries = jdbc.queryForList("select * from wrong_book_entry order by last_wrong_at desc,id").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "studentId", row.get("student_id"), "studentName", row.get("student_name"),
        "questionId", row.get("question_id"), "subject", row.get("subject"), "knowledgePoint", row.get("knowledge_point"),
        "type", row.get("type"), "title", row.get("title"), "latestAnswer", readList(row.get("latest_answer_json")),
        "expectedAnswer", readList(row.get("expected_answer_json")), "lastRetryAnswer", readList(row.get("last_retry_answer_json")),
        "retryCount", asInt(row.get("retry_count")), "wrongCount", asInt(row.get("wrong_count")),
        "fullScore", asInt(row.get("full_score")), "lastScore", asInt(row.get("last_score")),
        "lastWrongAt", asIso(row.get("last_wrong_at")), "lastRetryAt", asIso(row.get("last_retry_at")),
        "lastSourceSubmissionId", row.get("last_source_submission_id"), "lastSourceExamId", row.get("last_source_exam_id"),
        "lastRetryCorrect", asBool(row.get("last_retry_correct")), "removable", asBool(row.get("removable")),
        "removedAt", asIso(row.get("removed_at")), "status", row.get("status")
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load wrongBookEntries from database", e);
      store.wrongBookEntries = new ArrayList<>();
    }
    try {
      store.logs = jdbc.queryForList("select id,actor_id,action,detail,time from system_log order by time desc limit 100").stream().map(row -> {
        String actorId = str(row.get("actor_id"));
        return compact(mapOf(
          "id", row.get("id"),
          "actorId", actorId,
          "actorName", resolveActorName(actorId, store),
          "action", row.get("action"),
          "detail", row.get("detail"),
          "time", asIso(row.get("time"))
        ));
      }).toList();
    } catch (Exception e) {
      log.error("Failed to load logs from database", e);
      store.logs = new ArrayList<>();
    }
    try {
      store.backups = jdbc.queryForList("select id,teacher_id,questions_json,question_count,created_at from question_backup order by created_at desc").stream().map(row -> compact(mapOf(
        "id", row.get("id"),
        "teacherId", row.get("teacher_id"),
        "questions", readList(row.get("questions_json")),
        "questionCount", asInt(row.get("question_count")),
        "createdAt", asIso(row.get("created_at"))
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load backups from database", e);
      store.backups = new ArrayList<>();
    }
    try {
      store.notifications = jdbc.queryForList("select * from notification order by created_at desc limit 200").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "senderId", row.get("sender_id"),
        "targetRole", row.get("target_role"), "targetClassId", row.get("target_class_id"),
        "targetUserId", row.get("target_user_id"),
        "title", row.get("title"), "content", row.get("content"),
        "type", row.get("type"), "isRead", asBool(row.get("is_read")),
        "readAt", asIso(row.get("read_at")), "createdAt", asIso(row.get("created_at"))
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load notifications from database", e);
      store.notifications = new ArrayList<>();
    }
    return store;
  }

  @Transactional
  public void saveRecord(String entity, Map<String, Object> record) {
    switch (entity) {
      case "departments" -> upsertDepartment(record);
      case "classes" -> upsertClass(record);
      case "users" -> upsertUser(record);
      case "questions" -> upsertQuestion(record);
      case "papers" -> upsertPaper(record);
      case "exams" -> upsertExam(record);
      case "submissions" -> upsertSubmission(record);
      case "wrongBookEntries" -> upsertWrongBook(record);
      case "logs" -> upsertLog(record);
      case "backups" -> upsertBackup(record);
      case "notifications" -> upsertNotification(record);
      default -> throw new IllegalArgumentException("Unknown entity: " + entity);
    }
  }

  @Transactional
  public void deleteRecord(String entity, String id) {
    switch (entity) {
      case "questions" -> jdbc.update("delete from question where id=?", id);
      case "papers" -> jdbc.update("delete from paper where id=?", id);
      case "exams" -> jdbc.update("delete from exam where id=?", id);
      case "backups" -> jdbc.update("delete from question_backup where id=?", id);
      default -> {
        String table = switch (entity) {
          case "departments" -> "department";
          case "classes" -> "class_info";
          case "users" -> "user_account";
          case "submissions" -> "submission";
          case "wrongBookEntries" -> "wrong_book_entry";
          case "logs" -> "system_log";
          case "notifications" -> "notification";
          default -> throw new IllegalArgumentException("Unknown entity: " + entity);
        };
        jdbc.update("delete from " + table + " where id=?", id);
      }
    }
  }

  @Transactional
  public void restoreRecord(String entity, String id) {
    // deleted column not present in current DB schema — no-op
  }

  private void upsertDepartment(Map<String, Object> r) {
    jdbc.update("insert into department(id,name) values(?,?) on duplicate key update name=values(name)", str(r, "id"), str(r, "name"));
  }

  private void upsertClass(Map<String, Object> r) {
    jdbc.update("""
      insert into class_info(id,name,major,department_id) values(?,?,?,?)
      on duplicate key update name=values(name),major=values(major),department_id=values(department_id)
      """, str(r, "id"), str(r, "name"), str(r, "major"), str(r, "departmentId"));
  }

  private void upsertUser(Map<String, Object> r) {
    jdbc.update("""
      insert into user_account(id,role,username,password,name,department_id,class_id,major) values(?,?,?,?,?,?,?,?)
      on duplicate key update role=values(role),username=values(username),password=values(password),name=values(name),
      department_id=values(department_id),class_id=values(class_id),major=values(major)
      """, str(r, "id"), str(r, "role"), str(r, "username"), str(r, "password"), str(r, "name"),
      nullableStr(r, "departmentId"), nullableStr(r, "classId"), nullableStr(r, "major"));
  }

  private void upsertQuestion(Map<String, Object> r) {
    jdbc.update("""
      insert into question(id,teacher_id,subject,knowledge_point,difficulty,type,title,options_json,answer_json,score,source_tag)
      values(?,?,?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),subject=values(subject),knowledge_point=values(knowledge_point),
      difficulty=values(difficulty),type=values(type),title=values(title),options_json=values(options_json),
      answer_json=values(answer_json),score=values(score),source_tag=values(source_tag)
      """, str(r, "id"), str(r, "teacherId"), str(r, "subject"), str(r, "knowledgePoint"), str(r, "difficulty"),
      str(r, "type"), str(r, "title"), json(r.get("options")), json(r.get("answer")), number(r, "score"), nullableStr(r, "sourceTag"));
  }

  private void upsertPaper(Map<String, Object> r) {
    jdbc.update("""
      insert into paper(id,teacher_id,name,duration_minutes,total_score,pass_score,question_ids_json,paper_type,source_tag)
      values(?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),name=values(name),duration_minutes=values(duration_minutes),
      total_score=values(total_score),pass_score=values(pass_score),question_ids_json=values(question_ids_json),
      paper_type=values(paper_type),source_tag=values(source_tag)
      """, str(r, "id"), str(r, "teacherId"), str(r, "name"), number(r, "durationMinutes"), number(r, "totalScore"),
      number(r, "passScore"), json(r.get("questionIds")), nullableStr(r, "paperType"), nullableStr(r, "sourceTag"));
  }

  private void upsertExam(Map<String, Object> r) {
    jdbc.update("""
      insert into exam(id,teacher_id,paper_id,name,target_class_ids_json,start_time,end_time,anti_cheat_limit,published)
      values(?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),paper_id=values(paper_id),name=values(name),
      target_class_ids_json=values(target_class_ids_json),start_time=values(start_time),end_time=values(end_time),
      anti_cheat_limit=values(anti_cheat_limit),published=values(published)
      """, str(r, "id"), str(r, "teacherId"), str(r, "paperId"), str(r, "name"), json(r.get("targetClassIds")),
      timestamp(r.get("startTime")), timestamp(r.get("endTime")), number(r, "antiCheatLimit"), bool(r, "published"));
  }

  private void upsertSubmission(Map<String, Object> r) {
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
      """, str(r, "id"), str(r, "examId"), str(r, "studentId"), str(r, "studentName"), json(r.get("answers")),
      json(r.get("answerDetail")), number(r, "switchCount"), bool(r, "suspicious"), json(r.get("suspiciousReasons")),
      number(r, "autoScore"), number(r, "finalScore"), normalizeStatus(str(r, "status")), timestamp(r.get("startedAt")),
      timestamp(r.get("deadlineAt")), timestamp(r.get("submittedAt")), timestamp(r.get("updatedAt")),
      number(r, "manualExtendedMinutes"), nullableStr(r, "gradedBy"), json(r.get("questionOrder")), json(r.get("optionOrder")));
  }

  private void upsertWrongBook(Map<String, Object> r) {
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
      json(r.get("latestAnswer")), json(r.get("expectedAnswer")), json(r.get("lastRetryAnswer")), number(r, "retryCount"),
      number(r, "wrongCount"), number(r, "fullScore"), number(r, "lastScore"), timestamp(r.get("lastWrongAt")),
      timestamp(r.get("lastRetryAt")), nullableStr(r, "lastSourceSubmissionId"), nullableStr(r, "lastSourceExamId"),
      bool(r, "lastRetryCorrect"), bool(r, "removable"), timestamp(r.get("removedAt")), nullableStr(r, "status"));
  }

  private void upsertLog(Map<String, Object> r) {
    jdbc.update("""
      insert into system_log(id,actor_id,action,detail,time) values(?,?,?,?,?)
      on duplicate key update actor_id=values(actor_id),action=values(action),detail=values(detail),time=values(time)
      """, str(r, "id"), str(r, "actorId"), str(r, "action"), nullableStr(r, "detail"), timestamp(r.get("time")));
  }

  private void upsertBackup(Map<String, Object> r) {
    jdbc.update("""
      insert into question_backup(id,teacher_id,questions_json,question_count,created_at) values(?,?,?,?,?)
      on duplicate key update questions_json=values(questions_json),question_count=values(question_count),created_at=values(created_at)
      """, str(r, "id"), str(r, "teacherId"), json(r.get("questions")), number(r, "questionCount"), timestamp(r.get("createdAt")));
  }

  private void upsertNotification(Map<String, Object> r) {
    jdbc.update("""
      insert into notification(id,sender_id,target_role,target_class_id,target_user_id,title,content,type,is_read,read_at,created_at)
      values(?,?,?,?,?,?,?,?,?,?,?)
      on duplicate key update sender_id=values(sender_id),target_role=values(target_role),target_class_id=values(target_class_id),
      target_user_id=values(target_user_id),title=values(title),content=values(content),type=values(type),
      is_read=values(is_read),read_at=values(read_at),created_at=values(created_at)
      """, str(r, "id"), str(r, "senderId"), nullableStr(r, "targetRole"), nullableStr(r, "targetClassId"),
      nullableStr(r, "targetUserId"), str(r, "title"), str(r, "content"), str(r, "type"),
      bool(r, "isRead"), timestamp(r.get("readAt")), timestamp(r.get("createdAt")));
  }

  private List<Object> readList(Object raw) {
    if (raw == null) return new ArrayList<>();
    try {
      return mapper.readValue(String.valueOf(raw), new TypeReference<List<Object>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private Map<String, Object> readMap(Object raw) {
    if (raw == null) return new LinkedHashMap<>();
    try {
      return mapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  private String json(Object value) {
    try {
      Object next = value == null ? List.of() : value;
      return mapper.writeValueAsString(next);
    } catch (Exception e) {
      return "[]";
    }
  }

  private Timestamp timestamp(Object value) {
    if (value == null || String.valueOf(value).isBlank()) return null;
    if (value instanceof Timestamp t) return t;
    try {
      Instant instant = Instant.parse(String.valueOf(value));
      return Timestamp.from(instant);
    } catch (DateTimeParseException ignored) {
      try {
        return Timestamp.valueOf(LocalDateTime.parse(String.valueOf(value).replace("Z", "")));
      } catch (Exception e) {
        return null;
      }
    }
  }

  private String asIso(Object value) {
    if (value == null) return null;
    if (value instanceof Timestamp ts) return ts.toInstant().toString();
    if (value instanceof LocalDateTime dt) return dt.atZone(zone).toInstant().toString();
    return String.valueOf(value);
  }

  private int number(Map<String, Object> r, String key) {
    return asInt(r.get(key));
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value instanceof Boolean) return 0; // TINYINT(1) may arrive as Boolean without tinyInt1isBit=false
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private boolean bool(Map<String, Object> r, String key) {
    return asBool(r.get(key));
  }

  private boolean asBool(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    // TINYINT(1) may arrive as Integer even with instanceof checks — handle gracefully
    if (value != null) {
      try { return Integer.parseInt(String.valueOf(value)) != 0; } catch (NumberFormatException ignored) {}
    }
    return false;
  }

  private String str(Map<String, Object> r, String key) {
    return str(r.get(key));
  }

  private String resolveActorName(String actorId, Store store) {
    if (actorId == null || actorId.isBlank() || "system".equals(actorId)) return "系统";
    for (Map<String, Object> u : store.users) {
      if (actorId.equals(str(u, "id"))) return str(u, "name");
    }
    return actorId;
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String nullableStr(Map<String, Object> r, String key) {
    String value = str(r.get(key));
    return value.isBlank() ? null : value;
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
    for (int i = 0; i < pairs.length; i += 2) {
      map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return map;
  }

  public Map<String, Object> queryQuestionsPage(String teacherId, int page, int pageSize, String keyword, String type, String subject) {
    StringBuilder where = new StringBuilder("WHERE deleted=0");
    List<Object> params = new ArrayList<>();
    if (teacherId != null && !teacherId.isBlank()) {
      where.append(" AND teacher_id = ?");
      params.add(teacherId);
    }
    if (keyword != null && !keyword.isBlank()) {
      String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      where.append(" AND (title LIKE ? ESCAPE '\\\\' OR knowledge_point LIKE ? ESCAPE '\\\\')");
      params.add("%" + escaped + "%");
      params.add("%" + escaped + "%");
    }
    if (type != null && !type.equals("all")) {
      where.append(" AND type = ?");
      params.add(type);
    }
    if (subject != null && !subject.equals("all")) {
      where.append(" AND subject = ?");
      params.add(subject);
    }
    int total = Optional.ofNullable(jdbc.queryForObject(
      "SELECT COUNT(*) FROM question " + where, Integer.class, params.toArray())).orElse(0);
    int offset = (Math.max(1, page) - 1) * pageSize;
    List<Object> fullParams = new ArrayList<>(params);
    fullParams.add(pageSize);
    fullParams.add(offset);
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT * FROM question " + where + " ORDER BY id LIMIT ? OFFSET ?", fullParams.toArray()).stream()
      .map(row -> compact(mapOf(
        "id", row.get("id"), "teacherId", row.get("teacher_id"), "subject", row.get("subject"),
        "knowledgePoint", row.get("knowledge_point"), "difficulty", row.get("difficulty"), "type", row.get("type"),
        "title", row.get("title"), "options", readList(row.get("options_json")), "answer", readList(row.get("answer_json")),
        "score", asInt(row.get("score")), "sourceTag", row.get("source_tag")
      ))).toList();
    return mapOf("rows", rows, "total", total, "page", page, "pageSize", pageSize);
  }

  public List<String> queryQuestionSubjects(String teacherId) {
    return jdbc.queryForList("SELECT DISTINCT subject FROM question WHERE teacher_id = ? ORDER BY subject", String.class, teacherId);
  }

  /**
   * 通用分页查询 - 用户列表
   */
  public Map<String, Object> queryUsersPage(String role, int page, int pageSize, String keyword, String classId, String departmentId) {
    StringBuilder where = new StringBuilder("WHERE 1=1");
    List<Object> params = new ArrayList<>();
    if (role != null && !role.isBlank()) {
      where.append(" AND role = ?");
      params.add(role);
    }
    if (keyword != null && !keyword.isBlank()) {
      String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      where.append(" AND (username LIKE ? ESCAPE '\\\\' OR name LIKE ? ESCAPE '\\\\' OR major LIKE ? ESCAPE '\\\\')");
      params.add("%" + escaped + "%");
      params.add("%" + escaped + "%");
      params.add("%" + escaped + "%");
    }
    if (classId != null && !classId.isBlank()) {
      where.append(" AND class_id = ?");
      params.add(classId);
    }
    if (departmentId != null && !departmentId.isBlank()) {
      where.append(" AND department_id = ?");
      params.add(departmentId);
    }
    int total = Optional.ofNullable(jdbc.queryForObject(
      "SELECT COUNT(*) FROM user_account " + where, Integer.class, params.toArray())).orElse(0);
    int offset = (Math.max(1, page) - 1) * pageSize;
    List<Object> fullParams = new ArrayList<>(params);
    fullParams.add(pageSize);
    fullParams.add(offset);
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT id,role,username,name,department_id,class_id,major FROM user_account " + where + " ORDER BY id LIMIT ? OFFSET ?", fullParams.toArray()).stream()
      .map(row -> compact(mapOf(
        "id", row.get("id"), "role", row.get("role"), "username", row.get("username"),
        "name", row.get("name"), "departmentId", row.get("department_id"), "classId", row.get("class_id"), "major", row.get("major")
      ))).toList();
    return mapOf("rows", rows, "total", total, "page", page, "pageSize", pageSize);
  }

  /**
   * 通用分页查询 - 系统日志
   */
  public Map<String, Object> queryLogsPage(int page, int pageSize, String keyword, String action) {
    StringBuilder where = new StringBuilder("WHERE 1=1");
    List<Object> params = new ArrayList<>();
    if (keyword != null && !keyword.isBlank()) {
      String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      where.append(" AND (actor_id LIKE ? ESCAPE '\\\\' OR action LIKE ? ESCAPE '\\\\' OR detail LIKE ? ESCAPE '\\\\')");
      params.add("%" + escaped + "%");
      params.add("%" + escaped + "%");
      params.add("%" + escaped + "%");
    }
    if (action != null && !action.isBlank()) {
      where.append(" AND action = ?");
      params.add(action);
    }
    int total = Optional.ofNullable(jdbc.queryForObject(
      "SELECT COUNT(*) FROM system_log " + where, Integer.class, params.toArray())).orElse(0);
    int offset = (Math.max(1, page) - 1) * pageSize;
    List<Object> fullParams = new ArrayList<>(params);
    fullParams.add(pageSize);
    fullParams.add(offset);
    // 需要先获取用户列表来解析 actorName
    Store store = readStore();
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT id,actor_id,action,detail,time FROM system_log " + where + " ORDER BY time DESC LIMIT ? OFFSET ?", fullParams.toArray()).stream()
      .map(row -> {
        String actorId = str(row.get("actor_id"));
        return compact(mapOf(
          "id", row.get("id"), "actorId", actorId,
          "actorName", resolveActorName(actorId, store),
          "action", row.get("action"), "detail", row.get("detail"),
          "time", asIso(row.get("time"))
        ));
      }).toList();
    return mapOf("rows", rows, "total", total, "page", page, "pageSize", pageSize);
  }

  /**
   * 通用分页查询 - 错题本
   */
  public Map<String, Object> queryWrongBookPage(String studentId, int page, int pageSize, String subject, String status) {
    StringBuilder where = new StringBuilder("WHERE removed_at IS NULL");
    List<Object> params = new ArrayList<>();
    if (studentId != null && !studentId.isBlank()) {
      where.append(" AND student_id = ?");
      params.add(studentId);
    }
    if (subject != null && !subject.isBlank()) {
      where.append(" AND subject = ?");
      params.add(subject);
    }
    if (status != null && !status.isBlank()) {
      where.append(" AND status = ?");
      params.add(status);
    }
    int total = Optional.ofNullable(jdbc.queryForObject(
      "SELECT COUNT(*) FROM wrong_book_entry " + where, Integer.class, params.toArray())).orElse(0);
    int offset = (Math.max(1, page) - 1) * pageSize;
    List<Object> fullParams = new ArrayList<>(params);
    fullParams.add(pageSize);
    fullParams.add(offset);
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT * FROM wrong_book_entry " + where + " ORDER BY last_wrong_at DESC LIMIT ? OFFSET ?", fullParams.toArray()).stream()
      .map(row -> compact(mapOf(
        "id", row.get("id"), "studentId", row.get("student_id"), "studentName", row.get("student_name"),
        "questionId", row.get("question_id"), "subject", row.get("subject"), "knowledgePoint", row.get("knowledge_point"),
        "type", row.get("type"), "title", row.get("title"), "latestAnswer", readList(row.get("latest_answer_json")),
        "expectedAnswer", readList(row.get("expected_answer_json")), "lastRetryAnswer", readList(row.get("last_retry_answer_json")),
        "retryCount", asInt(row.get("retry_count")), "wrongCount", asInt(row.get("wrong_count")),
        "fullScore", asInt(row.get("full_score")), "lastScore", asInt(row.get("last_score")),
        "lastWrongAt", asIso(row.get("last_wrong_at")), "lastRetryAt", asIso(row.get("last_retry_at")),
        "lastRetryCorrect", asBool(row.get("last_retry_correct")), "removable", asBool(row.get("removable")),
        "removedAt", asIso(row.get("removed_at")), "status", row.get("status")
      ))).toList();
    return mapOf("rows", rows, "total", total, "page", page, "pageSize", pageSize);
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

  public static class Store {
    public List<Map<String, Object>> departments = new ArrayList<>();
    public List<Map<String, Object>> classes = new ArrayList<>();
    public List<Map<String, Object>> users = new ArrayList<>();
    public List<Map<String, Object>> questions = new ArrayList<>();
    public List<Map<String, Object>> papers = new ArrayList<>();
    public List<Map<String, Object>> exams = new ArrayList<>();
    public List<Map<String, Object>> submissions = new ArrayList<>();
    public List<Map<String, Object>> wrongBookEntries = new ArrayList<>();
    public List<Map<String, Object>> logs = new ArrayList<>();
    public List<Map<String, Object>> backups = new ArrayList<>();
    public List<Map<String, Object>> notifications = new ArrayList<>();

    public List<Map<String, Object>> entity(String name) {
      return switch (name) {
        case "departments" -> departments;
        case "classes" -> classes;
        case "users" -> users;
        case "questions" -> questions;
        case "papers" -> papers;
        case "exams" -> exams;
        case "submissions" -> submissions;
        case "wrongBookEntries" -> wrongBookEntries;
        case "logs" -> logs;
        case "backups" -> backups;
        case "notifications" -> notifications;
        default -> throw new IllegalArgumentException("Unknown entity: " + name);
      };
    }
  }
}
