const fs = require("fs");
const path = require("path");

const rootDir = path.resolve(__dirname, "..");
const storePath = path.join(rootDir, "data", "store.json");
const schemaPath = path.join(rootDir, "backend", "src", "main", "resources", "schema.sql");
const dataPath = path.join(rootDir, "backend", "src", "main", "resources", "data.sql");
const databasePath = path.join(rootDir, "database.sql");

const store = JSON.parse(fs.readFileSync(storePath, "utf8"));
const lines = [
  "SET NAMES utf8mb4;",
  "SET FOREIGN_KEY_CHECKS = 0;",
  "",
];

function sql(value) {
  if (value === undefined || value === null || value === "") return "NULL";
  return `'${String(value).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`;
}

function num(value, fallback = 0) {
  const next = Number(value);
  return Number.isFinite(next) ? String(next) : String(fallback);
}

function bool(value) {
  return value ? "1" : "0";
}

function json(value) {
  return sql(JSON.stringify(value || []));
}

function date(value) {
  if (!value) return "NULL";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "NULL";
  return sql(d.toISOString().replace("T", " ").replace("Z", "").slice(0, 23));
}

function status(value) {
  const raw = String(value || "");
  if (raw.includes("已完成") || raw.includes("宸插畬") || raw.includes("瀹告彃")) return "已完成";
  if (raw.includes("待阅卷") || raw.includes("寰呴槄") || raw.includes("瀵板懘")) return "待阅卷";
  if (raw.includes("进行中") || raw.includes("杩涜")) return "进行中";
  return raw || "进行中";
}

function insert(table, columns, rows) {
  if (!rows.length) return;
  const updateColumns = columns.filter((item) => item !== "id");
  rows.forEach((row) => {
    lines.push(
      `INSERT INTO ${table} (${columns.join(", ")}) VALUES (${row.join(", ")}) ` +
        `ON DUPLICATE KEY UPDATE ${updateColumns.map((column) => `${column}=VALUES(${column})`).join(", ")};`
    );
  });
  lines.push("");
}

insert(
  "department",
  ["id", "name"],
  (store.departments || []).map((item) => [sql(item.id), sql(item.name)])
);

insert(
  "class_info",
  ["id", "name", "major", "department_id"],
  (store.classes || []).map((item) => [sql(item.id), sql(item.name), sql(item.major), sql(item.departmentId)])
);

insert(
  "user_account",
  ["id", "role", "username", "password", "name", "department_id", "class_id", "major"],
  (store.users || []).map((item) => [
    sql(item.id),
    sql(item.role),
    sql(item.username),
    sql(item.password),
    sql(item.name),
    sql(item.departmentId),
    sql(item.classId),
    sql(item.major),
  ])
);

insert(
  "question",
  ["id", "teacher_id", "subject", "knowledge_point", "difficulty", "type", "title", "options_json", "answer_json", "score", "source_tag"],
  (store.questions || []).map((item) => [
    sql(item.id),
    sql(item.teacherId),
    sql(item.subject),
    sql(item.knowledgePoint),
    sql(item.difficulty),
    sql(item.type),
    sql(item.title),
    json(item.options),
    json(item.answer),
    num(item.score),
    sql(item.sourceTag),
  ])
);

insert(
  "paper",
  ["id", "teacher_id", "name", "duration_minutes", "total_score", "pass_score", "question_ids_json", "paper_type", "source_tag"],
  (store.papers || []).map((item) => [
    sql(item.id),
    sql(item.teacherId),
    sql(item.name),
    num(item.durationMinutes),
    num(item.totalScore),
    num(item.passScore),
    json(item.questionIds),
    sql(item.paperType),
    sql(item.sourceTag),
  ])
);

insert(
  "exam",
  ["id", "teacher_id", "paper_id", "name", "target_class_ids_json", "start_time", "end_time", "anti_cheat_limit", "published"],
  (store.exams || []).map((item) => [
    sql(item.id),
    sql(item.teacherId),
    sql(item.paperId),
    sql(item.name),
    json(item.targetClassIds),
    date(item.startTime),
    date(item.endTime),
    num(item.antiCheatLimit),
    bool(item.published !== false),
  ])
);

insert(
  "submission",
  [
    "id",
    "exam_id",
    "student_id",
    "student_name",
    "answers_json",
    "answer_detail_json",
    "switch_count",
    "suspicious",
    "suspicious_reasons_json",
    "auto_score",
    "final_score",
    "status",
    "started_at",
    "deadline_at",
    "submitted_at",
    "updated_at",
    "manual_extended_minutes",
    "graded_by",
  ],
  (store.submissions || []).map((item) => [
    sql(item.id),
    sql(item.examId),
    sql(item.studentId),
    sql(item.studentName),
    json(item.answers),
    json(item.answerDetail),
    num(item.switchCount),
    bool(item.suspicious),
    json(item.suspiciousReasons),
    num(item.autoScore),
    num(item.finalScore),
    sql(status(item.status)),
    date(item.startedAt),
    date(item.deadlineAt),
    date(item.submittedAt),
    date(item.updatedAt),
    num(item.manualExtendedMinutes),
    sql(item.gradedBy),
  ])
);

insert(
  "wrong_book_entry",
  [
    "id",
    "student_id",
    "student_name",
    "question_id",
    "subject",
    "knowledge_point",
    "type",
    "title",
    "latest_answer_json",
    "expected_answer_json",
    "last_retry_answer_json",
    "retry_count",
    "wrong_count",
    "full_score",
    "last_score",
    "last_wrong_at",
    "last_retry_at",
    "last_source_submission_id",
    "last_source_exam_id",
    "last_retry_correct",
    "removable",
    "removed_at",
    "status",
  ],
  (store.wrongBookEntries || []).map((item) => [
    sql(item.id),
    sql(item.studentId),
    sql(item.studentName),
    sql(item.questionId),
    sql(item.subject),
    sql(item.knowledgePoint),
    sql(item.type),
    sql(item.title),
    json(item.latestAnswer),
    json(item.expectedAnswer),
    json(item.lastRetryAnswer),
    num(item.retryCount),
    num(item.wrongCount),
    num(item.fullScore),
    num(item.lastScore),
    date(item.lastWrongAt),
    date(item.lastRetryAt),
    sql(item.lastSourceSubmissionId),
    sql(item.lastSourceExamId),
    bool(item.lastRetryCorrect),
    bool(item.removable),
    date(item.removedAt),
    sql(item.status || "active"),
  ])
);

insert(
  "system_log",
  ["id", "actor_id", "action", "detail", "time"],
  (store.logs || []).map((item) => [sql(item.id), sql(item.actorId), sql(item.action), sql(item.detail), date(item.time)])
);

lines.push("SET FOREIGN_KEY_CHECKS = 1;");
lines.push("");

fs.writeFileSync(dataPath, lines.join("\n"), "utf8");

const databaseSql = [
  "CREATE DATABASE IF NOT EXISTS online_exam_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
  "USE online_exam_system;",
  "",
  fs.readFileSync(schemaPath, "utf8"),
  "",
  fs.readFileSync(dataPath, "utf8"),
].join("\n");

fs.writeFileSync(databasePath, databaseSql, "utf8");

console.log(`Generated ${path.relative(rootDir, dataPath)}`);
console.log(`Generated ${path.relative(rootDir, databasePath)}`);
