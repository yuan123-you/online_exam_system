const http = require("http");
const fs = require("fs");
const path = require("path");
const { URL } = require("url");

const PORT = process.env.PORT || 3000;
const rootDir = __dirname;
const distDir = path.join(rootDir, "dist");
const publicDir = path.join(rootDir, "public");
const staticDir = fs.existsSync(distDir) ? distDir : publicDir;
const dataDir = path.join(rootDir, "data");
const storeFile = path.join(dataDir, "store.json");
const SUBJECTIVE_TYPES = ["short", "coding"];
const OBJECTIVE_TYPES = ["single", "multiple", "judge", "fill"];
const ADMIN_ENTITIES = ["users", "departments", "classes"];
const TEACHER_ENTITIES = ["questions", "papers", "exams"];

const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".md": "text/markdown; charset=utf-8",
  ".sql": "text/plain; charset=utf-8",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
};

ensureStore();

const BROKEN_TEXT_REPLACEMENTS = [
  ["璁＄畻鏈哄闄?", "计算机学院"],
  ["淇℃伅宸ョ▼瀛﹂櫌", "信息工程学院"],
  ["杞欢宸ョ▼", "软件工程"],
  ["璁＄畻鏈虹瀛︿笌鎶€鏈?", "计算机科学与技术"],
  ["绯荤粺绠＄悊鍛?", "系统管理员"],
  ["闄堣€佸笀", "陈老师"],
  ["寮犱笁", "张三"],
  ["鏉庡洓", "李四"],
  ["鏁版嵁搴?", "数据库"],
  ["鍓嶇寮€鍙?", "前端开发"],
  ["绯荤粺璁捐", "系统设计"],
  ["浜嬪姟", "事务"],
  ["鏄?", "易"],
  ["涓?", "中"],
  ["闅?", "难"],
  ["HTTP ??????", "HTTP 默认端口是？"],
  ["HTTP 榛樿绔彛鏄紵", "HTTP 默认端口是？"],
  ["涓嬪垪鍝簺灞炰簬 MVC 鍒嗗眰涓殑甯歌灞傛锛?", "下列哪些属于 MVC 分层中的常见层次？"],
  ["浜嬪姟鐨?ACID 涓紝C 浠ｈ〃涓€鑷存€с€?", "事务的 ACID 中，C 代表一致性。"],
  ["璇峰～鍐欐祻瑙堝櫒鏈湴鎸佷箙鍖栧瓨鍌?API 鍚嶇О銆?", "请填写浏览器本地持久化存储 API 名称。"],
  ["绠€杩板湪绾胯€冭瘯绯荤粺涓槻浣滃紛璁捐鐨勪袱绉嶆墜娈点€?", "简述在线考试系统中两种防作弊设计。"],
  ["闃插垏灞忕洃娴嬨€侀殢鏈轰贡搴忋€佸紓甯告椂闀挎娴嬬瓑", "防切屏监测、题目乱序、异常时长检测等"],
  ["Java Web 鏈熶腑妯℃嫙鍗?", "Java Web 期中模拟卷"],
  ["2310 鐝?Java Web 娴嬭瘯", "2310 班 Java Web 测试"],
  ["2310 ? Java Web ??", "2310 班 Java Web 测试"],
  ["鐧诲綍绯荤粺", "登录系统"],
  ["绯荤粺鍒濆鍖栧畬鎴?", "系统初始化完成"],
  ["宸插啓鍏ユ紨绀烘暟鎹?", "已写入演示数据"],
  ["宸插畬鎴?", "已完成"],
  ["寰呴槄鍗?", "待阅卷"],
  ["杩涜涓?", "进行中"],
  ["鏈紑濮?", "未开始"],
  ["宸茬粨鏉?", "已结束"],
  ["鏈綔绛?", "未作答"],
];

function normalizeBrokenText(text, key = "") {
  if (typeof text !== "string") {
    return text;
  }
  let next = text;
  if (key === "difficulty" && next === "?") {
    return "易";
  }
  BROKEN_TEXT_REPLACEMENTS.forEach(([from, to]) => {
    next = next.split(from).join(to);
  });
  return next;
}

function normalizeBrokenData(value, key = "") {
  if (typeof value === "string") {
    return normalizeBrokenText(value, key);
  }
  if (Array.isArray(value)) {
    return value.map((item) => normalizeBrokenData(item));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([entryKey, entryValue]) => [entryKey, normalizeBrokenData(entryValue, entryKey)])
    );
  }
  return value;
}

function ensureStore() {
  if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
  }
  if (!fs.existsSync(storeFile)) {
    writeStore(seedStore());
  }
}

function seedStore() {
  const now = new Date().toISOString();
  return {
    departments: [
      { id: "dept-1", name: "计算机学院" },
      { id: "dept-2", name: "信息工程学院" },
    ],
    classes: [
      { id: "class-1", name: "2310", major: "软件工程", departmentId: "dept-1" },
      { id: "class-2", name: "2311", major: "计算机科学与技术", departmentId: "dept-1" },
    ],
    users: [
      { id: "admin-1", role: "admin", username: "admin", password: "123456", name: "系统管理员", departmentId: "dept-1" },
      { id: "teacher-1", role: "teacher", username: "teacher", password: "123456", name: "陈老师", departmentId: "dept-1" },
      { id: "student-1", role: "student", username: "2023001", password: "123456", name: "张三", classId: "class-1", major: "软件工程" },
      { id: "student-2", role: "student", username: "2023002", password: "123456", name: "李四", classId: "class-1", major: "软件工程" },
    ],
    questions: [
      {
        id: "question-1",
        teacherId: "teacher-1",
        subject: "Java Web",
        knowledgePoint: "HTTP",
        difficulty: "易",
        type: "single",
        title: "HTTP 默认端口是？",
        options: ["21", "80", "443", "3306"],
        answer: ["80"],
        score: 5,
      },
      {
        id: "question-2",
        teacherId: "teacher-1",
        subject: "Java Web",
        knowledgePoint: "Servlet",
        difficulty: "中",
        type: "multiple",
        title: "下列哪些属于 MVC 分层中的常见层次？",
        options: ["Controller", "View", "Model", "Socket"],
        answer: ["Controller", "View", "Model"],
        score: 10,
      },
      {
        id: "question-3",
        teacherId: "teacher-1",
        subject: "数据库",
        knowledgePoint: "事务",
        difficulty: "中",
        type: "judge",
        title: "事务的 ACID 中，C 代表一致性。",
        options: ["正确", "错误"],
        answer: ["正确"],
        score: 5,
      },
      {
        id: "question-4",
        teacherId: "teacher-1",
        subject: "前端开发",
        knowledgePoint: "JavaScript",
        difficulty: "中",
        type: "fill",
        title: "请填写浏览器本地持久化存储 API 名称。",
        options: [],
        answer: ["localStorage"],
        score: 10,
      },
      {
        id: "question-5",
        teacherId: "teacher-1",
        subject: "软件工程",
        knowledgePoint: "系统设计",
        difficulty: "难",
        type: "short",
        title: "简述在线考试系统中防作弊设计的两种手段。",
        options: [],
        answer: ["防切屏监测、随机乱序、异常时长检测等"],
        score: 20,
      },
    ],
    papers: [
      {
        id: "paper-1",
        teacherId: "teacher-1",
        name: "Java Web 期中模拟卷",
        durationMinutes: 30,
        totalScore: 50,
        passScore: 30,
        questionIds: ["question-1", "question-2", "question-3", "question-4", "question-5"],
      },
    ],
    exams: [
      {
        id: "exam-1",
        teacherId: "teacher-1",
        paperId: "paper-1",
        name: "2310 班 Java Web 测试",
        targetClassIds: ["class-1"],
        startTime: "2026-04-20T08:00:00.000Z",
        endTime: "2026-05-20T10:00:00.000Z",
        antiCheatLimit: 3,
        published: true,
      },
    ],
    submissions: [],
    wrongBookEntries: [],
    wrongBookBackfilled: true,
    logs: [{ id: "log-1", actorId: "system", action: "系统初始化完成", time: now, detail: "已写入演示数据" }],
  };
}

function ensureStoreCollections(store) {
  const next = { ...store };
  ["departments", "classes", "users", "questions", "papers", "exams", "submissions", "wrongBookEntries", "logs"].forEach((key) => {
    if (!Array.isArray(next[key])) {
      next[key] = [];
    }
  });
  return next;
}

function readStore() {
  const raw = JSON.parse(fs.readFileSync(storeFile, "utf8"));
  const normalized = backfillWrongBookEntries(ensureStoreCollections(normalizeBrokenData(raw)));
  if (JSON.stringify(raw) !== JSON.stringify(normalized)) {
    writeStore(normalized);
  }
  return normalized;
}

function writeStore(store) {
  fs.writeFileSync(storeFile, JSON.stringify(store, null, 2), "utf8");
}

function json(res, statusCode, payload) {
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store, no-cache, must-revalidate",
    Pragma: "no-cache",
    Expires: "0",
  });
  res.end(JSON.stringify(payload));
}

function sendFile(res, filePath) {
  const ext = path.extname(filePath);
  const type = mimeTypes[ext] || "application/octet-stream";
  res.writeHead(200, {
    "Content-Type": type,
    "Cache-Control": "no-store, no-cache, must-revalidate",
    Pragma: "no-cache",
    Expires: "0",
  });
  fs.createReadStream(filePath).pipe(res);
}

function parseBody(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    let tooLarge = false;
    req.on("data", (chunk) => {
      if (tooLarge) {
        return;
      }
      raw += chunk;
      if (raw.length > 5 * 1024 * 1024) {
        tooLarge = true;
        reject(new Error("Payload too large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (tooLarge) {
        return;
      }
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

function getUser(req, store) {
  const userId = req.headers["x-user-id"];
  return store.users.find((item) => item.id === userId) || null;
}

function requireRole(res, user, roles) {
  if (!user || !roles.includes(user.role)) {
    json(res, 403, { message: "无权限访问" });
    return false;
  }
  return true;
}

function createId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
}

function logAction(store, actorId, action, detail) {
  store.logs.unshift({
    id: createId("log"),
    actorId,
    action,
    detail,
    time: new Date().toISOString(),
  });
}

function normalizeAnswerValue(answer) {
  if (Array.isArray(answer)) {
    return answer.map((item) => String(item ?? "").trim()).filter(Boolean);
  }
  if (answer == null) {
    return [];
  }
  const next = String(answer).trim();
  return next ? [next] : [];
}

function normalizeComparableText(value) {
  return String(value ?? "")
    .trim()
    .replace(/\s+/g, " ")
    .toLowerCase();
}

function compareQuestionAnswer(question, answer, options = {}) {
  const given = normalizeAnswerValue(answer);
  const expected = normalizeAnswerValue(question?.answer);

  if (!question) {
    return { answer: given, correct: false };
  }

  if (question.type === "single" || question.type === "judge") {
    return { answer: given, correct: given[0] === expected[0] };
  }

  if (question.type === "multiple") {
    const left = [...given].sort();
    const right = [...expected].sort();
    return { answer: given, correct: JSON.stringify(left) === JSON.stringify(right) };
  }

  if (question.type === "fill") {
    return {
      answer: given,
      correct: normalizeComparableText(given[0]) === normalizeComparableText(expected[0]),
    };
  }

  if (options.allowSubjectiveAuto) {
    return {
      answer: given,
      correct: Boolean(normalizeComparableText(given.join("\n"))) && normalizeComparableText(given.join("\n")) === normalizeComparableText(expected.join("\n")),
    };
  }

  return { answer: given, correct: null };
}

function getDuplicateValues(values) {
  const seen = new Set();
  const duplicated = new Set();
  (values || []).forEach((value) => {
    if (seen.has(value)) {
      duplicated.add(value);
      return;
    }
    seen.add(value);
  });
  return [...duplicated];
}

function getOverlappingPaperQuestions(store, questionIds, currentPaperId) {
  const targetIds = new Set(questionIds || []);
  return store.papers.flatMap((paper) => {
    if (paper.id === currentPaperId) {
      return [];
    }
    return (paper.questionIds || [])
      .filter((questionId) => targetIds.has(questionId))
      .map((questionId) => ({
        paperId: paper.id,
        paperName: paper.name,
        questionId,
      }));
  });
}

function buildWrongBookEntry(store, entry) {
  const question = store.questions.find((item) => item.id === entry.questionId);
  const normalizedQuestion = question ? sanitizeQuestionForStudent(question) : null;
  return {
    ...entry,
    subject: question?.subject || entry.subject || "",
    knowledgePoint: question?.knowledgePoint || entry.knowledgePoint || "",
    type: question?.type || entry.type || "",
    title: question?.title || entry.title || "",
    expectedAnswer: normalizeAnswerValue(question?.answer || entry.expectedAnswer),
    latestAnswer: normalizeAnswerValue(entry.latestAnswer),
    lastRetryAnswer: normalizeAnswerValue(entry.lastRetryAnswer),
    question: normalizedQuestion,
    statusText: entry.lastRetryCorrect ? "已重做通过" : "待重做",
    removable: Boolean(entry.lastRetryCorrect && !entry.removedAt),
  };
}

function buildWrongBookEntries(store, studentId) {
  return store.wrongBookEntries
    .filter((item) => item.studentId === studentId && !item.removedAt)
    .map((item) => buildWrongBookEntry(store, item))
    .sort((a, b) => new Date(b.lastWrongAt || 0).getTime() - new Date(a.lastWrongAt || 0).getTime());
}

function backfillWrongBookEntries(store) {
  if (store.wrongBookBackfilled && Array.isArray(store.wrongBookEntries) && store.wrongBookEntries.length) {
    return store;
  }
  store.wrongBookEntries = Array.isArray(store.wrongBookEntries) ? store.wrongBookEntries : [];
  if (!store.wrongBookEntries.length) {
    (store.submissions || []).forEach((submission) => {
      if (submission.status !== "已完成") {
        return;
      }
      (submission.answerDetail || []).forEach((detail) => {
        const fullScore = Number(detail.fullScore || 0);
        const currentScore = Number(detail.score || 0);
        if (!detail.questionId || fullScore <= 0 || currentScore >= fullScore) {
          return;
        }
        const question = store.questions.find((item) => item.id === detail.questionId);
        const existing = store.wrongBookEntries.find(
          (item) => item.studentId === submission.studentId && item.questionId === detail.questionId
        );
        const nextEntry = {
          ...(existing || { id: createId("wrong"), retryCount: 0, wrongCount: 0 }),
          studentId: submission.studentId,
          studentName: submission.studentName,
          questionId: detail.questionId,
          subject: question?.subject || detail.subject || "",
          knowledgePoint: question?.knowledgePoint || detail.knowledgePoint || "",
          type: question?.type || detail.type || "",
          title: question?.title || detail.title || "",
          latestAnswer: normalizeAnswerValue(detail.answer),
          expectedAnswer: normalizeAnswerValue(question?.answer || detail.expectedAnswer),
          fullScore,
          lastScore: currentScore,
          wrongCount: Number(existing?.wrongCount || 0) + 1,
          lastWrongAt: submission.submittedAt || submission.updatedAt || new Date().toISOString(),
          lastSourceSubmissionId: submission.id,
          lastSourceExamId: submission.examId,
          lastRetryCorrect: false,
          removable: false,
          removedAt: null,
          status: "active",
        };
        if (existing) {
          Object.assign(existing, nextEntry);
        } else {
          store.wrongBookEntries.unshift(nextEntry);
        }
      });
    });
  }
  store.wrongBookBackfilled = true;
  return store;
}

function syncWrongBookFromSubmission(store, submission) {
  if (!submission || submission.status !== "已完成" || !submission.studentId) {
    return;
  }

  (submission.answerDetail || []).forEach((detail) => {
    const fullScore = Number(detail.fullScore || 0);
    const currentScore = Number(detail.score || 0);
    if (!detail.questionId || fullScore <= 0 || currentScore >= fullScore) {
      return;
    }

    const question = store.questions.find((item) => item.id === detail.questionId);
    const existing = store.wrongBookEntries.find((item) => item.studentId === submission.studentId && item.questionId === detail.questionId);
    const wrongCount =
      existing && existing.lastSourceSubmissionId === submission.id ? Number(existing.wrongCount || 0) : Number(existing?.wrongCount || 0) + 1;

    const nextEntry = {
      ...(existing || { id: createId("wrong"), retryCount: 0 }),
      studentId: submission.studentId,
      studentName: submission.studentName,
      questionId: detail.questionId,
      subject: question?.subject || detail.subject || "",
      knowledgePoint: question?.knowledgePoint || detail.knowledgePoint || "",
      type: question?.type || detail.type || "",
      title: question?.title || detail.title || "",
      latestAnswer: normalizeAnswerValue(detail.answer),
      expectedAnswer: normalizeAnswerValue(question?.answer || detail.expectedAnswer),
      fullScore,
      lastScore: currentScore,
      wrongCount,
      lastWrongAt: submission.submittedAt || submission.updatedAt || new Date().toISOString(),
      lastSourceSubmissionId: submission.id,
      lastSourceExamId: submission.examId,
      lastRetryCorrect: false,
      removable: false,
      removedAt: null,
      status: "active",
    };

    if (existing) {
      Object.assign(existing, nextEntry);
    } else {
      store.wrongBookEntries.unshift(nextEntry);
    }
  });
}

function sanitizeUser(user) {
  const { password, ...safeUser } = user;
  return safeUser;
}

function sanitizeQuestionForStudent(question) {
  const { answer, ...safeQuestion } = question;
  return safeQuestion;
}

function examStatus(exam) {
  const now = Date.now();
  const start = new Date(exam.startTime).getTime();
  const end = new Date(exam.endTime).getTime();
  if (now < start) return "未开始";
  if (now > end) return "已结束";
  return "进行中";
}

function isObjective(question) {
  return OBJECTIVE_TYPES.includes(question.type);
}

function isSubjective(question) {
  return SUBJECTIVE_TYPES.includes(question.type);
}

function canManageEntity(user, entity) {
  if (user.role === "admin") {
    return ADMIN_ENTITIES.includes(entity);
  }
  if (user.role === "teacher") {
    return TEACHER_ENTITIES.includes(entity);
  }
  return false;
}

function findEntity(store, entity, id) {
  return (store[entity] || []).find((item) => item.id === id) || null;
}

function assertTeacherOwnership(user, entity, record) {
  if (user.role !== "teacher") {
    return true;
  }
  if (!record) {
    return false;
  }
  if (entity === "questions" || entity === "papers" || entity === "exams") {
    return record.teacherId === user.id;
  }
  return false;
}

function buildExamSnapshot(store, exam, options = {}) {
  const paper = store.papers.find((item) => item.id === exam.paperId);
  if (!paper) {
    return null;
  }
  const sanitizeQuestion = options.hideAnswers ? sanitizeQuestionForStudent : (question) => question;
  const questions = paper.questionIds
    .map((id) => store.questions.find((item) => item.id === id))
    .filter(Boolean)
    .map((item, index) => ({
      ...sanitizeQuestion(item),
      order: index + 1,
    }));
  return {
    ...exam,
    statusText: examStatus(exam),
    paper: {
      ...paper,
      questionIds: [...paper.questionIds],
    },
    questions,
  };
}

function getPaper(store, paperId) {
  return store.papers.find((item) => item.id === paperId) || null;
}

function computeDeadlineAt(exam, paper, startedAt) {
  const examEndMs = new Date(exam.endTime).getTime();
  const startMs = new Date(startedAt).getTime();
  const durationMs = Number(paper?.durationMinutes || 0) * 60 * 1000;
  if (!Number.isFinite(durationMs) || durationMs <= 0) {
    return new Date(examEndMs).toISOString();
  }
  return new Date(Math.min(examEndMs, startMs + durationMs)).toISOString();
}

function getRemainingMs(submission) {
  if (!submission?.deadlineAt) {
    return 0;
  }
  return Math.max(0, new Date(submission.deadlineAt).getTime() - Date.now());
}

function formatDurationText(ms) {
  const totalSeconds = Math.max(0, Math.floor(Number(ms || 0) / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}小时${minutes}分${seconds}秒`;
  }
  if (minutes > 0) {
    return `${minutes}分${seconds}秒`;
  }
  return `${seconds}秒`;
}

function normalizeQuestionRecord(record, teacherId) {
  return {
    ...record,
    teacherId: teacherId || record.teacherId,
    type: String(record.type || "").trim(),
    subject: String(record.subject || "").trim(),
    knowledgePoint: String(record.knowledgePoint || "").trim(),
    difficulty: String(record.difficulty || "").trim(),
    title: String(record.title || "").trim(),
    score: Number(record.score),
    options: Array.isArray(record.options) ? record.options.map((item) => String(item).trim()).filter(Boolean) : [],
    answer: Array.isArray(record.answer) ? record.answer.map((item) => String(item).trim()).filter(Boolean) : [],
  };
}

function importQuestions(store, user, records) {
  const created = [];
  const errors = [];

  records.forEach((record, index) => {
    const nextRecord = normalizeQuestionRecord(
      {
        ...record,
        id: createId("question"),
      },
      user.role === "teacher" ? user.id : record.teacherId
    );
    const errorMessage = validateRecord(store, "questions", nextRecord);
    if (errorMessage) {
      errors.push({
        index,
        title: nextRecord.title || `第 ${index + 1} 条`,
        message: errorMessage,
      });
      return;
    }
    store.questions.unshift(nextRecord);
    created.push(nextRecord);
  });

  if (created.length) {
    logAction(store, user.id, "批量导入questions", JSON.stringify({ count: created.length }));
    writeStore(store);
  }

  return {
    importedCount: created.length,
    failedCount: errors.length,
    created,
    errors,
  };
}

function deleteQuestionsInBatch(store, user, ids) {
  const deletedIds = [];
  const errors = [];

  ids.forEach((rawId) => {
    const id = String(rawId || "").trim();
    if (!id) {
      return;
    }
    const target = store.questions.find((item) => item.id === id);
    if (!target) {
      errors.push({ id, message: "题目不存在" });
      return;
    }
    if (!assertTeacherOwnership(user, "questions", target)) {
      errors.push({ id, title: target.title, message: "只能删除自己的题目" });
      return;
    }
    const deleteError = assertDeleteAllowed(store, "questions", id);
    if (deleteError) {
      errors.push({ id, title: target.title, message: deleteError });
      return;
    }
    deletedIds.push(id);
  });

  if (deletedIds.length) {
    const deletedSet = new Set(deletedIds);
    store.questions = store.questions.filter((item) => !deletedSet.has(item.id));
    logAction(store, user.id, "批量删除questions", JSON.stringify({ count: deletedIds.length }));
    writeStore(store);
  }

  return {
    deletedCount: deletedIds.length,
    failedCount: errors.length,
    deletedIds,
    errors,
  };
}

function normalizeUserRecord(record) {
  return {
    ...record,
    username: String(record.username || "").trim(),
    name: String(record.name || "").trim(),
    password: String(record.password || "").trim(),
    role: String(record.role || "").trim(),
    classId: record.classId ? String(record.classId).trim() : undefined,
    departmentId: record.departmentId ? String(record.departmentId).trim() : undefined,
    major: record.major ? String(record.major).trim() : "",
  };
}

function importUsersInBatch(store, user, records) {
  const created = [];
  const errors = [];

  records.forEach((record, index) => {
    const nextRecord = normalizeUserRecord({
      ...record,
      id: createId(record.role === "teacher" ? "teacher" : record.role === "admin" ? "admin" : "student"),
    });
    if (store.users.some((item) => item.username === nextRecord.username)) {
      errors.push({
        index,
        title: nextRecord.username || `record-${index + 1}`,
        message: "用户名已存在",
      });
      return;
    }
    const errorMessage = validateRecord(store, "users", nextRecord);
    if (errorMessage) {
      errors.push({
        index,
        title: nextRecord.username || `record-${index + 1}`,
        message: errorMessage,
      });
      return;
    }
    store.users.unshift(nextRecord);
    created.push(nextRecord);
  });

  if (created.length) {
    logAction(store, user.id, "批量导入users", JSON.stringify({ count: created.length }));
    writeStore(store);
  }

  return {
    importedCount: created.length,
    failedCount: errors.length,
    created,
    errors,
  };
}

function canStudentAccessExam(user, exam) {
  return Boolean(exam?.published && Array.isArray(exam.targetClassIds) && exam.targetClassIds.includes(user.classId));
}

function getStudentSubmission(store, examId, studentId) {
  return store.submissions.find((item) => item.examId === examId && item.studentId === studentId) || null;
}

function extendStudentDeadline(store, exam, studentId, extraMinutes) {
  const submission = getStudentSubmission(store, exam.id, studentId);
  if (!submission) {
    return { error: "未找到该考生的答卷记录" };
  }
  if (submission.status !== "进行中") {
    return { error: "只有进行中的答卷可以延时" };
  }
  const minutes = Number(extraMinutes);
  if (!Number.isFinite(minutes) || minutes <= 0) {
    return { error: "延时时长必须大于 0" };
  }
  const baseMs = submission.deadlineAt ? new Date(submission.deadlineAt).getTime() : Date.now();
  submission.deadlineAt = new Date(baseMs + minutes * 60 * 1000).toISOString();
  submission.updatedAt = new Date().toISOString();
  submission.manualExtendedMinutes = Number(submission.manualExtendedMinutes || 0) + minutes;
  return { submission };
}

function ensureStudentSession(store, exam, user) {
  const paper = getPaper(store, exam.paperId);
  if (!paper) {
    return { error: "试卷不存在" };
  }
  const currentStatus = examStatus(exam);
  if (currentStatus === "未开始") {
    return { error: "考试尚未开始" };
  }
  if (currentStatus === "已结束") {
    return { error: "考试已结束" };
  }

  const existing = getStudentSubmission(store, exam.id, user.id);
  if (existing && ["待阅卷", "已完成"].includes(existing.status)) {
    return { error: "该考试已交卷，不能重复进入" };
  }

  const startedAt = existing?.startedAt || new Date().toISOString();
  const deadlineAt = computeDeadlineAt(exam, paper, startedAt);
  const draft = {
    id: existing?.id || createId("submission"),
    examId: exam.id,
    studentId: user.id,
    studentName: user.name,
    answers: existing?.answers || [],
    switchCount: existing?.switchCount || 0,
    status: "进行中",
    startedAt,
    deadlineAt,
    updatedAt: new Date().toISOString(),
  };

  if (existing) {
    Object.assign(existing, draft);
  } else {
    store.submissions.unshift(draft);
  }

  return { draft: existing || draft, paper };
}

function buildSubmissionReview(store, submission) {
  const exam = store.exams.find((item) => item.id === submission.examId);
  const paper = exam ? store.papers.find((item) => item.id === exam.paperId) : null;
  const score = Number(submission.finalScore ?? submission.autoScore ?? 0);
  const durationMinutes = Number(paper?.durationMinutes || 0);
  const totalAllowedMs = durationMinutes * 60 * 1000;
  const usedMs =
    submission.startedAt && submission.submittedAt
      ? Math.max(0, new Date(submission.submittedAt).getTime() - new Date(submission.startedAt).getTime())
      : 0;
  const finishedRows = store.submissions
    .filter((item) => item.examId === submission.examId && item.status === "已完成")
    .sort((a, b) => {
      const scoreDiff = Number(b.finalScore ?? b.autoScore ?? 0) - Number(a.finalScore ?? a.autoScore ?? 0);
      if (scoreDiff !== 0) {
        return scoreDiff;
      }
      return new Date(a.submittedAt || a.updatedAt || 0).getTime() - new Date(b.submittedAt || b.updatedAt || 0).getTime();
    });
  const rankIndex = finishedRows.findIndex((item) => item.id === submission.id);
  const passScore = Number(paper?.passScore || 0);
  return {
    ...submission,
    examName: exam?.name || submission.examId,
    paperName: paper?.name || "-",
    totalScore: Number(paper?.totalScore || 0),
    durationMinutes,
    passScore,
    passStatus: submission.status === "已完成" ? (score >= passScore ? "已及格" : "未及格") : "待定",
    usedMinutes: usedMs ? (usedMs / 60000).toFixed(1) : null,
    usedMs,
    usedTimeText: usedMs ? formatDurationText(usedMs) : null,
    timeUsageRate: usedMs && totalAllowedMs > 0 ? Number(((usedMs / totalAllowedMs) * 100).toFixed(1)) : null,
    passDelta: submission.status === "已完成" ? score - passScore : null,
    scoreRate: paper?.totalScore ? Number(((score / Number(paper.totalScore)) * 100).toFixed(1)) : null,
    participantCount: store.submissions.filter((item) => item.examId === submission.examId).length,
    targetStudentCount:
      exam && Array.isArray(exam.targetClassIds)
        ? store.users.filter((item) => item.role === "student" && exam.targetClassIds.includes(item.classId)).length
        : 0,
    rank: rankIndex >= 0 ? rankIndex + 1 : null,
    finishedCount: finishedRows.length,
  };
}

function decorateExam(store, exam) {
  const paper = store.papers.find((item) => item.id === exam.paperId);
  return {
    ...exam,
    statusText: examStatus(exam),
    durationMinutes: paper?.durationMinutes || 0,
    totalScore: paper?.totalScore || 0,
    passScore: paper?.passScore || 0,
    paperName: paper?.name || "-",
  };
}

function gradeSubmission(store, submission) {
  let autoScore = 0;
  let needsManualReview = false;
  const exam = store.exams.find((item) => item.id === submission.examId);
  const paper = exam ? getPaper(store, exam.paperId) : null;
  const questionList = (paper?.questionIds || [])
    .map((questionId) => store.questions.find((item) => item.id === questionId))
    .filter(Boolean);
  const answerMap = new Map((submission.answers || []).map((entry) => [entry.questionId, normalizeAnswerValue(entry.answer)]));

  const detail = questionList.map((question) => {
    const given = answerMap.get(question.id) || [];
    const evaluation = compareQuestionAnswer(question, given);
    let score = 0;
    let correct = evaluation.correct;

    if (isSubjective(question)) {
      needsManualReview = true;
    } else if (correct) {
      score = Number(question.score || 0);
      autoScore += score;
    }

    return {
      questionId: question.id,
      answer: evaluation.answer,
      title: question.title,
      subject: question.subject,
      knowledgePoint: question.knowledgePoint,
      type: question.type,
      score,
      fullScore: Number(question.score || 0),
      correct,
      expectedAnswer: normalizeAnswerValue(question.answer),
    };
  });

  submission.answerDetail = detail;
  submission.autoScore = autoScore;
  submission.finalScore = autoScore;
  submission.status = needsManualReview ? "待阅卷" : "已完成";
  submission.submittedAt = new Date().toISOString();
  return submission;
}

function validateRecord(store, entity, record) {
  if (entity === "users") {
    if (!record.username || !record.name || !record.password || !record.role) {
      return "用户信息不完整";
    }
    if (!["admin", "teacher", "student"].includes(record.role)) {
      return "用户角色非法";
    }
    if (record.role === "student" && !record.classId) {
      return "学生必须绑定班级";
    }
    if (record.role !== "student" && !record.departmentId) {
      return "教师或管理员必须绑定院系";
    }
  }

  if (entity === "questions") {
    if (!record.title || !record.subject || !record.knowledgePoint || !record.type) {
      return "题目信息不完整";
    }
    if (![...OBJECTIVE_TYPES, ...SUBJECTIVE_TYPES].includes(record.type)) {
      return "题目类型非法";
    }
    if (!Number.isFinite(Number(record.score)) || Number(record.score) <= 0) {
      return "题目分值必须大于 0";
    }
    if (["single", "multiple", "judge"].includes(record.type) && (!Array.isArray(record.options) || record.options.length < 2)) {
      return "选择题或判断题至少需要两个选项";
    }
    if (!Array.isArray(record.answer) || record.answer.length === 0) {
      return "题目答案不能为空";
    }
  }

  if (entity === "papers") {
    if (!record.name || !Array.isArray(record.questionIds) || record.questionIds.length === 0) {
      return "试卷至少需要一道题";
    }
    const duplicateIds = getDuplicateValues(record.questionIds);
    if (duplicateIds.length) {
      return "试卷内不能出现重复题目";
    }
    const missing = record.questionIds.find((id) => !findEntity(store, "questions", id));
    if (missing) {
      return "试卷包含不存在的题目";
    }
    const totalScore = record.questionIds.reduce((sum, id) => {
      const question = findEntity(store, "questions", id);
      return sum + Number(question?.score || 0);
    }, 0);
    if (totalScore !== 100) {
      return `试卷满分必须为 100 分，当前所选题目合计 ${totalScore} 分`;
    }
    record.totalScore = totalScore;
    const overlaps = getOverlappingPaperQuestions(store, record.questionIds, record.id);
    if (overlaps.length) {
      const targetPaper = overlaps[0];
      return `题目已在试卷《${targetPaper.paperName || targetPaper.paperId}》中使用，不能重复组卷`;
    }
  }

  if (entity === "exams") {
    if (!record.name || !record.paperId || !Array.isArray(record.targetClassIds) || record.targetClassIds.length === 0) {
      return "考试信息不完整";
    }
    if (!findEntity(store, "papers", record.paperId)) {
      return "试卷不存在";
    }
    if (new Date(record.startTime).getTime() >= new Date(record.endTime).getTime()) {
      return "考试结束时间必须晚于开始时间";
    }
  }

  return "";
}

function assertDeleteAllowed(store, entity, id) {
  if (entity === "departments") {
    if (store.classes.some((item) => item.departmentId === id) || store.users.some((item) => item.departmentId === id)) {
      return "院系仍被班级或用户引用，不能删除";
    }
  }
  if (entity === "classes") {
    if (store.users.some((item) => item.classId === id) || store.exams.some((item) => item.targetClassIds.includes(id))) {
      return "班级仍被学生或考试引用，不能删除";
    }
  }
  if (entity === "users") {
    if (store.questions.some((item) => item.teacherId === id) || store.papers.some((item) => item.teacherId === id) || store.exams.some((item) => item.teacherId === id)) {
      return "该用户仍有关联业务数据，不能删除";
    }
  }
  if (entity === "questions") {
    if (store.papers.some((item) => item.questionIds.includes(id))) {
      return "题目已被试卷使用，不能删除";
    }
  }
  if (entity === "papers") {
    if (store.exams.some((item) => item.paperId === id)) {
      return "试卷已被考试使用，不能删除";
    }
  }
  if (entity === "exams") {
    if (store.submissions.some((item) => item.examId === id)) {
      return "考试已有答卷记录，不能删除";
    }
  }
  return "";
}

function buildBootstrap(store, user) {
  const safeUsers = store.users.map(sanitizeUser);

  if (user.role === "admin") {
    return {
      currentUser: sanitizeUser(user),
      departments: store.departments,
      classes: store.classes,
      users: safeUsers,
      questions: store.questions,
      papers: store.papers,
      exams: store.exams.map((item) => decorateExam(store, item)),
      submissions: store.submissions.map((item) => buildSubmissionReview(store, item)),
      wrongBookEntries: [],
      logs: store.logs.slice(0, 50),
    };
  }

  if (user.role === "teacher") {
    const ownExams = store.exams.filter((item) => item.teacherId === user.id);
    const ownExamIds = new Set(ownExams.map((item) => item.id));
    return {
      currentUser: sanitizeUser(user),
      departments: store.departments,
      classes: store.classes,
      users: safeUsers,
      questions: store.questions.filter((item) => item.teacherId === user.id),
      papers: store.papers.filter((item) => item.teacherId === user.id),
      exams: ownExams.map((item) => decorateExam(store, item)),
      submissions: store.submissions
        .filter((item) => ownExamIds.has(item.examId))
        .map((item) => buildSubmissionReview(store, item)),
      wrongBookEntries: [],
      logs: [],
    };
  }

  const studentExams = store.exams
    .filter((item) => canStudentAccessExam(user, item))
    .map((item) => decorateExam(store, item));

  return {
    currentUser: sanitizeUser(user),
    departments: store.departments,
    classes: store.classes,
    users: [sanitizeUser(user)],
    questions: [],
    papers: [],
    exams: studentExams,
    submissions: store.submissions
      .filter((item) => item.studentId === user.id)
      .map((item) => buildSubmissionReview(store, item)),
    wrongBookEntries: buildWrongBookEntries(store, user.id),
    logs: [],
  };
}

function apiRoutes(req, res, urlObj) {
  const store = readStore();
  const user = getUser(req, store);
  const pathname = urlObj.pathname;

  if (req.method === "GET" && pathname === "/api/health") {
    return json(res, 200, {
      status: "ok",
      time: new Date().toISOString(),
      storage: fs.existsSync(storeFile) ? "ready" : "missing",
    });
  }

  if (req.method === "POST" && pathname === "/api/login") {
    return parseBody(req)
      .then((body) => {
        const found = store.users.find((item) => item.username === body.username && item.password === body.password);
        if (!found) {
          return json(res, 401, { message: "账号或密码错误" });
        }
        logAction(store, found.id, "登录系统", `${found.role}:${found.name}`);
        writeStore(store);
        json(res, 200, { user: sanitizeUser(found) });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "GET" && pathname === "/api/bootstrap") {
    if (!user) {
      return json(res, 401, { message: "未登录" });
    }
    return json(res, 200, buildBootstrap(store, user));
  }

  if (req.method === "GET" && pathname === "/api/stats") {
    if (!requireRole(res, user, ["admin", "teacher"])) {
      return;
    }
    const teacherExamIds = new Set(store.exams.filter((item) => item.teacherId === user.id).map((item) => item.id));
    const scopeSubmissions =
      user.role === "admin" ? store.submissions : store.submissions.filter((item) => teacherExamIds.has(item.examId));
    const totalUsers = user.role === "admin" ? store.users.length : store.users.filter((item) => item.role === "student").length;
    const totalExams = user.role === "admin" ? store.exams.length : teacherExamIds.size;
    const totalSubmissions = scopeSubmissions.length;
    const finished = scopeSubmissions.filter((item) => item.status === "已完成").length;
    return json(res, 200, { totalUsers, totalExams, totalSubmissions, finished });
  }

  if (req.method === "POST" && pathname === "/api/questions/batch-import") {
    if (!requireRole(res, user, ["teacher"])) {
      return;
    }
    if (!canManageEntity(user, "questions")) {
      return json(res, 403, { message: "当前角色不能新增题库数据" });
    }
    return parseBody(req)
      .then((body) => {
        if (!Array.isArray(body.records) || body.records.length === 0) {
          return json(res, 400, { message: "请至少提供一道题目" });
        }
        const result = importQuestions(store, user, body.records);
        json(res, 200, result);
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname === "/api/questions/batch-delete") {
    if (!requireRole(res, user, ["teacher"])) {
      return;
    }
    if (!canManageEntity(user, "questions")) {
      return json(res, 403, { message: "当前角色不能删除题库数据" });
    }
    return parseBody(req)
      .then((body) => {
        const ids = [...new Set((Array.isArray(body.ids) ? body.ids : []).map((item) => String(item).trim()).filter(Boolean))];
        if (!ids.length) {
          return json(res, 400, { message: "请至少选择一道题目" });
        }
        const result = deleteQuestionsInBatch(store, user, ids);
        json(res, 200, result);
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname === "/api/users/batch-import") {
    if (!requireRole(res, user, ["admin"])) {
      return;
    }
    return parseBody(req)
      .then((body) => {
        if (!Array.isArray(body.records) || body.records.length === 0) {
          return json(res, 400, { message: "请至少提供一条用户记录" });
        }
        const result = importUsersInBatch(store, user, body.records);
        json(res, 200, result);
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname === "/api/entities") {
    if (!requireRole(res, user, ["admin", "teacher"])) {
      return;
    }
    return parseBody(req)
      .then((body) => {
        const { entity, record } = body;
        if (!store[entity] || !Array.isArray(store[entity])) {
          return json(res, 400, { message: "实体不存在" });
        }
        if (!canManageEntity(user, entity)) {
          return json(res, 403, { message: "当前角色不能新增该数据" });
        }
        const nextRecord = { ...record, id: record.id || createId(entity.slice(0, -1)) };
        if (entity === "users" && store.users.some((item) => item.username === nextRecord.username)) {
          return json(res, 400, { message: "账号已存在" });
        }
        const errorMessage = validateRecord(store, entity, nextRecord);
        if (errorMessage) {
          return json(res, 400, { message: errorMessage });
        }
        if (user.role === "teacher" && (entity === "questions" || entity === "papers" || entity === "exams")) {
          nextRecord.teacherId = user.id;
        }
        store[entity].unshift(nextRecord);
        logAction(store, user.id, `新增${entity}`, JSON.stringify({ id: nextRecord.id }));
        writeStore(store);
        json(res, 200, { record: nextRecord });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "PUT" && pathname.startsWith("/api/entities/")) {
    if (!requireRole(res, user, ["admin", "teacher"])) {
      return;
    }
    const entity = pathname.split("/").pop();
    return parseBody(req)
      .then((body) => {
        const records = store[entity];
        if (!records || !Array.isArray(records)) {
          return json(res, 400, { message: "实体不存在" });
        }
        if (!canManageEntity(user, entity)) {
          return json(res, 403, { message: "当前角色不能编辑该数据" });
        }
        const index = records.findIndex((item) => item.id === body.id);
        if (index < 0) {
          return json(res, 404, { message: "记录不存在" });
        }
        if (!assertTeacherOwnership(user, entity, records[index])) {
          return json(res, 403, { message: "只能编辑自己的数据" });
        }
        const nextRecord = { ...records[index], ...body };
        if (entity === "users" && store.users.some((item) => item.username === nextRecord.username && item.id !== nextRecord.id)) {
          return json(res, 400, { message: "账号已存在" });
        }
        const errorMessage = validateRecord(store, entity, nextRecord);
        if (errorMessage) {
          return json(res, 400, { message: errorMessage });
        }
        records[index] = nextRecord;
        logAction(store, user.id, `更新${entity}`, body.id);
        writeStore(store);
        json(res, 200, { record: records[index] });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "DELETE" && pathname.startsWith("/api/entities/")) {
    if (!requireRole(res, user, ["admin", "teacher"])) {
      return;
    }
    const [, , , entity, id] = pathname.split("/");
    const records = store[entity];
    if (!records || !Array.isArray(records)) {
      return json(res, 400, { message: "实体不存在" });
    }
    if (!canManageEntity(user, entity)) {
      return json(res, 403, { message: "当前角色不能删除该数据" });
    }
    const target = records.find((item) => item.id === id);
    if (!target) {
      return json(res, 404, { message: "记录不存在" });
    }
    if (!assertTeacherOwnership(user, entity, target)) {
      return json(res, 403, { message: "只能删除自己的数据" });
    }
    const deleteError = assertDeleteAllowed(store, entity, id);
    if (deleteError) {
      return json(res, 400, { message: deleteError });
    }
    store[entity] = records.filter((item) => item.id !== id);
    logAction(store, user.id, `删除${entity}`, id);
    writeStore(store);
    return json(res, 200, { success: true });
  }

  if (req.method === "GET" && pathname.startsWith("/api/exams/") && pathname.endsWith("/detail")) {
    if (!requireRole(res, user, ["student", "teacher"])) {
      return;
    }
    const examId = pathname.split("/")[3];
    const exam = store.exams.find((item) => item.id === examId);
    if (!exam) {
      return json(res, 404, { message: "考试不存在" });
    }

    if (user.role === "teacher") {
      if (exam.teacherId !== user.id) {
        return json(res, 403, { message: "只能查看自己发布的考试" });
      }
      const snapshot = buildExamSnapshot(store, exam, { hideAnswers: false });
      if (!snapshot) {
        return json(res, 404, { message: "试卷不存在" });
      }
      return json(res, 200, snapshot);
    }

    if (!canStudentAccessExam(user, exam)) {
      return json(res, 403, { message: "当前考试不可访问" });
    }
    const sessionState = ensureStudentSession(store, exam, user);
    if (sessionState.error) {
      return json(res, 400, { message: sessionState.error });
    }
    const snapshot = buildExamSnapshot(store, exam, { hideAnswers: true });
    if (!snapshot) {
      return json(res, 404, { message: "试卷不存在" });
    }
    const submission = getStudentSubmission(store, exam.id, user.id);
    writeStore(store);
    return json(res, 200, {
      ...snapshot,
      session: {
        submissionId: submission.id,
        startedAt: submission.startedAt,
        deadlineAt: submission.deadlineAt,
        switchCount: submission.switchCount || 0,
        answers: submission.answers || [],
        remainingMs: getRemainingMs(submission),
      },
    });
  }

  if (req.method === "POST" && pathname === "/api/submissions/save") {
    if (!requireRole(res, user, ["student"])) {
      return;
    }
    return parseBody(req)
      .then((body) => {
        const exam = store.exams.find((item) => item.id === body.examId);
        if (!exam || !canStudentAccessExam(user, exam)) {
          return json(res, 404, { message: "考试不存在" });
        }
        const sessionState = ensureStudentSession(store, exam, user);
        if (sessionState.error) {
          return json(res, 400, { message: sessionState.error });
        }
        const draft = getStudentSubmission(store, body.examId, user.id);
        if (getRemainingMs(draft) === 0) {
          return json(res, 400, { message: "考试时间已到，请直接交卷" });
        }
        draft.answers = Array.isArray(body.answers) ? body.answers : [];
        draft.switchCount = Number(body.switchCount || 0);
        draft.updatedAt = new Date().toISOString();
        logAction(store, user.id, "自动保存答卷", body.examId);
        writeStore(store);
        json(res, 200, { success: true, submission: draft });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname === "/api/submissions/submit") {
    if (!requireRole(res, user, ["student"])) {
      return;
    }
    return parseBody(req)
      .then((body) => {
        const exam = store.exams.find((item) => item.id === body.examId);
        if (!exam || !canStudentAccessExam(user, exam)) {
          return json(res, 404, { message: "考试不存在" });
        }
        const existing = getStudentSubmission(store, body.examId, user.id);
        if (existing && ["待阅卷", "已完成"].includes(existing.status)) {
          return json(res, 200, { submission: buildSubmissionReview(store, existing) });
        }
        const sessionState = ensureStudentSession(store, exam, user);
        if (sessionState.error && sessionState.error !== "考试已结束") {
          return json(res, 400, { message: sessionState.error });
        }
        const paper = getPaper(store, exam.paperId);
        const target = getStudentSubmission(store, body.examId, user.id);
        target.answers = Array.isArray(body.answers) ? body.answers : [];
        target.switchCount = Number(body.switchCount || 0);
        target.deadlineAt = target.deadlineAt || computeDeadlineAt(exam, paper, target.startedAt || new Date().toISOString());

        const suspiciousReasons = [];
        if (target.switchCount > exam.antiCheatLimit) {
          suspiciousReasons.push("切屏次数超限");
        }
        const usedMs = target.startedAt ? Date.now() - new Date(target.startedAt).getTime() : 0;
        const minExpectedMs = Math.min(5 * 60 * 1000, Number(paper?.durationMinutes || 0) * 60 * 1000 * 0.2);
        if (minExpectedMs > 0 && usedMs > 0 && usedMs < minExpectedMs) {
          suspiciousReasons.push("作答时长异常偏短");
        }

        target.suspicious = suspiciousReasons.length > 0;
        target.suspiciousReasons = suspiciousReasons;
        gradeSubmission(store, target);
        if (target.status === "宸插畬鎴?") {
          syncWrongBookFromSubmission(store, target);
        }
        logAction(store, user.id, "提交试卷", `${body.examId}, suspicious=${target.suspicious}`);
        writeStore(store);
        json(res, 200, { submission: buildSubmissionReview(store, target) });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname === "/api/submissions/manual-grade") {
    if (!requireRole(res, user, ["teacher"])) {
      return;
    }
    return parseBody(req)
      .then((body) => {
        const submission = store.submissions.find((item) => item.id === body.submissionId);
        if (!submission) {
          return json(res, 404, { message: "答卷不存在" });
        }
        const exam = store.exams.find((item) => item.id === submission.examId);
        if (!exam || exam.teacherId !== user.id) {
          return json(res, 403, { message: "只能阅览自己考试的答卷" });
        }
        let finalScore = 0;
        submission.answerDetail = (submission.answerDetail || []).map((item) => {
          const fullScore = Number(item.fullScore || 0);
          if (body.scores[item.questionId] != null) {
            const nextScore = Math.max(0, Number(body.scores[item.questionId]) || 0);
            const score = Math.min(nextScore, fullScore || nextScore);
            finalScore += score;
            return { ...item, score, correct: fullScore > 0 ? score >= fullScore : false };
          }
          finalScore += Number(item.score || 0);
          return { ...item, correct: fullScore > 0 ? Number(item.score || 0) >= fullScore : item.correct };
        });
        submission.finalScore = finalScore;
        submission.status = "已完成";
        submission.gradedBy = user.name;
        syncWrongBookFromSubmission(store, submission);
        logAction(store, user.id, "手动阅卷", submission.id);
        writeStore(store);
        json(res, 200, { submission: buildSubmissionReview(store, submission) });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname.match(/^\/api\/exams\/[^/]+\/extend-student$/)) {
    if (!requireRole(res, user, ["teacher"])) {
      return;
    }
    const examId = pathname.split("/")[3];
    const exam = store.exams.find((item) => item.id === examId);
    if (!exam || exam.teacherId !== user.id) {
      return json(res, 404, { message: "考试不存在" });
    }
    return parseBody(req)
      .then((body) => {
        const result = extendStudentDeadline(store, exam, body.studentId, body.extraMinutes);
        if (result.error) {
          return json(res, 400, { message: result.error });
        }
        logAction(store, user.id, "延长考试时间", `${examId}:${body.studentId}:${body.extraMinutes}`);
        writeStore(store);
        json(res, 200, { submission: buildSubmissionReview(store, result.submission) });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname.match(/^\/api\/wrongbook\/[^/]+\/retry$/)) {
    if (!requireRole(res, user, ["student"])) {
      return;
    }
    const wrongBookId = pathname.split("/")[3];
    return parseBody(req)
      .then((body) => {
        const target = store.wrongBookEntries.find((item) => item.id === wrongBookId && item.studentId === user.id && !item.removedAt);
        if (!target) {
          return json(res, 404, { message: "错题记录不存在" });
        }
        const question = store.questions.find((item) => item.id === target.questionId);
        if (!question) {
          return json(res, 404, { message: "题目不存在" });
        }
        const evaluation = compareQuestionAnswer(question, body.answer, { allowSubjectiveAuto: true });
        target.retryCount = Number(target.retryCount || 0) + 1;
        target.lastRetryAt = new Date().toISOString();
        target.lastRetryAnswer = evaluation.answer;
        target.lastRetryCorrect = Boolean(evaluation.correct);
        target.status = target.lastRetryCorrect ? "mastered" : "active";
        target.removable = target.lastRetryCorrect;
        if (!target.lastRetryCorrect) {
          target.latestAnswer = evaluation.answer;
          target.wrongCount = Number(target.wrongCount || 0) + 1;
          target.lastWrongAt = target.lastRetryAt;
        }
        logAction(store, user.id, "错题重做", `${wrongBookId}:${target.lastRetryCorrect}`);
        writeStore(store);
        json(res, 200, { entry: buildWrongBookEntry(store, target) });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname.match(/^\/api\/wrongbook\/[^/]+\/remove$/)) {
    if (!requireRole(res, user, ["student"])) {
      return;
    }
    const wrongBookId = pathname.split("/")[3];
    const target = store.wrongBookEntries.find((item) => item.id === wrongBookId && item.studentId === user.id && !item.removedAt);
    if (!target) {
      return json(res, 404, { message: "错题记录不存在" });
    }
    if (!target.lastRetryCorrect) {
      return json(res, 400, { message: "请先重做并答对后再移出错题本" });
    }
    target.removedAt = new Date().toISOString();
    target.status = "removed";
    target.removable = false;
    logAction(store, user.id, "移出错题本", wrongBookId);
    writeStore(store);
    return json(res, 200, { success: true });
  }

  if (req.method === "POST" && pathname === "/api/user/password") {
    if (!user) {
      return json(res, 401, { message: "未登录" });
    }
    return parseBody(req)
      .then((body) => {
        const found = store.users.find((item) => item.id === user.id);
        if (!found || found.password !== body.oldPassword) {
          return json(res, 400, { message: "原密码错误" });
        }
        if (!body.newPassword || String(body.newPassword).trim().length < 6) {
          return json(res, 400, { message: "新密码至少 6 位" });
        }
        found.password = body.newPassword;
        logAction(store, user.id, "修改密码", user.username);
        writeStore(store);
        json(res, 200, { success: true });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  if (req.method === "POST" && pathname === "/api/admin/reset-password") {
    if (!requireRole(res, user, ["admin"])) {
      return;
    }
    return parseBody(req)
      .then((body) => {
        const targetUser = store.users.find((item) => item.id === body.userId);
        if (!targetUser) {
          return json(res, 404, { message: "用户不存在" });
        }
        const nextPassword = String(body.newPassword || "123456").trim();
        if (nextPassword.length < 6) {
          return json(res, 400, { message: "密码至少 6 位" });
        }
        targetUser.password = nextPassword;
        logAction(store, user.id, "重置密码", `${targetUser.username}`);
        writeStore(store);
        json(res, 200, { success: true });
      })
      .catch((error) => json(res, 400, { message: error.message }));
  }

  json(res, 404, { message: "接口不存在" });
}

function resolveStaticPath(pathname) {
  const staticRoot = path.resolve(staticDir);
  const requestPath = pathname === "/" ? "index.html" : decodeURIComponent(pathname).replace(/^\/+/, "");
  const resolved = path.resolve(staticRoot, requestPath);
  if (resolved !== staticRoot && !resolved.startsWith(`${staticRoot}${path.sep}`)) {
    return null;
  }
  if (!fs.existsSync(resolved) || fs.statSync(resolved).isDirectory()) {
    return path.join(staticRoot, "index.html");
  }
  return resolved;
}

const server = http.createServer((req, res) => {
  const urlObj = new URL(req.url, `http://${req.headers.host}`);
  if (urlObj.pathname.startsWith("/api/")) {
    apiRoutes(req, res, urlObj);
    return;
  }

  let filePath;
  try {
    filePath = resolveStaticPath(urlObj.pathname);
  } catch (error) {
    filePath = null;
  }
  if (!filePath) {
    res.writeHead(403, {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "no-store, no-cache, must-revalidate",
      Pragma: "no-cache",
      Expires: "0",
    });
    res.end("Forbidden");
    return;
  }
  sendFile(res, filePath);
});

server.listen(PORT, () => {
  console.log(`Online exam system is running at http://localhost:${PORT}`);
});
