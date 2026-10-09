package com.onlineexam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.service.ExamContent;
import com.onlineexam.common.JsonHelper;
import com.onlineexam.repository.SubmissionWrites;
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
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class StoreService {
  private static final Logger log = LoggerFactory.getLogger(StoreService.class);
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final JsonHelper submissionJson;
  private final ZoneId zone = ZoneId.systemDefault();

  /** 缓存全量 Store，避免每次请求都执行 10+ SQL 查询 */
  private volatile Store cachedStore;
  private volatile long cachedStoreTimestamp = 0;
  private final Object cacheMonitor = new Object();
  private long cacheGeneration;
  /** 缓存有效期 3 秒，写操作后立即失效 */
  private static final long CACHE_TTL_MS = 3000;

  public StoreService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.submissionJson = new JsonHelper(mapper);
    ensureSchemaColumns();
  }

  private void ensureSchemaColumns() {
    if (jdbc == null) return;
    ensureColumn("question", "deleted", "TINYINT(1) NOT NULL DEFAULT 0");
    ensureColumn("paper", "deleted", "TINYINT(1) NOT NULL DEFAULT 0");
    ensureColumn("exam", "deleted", "TINYINT(1) NOT NULL DEFAULT 0");
  }

  private void ensureColumn(String table, String column, String definition) {
    try {
      Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
        Integer.class, table, column
      );
      if (count == null || count == 0) {
        jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("Added missing column {}.{}", table, column);
      }
    } catch (Exception e) {
      log.debug("Auto-column check for {}.{}: {}", table, column, e.getMessage());
    }
  }

  /** 使缓存失效（写操作后调用） */
  public void invalidateCache() {
    synchronized (cacheMonitor) {
      cacheGeneration++;
      cachedStore = null;
      cachedStoreTimestamp = 0;
    }
  }

  public Store readStore() {
    long generation;
    synchronized (cacheMonitor) {
      if (cachedStore != null && (System.currentTimeMillis() - cachedStoreTimestamp) < CACHE_TTL_MS) {
        return cachedStore;
      }
      generation = cacheGeneration;
    }
    // Never hold the cache monitor while loading JDBC sections or waiting on database locks.
    Store store = loadStoreFromDb();
    synchronized (cacheMonitor) {
      if (generation == cacheGeneration) {
        cachedStore = store;
        cachedStoreTimestamp = System.currentTimeMillis();
      }
    }
    return store;
  }

  private Store loadStoreFromDb() {
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
      store.questions = jdbc.queryForList("select * from question where deleted=0").stream().map(this::questionRow).toList();
    } catch (Exception e) {
      log.error("Failed to load questions from database", e);
      store.questions = new ArrayList<>();
    }
    try {
      store.papers = jdbc.queryForList("select * from paper where deleted=0").stream().map(this::paperRow).toList();
    } catch (Exception e) {
      log.error("Failed to load papers from database", e);
      store.papers = new ArrayList<>();
    }
    try {
      store.exams = jdbc.queryForList("select * from exam where deleted=0 order by start_time desc,id").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "teacherId", row.get("teacher_id"), "paperId", row.get("paper_id"), "name", row.get("name"),
        "targetClassIds", readList(row.get("target_class_ids_json")), "startTime", asIso(row.get("start_time")),
        "endTime", asIso(row.get("end_time")), "antiCheatLimit", asInt(row.get("anti_cheat_limit")),
        "published", asBool(row.get("published")), "deleted", asBool(row.get("deleted"))
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
        "revision", SubmissionWrites.readRevision(row.get("revision")),
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
      // History needs original headers, but deleted exams must never join the active exam/entity lists.
      for (Map<String,Object> row : jdbc.queryForList("select e.* from exam e where e.deleted=1 "
          + "and exists (select 1 from submission s where s.exam_id=e.id) order by e.start_time desc,e.id")) {
        Map<String,Object> header = compact(mapOf("id",row.get("id"),"teacherId",row.get("teacher_id"),"paperId",row.get("paper_id"),
            "name",row.get("name"),"targetClassIds",readList(row.get("target_class_ids_json")),"startTime",asIso(row.get("start_time")),
            "endTime",asIso(row.get("end_time")),"antiCheatLimit",asInt(row.get("anti_cheat_limit")),"published",asBool(row.get("published")),"deleted",true));
        store.archivedExams.put(String.valueOf(row.get("id")),header);
      }
    } catch (Exception e) {
      log.error("Failed to load archived exam metadata",e);
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"历史考试元数据暂时无法读取，请稍后重试。");
    }
    try {
      store.wrongBookEntries = jdbc.queryForList("select * from wrong_book_entry").stream().map(row -> compact(mapOf(
        "id", row.get("id"), "studentId", row.get("student_id"), "studentName", row.get("student_name"),
        "questionId", row.get("question_id"), "subject", row.get("subject"), "knowledgePoint", row.get("knowledge_point"),
        "type", row.get("type"), "title", row.get("title"), "latestAnswer", readList(row.get("latest_answer_json")),
        "expectedAnswer", readList(row.get("expected_answer_json")), "lastRetryAnswer", readList(row.get("last_retry_answer_json")),
        "retryCount", asInt(row.get("retry_count")), "wrongCount", asInt(row.get("wrong_count")),
        "fullScore", asInt(row.get("full_score")), "lastScore", asInt(row.get("last_score")),
        "lastWrongAt", asIso(row.get("last_wrong_at")), "lastRetryAt", asIso(row.get("last_retry_at")),
        "lastSourceSubmissionId", row.get("last_source_submission_id"), "lastSourceExamId", row.get("last_source_exam_id"),
        "lastRetryCorrect", asBool(row.get("last_retry_correct")), "removable", asBool(row.get("removable")),
        "removedAt", asIso(row.get("removed_at")), "archivedAt", asIso(row.get("archived_at")), "status", row.get("status")
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
        "type", row.get("type"), "createdAt", asIso(row.get("created_at"))
      ))).toList();
    } catch (Exception e) {
      log.error("Failed to load notifications from database", e);
      store.notifications = new ArrayList<>();
    }
    // Private content is deliberately not embedded in store.exams / bootstrap exam responses.
    for (Map<String, Object> row : jdbc.queryForList("select exam_id,content_json from exam_snapshot")) {
      store.examSnapshots.put(String.valueOf(row.get("exam_id")), readExamSnapshot(row.get("content_json")));
    }
    return store;
  }

  private Map<String, Object> questionRow(Map<String, Object> row) {
    return compact(mapOf(
        "id", row.get("id"), "teacherId", row.get("teacher_id"), "subject", row.get("subject"),
        "knowledgePoint", row.get("knowledge_point"), "difficulty", row.get("difficulty"), "type", row.get("type"),
        "title", row.get("title"), "explanation", row.get("explanation"), "options", readList(row.get("options_json")), "answer", readList(row.get("answer_json")),
        "score", asInt(row.get("score")), "sourceTag", row.get("source_tag"), "deleted", asBool(row.get("deleted"))
    ));
  }

  private Map<String, Object> paperRow(Map<String, Object> row) {
    return compact(mapOf(
        "id", row.get("id"), "teacherId", row.get("teacher_id"), "name", row.get("name"),
        "durationMinutes", asInt(row.get("duration_minutes")), "totalScore", asInt(row.get("total_score")),
        "passScore", asInt(row.get("pass_score")), "questionIds", readList(row.get("question_ids_json")),
        "paperType", row.get("paper_type"), "sourceTag", row.get("source_tag"), "deleted", asBool(row.get("deleted"))
    ));
  }

  private Map<String, Object> readExamSnapshot(Object raw) {
    try {
      return mapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw ExamContent.unavailable("考试发布版本无法读取，请联系管理员恢复");
    }
  }

  @Transactional
  public void saveRecord(String entity, Map<String, Object> record) {
    invalidateCache();
    switch (entity) {
      case "departments" -> upsertDepartment(record, false);
      case "classes" -> upsertClass(record, false);
      case "users" -> insertUser(record);
      case "questions" -> upsertQuestion(record, false);
      case "papers" -> upsertPaper(record, false);
      case "exams" -> saveExamWithSnapshot(record, false);
      case "submissions" -> upsertSubmission(record);
      case "wrongBookEntries" -> upsertWrongBook(record);
      case "logs" -> upsertLog(record);
      case "backups" -> upsertBackup(record);
      case "notifications" -> upsertNotification(record);
      default -> throw new IllegalArgumentException("Unknown entity: " + entity);
    }
  }

  /** Creation has an explicit insert-only authority, never an implicit update on a key collision. */
  @Transactional
  public void createRecord(String entity, Map<String,Object> record) {
    invalidateCacheAtCompletion();
    switch (entity) {
      case "departments" -> upsertDepartment(record, true);
      case "classes" -> upsertClass(record, true);
      case "users" -> insertUser(record);
      case "questions" -> upsertQuestion(record, true);
      case "papers" -> upsertPaper(record, true);
      case "exams" -> saveExamWithSnapshot(record, true);
      default -> throw new IllegalArgumentException("Unsupported resource creation: " + entity);
    }
  }

  private void invalidateCacheAtCompletion() {
    invalidateCache();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) { invalidateCache(); }
      });
    }
  }

  private void writeResource(boolean insertOnly, String sql, Object... arguments) {
    if (!insertOnly) {
      jdbc.update(sql, arguments);
      return;
    }
    // The SQL is an internal literal; parameters never participate in this INSERT-clause selection.
    int duplicateClause = sql.toLowerCase(java.util.Locale.ROOT).indexOf("on duplicate key update");
    String insert = duplicateClause < 0 ? sql : sql.substring(0,duplicateClause);
    try {
      if (jdbc.update(insert,arguments) != 1) throw ExamContent.unavailable("资源创建未完成，本次未覆盖已有数据。");
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw ExamContent.unavailable("资源ID或唯一字段已存在，请重试创建；原记录未被覆盖。");
    }
  }

  /** Lock the current class before checking references; SET NULL foreign keys are not a business blocker. */
  @Transactional
  public void deleteClassAndReferences(String classId) {
    invalidateCacheAtCompletion();
    if (jdbc.queryForList("select id from class_info where id=? for update",classId).isEmpty()) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"班级不存在。");
    }
    if (!jdbc.queryForList("select id from user_account where class_id=? for update",classId).isEmpty()) {
      throw ExamContent.unavailable("班级仍有绑定用户，当前数据已变化，请刷新并先转移用户。");
    }
    removeExamClassReferences(classId);
    // Recheck in the final write too: engines can admit a new FK child after the earlier locking read.
    if (jdbc.update("delete from class_info where id=? and not exists (select 1 from user_account where class_id=?)",classId,classId) != 1) {
      throw ExamContent.unavailable("班级删除未完成，考试班级引用未提交。");
    }
  }

  /** A cached organization view cannot authorize SET NULL detachment of bound users. */
  @Transactional
  public void deleteDepartmentIfUnreferenced(String departmentId) {
    invalidateCacheAtCompletion();
    if (jdbc.queryForList("select id from department where id=? for update",departmentId).isEmpty()) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"院系不存在。");
    }
    if (!jdbc.queryForList("select id from class_info where department_id=? for update",departmentId).isEmpty()
        || !jdbc.queryForList("select id from user_account where department_id=? for update",departmentId).isEmpty()) {
      throw ExamContent.unavailable("院系仍有班级或绑定用户，当前数据已变化，请刷新并先转移相关数据。");
    }
    // Repeat both reference predicates atomically; FK SET NULL is not an acceptable business outcome.
    if (jdbc.update("delete from department where id=? and not exists (select 1 from class_info where department_id=?) "
        + "and not exists (select 1 from user_account where department_id=?)",departmentId,departmentId,departmentId) != 1) {
      throw ExamContent.unavailable("院系删除未完成，原绑定关系未提交改变。");
    }
  }

  /** Remove only roster references from locked current rows; content versions/status/times/owners are untouched. */
  @Transactional
  public void removeExamClassReferences(String classId) {
    invalidateCacheAtCompletion();
    for (Map<String,Object> row : jdbc.queryForList("select id,target_class_ids_json from exam where deleted=0 order by id for update")) {
      List<?> ids;
      try {
        Object raw = row.get("target_class_ids_json");
        Object parsed = raw == null ? null : mapper.readValue(String.valueOf(raw),Object.class);
        if (!(parsed instanceof List<?> list) || list.stream().anyMatch(id -> !(id instanceof String))) {
          throw new IllegalArgumentException("Invalid roster");
        }
        ids = list;
      } catch (Exception e) {
        throw ExamContent.unavailable("考试目标班级无法校验，请修复元数据后再删除班级。");
      }
      if (ids.contains(classId)) {
        List<?> remaining = ids.stream().filter(id -> !classId.equals(id)).toList();
        if (jdbc.update("update exam set target_class_ids_json=? where id=? and deleted=0",json(remaining),row.get("id")) != 1) {
          throw ExamContent.unavailable("考试班级引用已变化，本次删除未提交。");
        }
      }
    }
  }

  /** Teacher deletion binds ownership and active state in the final write, not only a cached permission read. */
  @Transactional
  public void deleteOwnedResource(String entity,String id,String teacherId) {
    String table = switch (entity) {
      case "questions" -> "question";
      case "papers" -> "paper";
      case "exams" -> "exam";
      default -> throw new IllegalArgumentException("Unsupported teacher-owned resource: " + entity);
    };
    invalidateCacheAtCompletion();
    if (jdbc.update("update " + table + " set deleted=1 where id=? and teacher_id=? and deleted=0",id,teacherId) != 1) {
      throw ExamContent.unavailable("资源已变化或不属于当前教师，请刷新后重试；本次未删除其他资源。");
    }
  }

  @Transactional
  public void deleteRecord(String entity, String id) {
    invalidateCache();
    switch (entity) {
      case "questions" -> jdbc.update("update question set deleted=1 where id=?", id);
      case "papers" -> jdbc.update("update paper set deleted=1 where id=?", id);
      case "exams" -> jdbc.update("update exam set deleted=1 where id=?", id);
      default -> {
        String table = switch (entity) {
          case "departments" -> "department";
          case "classes" -> "class_info";
          case "users" -> "user_account";
          case "submissions" -> "submission";
          case "wrongBookEntries" -> "wrong_book_entry";
          case "logs" -> "system_log";
          case "backups" -> "question_backup";
          case "notifications" -> "notification";
          default -> throw new IllegalArgumentException("Unknown entity: " + entity);
        };
        jdbc.update("delete from " + table + " where id=?", id);
      }
    }
  }

  @Transactional
  public void restoreRecord(String entity, String id) {
    invalidateCache();
    switch (entity) {
      case "questions" -> jdbc.update("update question set deleted=0 where id=?", id);
      case "papers" -> jdbc.update("update paper set deleted=0 where id=?", id);
      case "exams" -> jdbc.update("update exam set deleted=0 where id=?", id);
      default -> { /* 其他实体无软删除，无需恢复 */ }
    }
  }

  private void upsertDepartment(Map<String, Object> r, boolean insertOnly) {
    writeResource(insertOnly, "insert into department(id,name) values(?,?) on duplicate key update name=values(name)", str(r, "id"), str(r, "name"));
  }

  private void upsertClass(Map<String, Object> r, boolean insertOnly) {
    writeResource(insertOnly, """
      insert into class_info(id,name,major,department_id) values(?,?,?,?)
      on duplicate key update name=values(name),major=values(major),department_id=values(department_id)
      """, str(r, "id"), str(r, "name"), str(r, "major"), str(r, "departmentId"));
  }

  /** User creation is not permission to replace another account on an ID/username collision. */
  private void insertUser(Map<String, Object> record) {
    try {
      int affected = jdbc.update("""
          insert into user_account(id,role,username,password,name,department_id,class_id,major) values(?,?,?,?,?,?,?,?)
          """, str(record,"id"), str(record,"role"), str(record,"username"), str(record,"password"), str(record,"name"),
          nullableStr(record,"departmentId"), nullableStr(record,"classId"), nullableStr(record,"major"));
      if (affected != 1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"账号创建未完成。");
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"账号ID或用户名已存在，本次未覆盖原账号。");
    }
  }

  /** Update existing accounts using only caller-validated, explicitly supplied profile fields. */
  @Transactional
  public void updateUserRecord(Map<String,Object> patch, Map<String,Object> expectedProfile) {
    invalidateCache();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) { invalidateCache(); }
      });
    }
    Map<String,String> columns = Map.of("role","role", "username","username", "name","name", "password","password",
        "departmentId","department_id", "classId","class_id", "major","major");
    List<String> assignments = new ArrayList<>();
    List<Object> arguments = new ArrayList<>();
    for (String key : List.of("role","username","name","departmentId","classId","major","password")) {
      if (patch.containsKey(key)) {
        assignments.add(columns.get(key) + "=?");
        arguments.add(nullableStr(patch,key));
      }
    }
    String id = str(patch,"id");
    if (expectedProfile == null || !id.equals(str(expectedProfile,"id"))) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"账号校验依据已变化，请刷新后重试。");
    }
    // Role and bindings are coupled validation inputs. Do not merge a stale student patch into a newer teacher/admin.
    String guard = " where id=? and COALESCE(role,'')=? and COALESCE(department_id,'')=? and COALESCE(class_id,'')=?";
    List<Object> guards = List.of(id,str(expectedProfile,"role"),str(expectedProfile,"departmentId"),str(expectedProfile,"classId"));
    List<Object> requestedValues = new ArrayList<>(arguments);
    int affected = 0;
    if (!assignments.isEmpty()) {
      arguments.addAll(guards);
      try {
        affected = jdbc.update("update user_account set " + String.join(",",assignments) + guard,arguments.toArray());
      } catch (org.springframework.dao.DuplicateKeyException e) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"用户名已被占用，本次未覆盖其他账号。");
      }
    }
    if (affected == 0) {
      Integer count = jdbc.queryForObject("select count(*) from user_account where id=?",Integer.class,id);
      if (count == null || count == 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"账号不存在，本次未重建账号。");
      // Distinguish an existing no-op from a stale validation tuple or an unapplied requested value.
      StringBuilder desired = new StringBuilder(guard);
      for (String assignment : assignments) {
        String column = assignment.substring(0,assignment.indexOf('='));
        desired.append(" and COALESCE(").append(column).append(",'')=COALESCE(?,'')");
      }
      List<Object> checks = new ArrayList<>(guards); checks.addAll(requestedValues);
      Integer matches = jdbc.queryForObject("select count(*) from user_account" + desired,Integer.class,checks.toArray());
      if (matches == null || matches == 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"账号角色或归属已变化，请刷新后重试；本次未覆盖新资料。");
    }
  }

  private void upsertQuestion(Map<String, Object> r, boolean insertOnly) {
    writeResource(insertOnly, """
      insert into question(id,teacher_id,subject,knowledge_point,difficulty,type,title,options_json,answer_json,score,source_tag,deleted,explanation)
      values(?,?,?,?,?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),subject=values(subject),knowledge_point=values(knowledge_point),
      difficulty=values(difficulty),type=values(type),title=values(title),options_json=values(options_json),
      answer_json=values(answer_json),score=values(score),source_tag=values(source_tag),deleted=values(deleted),explanation=values(explanation)
      """, str(r, "id"), str(r, "teacherId"), str(r, "subject"), str(r, "knowledgePoint"), str(r, "difficulty"),
      str(r, "type"), str(r, "title"), json(r.get("options")), json(r.get("answer")), number(r, "score"), nullableStr(r, "sourceTag"),
      bool(r, "deleted") ? 1 : 0, nullableStr(r, "explanation"));
  }

  private void upsertPaper(Map<String, Object> r, boolean insertOnly) {
    writeResource(insertOnly, """
      insert into paper(id,teacher_id,name,duration_minutes,total_score,pass_score,question_ids_json,paper_type,source_tag,deleted)
      values(?,?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),name=values(name),duration_minutes=values(duration_minutes),
      total_score=values(total_score),pass_score=values(pass_score),question_ids_json=values(question_ids_json),
      paper_type=values(paper_type),source_tag=values(source_tag),deleted=values(deleted)
      """, str(r, "id"), str(r, "teacherId"), str(r, "name"), number(r, "durationMinutes"), number(r, "totalScore"),
      number(r, "passScore"), json(r.get("questionIds")), nullableStr(r, "paperType"), nullableStr(r, "sourceTag"),
      bool(r, "deleted") ? 1 : 0);
  }

  private void saveExamWithSnapshot(Map<String, Object> record, boolean insertOnly) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      // A concurrent reader may refill the cache before publication commits or rolls back.
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) { invalidateCache(); }
      });
    }
    String id = str(record, "id");
    List<Map<String, Object>> existing = jdbc.queryForList("select * from exam where id=? for update", id);
    if (insertOnly && !existing.isEmpty()) throw ExamContent.unavailable("考试ID已存在，本次创建未更新原考试。");
    List<Map<String, Object>> snapshots = jdbc.queryForList(
        "select content_json from exam_snapshot where exam_id=? for update", id);
    if (insertOnly && !snapshots.isEmpty()) throw ExamContent.unavailable("考试ID已有历史版本，本次创建不会认领或改写该版本。");
    if (!snapshots.isEmpty()) {
      Map<String, Object> content = readExamSnapshot(snapshots.getFirst().get("content_json"));
      Object rawPaper = content.get("paper");
      if (!(rawPaper instanceof Map<?, ?> paper) || !Objects.equals(paper.get("id"), str(record, "paperId"))) {
        throw ExamContent.unavailable("已冻结的考试不能更换试卷；请创建新的考试");
      }
      upsertExam(record, insertOnly);
      return;
    }
    boolean hasSubmissions = !jdbc.queryForList("select id from submission where exam_id=? limit 1", id).isEmpty();
    if ((!existing.isEmpty() && asBool(existing.getFirst().get("published")))
        || (hasSubmissions && asBool(record.get("published")))) {
      throw ExamContent.unavailable("历史考试缺少原始发布版本，禁止自动使用当前题库回填");
    }
    Map<String, Object> content = null;
    if (asBool(record.get("published"))) {
      Store source = new Store();
      source.papers = jdbc.queryForList("select * from paper where id=? and deleted=0 for update", str(record, "paperId"))
          .stream().map(this::paperRow).toList();
      if (!source.papers.isEmpty()) {
        // Deterministic lock order avoids opposite question-order lock acquisition.
        List<String> ids = readListFromValue(source.papers.getFirst().get("questionIds")).stream()
            .map(String::valueOf).distinct().sorted().toList();
        for (String questionId : ids) {
          source.questions.addAll(jdbc.queryForList("select * from question where id=? and deleted=0 for update", questionId)
              .stream().map(this::questionRow).toList());
        }
      }
      content = ExamContent.capture(source, record);
    }
    upsertExam(record, insertOnly);
    if (content != null) {
      try {
        jdbc.update("insert into exam_snapshot(exam_id,content_json) values(?,?)", id, mapper.writeValueAsString(content));
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException("考试发布版本序列化失败", e);
      }
    }
  }

  private List<?> readListFromValue(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }

  private void upsertExam(Map<String, Object> r, boolean insertOnly) {
    writeResource(insertOnly, """
      insert into exam(id,teacher_id,paper_id,name,target_class_ids_json,start_time,end_time,anti_cheat_limit,published,deleted)
      values(?,?,?,?,?,?,?,?,?,?)
      on duplicate key update teacher_id=values(teacher_id),paper_id=values(paper_id),name=values(name),
      target_class_ids_json=values(target_class_ids_json),start_time=values(start_time),end_time=values(end_time),
      anti_cheat_limit=values(anti_cheat_limit),published=values(published),deleted=values(deleted)
      """, str(r, "id"), str(r, "teacherId"), str(r, "paperId"), str(r, "name"), json(r.get("targetClassIds")),
      timestamp(r.get("startTime")), timestamp(r.get("endTime")), number(r, "antiCheatLimit"), bool(r, "published"),
      bool(r, "deleted") ? 1 : 0);
  }

  private void upsertSubmission(Map<String, Object> record) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      // A reader may refill the shared cache while this transaction has not committed yet.
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) { invalidateCache(); }
      });
    }
    SubmissionWrites.save(jdbc, submissionJson, record);
  }

  private void upsertWrongBook(Map<String, Object> r) {
    jdbc.update("""
      insert into wrong_book_entry(id,student_id,student_name,question_id,subject,knowledge_point,type,title,latest_answer_json,
      expected_answer_json,last_retry_answer_json,retry_count,wrong_count,full_score,last_score,last_wrong_at,last_retry_at,
      last_source_submission_id,last_source_exam_id,last_retry_correct,removable,removed_at,archived_at,status)
      values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
      on duplicate key update student_name=values(student_name),subject=values(subject),knowledge_point=values(knowledge_point),
      type=values(type),title=values(title),latest_answer_json=values(latest_answer_json),expected_answer_json=values(expected_answer_json),
      last_retry_answer_json=values(last_retry_answer_json),retry_count=values(retry_count),wrong_count=values(wrong_count),
      full_score=values(full_score),last_score=values(last_score),last_wrong_at=values(last_wrong_at),last_retry_at=values(last_retry_at),
      last_source_submission_id=values(last_source_submission_id),last_source_exam_id=values(last_source_exam_id),
      last_retry_correct=values(last_retry_correct),removable=values(removable),removed_at=values(removed_at),
      archived_at=values(archived_at),status=values(status)
      """, str(r, "id"), str(r, "studentId"), nullableStr(r, "studentName"), str(r, "questionId"),
      nullableStr(r, "subject"), nullableStr(r, "knowledgePoint"), nullableStr(r, "type"), nullableStr(r, "title"),
      json(r.get("latestAnswer")), json(r.get("expectedAnswer")), json(r.get("lastRetryAnswer")), number(r, "retryCount"),
      number(r, "wrongCount"), number(r, "fullScore"), number(r, "lastScore"), timestamp(r.get("lastWrongAt")),
      timestamp(r.get("lastRetryAt")), nullableStr(r, "lastSourceSubmissionId"), nullableStr(r, "lastSourceExamId"),
      bool(r, "lastRetryCorrect"), bool(r, "removable"), timestamp(r.get("removedAt")), timestamp(r.get("archivedAt")),
      nullableStr(r, "status"));
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
      insert into notification(id,sender_id,target_role,target_class_id,target_user_id,title,content,type,created_at)
      values(?,?,?,?,?,?,?,?,?)
      on duplicate key update sender_id=values(sender_id),target_role=values(target_role),target_class_id=values(target_class_id),
      target_user_id=values(target_user_id),title=values(title),content=values(content),type=values(type),
      created_at=values(created_at)
      """, str(r, "id"), str(r, "senderId"), nullableStr(r, "targetRole"), nullableStr(r, "targetClassId"),
      nullableStr(r, "targetUserId"), str(r, "title"), str(r, "content"), str(r, "type"),
      timestamp(r.get("createdAt")));
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
        "title", row.get("title"), "explanation", row.get("explanation"), "options", readList(row.get("options_json")), "answer", readList(row.get("answer_json")),
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
    // 只加载用户列表来解析 actorName，避免 readStore() 的 10+ SQL 查询
    Map<String, String> actorNameMap = jdbc.queryForList("select id,name from user_account").stream()
      .collect(java.util.stream.Collectors.toMap(
        row -> str(row.get("id")),
        row -> str(row.get("name")),
        (a, b) -> a
      ));
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT id,actor_id,action,detail,time FROM system_log " + where + " ORDER BY time DESC LIMIT ? OFFSET ?", fullParams.toArray()).stream()
      .map(row -> {
        String actorId = str(row.get("actor_id"));
        String actorName = actorNameMap.getOrDefault(actorId, "系统".equals(actorId) || actorId.isBlank() ? "系统" : actorId);
        return compact(mapOf(
          "id", row.get("id"), "actorId", actorId,
          "actorName", actorName,
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

    // 批量关联题目信息（含选项 options，剔除 answer 以防泄露答案），
    // 供前端错题重做弹窗渲染交互式选项卡片
    if (!rows.isEmpty()) {
      List<String> questionIds = rows.stream()
        .map(r -> str(r.get("questionId")))
        .filter(id -> !id.isBlank())
        .distinct()
        .toList();
      if (!questionIds.isEmpty()) {
        String placeholders = String.join(",", java.util.Collections.nCopies(questionIds.size(), "?"));
        List<Map<String, Object>> questionRows = jdbc.queryForList(
          "SELECT id, subject, knowledge_point, type, title, options_json, score, source_tag, difficulty " +
          "FROM question WHERE id IN (" + placeholders + ")", questionIds.toArray());
        Map<String, Map<String, Object>> questionMap = new java.util.HashMap<>();
        for (Map<String, Object> q : questionRows) {
          Map<String, Object> sanitized = compact(mapOf(
            "id", q.get("id"), "subject", q.get("subject"), "knowledgePoint", q.get("knowledge_point"),
            "type", q.get("type"), "title", q.get("title"), "options", readList(q.get("options_json")),
            "score", asInt(q.get("score")), "sourceTag", q.get("source_tag"), "difficulty", q.get("difficulty")
          ));
          questionMap.put(str(q.get("id")), sanitized);
        }
        rows = rows.stream().map(r -> {
          Map<String, Object> next = new LinkedHashMap<>(r);
          String qid = str(r.get("questionId"));
          Map<String, Object> q = questionMap.get(qid);
          if (q != null) {
            next.put("question", q);
            // 用题目最新数据补全条目字段（防止题目被编辑后错题本字段过期）
            if (q.containsKey("subject")) next.put("subject", q.get("subject"));
            if (q.containsKey("knowledgePoint")) next.put("knowledgePoint", q.get("knowledgePoint"));
            if (q.containsKey("type")) next.put("type", q.get("type"));
            if (q.containsKey("title")) next.put("title", q.get("title"));
          }
          return next;
        }).toList();
      }
    }

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
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Map<String, Map<String,Object>> archivedExams = new LinkedHashMap<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Map<String, Map<String, Object>> examSnapshots = new LinkedHashMap<>();
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
