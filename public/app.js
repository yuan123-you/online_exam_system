const state = {
  currentUser: null,
  bootstrap: null,
  activeMenu: "",
  teacherTab: "questions",
  questionPage: 1,
  QUESTION_PAGE_SIZE: 20,
  studentTab: "overview",
  recordPage: 1,
  wrongBookPage: 1,
  STUDENT_PAGE_SIZE: 20,
  selectedQuestionIds: [],
  _delegatedEventsSetup: false,
  _cachedQuestionSubjects: null,
  questionPageData: null,
  questionSubjects: null,
  _questionPageCacheKey: "",
  examSession: null,
  examTimer: null,
  examAutoSaveTimer: null,
  gradingSession: null,
  analysisTab: "exam-stats",
  filters: {
    user_student: "",
    user_teacher: "",
    logs: "",
    question_keyword: "",
    question_type: "all",
    question_subject: "all",
    student_exam_status: "all",
    record_status: "all",
    wrong_subject: "all",
  },
};

const loginView = document.getElementById("loginView");
const dashboardView = document.getElementById("dashboardView");
const loginForm = document.getElementById("loginForm");
const loginMessage = document.getElementById("loginMessage");
const modal = document.getElementById("modal");
const modalContent = document.getElementById("modalContent");
const closeModalBtn = document.getElementById("closeModalBtn");

loginForm.addEventListener("submit", handleLogin);
closeModalBtn.addEventListener("click", () => closeModal({ preserveExamSession: false }));
document.addEventListener("visibilitychange", handleVisibilityChange);
window.addEventListener("beforeunload", (event) => {
  if (state.examSession) {
    event.preventDefault();
    event.returnValue = "考试进行中，离开将导致答卷自动保存。确定要离开吗？";
    return event.returnValue;
  }
});
document.addEventListener("keydown", (event) => {
  // Exam keyboard navigation
  if (state.examSession && !modal.classList.contains("hidden")) {
    const tag = document.activeElement?.tagName;
    const isInput = tag === "TEXTAREA" || tag === "INPUT";

    if (event.key === "ArrowLeft" && !isInput) {
      event.preventDefault();
      document.getElementById("examPrevBtn")?.click();
      return;
    }
    if (event.key === "ArrowRight" && !isInput) {
      event.preventDefault();
      document.getElementById("examNextBtn")?.click();
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      return; // Block Escape during exam to prevent accidental close
    }
    return;
  }

  // Normal modal close
  if (event.key === "Escape" && !modal.classList.contains("hidden")) {
    closeModal({ preserveExamSession: false });
  }
});
window.addEventListener("error", (event) => {
  const message = getFriendlyErrorMessage(event.error || event.message);
  if (loginView && !loginView.classList.contains("hidden")) {
    loginMessage.textContent = message;
  } else {
    alert(message);
  }
});
window.addEventListener("unhandledrejection", (event) => {
  const message = getFriendlyErrorMessage(event.reason);
  event.preventDefault();
  if (loginView && !loginView.classList.contains("hidden")) {
    loginMessage.textContent = message;
  } else {
    alert(message);
  }
});

const DISPLAY_TEXT_REPLACEMENTS = [
  ["绠＄悊鍛?", "管理员"],
  ["鏁欏笀", "教师"],
  ["瀛︾敓", "学生"],
  ["鏁版嵁鎬昏", "数据总览"],
  ["瀛︾敓绠＄悊", "学生管理"],
  ["鏁欏笀绠＄悊", "教师管理"],
  ["缁勭粐绠＄悊", "组织管理"],
  ["绯荤粺鏃ュ織", "系统日志"],
  ["鏁版嵁涓績", "数据中心"],
  ["鏁欏鐪嬫澘", "教学看板"],
  ["棰樺簱绠＄悊", "题库管理"],
  ["璇曞嵎绠＄悊", "试卷管理"],
  ["鑰冭瘯绠＄悊", "考试管理"],
  ["闃呭嵎涓績", "阅卷中心"],
  ["鎴愮哗鍒嗘瀽", "成绩分析"],
  ["鎴戠殑棣栭〉", "我的首页"],
  ["鍦ㄧ嚎鑰冭瘯", "在线考试"],
  ["鑰冭瘯璁板綍", "考试记录"],
  ["閿欓鏈?", "错题本"],
  ["涓汉淇℃伅", "个人信息"],
  ["鍒锋柊鏁版嵁", "刷新数据"],
  ["閫€鍑虹櫥褰?", "退出登录"],
  ["鏂板瀛︾敓", "新增学生"],
  ["鏂板鏁欏笀", "新增教师"],
  ["鏂板棰樼洰", "新增题目"],
  ["鍙戝竷鑰冭瘯", "发布考试"],
  ["杩涘叆鑰冭瘯鍒楄〃", "进入考试列表"],
  ["鏌ョ湅鑰冭瘯璁板綍", "查看考试记录"],
  ["杩愯姒傝", "运行概览"],
  ["鏈€杩戣€冭瘯", "最近考试"],
  ["鏈€杩戞搷浣?", "最近操作"],
  ["褰撳墠鐧诲綍", "当前登录"],
  ["闄㈢郴绠＄悊", "院系管理"],
  ["鐝骇绠＄悊", "班级管理"],
  ["鍒嗚€冭瘯缁熻", "分考试统计"],
  ["鏈€杩戞垚缁?", "最近成绩"],
  ["鎴愮哗瓒嬪娍", "成绩趋势"],
  ["鐭ヨ瘑鐐规帉鎻?", "知识点掌握"],
  ["鍏抽敭璇?", "关键词"],
  ["绉戠洰", "科目"],
  ["棰樺瀷", "题型"],
  ["鍏ㄩ儴", "全部"],
  ["鍗曢€?", "单选"],
  ["澶氶€?", "多选"],
  ["鍒ゆ柇", "判断"],
  ["濉┖", "填空"],
  ["绠€绛?", "简答"],
  ["缂栫▼", "编程"],
  ["棰樼洰", "题目"],
  ["鐭ヨ瘑鐐?", "知识点"],
  ["闅惧害", "难度"],
  ["鍒嗗€?", "分值"],
  ["鎿嶄綔", "操作"],
  ["缂栬緫", "编辑"],
  ["鍒犻櫎", "删除"],
  ["棰勮", "预览"],
  ["鐩戞帶", "监控"],
  ["寮傚父", "异常"],
  ["寰楀垎", "得分"],
  ["鎻愪氦鏃堕棿", "提交时间"],
  ["姝ｅ父", "正常"],
  ["鐤戜技寮傚父", "疑似异常"],
  ["寮€濮嬪鍏?", "开始导入"],
  ["寤舵椂", "延时"],
];

// Build a lookup map + single compiled regex for O(1) text normalization
const _textReplaceMap = new Map(DISPLAY_TEXT_REPLACEMENTS);
const _textReplaceRegex = new RegExp(
  DISPLAY_TEXT_REPLACEMENTS.map(([from]) => from.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|"),
  "g"
);

function normalizeDisplayText(text) {
  if (typeof text !== "string") {
    return text;
  }
  return text.replace(_textReplaceRegex, (match) => _textReplaceMap.get(match) || match);
}

function escapeHtml(value) {
  return normalizeDisplayText(String(value ?? ""))
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}

function formatAnswerList(answer) {
  const values = Array.isArray(answer) ? answer : answer == null ? [] : [answer];
  const text = values.map((item) => String(item ?? "").trim()).filter(Boolean);
  return text.length ? text.map(escapeHtml).join("、") : "-";
}

function repairRenderedText(root) {
  if (!root) return;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let current = walker.nextNode();
  while (current) {
    current.nodeValue = normalizeDisplayText(current.nodeValue);
    current = walker.nextNode();
  }
  root.querySelectorAll("*").forEach((element) => {
    if (element.placeholder) {
      element.placeholder = normalizeDisplayText(element.placeholder);
    }
    if (element.title) {
      element.title = normalizeDisplayText(element.title);
    }
    if ((element.tagName === "INPUT" || element.tagName === "OPTION") && typeof element.value === "string") {
      if (["button", "submit"].includes((element.type || "").toLowerCase())) {
        element.value = normalizeDisplayText(element.value);
      }
    }
  });
}

function getFriendlyErrorMessage(error) {
  const raw = String(error?.message || error || "").trim();
  if (!raw) return "操作失败，请稍后重试";
  if (raw.includes("Failed to fetch")) return "无法连接服务器，请确认服务已启动";
  if (raw.includes("Payload too large")) return "提交内容过大，请精简后重试";
  if (raw.includes("Unexpected token")) return "页面脚本解析失败，请刷新页面后重试";
  if (raw.includes("ReferenceError") || raw.includes("is not defined")) return "页面脚本运行失败，请刷新页面后重试";
  if (!/[\u4e00-\u9fa5]/.test(raw) && (/[A-Za-z]/.test(raw) || /[^\x00-\x7F]/.test(raw))) {
    return "系统处理失败，请刷新页面后重试";
  }
  return raw;
}

function api(path, options = {}) {
  const headers = { "Content-Type": "application/json" };
  if (state.currentUser) {
    headers["X-User-Id"] = state.currentUser.id;
  }
  return fetch(path, { ...options, headers: { ...headers, ...(options.headers || {}) } }).then(async (res) => {
    const data = await res.json();
    if (!res.ok) {
      throw new Error(getFriendlyErrorMessage(data.message || "请求失败"));
    }
    return data;
  });
}

function handleLogin(event) {
  event.preventDefault();
  loginMessage.textContent = "";
  const formData = new FormData(loginForm);
  api("/api/login", {
    method: "POST",
    body: JSON.stringify({
      username: formData.get("username"),
      password: formData.get("password"),
    }),
  })
    .then((data) => {
      state.currentUser = data.user;
      localStorage.setItem("exam-user", JSON.stringify(data.user));
      return loadBootstrap();
    })
    .catch((error) => {
      loginMessage.textContent = getFriendlyErrorMessage(error);
    });
}

function logout() {
  state.currentUser = null;
  state.bootstrap = null;
  state.activeMenu = "";
  state.examSession = null;
  stopExamTimers();
  closeModal({ preserveExamSession: true });
  localStorage.removeItem("exam-user");
  dashboardView.classList.add("hidden");
  loginView.classList.remove("hidden");
}

function loadBootstrap() {
  return api("/api/bootstrap")
    .then((data) => {
      state.bootstrap = data;
      state._cachedQuestionSubjects = null; // invalidate subject cache
      invalidateQuestionCache(); // invalidate server-side question page cache
      state.selectedQuestionIds = state.selectedQuestionIds.filter((id) =>
        data.questions.some((item) => item.id === id)
      );
      state.currentUser = data.currentUser;
      const menus = menuMap()[state.currentUser.role];
      if (!menus.find((item) => item.key === state.activeMenu)) {
        state.activeMenu = defaultMenu();
      }
      loginView.classList.add("hidden");
      dashboardView.classList.remove("hidden");
      renderDashboard();
    })
    .catch((error) => {
      loginMessage.textContent = getFriendlyErrorMessage(error);
      logout();
    });
}

function defaultMenu() {
  return {
    admin: "stats",
    teacher: "overview",
    student: "overview",
  }[state.currentUser.role];
}

function roleLabel(role) {
  return {
    admin: "管理员",
    teacher: "教师",
    student: "学生",
  }[role] || role;
}

function debounce(fn, delay) {
  let timer = null;
  return function (...args) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}

const debouncedRenderDashboard = debounce(renderDashboard, 300);

function menuMap() {
  return {
    admin: [
      { key: "stats", label: "数据总览", desc: "查看全局用户、考试、答卷与最近动态。" },
      { key: "students", label: "学生管理", desc: "维护学生账号，支持搜索、编辑、重置密码。" },
      { key: "teachers", label: "教师管理", desc: "维护教师账号与所属院系信息。" },
      { key: "org", label: "组织管理", desc: "维护院系与班级，查看组织结构分布。" },
      { key: "logs", label: "系统日志", desc: "检索关键操作记录，便于演示追踪。" },
    ],
    teacher: [
      { key: "overview", label: "教学看板", desc: "查看题库、试卷、考试和待阅卷概况。" },
      { key: "questions", label: "题库管理", desc: "按题型、科目筛选题库并维护题目。" },
      { key: "papers", label: "试卷管理", desc: "管理试卷、预览试卷结构与组成题目。" },
      { key: "exams", label: "考试管理", desc: "发布考试、监控考生状态、查看异常答卷。" },
      { key: "grading", label: "阅卷中心", desc: "处理待阅卷答卷并查看完整答题详情。" },
      { key: "analysis", label: "成绩分析", desc: "统计考试成绩并导出成绩明细。" },
    ],
    student: [
      { key: "overview", label: "我的首页", desc: "查看待考、已交卷、平均分与最近记录。" },
      { key: "student-exams", label: "在线考试", desc: "按状态查看考试并进入作答。" },
      { key: "records", label: "考试记录", desc: "查看成绩、异常标记与答卷详情。" },
      { key: "wrongbook", label: "错题本", desc: "按科目回顾错题与参考答案。" },
      { key: "profile", label: "个人信息", desc: "查看账号资料并修改密码。" },
    ],
  };
}

function iconForMenuKey(key) {
  const icons = {
    stats: '<svg viewBox="0 0 24 24"><rect x="3" y="12" width="4" height="9" rx="1"/><rect x="10" y="8" width="4" height="13" rx="1"/><rect x="17" y="3" width="4" height="18" rx="1"/></svg>',
    students: '<svg viewBox="0 0 24 24"><circle cx="9" cy="7" r="4"/><path d="M3 21v-2a4 4 0 014-4h4a4 4 0 014 4v2"/><circle cx="17" cy="7" r="3"/><path d="M21 21v-2a4 4 0 00-3-3.87"/></svg>',
    teachers: '<svg viewBox="0 0 24 24"><path d="M22 10L12 5 2 10l10 5 10-5z"/><path d="M6 12v5c0 0 3 3 6 3s6-3 6-3v-5"/></svg>',
    org: '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>',
    logs: '<svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="14" y2="17"/></svg>',
    overview: '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>',
    questions: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 015.83 1c0 2-3 3-3 3"/><circle cx="12" cy="17" r=".5"/></svg>',
    papers: '<svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>',
    exams: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>',
    grading: '<svg viewBox="0 0 24 24"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg>',
    analysis: '<svg viewBox="0 0 24 24"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>',
    "student-exams": '<svg viewBox="0 0 24 24"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>',
    records: '<svg viewBox="0 0 24 24"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg>',
    wrongbook: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>',
    profile: '<svg viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
  };
  return icons[key] || '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/></svg>';
}

function renderDashboard() {
  const menuItems = menuMap()[state.currentUser.role];
  const isCollapsed = sessionStorage.getItem("sidebar-collapsed") === "true";

  if (isCollapsed) {
    dashboardView.classList.add("sidebar-collapsed");
  } else {
    dashboardView.classList.remove("sidebar-collapsed");
  }

  dashboardView.innerHTML = `
    <aside class="sidebar">
      <button class="sidebar-expand-btn" id="sidebarExpandBtn" type="button" title="展开侧边栏">&#9654;</button>
      <div class="sidebar-top">
        <div class="sidebar-brand">
          <div class="sidebar-brand-icon">&#128221;</div>
          <div class="sidebar-brand-text">
            <h2>在线考试</h2>
            <p class="eyebrow">${roleLabel(state.currentUser.role)}</p>
          </div>
        </div>
        <button class="sidebar-collapse-btn" id="sidebarCollapseBtn" type="button" title="收起侧边栏">&#9664;</button>
      </div>
      <div class="sidebar-body">
        <div class="sidebar-user">
          <div class="sidebar-user-avatar">${escapeHtml(state.currentUser.name.charAt(0))}</div>
          <div class="sidebar-user-info">
            <h3>${escapeHtml(state.currentUser.name)}</h3>
            <span>${roleLabel(state.currentUser.role)} &middot; ${escapeHtml(state.currentUser.username)}</span>
          </div>
        </div>
        <nav class="menu-list">
          <div class="menu-section-label">功能菜单</div>
          ${menuItems
            .map(
              (item) => `
            <button class="menu-btn ${state.activeMenu === item.key ? "active" : ""}" data-menu="${item.key}" title="${item.desc}">
              <span class="menu-icon">${iconForMenuKey(item.key)}</span>
              <span class="menu-label">
                <strong>${item.label}</strong>
              </span>
            </button>`
            )
            .join("")}
        </nav>
        <div class="menu-footer">
          <button class="ghost-btn" id="refreshBtn" type="button">
            <span class="menu-icon"><svg viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg></span>
            <span class="menu-footer-label">刷新数据</span>
          </button>
          <button class="danger-btn" id="logoutBtn" type="button">
            <span class="menu-icon"><svg viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg></span>
            <span class="menu-footer-label">退出登录</span>
          </button>
        </div>
      </div>
    </aside>
    <div class="sidebar-overlay" id="sidebarOverlay"></div>
    <section class="workspace-main">
      <div class="mobile-topbar">
        <button class="mobile-menu-btn" id="mobileMenuBtn" type="button" title="打开菜单">
          <svg viewBox="0 0 24 24"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
        </button>
      </div>
      <div class="content-grid">${renderContent()}</div>
    </section>
  `;

  setupDelegatedEvents();
  bindPermanentEvents();
  repairRenderedText(dashboardView);

  // Bind password form if present (student profile tab)
  const passwordForm = document.getElementById("passwordForm");
  if (passwordForm) passwordForm.onsubmit = updatePassword;

  // Trigger async fetch for questions tab if teacher is on it
  if (state.currentUser?.role === "teacher" && state.teacherTab === "questions") {
    refreshQuestionBankView();
  }
}

function collapseSidebar() {
  dashboardView.classList.add("sidebar-collapsed");
  sessionStorage.setItem("sidebar-collapsed", "true");
}

function expandSidebar() {
  dashboardView.classList.remove("sidebar-collapsed");
  sessionStorage.setItem("sidebar-collapsed", "false");
}

function openMobileSidebar() {
  dashboardView.classList.add("sidebar-open");
}

function closeMobileSidebar() {
  dashboardView.classList.remove("sidebar-open");
}

function renderContent() {
  switch (state.activeMenu) {
    case "stats":
      return renderAdminStats();
    case "students":
      return renderUserSection("student");
    case "teachers":
      return renderUserSection("teacher");
    case "org":
      return renderOrgSection();
    case "logs":
      return renderLogs();
    case "overview":
      if (state.currentUser.role === "teacher") return renderTeacherOverview();
      if (state.currentUser.role === "student") {
        state.studentTab = "overview";
        return renderStudentUnifiedPanel();
      }
      return renderStudentOverview();
    case "questions":
    case "papers":
    case "exams":
    case "grading":
      // Teacher: unified panel with tabs
      if (state.currentUser.role === "teacher") {
        if (["questions", "papers", "exams", "grading"].includes(state.activeMenu)) {
          state.teacherTab = state.activeMenu;
        }
        return renderTeacherUnifiedPanel();
      }
      return state.activeMenu === "questions" ? renderQuestions() : "";
    case "analysis":
      return renderAnalysis();
    case "student-exams":
    case "records":
    case "wrongbook":
    case "profile":
      if (state.currentUser.role === "student") {
        if (["overview", "student-exams", "records", "wrongbook", "profile"].includes(state.activeMenu)) {
          state.studentTab = state.activeMenu;
        }
        return renderStudentUnifiedPanel();
      }
      if (state.activeMenu === "student-exams") return renderStudentExams();
      if (state.activeMenu === "records") return renderRecords();
      if (state.activeMenu === "wrongbook") return renderWrongBook();
      if (state.activeMenu === "profile") return renderProfile();
      return "";
    default:
      return "";
  }
}

function panel(content, extraClass = "") {
  return `<section class="panel${extraClass ? " " + extraClass : ""}">${content}</section>`;
}

function sectionTitle(title, subtitle, actions = "") {
  const sub = subtitle ? `<p class="section-subtitle">${subtitle}</p>` : "";
  const act = actions ? `<div class="section-actions">${actions}</div>` : "";
  if (!sub && !act) {
    return `<div class="section-title"><h3>${title}</h3></div>`;
  }
  return `<div class="section-title">
    <div><h3>${title}</h3>${sub}</div>
    ${act}
  </div>`;
}

function dataTable(headCols, bodyRows, emptyText) {
  const empty = `<tr><td colspan="${headCols.length}">${emptyText || "暂无数据"}</td></tr>`;
  return `<div class="table-wrap"><table>
    <thead><tr>${headCols.map((col) => `<th>${col}</th>`).join("")}</tr></thead>
    <tbody>${bodyRows || empty}</tbody>
  </table></div>`;
}

function renderAdminStats() {
  const users = state.bootstrap.users;
  const exams = state.bootstrap.exams;
  const submissions = state.bootstrap.submissions;
  const students = users.filter((item) => item.role === "student").length;
  const teachers = users.filter((item) => item.role === "teacher").length;
  const suspicious = submissions.filter((item) => item.suspicious).length;
  const finished = submissions.filter((item) => item.status === "已完成").length;
  const completion = submissions.length ? `${((finished / submissions.length) * 100).toFixed(1)}%` : "0%";
  const recentExams = exams.slice(0, 5);

  return `
    ${panel(`
      ${sectionTitle("运行概览")}
      <div class="stats-grid">
        <article class="stat-card"><span>总用户数</span><strong>${users.length}</strong></article>
        <article class="stat-card"><span>学生数</span><strong>${students}</strong></article>
        <article class="stat-card"><span>教师数</span><strong>${teachers}</strong></article>
        <article class="stat-card"><span>考试场次</span><strong>${exams.length}</strong></article>
        <article class="stat-card"><span>答卷总数</span><strong>${submissions.length}</strong></article>
        <article class="stat-card"><span>完成率</span><strong>${completion}</strong></article>
        <article class="stat-card"><span>异常答卷</span><strong>${suspicious}</strong></article>
        <article class="stat-card"><span>组织数量</span><strong>${state.bootstrap.departments.length + state.bootstrap.classes.length}</strong></article>
      </div>
    `)}
    <section class="two-column">
      <article class="panel">
        ${sectionTitle("最近考试")}
        ${recentExams.length ? renderMiniExamList(recentExams) : renderEmptyState("暂无考试数据")}
      </article>
      <article class="panel">
        ${sectionTitle("最近操作")}
        ${state.bootstrap.logs.length ? renderMiniLogList(state.bootstrap.logs.slice(0, 6)) : renderEmptyState("暂无日志")}
      </article>
    </section>
  `;
}

function renderUserSection(role) {
  const filterKey = role === "student" ? "user_student" : "user_teacher";
  const keyword = state.filters[filterKey].trim().toLowerCase();
  const users = state.bootstrap.users
    .filter((item) => item.role === role)
    .filter((item) => {
      if (!keyword) return true;
      const text = `${item.username} ${item.name} ${item.major || ""}`.toLowerCase();
      return text.includes(keyword);
    });
  const classes = indexBy(state.bootstrap.classes);
  const departments = indexBy(state.bootstrap.departments);
  const title = role === "student" ? "学生管理" : "教师管理";

  return panel(`
    ${sectionTitle(title, "支持搜索、编辑、重置密码和删除。", `
      <button class="ghost-btn" type="button" data-import-users="${role}">批量导入</button>
      ${role === "student" ? `<button class="accent-btn" type="button" data-export-users="users">导出名单</button>` : ""}
      <button class="primary-btn" type="button" data-open-form="${role}">新增${role === "student" ? "学生" : "教师"}</button>
    `)}
    <div class="filter-bar">
      <div class="toolbar-form">
        <label>
          <span>关键词搜索</span>
          <input value="${escapeAttr(state.filters[filterKey])}" data-filter-key="${filterKey}" placeholder="账号 / 姓名 / 专业" />
        </label>
      </div>
      <p class="toolbar-note">当前共 ${users.length} 条记录。</p>
    </div>
    ${dataTable(["账号", "姓名", "附属信息", "操作"],
      users.length
        ? users.map((user) => {
            const extra =
              role === "student"
                ? `${escapeHtml(classes[user.classId]?.name || "-")} / ${escapeHtml(user.major || "-")}`
                : escapeHtml(departments[user.departmentId]?.name || "-");
            return `
              <tr>
                <td>${escapeHtml(user.username)}</td>
                <td>${escapeHtml(user.name)}</td>
                <td>${extra}</td>
                <td>
                  <div class="action-row">
                    <button class="ghost-btn" type="button" data-edit-user="${escapeAttr(user.id)}">编辑</button>
                    <button class="accent-btn" type="button" data-reset-password="${escapeAttr(user.id)}">重置密码</button>
                    <button class="danger-btn" type="button" data-delete="users:${escapeAttr(user.id)}">删除</button>
                  </div>
                </td>
              </tr>
            `;
          }).join("")
        : null,
      "暂无匹配记录"
    )}
  `);
}

function renderOrgSection() {
  const departmentCounts = state.bootstrap.departments.map((item) => ({
    ...item,
    classCount: state.bootstrap.classes.filter((row) => row.departmentId === item.id).length,
    teacherCount: state.bootstrap.users.filter((row) => row.role !== "student" && row.departmentId === item.id).length,
  }));

  return `
    <section class="stats-grid">
      <article class="stat-card"><span>院系数</span><strong>${state.bootstrap.departments.length}</strong></article>
      <article class="stat-card"><span>班级数</span><strong>${state.bootstrap.classes.length}</strong></article>
      <article class="stat-card"><span>在读学生</span><strong>${state.bootstrap.users.filter((item) => item.role === "student").length}</strong></article>
    </section>
    <section class="org-column">
      ${panel(`
        ${sectionTitle("院系管理", "支持新增、编辑和删除院系。",
          `<button class="primary-btn" type="button" data-open-form="department">新增院系</button>`
        )}
        ${dataTable(["院系", "班级数", "教师/管理员", "操作"],
          departmentCounts.map((item) => `
            <tr>
              <td>${escapeHtml(item.name)}</td>
              <td>${item.classCount}</td>
              <td>${item.teacherCount}</td>
              <td>
                <div class="action-row">
                  <button class="ghost-btn" type="button" data-edit-entity="departments:${escapeAttr(item.id)}">编辑</button>
                  <button class="danger-btn" type="button" data-delete="departments:${escapeAttr(item.id)}">删除</button>
                </div>
              </td>
            </tr>`
          ).join("")
        )}
      `, "org-panel")}
      ${panel(`
        ${sectionTitle("班级管理", "支持新增、编辑和删除班级。",
          `<button class="primary-btn" type="button" data-open-form="class">新增班级</button>`
        )}
        ${dataTable(["班级", "专业", "院系", "学生数", "操作"],
          state.bootstrap.classes.map((item) => {
            const studentCount = state.bootstrap.users.filter((row) => row.classId === item.id).length;
            const department = state.bootstrap.departments.find((row) => row.id === item.departmentId);
            return `
              <tr>
                <td>${escapeHtml(item.name)}</td>
                <td>${escapeHtml(item.major)}</td>
                <td>${escapeHtml(department?.name || "-")}</td>
                <td>${studentCount}</td>
                <td>
                  <div class="action-row">
                    <button class="ghost-btn" type="button" data-edit-entity="classes:${escapeAttr(item.id)}">编辑</button>
                    <button class="danger-btn" type="button" data-delete="classes:${escapeAttr(item.id)}">删除</button>
                  </div>
                </td>
              </tr>
            `;
          }).join("")
        )}
      `, "org-panel")}
    </section>
  `;
}

function renderLogs() {
  const keyword = state.filters.logs.trim().toLowerCase();
  const users = indexBy(state.bootstrap.users);
  const rows = state.bootstrap.logs.filter((item) => {
    if (!keyword) return true;
    const text = `${item.action} ${item.detail || ""} ${users[item.actorId]?.name || item.actorId}`.toLowerCase();
    return text.includes(keyword);
  });

  return panel(`
    ${sectionTitle("系统日志", "按动作、详情或操作人过滤。")}
    <div class="filter-bar">
      <div class="toolbar-form">
        <label>
          <span>关键词</span>
          <input value="${escapeAttr(state.filters.logs)}" data-filter-key="logs" placeholder="登录、删除、用户名..." />
        </label>
      </div>
      <p class="toolbar-note">当前显示 ${rows.length} 条日志。</p>
    </div>
    ${dataTable(["时间", "操作人", "动作", "详情"],
      rows.length
        ? rows.map((log) => `
            <tr>
              <td>${formatDate(log.time)}</td>
              <td>${escapeHtml(users[log.actorId]?.name || log.actorId)}</td>
              <td>${escapeHtml(log.action)}</td>
              <td>${escapeHtml(log.detail || "-")}</td>
            </tr>
          `).join("")
        : null,
      "暂无匹配日志"
    )}
  `);
}

function renderTeacherOverview() {
  const questions = state.bootstrap.questions;
  const papers = state.bootstrap.papers;
  const exams = state.bootstrap.exams;
  const submissions = state.bootstrap.submissions;
  const pending = submissions.filter((item) => item.status === "待阅卷");

  return `
    <section class="stats-grid">
      <article class="stat-card"><span>我的题目</span><strong>${questions.length}</strong></article>
      <article class="stat-card"><span>我的试卷</span><strong>${papers.length}</strong></article>
      <article class="stat-card"><span>已发布考试</span><strong>${exams.length}</strong></article>
      <article class="stat-card"><span>待阅卷</span><strong>${pending.length}</strong></article>
      <article class="stat-card"><span>已收答卷</span><strong>${submissions.length}</strong></article>
    </section>
    <section class="two-column">
      <article class="panel">
        ${sectionTitle("最近考试")}
        ${exams.length ? renderMiniExamList(exams.slice(0, 5)) : renderEmptyState("暂无考试")}
      </article>
      <article class="panel">
        ${sectionTitle("待处理答卷")}
        ${pending.length
          ? renderMiniSubmissionList(pending.slice(0, 5))
          : renderEmptyState("当前没有待阅卷答卷")
        }
      </article>
    </section>
  `;
}

function renderQuestions() {
  // If server data is loaded, use it; otherwise trigger async fetch
  if (state.questionPageData) return renderQuestionsFromServer();
  return `<div class="empty-state">加载中…</div>`;
}

function renderQuestionsFromServer() {
  const type = state.filters.question_type;
  const subject = state.filters.question_subject;
  const subjects = state.questionSubjects || state._cachedQuestionSubjects || [];
  const data = state.questionPageData;
  const pageRows = data.rows || [];
  const total = data.total || 0;
  const pageSize = data.pageSize || state.QUESTION_PAGE_SIZE;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const currentPage = data.page || state.questionPage;
  const selectedSet = new Set(state.selectedQuestionIds);
  const allSelected = pageRows.length > 0 && pageRows.every((item) => selectedSet.has(item.id));
  const pageStart = (currentPage - 1) * pageSize;

  const paginationHtml = total > pageSize ? `
    <div class="pagination-bar">
      <button class="ghost-btn" type="button" data-question-page="first" ${currentPage <= 1 ? "disabled" : ""}>首页</button>
      <button class="ghost-btn" type="button" data-question-page="prev" ${currentPage <= 1 ? "disabled" : ""}>上一页</button>
      <span class="pagination-info">第 <strong>${currentPage}</strong> / ${totalPages} 页（共 ${total} 条）</span>
      <button class="ghost-btn" type="button" data-question-page="next" ${currentPage >= totalPages ? "disabled" : ""}>下一页</button>
      <button class="ghost-btn" type="button" data-question-page="last" ${currentPage >= totalPages ? "disabled" : ""}>末页</button>
    </div>
  ` : "";

  return panel(`
    ${sectionTitle("题库管理", "按关键词、题型、科目筛选题目。", `
      <button class="ghost-btn" type="button" data-import-questions="open">批量导入</button>
      <button class="danger-btn" type="button" data-delete-selected-questions="1" ${state.selectedQuestionIds.length ? "" : "disabled"}>批量删除</button>
      <button class="accent-btn" type="button" data-clear-selected-questions="1" ${state.selectedQuestionIds.length ? "" : "disabled"}>清空已选</button>
      <button class="primary-btn" type="button" data-open-form="question">新增题目</button>
    `)}
    <div class="filter-bar">
      <div class="toolbar-form">
        <label><span>关键词</span><input value="${escapeAttr(state.filters.question_keyword)}" data-filter-key="question_keyword" placeholder="题目 / 知识点" /></label>
        <label>
          <span>题型</span>
          <select data-filter-key="question_type">
            <option value="all" ${type === "all" ? "selected" : ""}>全部</option>
            <option value="single" ${type === "single" ? "selected" : ""}>单选</option>
            <option value="multiple" ${type === "multiple" ? "selected" : ""}>多选</option>
            <option value="judge" ${type === "judge" ? "selected" : ""}>判断</option>
            <option value="fill" ${type === "fill" ? "selected" : ""}>填空</option>
            <option value="short" ${type === "short" ? "selected" : ""}>简答</option>
            <option value="coding" ${type === "coding" ? "selected" : ""}>编程</option>
          </select>
        </label>
        <label>
          <span>科目</span>
          <select data-filter-key="question_subject">
            <option value="all" ${subject === "all" ? "selected" : ""}>全部</option>
            ${subjects.map((item) => `<option value="${escapeAttr(item)}" ${subject === item ? "selected" : ""}>${escapeHtml(item)}</option>`).join("")}
          </select>
        </label>
      </div>
      <p class="toolbar-note">筛选后共 ${total} 道题，当前已选 ${state.selectedQuestionIds.length} 道。${total > pageSize ? `（第 ${pageStart + 1}–${Math.min(pageStart + pageSize, total)} 条）` : ""}</p>
    </div>
    ${dataTable(
      [`<input type="checkbox" data-select-all-questions="1" ${allSelected ? "checked" : ""} />`, "题型", "题目", "科目", "知识点", "难度", "分值", "操作"],
      pageRows.length
        ? pageRows.map((item) => `
            <tr>
              <td><input type="checkbox" data-select-question="${escapeAttr(item.id)}" ${selectedSet.has(item.id) ? "checked" : ""} /></td>
              <td>${escapeHtml(typeLabel(item.type))}</td>
              <td>${escapeHtml(item.title)}</td>
              <td>${escapeHtml(item.subject)}</td>
              <td>${escapeHtml(item.knowledgePoint)}</td>
              <td>${escapeHtml(item.difficulty)}</td>
              <td>${item.score}</td>
              <td>
                <div class="action-row">
                  <button class="ghost-btn" type="button" data-edit-question="${escapeAttr(item.id)}">编辑</button>
                  <button class="danger-btn" type="button" data-delete="questions:${escapeAttr(item.id)}">删除</button>
                </div>
              </td>
            </tr>`
          ).join("")
        : null,
      "暂无匹配题目"
    )}
    ${paginationHtml}
  `);
}

function renderPapers() {
  return panel(`
    ${sectionTitle("试卷管理", "支持预览试卷内容与删除未使用试卷。", `
      <button class="ghost-btn" type="button" data-auto-paper="open">自动组卷</button>
      <button class="primary-btn" type="button" data-open-form="paper">新增试卷</button>
    `)}
    ${dataTable(["试卷名称", "题量", "总分", "时长", "及格线", "操作"],
      state.bootstrap.papers.length
        ? state.bootstrap.papers.map((paper) => `
            <tr>
              <td>${escapeHtml(paper.name)}</td>
              <td>${paper.questionIds.length}</td>
              <td>${paper.totalScore}</td>
              <td>${paper.durationMinutes} 分钟</td>
              <td>${paper.passScore}</td>
              <td>
                <div class="action-row">
                  <button class="accent-btn" type="button" data-edit-paper="${escapeAttr(paper.id)}">编辑</button>
                  <button class="ghost-btn" type="button" data-preview-paper="${escapeAttr(paper.id)}">预览</button>
                  <button class="danger-btn" type="button" data-delete="papers:${escapeAttr(paper.id)}">删除</button>
                </div>
              </td>
            </tr>`
          ).join("")
        : null,
      "暂无试卷"
    )}
  `);
}

function renderTeacherExams() {
  const classes = indexBy(state.bootstrap.classes);
  return panel(`
    ${sectionTitle("考试管理", "支持考试监控、查看异常答卷与删除无答卷考试。",
      `<button class="primary-btn" type="button" data-open-form="exam">发布考试</button>`
    )}
    ${dataTable(["考试名称", "试卷", "班级", "时间", "状态", "操作"],
      state.bootstrap.exams.length
        ? state.bootstrap.exams.map((exam) => `
            <tr>
              <td>${escapeHtml(exam.name)}</td>
              <td>${escapeHtml(exam.paperName)}</td>
              <td>${escapeHtml(exam.targetClassIds.map((id) => classes[id]?.name || id).join("、"))}</td>
              <td>${formatDate(exam.startTime)}<br/>${formatDate(exam.endTime)}</td>
              <td><span class="tag">${escapeHtml(exam.statusText)}</span></td>
              <td>
                <div class="action-row">
                  <button class="accent-btn" type="button" data-edit-exam="${escapeAttr(exam.id)}">编辑</button>
                  <button class="ghost-btn" type="button" data-monitor-exam="${escapeAttr(exam.id)}">监控</button>
                  <button class="danger-btn" type="button" data-delete="exams:${escapeAttr(exam.id)}">删除</button>
                </div>
              </td>
            </tr>`
          ).join("")
        : null,
      "暂无考试"
    )}
  `);
}

function renderGrading() {
  const exams = indexBy(state.bootstrap.exams);
  const rows = [...state.bootstrap.submissions].sort((a, b) => {
    return new Date(b.submittedAt || b.updatedAt || 0) - new Date(a.submittedAt || a.updatedAt || 0);
  });
  return panel(`
    ${sectionTitle("阅卷中心", "待阅卷答卷可直接评分，已交卷答卷可查看详情。")}
    ${dataTable(["学生", "考试", "得分", "状态", "异常", "操作"],
      rows.length
        ? rows.map((item) => `
            <tr>
              <td>${escapeHtml(item.studentName)}</td>
              <td>${escapeHtml(exams[item.examId]?.name || item.examName || item.examId)}</td>
              <td>${item.finalScore ?? item.autoScore ?? "-"}</td>
              <td>${escapeHtml(item.status)}</td>
              <td class="${item.suspicious ? "danger" : "ok"}">${item.suspicious ? escapeHtml(item.suspiciousReasons?.join("、") || "疑似异常") : "正常"}</td>
              <td>
                <div class="action-row">
                  ${item.status === "待阅卷" ? `<button class="primary-btn" type="button" data-grade="${escapeAttr(item.id)}">评分</button>` : ""}
                  <button class="ghost-btn" type="button" data-view-submission="${escapeAttr(item.id)}">详情</button>
                </div>
              </td>
            </tr>`
          ).join("")
        : null,
      "暂无答卷"
    )}
  `);
}

function renderTeacherUnifiedPanel() {
  const tabs = [
    { key: "questions", label: "题库管理" },
    { key: "papers", label: "试卷管理" },
    { key: "exams", label: "考试管理" },
    { key: "grading", label: "阅卷中心" },
  ];
  const activeTab = state.teacherTab || "questions";

  let content = "";
  if (activeTab === "questions") content = renderQuestions();
  else if (activeTab === "papers") content = renderPapers();
  else if (activeTab === "exams") content = renderTeacherExams();
  else if (activeTab === "grading") content = renderGrading();

  return `
    <div class="panel">
      <div class="teacher-workspace-tabs">
        ${tabs.map((tab) => `
          <button class="teacher-tab-btn ${activeTab === tab.key ? "active" : ""}" type="button" data-teacher-tab="${tab.key}">${tab.label}</button>
        `).join("")}
      </div>
      <div class="teacher-workspace-content">
        ${content}
      </div>
    </div>
  `;
}

function switchTeacherTab(tabKey) {
  state.teacherTab = tabKey;
  state.activeMenu = tabKey;
  if (tabKey === "questions") state.questionPage = 1;

  // Update tab buttons active state
  const tabContainer = dashboardView.querySelector(".teacher-workspace-tabs");
  if (tabContainer) {
    tabContainer.querySelectorAll(".teacher-tab-btn").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.teacherTab === tabKey);
    });
  }

  // Re-render only the content area
  const contentArea = dashboardView.querySelector(".teacher-workspace-content");
  if (!contentArea) { renderDashboard(); return; }

  // For questions tab, use server-side fetch
  if (tabKey === "questions") {
    contentArea.innerHTML = `<div class="empty-state">加载中…</div>`;
    const needsSubjects = !state.questionSubjects;
    const fetches = [fetchQuestionPage()];
    if (needsSubjects) fetches.push(fetchQuestionSubjects());
    Promise.all(fetches).then(() => {
      if (state.teacherTab === "questions") {
        contentArea.innerHTML = renderQuestionsFromServer();
      }
    }).catch((err) => {
      if (state.teacherTab === "questions") {
        contentArea.innerHTML = `<div class="empty-state">${escapeHtml(getFriendlyErrorMessage(err))}</div>`;
      }
    });
  } else {
    let content = "";
    if (tabKey === "papers") content = renderPapers();
    else if (tabKey === "exams") content = renderTeacherExams();
    else if (tabKey === "grading") content = renderGrading();
    contentArea.innerHTML = content;
  }

  // Update sidebar active state
  dashboardView.querySelectorAll(".menu-btn").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.menu === tabKey);
  });
}

function renderAnalysis() {
  const rows = state.bootstrap.exams.map((exam) => {
    const paper = state.bootstrap.papers.find((item) => item.id === exam.paperId);
    const submissions = state.bootstrap.submissions.filter((item) => item.examId === exam.id && item.status === "已完成");
    const scores = submissions.map((item) => Number(item.finalScore ?? item.autoScore ?? 0));
    const avg = scores.length ? (scores.reduce((sum, item) => sum + item, 0) / scores.length).toFixed(1) : "0";
    const max = scores.length ? Math.max(...scores) : 0;
    const min = scores.length ? Math.min(...scores) : 0;
    const passCount = submissions.filter((item) => Number(item.finalScore ?? item.autoScore ?? 0) >= Number(paper?.passScore || 0)).length;
    return {
      exam,
      paper,
      count: submissions.length,
      avg,
      max,
      min,
      passRate: submissions.length ? `${((passCount / submissions.length) * 100).toFixed(1)}%` : "0%",
    };
  });
  const finished = state.bootstrap.submissions.filter((item) => item.status === "已完成");
  const allScores = finished.map((item) => Number(item.finalScore ?? item.autoScore ?? 0));
  const classCompareRows = buildClassComparisonRows();
  const questionQualityRows = buildQuestionQualityRows();
  const activeTab = state.analysisTab || "exam-stats";

  const tabItems = [
    { key: "exam-stats", label: "考试统计" },
    { key: "class-compare", label: "班级成绩对比" },
    { key: "question-quality", label: "试题质量分析" },
  ];

  return panel(`
    <nav class="analysis-tabs">
      ${tabItems.map((tab) => `<button class="analysis-tab-btn ${activeTab === tab.key ? "active" : ""}" data-analysis-tab="${tab.key}">${tab.label}</button>`).join("")}
    </nav>
    <div class="analysis-tab-content">
      ${activeTab === "exam-stats" ? `
        <section class="stats-grid">
          <article class="stat-card"><span>已完成答卷</span><strong>${finished.length}</strong></article>
          <article class="stat-card"><span>平均分</span><strong>${allScores.length ? (allScores.reduce((sum, item) => sum + item, 0) / allScores.length).toFixed(1) : "0"}</strong></article>
          <article class="stat-card"><span>最高分</span><strong>${allScores.length ? Math.max(...allScores) : 0}</strong></article>
          <article class="stat-card"><span>最低分</span><strong>${allScores.length ? Math.min(...allScores) : 0}</strong></article>
        </section>
        ${sectionTitle("分考试统计", "统计每场考试的人数、平均分和及格率。", '<button class="primary-btn" type="button" data-export-scores="teacher">导出成绩 CSV</button>')}
        ${dataTable(
          ["考试", "试卷", "完成人数", "平均分", "最高分", "最低分", "及格率"],
          rows.length
            ? rows.map((item) => `
              <tr>
                <td>${escapeHtml(item.exam.name)}</td>
                <td>${escapeHtml(item.paper?.name || "-")}</td>
                <td>${item.count}</td>
                <td>${item.avg}</td>
                <td>${item.max}</td>
                <td>${item.min}</td>
                <td>${item.passRate}</td>
              </tr>`).join("")
            : null,
          "暂无可分析考试"
        )}
      ` : ""}
      ${activeTab === "class-compare" ? `
        ${sectionTitle("班级成绩对比")}
        ${dataTable(
          ["班级", "考试人次", "平均分", "及格率"],
          classCompareRows.length
            ? classCompareRows.map((item) => `
              <tr>
                <td>${escapeHtml(item.className)}</td>
                <td>${item.count}</td>
                <td>${item.avgScore}</td>
                <td>${item.passRate}</td>
              </tr>`).join("")
            : null,
          "暂无班级成绩数据"
        )}
      ` : ""}
      ${activeTab === "question-quality" ? `
        ${sectionTitle("试题质量分析")}
        ${dataTable(
          ["题目", "答题人数", "正确率", "平均得分"],
          questionQualityRows.length
            ? questionQualityRows.map((item) => `
              <tr>
                <td>${escapeHtml(item.title)}</td>
                <td>${item.count}</td>
                <td>${item.correctRate}</td>
                <td>${item.avgScore}</td>
              </tr>`).join("")
            : null,
          "暂无试题质量数据"
        )}
      ` : ""}
    </div>
  `);
}

function renderStudentOverview() {
  const exams = state.bootstrap.exams;
  const submissions = state.bootstrap.submissions;
  const pending = exams.filter((item) => item.statusText === "进行中").length;
  const upcoming = exams.filter((item) => item.statusText === "未开始").length;
  const finished = submissions.filter((item) => item.status === "已完成").length;
  const avg =
    finished > 0
      ? (
          submissions
            .filter((item) => item.status === "已完成")
            .reduce((sum, item) => sum + Number(item.finalScore ?? item.autoScore ?? 0), 0) / finished
        ).toFixed(1)
      : "0";
  const latest = submissions.slice(0, 4);
  const subjectStats = buildStudentSubjectStats().slice(0, 6);
  const scoreTrend = submissions
    .filter((item) => item.status === "已完成")
    .slice(0, 5)
    .map((item) => {
      const exam = state.bootstrap.exams.find((row) => row.id === item.examId);
      return {
        label: exam?.name || item.examName || item.examId,
        score: Number(item.finalScore ?? item.autoScore ?? 0),
      };
    });

  return `
    <section class="stats-grid">
      <article class="stat-card"><span>进行中考试</span><strong>${pending}</strong></article>
      <article class="stat-card"><span>未开始考试</span><strong>${upcoming}</strong></article>
      <article class="stat-card"><span>已完成答卷</span><strong>${finished}</strong></article>
      <article class="stat-card"><span>平均分</span><strong>${avg}</strong></article>
    </section>
    <section class="three-column">
      ${panel(`
        ${sectionTitle("近期考试")}
        ${exams.length ? renderMiniExamList(exams.slice(0, 5)) : renderEmptyState("暂无考试")}
      `)}
      ${panel(`
        ${sectionTitle("最近成绩")}
        ${latest.length ? renderMiniSubmissionList(latest) : renderEmptyState("暂无成绩记录")}
      `)}
      ${panel(`
        ${sectionTitle("成绩趋势")}
        ${
          scoreTrend.length
            ? `
              <div class="list-pairs">
                ${scoreTrend
                  .map(
                    (item) => `
                  <div class="pair-row">
                    <div class="action-row" style="justify-content:space-between;">
                      <span>${escapeHtml(item.label)}</span>
                      <strong>${item.score}</strong>
                    </div>
                    <div class="chart-bar"><span style="width:${Math.min(100, item.score)}%;"></span></div>
                  </div>`
                  )
                  .join("")}
              </div>
            `
            : renderEmptyState("暂无成绩趋势")
        }
      `)}
    </section>
    ${panel(`
      ${sectionTitle("知识点掌握", "基于已完成答卷统计各科正确率。")}
      ${
        subjectStats.length
          ? `
            <div class="three-column">
              ${subjectStats
                .map(
                  (item) => `
                <div class="mini-item">
                  <h4>${escapeHtml(item.subject)}</h4>
                  <p>正确 ${item.correct} / 总计 ${item.total}</p>
                  <div class="chart-bar"><span style="width:${item.rate}%;"></span></div>
                  <p style="margin-top:8px;">正确率 ${item.rate}%</p>
                </div>`
                )
                .join("")}
            </div>
          `
          : renderEmptyState("提交答卷后会显示知识点掌握情况")
      }
    `)}
  `;
}

function renderStudentExams() {
  const filter = state.filters.student_exam_status;
  const rows = [...state.bootstrap.exams]
    .sort((a, b) => new Date(b.startTime) - new Date(a.startTime))
    .filter((item) => filter === "all" || item.statusText === filter);

  return panel(`
    ${sectionTitle("在线考试", "按状态筛选考试，进入后支持自动保存与防切屏。")}
    <div class="filter-bar">
      <div class="toolbar-form">
        <label>
          <span>考试状态</span>
          <select data-filter-key="student_exam_status">
            <option value="all" ${filter === "all" ? "selected" : ""}>全部</option>
            <option value="未开始" ${filter === "未开始" ? "selected" : ""}>未开始</option>
            <option value="进行中" ${filter === "进行中" ? "selected" : ""}>进行中</option>
            <option value="已结束" ${filter === "已结束" ? "selected" : ""}>已结束</option>
          </select>
        </label>
      </div>
    </div>
    <div class="card-grid">
      ${
        rows.length
          ? rows.map((exam) => renderStudentExamCard(exam)).join("")
          : renderEmptyState("暂无匹配考试")
      }
    </div>
  `);
}

function renderStudentExamCard(exam) {
  const submission = state.bootstrap.submissions.find((item) => item.examId === exam.id && item.studentId === state.currentUser.id);
  const disabled =
    exam.statusText !== "进行中" || submission?.status === "待阅卷" || submission?.status === "已完成";
  const buttonText =
    submission?.status === "进行中"
      ? "继续考试"
      : submission?.status === "待阅卷" || submission?.status === "已完成"
        ? "已交卷"
        : exam.statusText === "未开始"
          ? "未开始"
          : exam.statusText === "已结束"
            ? "已结束"
            : "进入考试";

  return `
    <article class="stat-card">
      <span class="tag">${escapeHtml(exam.statusText)}</span>
      <h4>${escapeHtml(exam.name)}</h4>
      <p class="muted">试卷：${escapeHtml(exam.paperName)}</p>
      <p class="muted">时长：${exam.durationMinutes} 分钟</p>
      <p class="muted">时间：${formatDate(exam.startTime)} - ${formatDate(exam.endTime)}</p>
      <p class="muted">我的状态：${submission?.status || "未作答"}</p>
      <button class="primary-btn wide" type="button" data-start-exam="${exam.id}" ${disabled ? "disabled" : ""}>${buttonText}</button>
    </article>
  `;
}

function renderRecords() {
  const filter = state.filters.record_status;
  const exams = indexBy(state.bootstrap.exams);
  const rows = state.bootstrap.submissions
    .filter((item) => filter === "all" || item.status === filter)
    .sort(
      (a, b) =>
        new Date(b.submittedAt || b.updatedAt || b.startedAt || 0).getTime() -
        new Date(a.submittedAt || a.updatedAt || a.startedAt || 0).getTime()
    );
  const summary = buildStudentRecordSummary(rows);

  // Pagination
  const pageSize = state.STUDENT_PAGE_SIZE;
  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  if (state.recordPage > totalPages) state.recordPage = totalPages;
  if (state.recordPage < 1) state.recordPage = 1;
  const pageStart = (state.recordPage - 1) * pageSize;
  const pageRows = rows.slice(pageStart, pageStart + pageSize);

  const recordPaginationHtml = rows.length > pageSize ? `
    <div class="pagination-bar">
      <button class="ghost-btn" type="button" data-record-page="first" ${state.recordPage <= 1 ? "disabled" : ""}>首页</button>
      <button class="ghost-btn" type="button" data-record-page="prev" ${state.recordPage <= 1 ? "disabled" : ""}>上一页</button>
      <span class="pagination-info">第 <strong>${state.recordPage}</strong> / ${totalPages} 页（共 ${rows.length} 条）</span>
      <button class="ghost-btn" type="button" data-record-page="next" ${state.recordPage >= totalPages ? "disabled" : ""}>下一页</button>
      <button class="ghost-btn" type="button" data-record-page="last" ${state.recordPage >= totalPages ? "disabled" : ""}>末页</button>
    </div>
  ` : "";

  return panel(`
    ${sectionTitle("考试记录", "查看成绩、异常提示和答卷详情。")}
    <div class="filter-bar">
      <div class="toolbar-form">
        <label>
          <span>答卷状态</span>
          <select data-filter-key="record_status">
            <option value="all" ${filter === "all" ? "selected" : ""}>全部</option>
            <option value="进行中" ${filter === "进行中" ? "selected" : ""}>进行中</option>
            <option value="待阅卷" ${filter === "待阅卷" ? "selected" : ""}>待阅卷</option>
            <option value="已完成" ${filter === "已完成" ? "selected" : ""}>已完成</option>
          </select>
        </label>
      </div>
    </div>
    <section class="record-highlight-grid">
      <article class="stat-card compact-card">
        <span>平均成绩</span>
        <strong>${summary.averageScoreText}</strong>
        <p class="muted">${summary.averageScoreMeta}</p>
      </article>
      <article class="stat-card compact-card">
        <span>最高分</span>
        <strong>${summary.bestScoreText}</strong>
        <p class="muted">${summary.bestScoreMeta}</p>
      </article>
      <article class="stat-card compact-card">
        <span>通过率</span>
        <strong>${summary.passRate}</strong>
        <p class="muted">${summary.passRateMeta}</p>
      </article>
    </section>
    <section class="stats-grid compact-stats">
      <article class="stat-card compact-card">
        <span>已完成场次</span>
        <strong>${summary.finishedCount}</strong>
        <p class="muted">当前筛选结果中的已完成答卷</p>
      </article>
      <article class="stat-card compact-card">
        <span>及格场次</span>
        <strong>${summary.passCount}</strong>
        <p class="muted">${summary.finishedCount ? `通过率 ${summary.passRate}` : "暂无已完成成绩"}</p>
      </article>
      <article class="stat-card compact-card">
        <span>最佳排名</span>
        <strong>${summary.bestRankText}</strong>
        <p class="muted">${summary.bestRankMeta}</p>
      </article>
      <article class="stat-card compact-card">
        <span>平均用时</span>
        <strong>${summary.averageDurationText}</strong>
        <p class="muted">${summary.averageDurationMeta}</p>
      </article>
    </section>
    ${dataTable(
      ["考试", "分数", "及格状态", "排名", "用时", "状态", "异常", "提交时间", "操作"],
      pageRows.length
        ? pageRows.map((item) => `
          <tr>
            <td>${escapeHtml(exams[item.examId]?.name || item.examName || item.examId)}</td>
            <td>
              <div class="cell-main">${item.finalScore ?? item.autoScore ?? "-"}</div>
              <div class="cell-sub">${item.totalScore ? `总分 ${item.totalScore} · 得分率 ${formatPercent(item.scoreRate)}` : `及格线 ${item.passScore ?? 0} 分`}</div>
            </td>
            <td>
              <div class="cell-main ${item.passStatus === "已及格" ? "ok" : item.passStatus === "未及格" ? "danger" : "warn"}">${item.passStatus || "待定"}</div>
              <div class="cell-sub">${renderPassStatusMeta(item)}</div>
            </td>
            <td>
              <div class="cell-main">${item.rank ? `${item.rank} / ${item.finishedCount || item.rank}` : "-"}</div>
              <div class="cell-sub">${renderRankMeta(item)}</div>
            </td>
            <td>
              <div class="cell-main">${item.usedTimeText || (item.usedMinutes ? `${item.usedMinutes} 分钟` : "-")}</div>
              <div class="cell-sub">${renderDurationMeta(item)}</div>
            </td>
            <td>${escapeHtml(item.status)}</td>
            <td class="${item.suspicious ? "danger" : "ok"}">${item.suspicious ? escapeHtml(item.suspiciousReasons?.join("、") || "疑似异常") : "正常"}</td>
            <td>${item.submittedAt ? formatDate(item.submittedAt) : "-"}</td>
            <td>${item.answerDetail?.length ? `<button class="ghost-btn" type="button" data-view-submission="${item.id}">查看详情</button>` : "-"}</td>
          </tr>`).join("")
        : null,
      "暂无记录"
    )}
    ${recordPaginationHtml}
  `);
}

function renderWrongBook() {
  const allRows = state.bootstrap.wrongBookEntries || [];
  const subjects = [...new Set(allRows.map((item) => item.subject).filter(Boolean))];
  const filter = state.filters.wrong_subject;
  const rows = allRows.filter((item) => filter === "all" || item.subject === filter);
  const masteredCount = allRows.filter((item) => item.removable).length;
  const retryCount = allRows.reduce((sum, item) => sum + Number(item.retryCount || 0), 0);

  // Pagination
  const pageSize = state.STUDENT_PAGE_SIZE;
  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  if (state.wrongBookPage > totalPages) state.wrongBookPage = totalPages;
  if (state.wrongBookPage < 1) state.wrongBookPage = 1;
  const pageStart = (state.wrongBookPage - 1) * pageSize;
  const pageRows = rows.slice(pageStart, pageStart + pageSize);

  const wrongBookPaginationHtml = rows.length > pageSize ? `
    <div class="pagination-bar">
      <button class="ghost-btn" type="button" data-wrongbook-page="first" ${state.wrongBookPage <= 1 ? "disabled" : ""}>首页</button>
      <button class="ghost-btn" type="button" data-wrongbook-page="prev" ${state.wrongBookPage <= 1 ? "disabled" : ""}>上一页</button>
      <span class="pagination-info">第 <strong>${state.wrongBookPage}</strong> / ${totalPages} 页（共 ${rows.length} 条）</span>
      <button class="ghost-btn" type="button" data-wrongbook-page="next" ${state.wrongBookPage >= totalPages ? "disabled" : ""}>下一页</button>
      <button class="ghost-btn" type="button" data-wrongbook-page="last" ${state.wrongBookPage >= totalPages ? "disabled" : ""}>末页</button>
    </div>
  ` : "";

  return panel(`
    ${sectionTitle("错题本", "按科目复盘错题，重做正确后可移出错题本。")}
    <section class="stats-grid compact-stats">
      <article class="stat-card compact-card">
        <span>错题总数</span>
        <strong>${allRows.length}</strong>
        <p class="muted">当前仍保留在错题本中的题目</p>
      </article>
      <article class="stat-card compact-card">
        <span>涉及科目</span>
        <strong>${subjects.length}</strong>
        <p class="muted">${filter === "all" ? "正在查看全部科目" : `正在查看 ${escapeHtml(filter)}`}</p>
      </article>
      <article class="stat-card compact-card">
        <span>已掌握</span>
        <strong>${masteredCount}</strong>
        <p class="muted">重做正确后可移出</p>
      </article>
      <article class="stat-card compact-card">
        <span>重做次数</span>
        <strong>${retryCount}</strong>
        <p class="muted">累计重做练习次数</p>
      </article>
    </section>
    <div class="filter-bar">
      <div class="toolbar-form">
        <label>
          <span>科目</span>
          <select data-filter-key="wrong_subject">
            <option value="all" ${filter === "all" ? "selected" : ""}>全部</option>
            ${subjects.map((item) => `<option value="${escapeAttr(item)}" ${filter === item ? "selected" : ""}>${escapeHtml(item)}</option>`).join("")}
          </select>
        </label>
      </div>
    </div>
    ${dataTable(
      ["科目", "题型", "题目", "最近答案", "参考答案", "状态", "操作"],
      pageRows.length
        ? pageRows.map((item) => {
            const statusText = item.statusText || (item.removable ? "已重做通过" : "待重做");
            const lastWrongText = item.lastWrongAt ? `最近出错 ${formatDate(item.lastWrongAt)}` : "暂无出错时间";
            return `
          <tr>
            <td>${escapeHtml(item.subject || "-")}</td>
            <td>${escapeHtml(typeLabel(item.type))}</td>
            <td><span class="cell-main">${escapeHtml(item.title)}</span><span class="cell-sub">${escapeHtml(item.knowledgePoint || "-")}</span></td>
            <td>${formatAnswerList(item.latestAnswer)}</td>
            <td>${formatAnswerList(item.expectedAnswer)}</td>
            <td>
              <span class="${item.removable ? "ok" : "warn"}">${escapeHtml(statusText)}</span>
              <span class="cell-sub">错误 ${item.wrongCount || 1} 次 · 重做 ${item.retryCount || 0} 次</span>
              <span class="cell-sub">${escapeHtml(lastWrongText)}</span>
            </td>
            <td>
              <div class="action-row">
                <button class="ghost-btn" type="button" data-retry-wrong-question="${escapeAttr(item.id)}">重做</button>
                <button class="accent-btn" type="button" data-remove-wrong-question="${escapeAttr(item.id)}" ${item.removable ? "" : "disabled"}>移出</button>
              </div>
            </td>
          </tr>`;
          }).join("")
        : null,
      "暂无错题记录"
    )}
    ${wrongBookPaginationHtml}
  `);
}

function renderProfile() {
  const userClass = state.bootstrap.classes.find((item) => item.id === state.currentUser.classId);
  const userDepartment = state.bootstrap.departments.find((item) => item.id === state.currentUser.departmentId);
  return `
    <section class="two-column">
      ${panel(`
        ${sectionTitle("基础信息")}
        <div class="mini-list">
          <div class="mini-item"><h4>姓名</h4><p>${escapeHtml(state.currentUser.name)}</p></div>
          <div class="mini-item"><h4>账号</h4><p>${escapeHtml(state.currentUser.username)}</p></div>
          <div class="mini-item"><h4>身份</h4><p>${roleLabel(state.currentUser.role)}</p></div>
          <div class="mini-item"><h4>班级 / 院系</h4><p>${escapeHtml(userClass?.name || userDepartment?.name || "-")}</p></div>
        </div>
      `)}
      ${panel(`
        ${sectionTitle("修改密码")}
        <form id="passwordForm" class="form-grid">
          <label><span>原密码</span><input name="oldPassword" type="password" required /></label>
          <label><span>新密码</span><input name="newPassword" type="password" minlength="6" required /></label>
          <button class="primary-btn" type="submit">提交修改</button>
        </form>
        <p id="passwordMessage" class="message"></p>
      `)}
    </section>
  `;
}

function renderStudentUnifiedPanel() {
  const tabs = [
    { key: "overview", label: "学习概览" },
    { key: "student-exams", label: "在线考试" },
    { key: "records", label: "考试记录" },
    { key: "wrongbook", label: "错题本" },
    { key: "profile", label: "个人信息" },
  ];
  const activeTab = state.studentTab || "overview";

  let content = "";
  if (activeTab === "overview") content = renderStudentOverview();
  else if (activeTab === "student-exams") content = renderStudentExams();
  else if (activeTab === "records") content = renderRecords();
  else if (activeTab === "wrongbook") content = renderWrongBook();
  else if (activeTab === "profile") content = renderProfile();

  return `
    <div class="panel">
      <div class="teacher-workspace-tabs">
        ${tabs.map((tab) => `
          <button class="teacher-tab-btn ${activeTab === tab.key ? "active" : ""}" type="button" data-student-tab="${tab.key}">${tab.label}</button>
        `).join("")}
      </div>
      <div class="teacher-workspace-content">
        ${content}
      </div>
    </div>
  `;
}

function switchStudentTab(tabKey) {
  state.studentTab = tabKey;
  state.activeMenu = tabKey;
  if (tabKey === "records") state.recordPage = 1;
  if (tabKey === "wrongbook") state.wrongBookPage = 1;

  // Update tab buttons
  const tabContainer = dashboardView.querySelector(".teacher-workspace-tabs");
  if (tabContainer) {
    tabContainer.querySelectorAll(".teacher-tab-btn").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.studentTab === tabKey);
    });
  }

  // Re-render only content area
  const contentArea = dashboardView.querySelector(".teacher-workspace-content");
  if (!contentArea) { renderDashboard(); return; }

  let content = "";
  if (tabKey === "overview") content = renderStudentOverview();
  else if (tabKey === "student-exams") content = renderStudentExams();
  else if (tabKey === "records") content = renderRecords();
  else if (tabKey === "wrongbook") content = renderWrongBook();
  else if (tabKey === "profile") content = renderProfile();
  contentArea.innerHTML = content;

  // Bind password form if profile tab is active
  const passwordForm = document.getElementById("passwordForm");
  if (passwordForm) passwordForm.onsubmit = updatePassword;

  // Update sidebar
  dashboardView.querySelectorAll(".menu-btn").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.menu === tabKey);
  });
}

function setupDelegatedEvents() {
  if (state._delegatedEventsSetup) return;
  state._delegatedEventsSetup = true;

  // Single delegated click handler for the entire dashboard
  dashboardView.addEventListener("click", function (e) {
    const target = e.target;
    let el;

    // Teacher tab switching
    if ((el = target.closest("[data-teacher-tab]"))) {
      switchTeacherTab(el.dataset.teacherTab);
      return;
    }
    // Question pagination
    if ((el = target.closest("[data-question-page]"))) {
      const action = el.dataset.questionPage;
      const data = state.questionPageData;
      const totalPages = data ? Math.max(1, Math.ceil(data.total / data.pageSize)) : 1;
      if (action === "first") state.questionPage = 1;
      else if (action === "prev") state.questionPage = Math.max(1, state.questionPage - 1);
      else if (action === "next") state.questionPage = Math.min(totalPages, state.questionPage + 1);
      else if (action === "last") state.questionPage = totalPages;
      state._questionPageCacheKey = "";
      refreshQuestionBankView();
      return;
    }
    // Student tab switching
    if ((el = target.closest("[data-student-tab]"))) {
      switchStudentTab(el.dataset.studentTab);
      return;
    }
    // Record pagination
    if ((el = target.closest("[data-record-page]"))) {
      const action = el.dataset.recordPage;
      const filter = state.filters.record_status;
      const total = state.bootstrap.submissions.filter((item) => filter === "all" || item.status === filter).length;
      const totalPages = Math.max(1, Math.ceil(total / state.STUDENT_PAGE_SIZE));
      if (action === "first") state.recordPage = 1;
      else if (action === "prev") state.recordPage = Math.max(1, state.recordPage - 1);
      else if (action === "next") state.recordPage = Math.min(totalPages, state.recordPage + 1);
      else if (action === "last") state.recordPage = totalPages;
      switchStudentTab("records");
      return;
    }
    // Wrong book pagination
    if ((el = target.closest("[data-wrongbook-page]"))) {
      const action = el.dataset.wrongbookPage;
      const filter = state.filters.wrong_subject;
      const total = (state.bootstrap.wrongBookEntries || []).filter((item) => filter === "all" || item.subject === filter).length;
      const totalPages = Math.max(1, Math.ceil(total / state.STUDENT_PAGE_SIZE));
      if (action === "first") state.wrongBookPage = 1;
      else if (action === "prev") state.wrongBookPage = Math.max(1, state.wrongBookPage - 1);
      else if (action === "next") state.wrongBookPage = Math.min(totalPages, state.wrongBookPage + 1);
      else if (action === "last") state.wrongBookPage = totalPages;
      switchStudentTab("wrongbook");
      return;
    }
    // Menu navigation
    if ((el = target.closest("[data-menu-jump]"))) {
      state.activeMenu = el.dataset.menuJump;
      renderDashboard();
      return;
    }
    if ((el = target.closest("[data-open-form]"))) {
      openCreateForm(el.dataset.openForm);
      return;
    }
    if ((el = target.closest("[data-delete]"))) {
      handleDelete(el.dataset.delete);
      return;
    }
    if ((el = target.closest("[data-edit-user]"))) {
      openEditUser(el.dataset.editUser);
      return;
    }
    if ((el = target.closest("[data-edit-entity]"))) {
      openEditEntity(el.dataset.editEntity);
      return;
    }
    if ((el = target.closest("[data-edit-question]"))) {
      openEditQuestion(el.dataset.editQuestion);
      return;
    }
    if ((el = target.closest("[data-import-questions]"))) {
      openBatchQuestionImport();
      return;
    }
    if ((el = target.closest("[data-import-users]"))) {
      openBatchUserImport(el.dataset.importUsers || "student");
      return;
    }
    if ((el = target.closest("[data-delete-selected-questions]"))) {
      deleteSelectedQuestions();
      return;
    }
    if ((el = target.closest("[data-clear-selected-questions]"))) {
      clearSelectedQuestions();
      return;
    }
    if ((el = target.closest("[data-edit-paper]"))) {
      openEditPaper(el.dataset.editPaper);
      return;
    }
    if ((el = target.closest("[data-edit-exam]"))) {
      openEditExam(el.dataset.editExam);
      return;
    }
    if ((el = target.closest("[data-reset-password]"))) {
      openResetPassword(el.dataset.resetPassword);
      return;
    }
    if ((el = target.closest("[data-preview-paper]"))) {
      openPaperPreview(el.dataset.previewPaper);
      return;
    }
    if ((el = target.closest("[data-auto-paper]"))) {
      openAutoPaperForm();
      return;
    }
    if ((el = target.closest("[data-monitor-exam]"))) {
      openExamMonitor(el.dataset.monitorExam);
      return;
    }
    if ((el = target.closest("[data-grade]"))) {
      openGrading(el.dataset.grade);
      return;
    }
    if ((el = target.closest("[data-view-submission]"))) {
      openSubmissionReview(el.dataset.viewSubmission);
      return;
    }
    if ((el = target.closest("[data-start-exam]"))) {
      startExam(el.dataset.startExam);
      return;
    }
    if ((el = target.closest("[data-retry-wrong-question]"))) {
      openWrongBookRetrySafe(el.dataset.retryWrongQuestion);
      return;
    }
    if ((el = target.closest("[data-remove-wrong-question]"))) {
      removeWrongBookEntry(el.dataset.removeWrongQuestion);
      return;
    }
    if ((el = target.closest("[data-export-users]"))) {
      exportUsersCsv();
      return;
    }
    if ((el = target.closest("[data-export-scores]"))) {
      exportTeacherScores();
      return;
    }
    if ((el = target.closest("[data-analysis-tab]"))) {
      state.analysisTab = el.dataset.analysisTab;
      renderDashboard();
      return;
    }
  });

  // Delegated input/change handler for filters
  dashboardView.addEventListener("input", function (e) {
    const el = e.target.closest("[data-filter-key]");
    if (el) handleFilterChange(e);
  });
  dashboardView.addEventListener("change", function (e) {
    const el = e.target.closest("[data-filter-key]");
    if (el) handleFilterChange(e);
    // Question checkbox selection
    let sel;
    if ((sel = e.target.closest("[data-select-question]"))) {
      toggleQuestionSelection(sel.dataset.selectQuestion, sel.checked);
    }
    if ((sel = e.target.closest("[data-select-all-questions]"))) {
      toggleAllQuestionSelection(sel.checked);
    }
  });
}

function bindPermanentEvents() {
  // These elements are part of the sidebar which is only rendered once per full renderDashboard()
  const refreshBtn = document.getElementById("refreshBtn");
  if (refreshBtn) refreshBtn.onclick = loadBootstrap;
  const logoutBtn = document.getElementById("logoutBtn");
  if (logoutBtn) logoutBtn.onclick = logout;
  const collapseBtn = document.getElementById("sidebarCollapseBtn");
  if (collapseBtn) collapseBtn.onclick = collapseSidebar;
  const expandBtn = document.getElementById("sidebarExpandBtn");
  if (expandBtn) expandBtn.onclick = expandSidebar;
  const mobileMenuBtn = document.getElementById("mobileMenuBtn");
  if (mobileMenuBtn) mobileMenuBtn.onclick = openMobileSidebar;
  const overlay = document.getElementById("sidebarOverlay");
  if (overlay) overlay.onclick = closeMobileSidebar;

  // Menu buttons - use onclick to avoid accumulating listeners
  dashboardView.querySelectorAll("[data-menu]").forEach((button) => {
    button.onclick = () => {
      state.activeMenu = button.dataset.menu;
      dashboardView.classList.remove("sidebar-open");
      renderDashboard();
    };
  });
}

// Kept as no-op for backward compatibility; events are now delegated
function bindContentEvents() {}

function handleFilterChange(event) {
  const key = event.target.dataset.filterKey;
  state.filters[key] = event.target.value;
  // Reset question page when question filters change
  if (key.startsWith("question_")) {
    state.questionPage = 1;
    state._questionPageCacheKey = "";
    if (state.teacherTab === "questions") {
      debouncedRefreshQuestionBank();
      return;
    }
  }
  // Student exam filter → partial refresh
  if (key === "student_exam_status") {
    if (state.studentTab === "student-exams") {
      debouncedRefreshStudentContent();
      return;
    }
  }
  // Student record filter → partial refresh
  if (key === "record_status") {
    state.recordPage = 1;
    if (state.studentTab === "records") {
      debouncedRefreshStudentContent();
      return;
    }
  }
  // Student wrong book filter → partial refresh
  if (key === "wrong_subject") {
    state.wrongBookPage = 1;
    if (state.studentTab === "wrongbook") {
      debouncedRefreshStudentContent();
      return;
    }
  }
  debouncedRenderDashboard();
}

const debouncedRefreshQuestionList = debounce(() => {
  const contentArea = dashboardView.querySelector(".teacher-workspace-content");
  if (contentArea && state.teacherTab === "questions") {
    contentArea.innerHTML = renderQuestions();
  } else {
    renderDashboard();
  }
}, 200);

const debouncedRefreshStudentContent = debounce(() => {
  const contentArea = dashboardView.querySelector(".teacher-workspace-content");
  if (contentArea && state.studentTab) {
    let content = "";
    if (state.studentTab === "overview") content = renderStudentOverview();
    else if (state.studentTab === "student-exams") content = renderStudentExams();
    else if (state.studentTab === "records") content = renderRecords();
    else if (state.studentTab === "wrongbook") content = renderWrongBook();
    else if (state.studentTab === "profile") content = renderProfile();
    contentArea.innerHTML = content;
    const pf = document.getElementById("passwordForm");
    if (pf) pf.onsubmit = updatePassword;
  } else {
    renderDashboard();
  }
}, 200);

function getReservedQuestionIds(excludePaperId = "") {
  return new Set(
    (state.bootstrap.papers || [])
      .filter((paper) => paper.id !== excludePaperId)
      .flatMap((paper) => paper.questionIds || [])
  );
}

function getSelectablePaperQuestions(excludePaperId = "") {
  const reserved = getReservedQuestionIds(excludePaperId);
  const currentPaper = excludePaperId ? state.bootstrap.papers.find((paper) => paper.id === excludePaperId) : null;
  const selectedIds = new Set(currentPaper?.questionIds || []);
  return state.bootstrap.questions.filter((question) => !reserved.has(question.id) || selectedIds.has(question.id));
}

function renderPaperQuestionOptions(questions, selectedIds = []) {
  const selectedSet = new Set(selectedIds);
  return questions
    .map((item) => `<option value="${escapeAttr(item.id)}" ${selectedSet.has(item.id) ? "selected" : ""}>${escapeHtml(typeLabel(item.type))} · ${escapeHtml(item.title)}</option>`)
    .join("");
}

function openCreateForm(type) {
  const classes = state.bootstrap.classes;
  const departments = state.bootstrap.departments;
  const questions = state.bootstrap.questions;
  const availableQuestions = type === "paper" ? getSelectablePaperQuestions() : questions;
  let content = "";

  if (type === "student" || type === "teacher") {
    content = `
      <h3>${type === "student" ? "新增学生" : "新增教师"}</h3>
      <form id="entityForm" class="form-grid">
        <input type="hidden" name="entity" value="users" />
        <input type="hidden" name="role" value="${type}" />
        <label><span>账号</span><input name="username" required /></label>
        <label><span>姓名</span><input name="name" required /></label>
        <label><span>默认密码</span><input name="password" value="123456" required /></label>
        ${
          type === "student"
            ? `<label><span>班级</span><select name="classId">${classes.map((item) => `<option value="${escapeAttr(item.id)}">${escapeHtml(item.name)}</option>`).join("")}</select></label>
               <label><span>专业</span><input name="major" required /></label>`
            : `<label><span>院系</span><select name="departmentId">${departments.map((item) => `<option value="${escapeAttr(item.id)}">${escapeHtml(item.name)}</option>`).join("")}</select></label>`
        }
        <button class="primary-btn" type="submit">保存</button>
      </form>
    `;
  } else if (type === "department") {
    content = `
      <h3>新增院系</h3>
      <form id="entityForm" class="form-grid">
        <input type="hidden" name="entity" value="departments" />
        <label><span>院系名称</span><input name="name" required /></label>
        <button class="primary-btn" type="submit">保存</button>
      </form>
    `;
  } else if (type === "class") {
    content = `
      <h3>新增班级</h3>
      <form id="entityForm" class="form-grid">
        <input type="hidden" name="entity" value="classes" />
        <label><span>班级名称</span><input name="name" required /></label>
        <label><span>专业</span><input name="major" required /></label>
        <label><span>院系</span><select name="departmentId">${departments.map((item) => `<option value="${escapeAttr(item.id)}">${escapeHtml(item.name)}</option>`).join("")}</select></label>
        <button class="primary-btn" type="submit">保存</button>
      </form>
    `;
  } else if (type === "question") {
    content = `
      <h3>新增题目</h3>
      <form id="entityForm" class="form-grid">
        <input type="hidden" name="entity" value="questions" />
        <label><span>题型</span>
          <select name="type">
            <option value="single">单选</option>
            <option value="multiple">多选</option>
            <option value="judge">判断</option>
            <option value="fill">填空</option>
            <option value="short">简答</option>
            <option value="coding">编程</option>
          </select>
        </label>
        <label><span>题目</span><textarea name="title" required></textarea></label>
        <label><span>科目</span><input name="subject" required /></label>
        <label><span>知识点</span><input name="knowledgePoint" required /></label>
        <label><span>难度</span><select name="difficulty"><option>易</option><option>中</option><option>难</option></select></label>
        <label><span>选项（选择题使用 | 分隔）</span><input name="options" /></label>
        <label><span>答案（多值用 | 分隔）</span><textarea name="answer" required></textarea></label>
        <label><span>分值</span><input name="score" type="number" min="1" value="5" required /></label>
        <button class="primary-btn" type="submit">保存</button>
      </form>
    `;
  } else if (type === "paper") {
    content = `
      <h3>新增试卷</h3>
      <form id="entityForm" class="form-grid">
        <input type="hidden" name="entity" value="papers" />
        <label><span>试卷名称</span><input name="name" required /></label>
        <label><span>考试时长（分钟）</span><input name="durationMinutes" type="number" min="1" value="30" required /></label>
        <label><span>及格线</span><input name="passScore" type="number" min="0" value="60" required /></label>
        <p class="toolbar-note">仅展示未被现有试卷占用的题目，当前可选 ${availableQuestions.length} 道。</p>
        <label>
          <span>题目</span>
          <select name="questionIds" multiple size="10">${renderPaperQuestionOptions(availableQuestions)}</select>
        </label>
        <button class="primary-btn" type="submit">保存</button>
      </form>
    `;
  } else if (type === "exam") {
    const availableQuestions = getSelectablePaperQuestions();
    const subjects = [...new Set(availableQuestions.map((item) => item.subject))];
    content = `
      <h3>发布考试</h3>
      <form id="entityForm" class="form-grid">
        <input type="hidden" name="entity" value="exams" />
        <label><span>考试名称</span><input name="name" required placeholder="例如：2025年春季期末考试" /></label>

        <fieldset style="border:none;padding:0;margin:0;">
          <legend style="font-size:13px;font-weight:600;color:var(--muted);margin-bottom:8px;">组卷设置</legend>
          <p class="toolbar-note" style="margin-bottom:8px;">系统将自动从题库中随机抽取未使用的题目组卷。当前可用题目：${availableQuestions.length} 道。</p>
          <div class="two-column" style="gap:10px;">
            <label>
              <span>限定科目</span>
              <select name="subject">
                <option value="all">全部科目</option>
                ${subjects.map((item) => `<option value="${escapeAttr(item)}">${escapeHtml(item)}</option>`).join("")}
              </select>
            </label>
            <label>
              <span>考试时长（分钟）</span>
              <input name="durationMinutes" type="number" min="1" value="60" required />
            </label>
            <label>
              <span>单选题数量</span>
              <input name="singleCount" type="number" min="0" value="5" />
            </label>
            <label>
              <span>多选题数量</span>
              <input name="multipleCount" type="number" min="0" value="5" />
            </label>
            <label>
              <span>判断题数量</span>
              <input name="judgeCount" type="number" min="0" value="5" />
            </label>
            <label>
              <span>填空题数量</span>
              <input name="fillCount" type="number" min="0" value="5" />
            </label>
            <label>
              <span>简答题数量</span>
              <input name="shortCount" type="number" min="0" value="2" />
            </label>
            <label>
              <span>编程题数量</span>
              <input name="codingCount" type="number" min="0" value="1" />
            </label>
          </div>
          <div class="two-column" style="gap:10px;margin-top:8px;">
            <label>
              <span>及格线</span>
              <input name="passScore" type="number" min="0" max="100" value="60" required />
            </label>
          </div>
        </fieldset>

        <label><span>开始时间</span><input name="startTime" type="datetime-local" required id="examStartTime" /></label>

        <label><span>目标班级</span>
          <div class="class-multi-select">
            <div class="class-multi-select-header">
              <label><input type="checkbox" id="classSelectAll" /> 全选</label>
              <span class="class-selected-count" id="classSelectedCount">已选 0 个班级</span>
            </div>
            <div class="class-multi-select-list">
              ${classes.map((item) => `
                <label class="class-multi-select-item">
                  <input type="checkbox" name="targetClassId" value="${escapeAttr(item.id)}" />
                  <span>${escapeHtml(item.name)}</span>
                </label>
              `).join("")}
            </div>
          </div>
        </label>

        <label><span>切屏上限</span><input name="antiCheatLimit" type="number" min="0" value="3" required /></label>
        <button class="primary-btn" type="submit">发布考试</button>
      </form>
    `;
  }

  openModal(content);
  document.getElementById("entityForm")?.addEventListener("submit", submitEntityForm);

  // Exam form: class multi-select + auto end time
  if (type === "exam") {
    const selectAll = document.getElementById("classSelectAll");
    const classCheckboxes = modalContent.querySelectorAll('input[name="targetClassId"]');
    const countDisplay = document.getElementById("classSelectedCount");

    function updateClassCount() {
      const checked = modalContent.querySelectorAll('input[name="targetClassId"]:checked').length;
      if (countDisplay) countDisplay.textContent = `已选 ${checked} 个班级`;
      if (selectAll) selectAll.checked = checked === classCheckboxes.length && classCheckboxes.length > 0;
    }

    if (selectAll) {
      selectAll.addEventListener("change", () => {
        classCheckboxes.forEach((cb) => { cb.checked = selectAll.checked; });
        updateClassCount();
      });
    }
    classCheckboxes.forEach((cb) => cb.addEventListener("change", updateClassCount));

    // Auto-calculate end time from start time + duration
    const startTimeInput = document.getElementById("examStartTime");
    const durationInput = modalContent.querySelector('[name="durationMinutes"]');
    function updateEndTime() {
      if (startTimeInput?.value && durationInput?.value) {
        const start = new Date(startTimeInput.value);
        const end = new Date(start.getTime() + Number(durationInput.value) * 60000);
        const year = end.getFullYear();
        const month = String(end.getMonth() + 1).padStart(2, "0");
        const day = String(end.getDate()).padStart(2, "0");
        const hours = String(end.getHours()).padStart(2, "0");
        const mins = String(end.getMinutes()).padStart(2, "0");
        const endTimeStr = `${year}-${month}-${day}T${hours}:${mins}`;
        // Store the calculated end time for form submission
        if (startTimeInput) startTimeInput.dataset.calculatedEndTime = endTimeStr;
      }
    }
    if (startTimeInput) startTimeInput.addEventListener("change", updateEndTime);
    if (durationInput) durationInput.addEventListener("change", updateEndTime);
  }
}

function openEditUser(userId) {
  const user = state.bootstrap.users.find((item) => item.id === userId);
  if (!user) return;
  const departments = state.bootstrap.departments;
  const classes = state.bootstrap.classes;
  openModal(`
    <h3>编辑用户</h3>
    <form id="editUserForm" class="form-grid">
      <input type="hidden" name="id" value="${escapeAttr(user.id)}" />
      <label><span>账号</span><input name="username" value="${escapeAttr(user.username)}" required /></label>
      <label><span>姓名</span><input name="name" value="${escapeAttr(user.name)}" required /></label>
      ${
        user.role === "student"
          ? `<label><span>班级</span><select name="classId">${classes.map((item) => `<option value="${escapeAttr(item.id)}" ${item.id === user.classId ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}</select></label>
             <label><span>专业</span><input name="major" value="${escapeAttr(user.major || "")}" required /></label>`
          : `<label><span>院系</span><select name="departmentId">${departments.map((item) => `<option value="${escapeAttr(item.id)}" ${item.id === user.departmentId ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}</select></label>`
      }
      <button class="primary-btn" type="submit">更新</button>
    </form>
  `);
  document.getElementById("editUserForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.target).entries());
    api("/api/entities/users", { method: "PUT", body: JSON.stringify(body) })
      .then(() => {
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
  });
}

function openEditEntity(value) {
  const [entity, id] = value.split(":");
  if (entity === "departments") {
    const record = state.bootstrap.departments.find((item) => item.id === id);
    if (!record) return;
    openModal(`
      <h3>编辑院系</h3>
      <form id="editEntityForm" class="form-grid">
        <input type="hidden" name="id" value="${escapeAttr(record.id)}" />
        <label><span>院系名称</span><input name="name" value="${escapeAttr(record.name)}" required /></label>
        <button class="primary-btn" type="submit">更新</button>
      </form>
    `);
  } else if (entity === "classes") {
    const record = state.bootstrap.classes.find((item) => item.id === id);
    if (!record) return;
    openModal(`
      <h3>编辑班级</h3>
      <form id="editEntityForm" class="form-grid">
        <input type="hidden" name="id" value="${escapeAttr(record.id)}" />
        <label><span>班级名称</span><input name="name" value="${escapeAttr(record.name)}" required /></label>
        <label><span>专业</span><input name="major" value="${escapeAttr(record.major)}" required /></label>
        <label><span>院系</span><select name="departmentId">${state.bootstrap.departments.map((item) => `<option value="${escapeAttr(item.id)}" ${item.id === record.departmentId ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}</select></label>
        <button class="primary-btn" type="submit">更新</button>
      </form>
    `);
  }

  document.getElementById("editEntityForm")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.target).entries());
    api(`/api/entities/${entity}`, { method: "PUT", body: JSON.stringify(body) })
      .then(() => {
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
  });
}

function openEditQuestion(questionId) {
  const question = state.bootstrap.questions.find((item) => item.id === questionId);
  if (!question) return;
  openModal(`
    <h3>编辑题目</h3>
    <form id="editQuestionForm" class="form-grid">
      <input type="hidden" name="id" value="${escapeAttr(question.id)}" />
      <label><span>题型</span>
        <select name="type">
          <option value="single" ${question.type === "single" ? "selected" : ""}>单选</option>
          <option value="multiple" ${question.type === "multiple" ? "selected" : ""}>多选</option>
          <option value="judge" ${question.type === "judge" ? "selected" : ""}>判断</option>
          <option value="fill" ${question.type === "fill" ? "selected" : ""}>填空</option>
          <option value="short" ${question.type === "short" ? "selected" : ""}>简答</option>
          <option value="coding" ${question.type === "coding" ? "selected" : ""}>编程</option>
        </select>
      </label>
      <label><span>题目</span><textarea name="title" required>${escapeHtml(question.title)}</textarea></label>
      <label><span>科目</span><input name="subject" value="${escapeAttr(question.subject)}" required /></label>
      <label><span>知识点</span><input name="knowledgePoint" value="${escapeAttr(question.knowledgePoint)}" required /></label>
      <label><span>难度</span>
        <select name="difficulty">
          <option ${question.difficulty === "易" ? "selected" : ""}>易</option>
          <option ${question.difficulty === "中" ? "selected" : ""}>中</option>
          <option ${question.difficulty === "难" ? "selected" : ""}>难</option>
        </select>
      </label>
      <label><span>选项（选择题使用 | 分隔）</span><input name="options" value="${escapeAttr((question.options || []).join(" | "))}" /></label>
      <label><span>答案（多值用 | 分隔）</span><textarea name="answer" required>${escapeHtml((question.answer || []).join(" | "))}</textarea></label>
      <label><span>分值</span><input name="score" type="number" min="1" value="${question.score}" required /></label>
      <button class="primary-btn" type="submit">保存修改</button>
    </form>
  `);

  document.getElementById("editQuestionForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.target).entries());
    body.options = body.options ? body.options.split("|").map((item) => item.trim()).filter(Boolean) : [];
    body.answer = body.answer.split("|").map((item) => item.trim()).filter(Boolean);
    body.score = Number(body.score);
    api("/api/entities/questions", { method: "PUT", body: JSON.stringify(body) })
      .then(() => {
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
  });
}

function toggleQuestionSelection(questionId, checked) {
  const next = new Set(state.selectedQuestionIds);
  if (checked) {
    next.add(questionId);
  } else {
    next.delete(questionId);
  }
  state.selectedQuestionIds = [...next];
  renderDashboard();
}

function toggleAllQuestionSelection(checked) {
  const rows = getVisibleQuestionRows();
  if (checked) {
    const next = new Set(state.selectedQuestionIds);
    rows.forEach((item) => next.add(item.id));
    state.selectedQuestionIds = [...next];
  } else {
    const visibleIds = new Set(rows.map((item) => item.id));
    state.selectedQuestionIds = state.selectedQuestionIds.filter((id) => !visibleIds.has(id));
  }
  renderDashboard();
}

function clearSelectedQuestions() {
  state.selectedQuestionIds = [];
  renderDashboard();
}

function openBatchQuestionImport() {
  openModal(`
    <h3>批量导入题目</h3>
    <p class="muted">每行一题，格式为：题型|科目|知识点|难度|分值|题目|选项|答案</p>
    <p class="muted">支持空行、表头，判断/填空/简答/编程题可留空“选项”列；选择题选项与答案可用 顿号 / 逗号 / 分号 分隔。</p>
    <div class="modal-toolbar">
      <button class="ghost-btn" type="button" id="downloadQuestionTemplateBtn">下载模板</button>
      <label class="ghost-btn file-trigger" for="questionImportFile">选择文件</label>
    </div>
    <form id="batchQuestionForm" class="form-grid">
      <input id="questionImportFile" name="file" type="file" accept=".txt,.csv,text/plain,text/csv" class="hidden" />
      <div id="questionImportPreview" class="import-preview"></div>
      <label><span>批量题目文本</span><textarea name="payload" placeholder="题型|科目|知识点|难度|分值|题目|选项|答案&#10;single|Java Web|HTTP|易|5|HTTP默认端口是？|21、80、443、3306|80&#10;judge|数据库|事务|中|5|事务的ACID中 C 代表一致性。|正确、错误|正确&#10;fill|前端开发|JavaScript|中|10|请填写浏览器本地持久化存储 API 名称。||localStorage" required></textarea></label>
      <button class="primary-btn" type="submit">开始导入</button>
    </form>
  `);

  const batchQuestionForm = document.getElementById("batchQuestionForm");
  const payloadInput = batchQuestionForm.querySelector('[name="payload"]');
  const fileInput = document.getElementById("questionImportFile");
  const preview = document.getElementById("questionImportPreview");
  const refreshPreview = () => {
    preview.innerHTML = renderQuestionImportPreview(parseQuestionImportPayload(payloadInput.value));
  };

  document.getElementById("downloadQuestionTemplateBtn").addEventListener("click", downloadQuestionImportTemplate);
  payloadInput.addEventListener("input", refreshPreview);
  fileInput.addEventListener("change", async () => {
    const [file] = fileInput.files || [];
    if (!file) return;
    payloadInput.value = await file.text();
    refreshPreview();
  });
  refreshPreview();

  batchQuestionForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const parsed = parseQuestionImportPayload(payloadInput.value);

      if (!parsed.records.length) {
        alert(parsed.errors.length ? parsed.errors.map((item) => `第 ${item.lineNumber} 行：${item.message}`).join("\n") : "没有可导入的题目");
        return;
      }

      const result = await api("/api/questions/batch-import", {
        method: "POST",
        body: JSON.stringify({ records: parsed.records }),
      });

      state.selectedQuestionIds = [];
      closeModal({ preserveExamSession: true });
      await loadBootstrap();

      const messages = [`已导入 ${result.importedCount} 道题目`];
      const errorLines = [
        ...parsed.errors.map((item) => `第 ${item.lineNumber} 行：${item.message}`),
        ...(result.errors || []).map((item) => `${item.title || "题目"}：${item.message}`),
      ];
      if (errorLines.length) {
        messages.push(`未导入 ${errorLines.length} 条：\n${errorLines.slice(0, 8).join("\n")}${errorLines.length > 8 ? "\n..." : ""}`);
      }
      alert(messages.join("\n\n"));
    } catch (error) {
      alert(getFriendlyErrorMessage(error) || "批量导入失败");
    }
  });
}

async function deleteSelectedQuestions() {
  if (!state.selectedQuestionIds.length) return;
  const confirmed = await openConfirmDialog(`确认删除已选的 ${state.selectedQuestionIds.length} 道题目吗？此操作不可撤销。`);
  if (!confirmed) {
    return;
  }
  const result = await api("/api/questions/batch-delete", {
    method: "POST",
    body: JSON.stringify({ ids: state.selectedQuestionIds }),
  });
  state.selectedQuestionIds = (result.errors || []).map((item) => item.id).filter(Boolean);
  await loadBootstrap();
  const messages = [`已删除 ${result.deletedCount} 道题目`];
  if (result.errors?.length) {
    messages.push(
      `未删除 ${result.errors.length} 道：\n${result.errors
        .slice(0, 8)
        .map((item) => `${item.title || item.id}：${item.message}`)
        .join("\n")}${result.errors.length > 8 ? "\n..." : ""}`
    );
  }
  alert(messages.join("\n\n"));
}

function openBatchUserImport(role) {
  const isStudent = role === "student";
  const title = isStudent ? "批量导入学生" : "批量导入教师";
  const templateText = isStudent ? "学号|姓名|班级|专业|密码" : "账号|姓名|院系|密码";
  const placeholder = isStudent
    ? "2023003|王五|2310|软件工程|123456\n2023004|赵六|class-1|软件工程|123456"
    : "t1001|李老师|计算机学院|123456\nt1002|周老师|dept-1|123456";

  openModal(`
    <h3>${title}</h3>
    <p class="muted">支持上传 Excel .xlsx、CSV/TXT，或直接粘贴从表格复制的内容。${isStudent ? "学生列：" : "教师列："}${templateText}</p>
    <div class="modal-toolbar">
      <button class="ghost-btn" type="button" id="downloadUserTemplateBtn">下载模板</button>
    </div>
    <form id="batchUserForm" class="form-grid">
      <label>
        <span>导入文件</span>
        <input name="file" type="file" accept=".xlsx,.csv,.txt" />
      </label>
      <label>
        <span>${isStudent ? "学生名单" : "教师名单"}</span>
        <textarea name="payload" placeholder="${escapeAttr(placeholder)}"></textarea>
      </label>
      <div id="userImportPreview" class="import-preview"></div>
      <button class="primary-btn" type="submit">开始导入</button>
    </form>
  `);

  const form = document.getElementById("batchUserForm");
  const fileInput = form.querySelector('[name="file"]');
  const payloadInput = form.querySelector('[name="payload"]');
  const preview = document.getElementById("userImportPreview");
  let fileRows = [];
  let fileErrors = [];

  const refreshPreview = () => {
    const parsed = parseUserImportRows(role, fileRows.length ? fileRows : rowsFromDelimitedText(payloadInput.value));
    preview.innerHTML = renderUserImportPreview(role, {
      records: parsed.records,
      errors: [...fileErrors, ...parsed.errors],
    });
    repairRenderedText(preview);
  };

  document.getElementById("downloadUserTemplateBtn").addEventListener("click", () => downloadUserImportTemplate(role));
  payloadInput.addEventListener("input", () => {
    if (payloadInput.value.trim()) {
      fileRows = [];
      fileErrors = [];
      fileInput.value = "";
    }
    refreshPreview();
  });
  fileInput.addEventListener("change", async () => {
    fileRows = [];
    fileErrors = [];
    try {
      const file = fileInput.files?.[0];
      if (file) {
        fileRows = await readImportFileRows(file);
        payloadInput.value = rowsToPreviewText(fileRows);
      }
    } catch (error) {
      fileErrors = [{ lineNumber: 0, message: getFriendlyErrorMessage(error) || "文件解析失败" }];
    }
    refreshPreview();
  });
  refreshPreview();

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const parsed = parseUserImportRows(role, fileRows.length ? fileRows : rowsFromDelimitedText(payloadInput.value));
      const allErrors = [...fileErrors, ...parsed.errors];
      if (!parsed.records.length) {
        alert(allErrors.length ? allErrors.map(formatImportError).join("\n") : `没有可导入的${isStudent ? "学生" : "教师"}`);
        return;
      }
      const result = await api("/api/users/batch-import", {
        method: "POST",
        body: JSON.stringify({ records: parsed.records }),
      });
      closeModal({ preserveExamSession: true });
      await loadBootstrap();
      const messages = [`已导入 ${result.importedCount} 名${isStudent ? "学生" : "教师"}`];
      const errorLines = [
        ...allErrors.map(formatImportError),
        ...(result.errors || []).map((item) => `${item.title || (isStudent ? "学生" : "教师")}：${item.message}`),
      ];
      if (errorLines.length) {
        messages.push(`未导入 ${errorLines.length} 条：\n${errorLines.slice(0, 8).join("\n")}${errorLines.length > 8 ? "\n..." : ""}`);
      }
      alert(messages.join("\n\n"));
    } catch (error) {
      alert(getFriendlyErrorMessage(error) || "批量导入失败");
    }
  });
}

function openEditPaper(paperId) {
  const paper = state.bootstrap.papers.find((item) => item.id === paperId);
  if (!paper) return;
  const availableQuestions = getSelectablePaperQuestions(paper.id);
  openModal(`
    <h3>编辑试卷</h3>
    <form id="editPaperForm" class="form-grid">
      <input type="hidden" name="id" value="${escapeAttr(paper.id)}" />
      <label><span>试卷名称</span><input name="name" value="${escapeAttr(paper.name)}" required /></label>
      <label><span>考试时长（分钟）</span><input name="durationMinutes" type="number" min="1" value="${paper.durationMinutes}" required /></label>
      <label><span>及格线</span><input name="passScore" type="number" min="0" value="${paper.passScore}" required /></label>
      <p class="toolbar-note">可选题目已自动排除其他试卷已占用题目；当前可选 ${availableQuestions.length} 道。</p>
      <label>
        <span>题目</span>
        <select name="questionIds" multiple size="10">
          ${renderPaperQuestionOptions(availableQuestions, paper.questionIds)}
        </select>
      </label>
      <button class="primary-btn" type="submit">保存修改</button>
    </form>
  `);

  document.getElementById("editPaperForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = event.target;
    const selected = [...form.querySelector('[name="questionIds"]').selectedOptions].map((option) => option.value);
    if (!selected.length) {
      alert("试卷至少选择一道题");
      return;
    }
    const body = Object.fromEntries(new FormData(form).entries());
    body.questionIds = selected;
    body.durationMinutes = Number(body.durationMinutes);
    body.passScore = Number(body.passScore);
    body.totalScore = selected
      .map((id) => state.bootstrap.questions.find((item) => item.id === id))
      .filter(Boolean)
      .reduce((sum, item) => sum + Number(item.score), 0);
    api("/api/entities/papers", { method: "PUT", body: JSON.stringify(body) })
      .then(() => {
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
  });
}

function openEditExam(examId) {
  const exam = state.bootstrap.exams.find((item) => item.id === examId);
  if (!exam) return;
  openModal(`
    <h3>编辑考试</h3>
    <form id="editExamForm" class="form-grid">
      <input type="hidden" name="id" value="${escapeAttr(exam.id)}" />
      <label><span>考试名称</span><input name="name" value="${escapeAttr(exam.name)}" required /></label>
      <label><span>开始时间</span><input name="startTime" type="datetime-local" value="${toDateTimeLocalValue(exam.startTime)}" required /></label>
      <label><span>结束时间</span><input name="endTime" type="datetime-local" value="${toDateTimeLocalValue(exam.endTime)}" required /></label>
      <label><span>目标班级</span>
        <div class="class-multi-select">
          <div class="class-multi-select-header">
            <label><input type="checkbox" id="editClassSelectAll" /> 全选</label>
            <span class="class-selected-count" id="editClassSelectedCount">已选 ${exam.targetClassIds.length} 个班级</span>
          </div>
          <div class="class-multi-select-list">
            ${state.bootstrap.classes
              .map(
                (item) =>
                  `<label class="class-multi-select-item">
                    <input type="checkbox" name="targetClassId" value="${escapeAttr(item.id)}" ${exam.targetClassIds.includes(item.id) ? "checked" : ""} />
                    <span>${escapeHtml(item.name)}</span>
                  </label>`
              )
              .join("")}
          </div>
        </div>
      </label>
      <label><span>切屏上限</span><input name="antiCheatLimit" type="number" min="0" value="${exam.antiCheatLimit}" required /></label>
      <button class="primary-btn" type="submit">保存修改</button>
    </form>
  `);

  // Multi-select events
  const selectAll = document.getElementById("editClassSelectAll");
  const classCheckboxes = modalContent.querySelectorAll('input[name="targetClassId"]');
  const countDisplay = document.getElementById("editClassSelectedCount");

  function updateEditClassCount() {
    const checked = modalContent.querySelectorAll('input[name="targetClassId"]:checked').length;
    if (countDisplay) countDisplay.textContent = `已选 ${checked} 个班级`;
    if (selectAll) selectAll.checked = checked === classCheckboxes.length && classCheckboxes.length > 0;
  }

  if (selectAll) {
    selectAll.addEventListener("change", () => {
      classCheckboxes.forEach((cb) => { cb.checked = selectAll.checked; });
      updateEditClassCount();
    });
  }
  classCheckboxes.forEach((cb) => cb.addEventListener("change", updateEditClassCount));

  document.getElementById("editExamForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.target).entries());
    const selectedClassIds = [...modalContent.querySelectorAll('input[name="targetClassId"]:checked')].map((cb) => cb.value);
    if (!selectedClassIds.length) {
      alert("请至少选择一个目标班级");
      return;
    }
    body.targetClassIds = selectedClassIds;
    body.antiCheatLimit = Number(body.antiCheatLimit);
    body.startTime = new Date(body.startTime).toISOString();
    body.endTime = new Date(body.endTime).toISOString();
    body.published = true;
    api("/api/entities/exams", { method: "PUT", body: JSON.stringify(body) })
      .then(() => {
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
  });
}

function openResetPassword(userId) {
  const user = state.bootstrap.users.find((item) => item.id === userId);
  if (!user) return;
  openModal(`
    <h3>重置密码</h3>
    <form id="resetPasswordForm" class="form-grid">
      <input type="hidden" name="userId" value="${escapeAttr(user.id)}" />
      <p>用户：${escapeHtml(user.name)}（${escapeHtml(user.username)}）</p>
      <label><span>新密码</span><input name="newPassword" value="123456" minlength="6" required /></label>
      <button class="primary-btn" type="submit">确认重置</button>
    </form>
  `);
  document.getElementById("resetPasswordForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.target).entries());
    api("/api/admin/reset-password", { method: "POST", body: JSON.stringify(body) })
      .then(() => {
        alert("密码已重置");
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
  });
}

function openPaperPreview(paperId) {
  const paper = state.bootstrap.papers.find((item) => item.id === paperId);
  if (!paper) return;
  const questions = paper.questionIds
    .map((id) => state.bootstrap.questions.find((item) => item.id === id))
    .filter(Boolean);
  openModal(`
    <h3>${escapeHtml(paper.name)}</h3>
    <p class="muted">题量 ${paper.questionIds.length} · 总分 ${paper.totalScore} · 时长 ${paper.durationMinutes} 分钟</p>
    <div class="mini-list">
      ${questions
        .map(
          (item, index) => `
        <div class="mini-item">
          <h4>${index + 1}. ${escapeHtml(item.title)}</h4>
          <p>${escapeHtml(typeLabel(item.type))} · ${escapeHtml(item.subject)} / ${escapeHtml(item.knowledgePoint)} · ${item.score} 分</p>
        </div>`
        )
        .join("")}
    </div>
  `);
}

function openAutoPaperForm() {
  const availableQuestions = getSelectablePaperQuestions();
  const subjects = [...new Set(availableQuestions.map((item) => item.subject))];
  openModal(`
    <h3>自动组卷</h3>
    <form id="autoPaperForm" class="form-grid">
      <label><span>试卷名称</span><input name="name" placeholder="例如：Java Web 自动组卷 A 卷" required /></label>
      <label>
        <span>限定科目</span>
        <select name="subject">
          <option value="all">全部科目</option>
          ${subjects.map((item) => `<option value="${escapeAttr(item)}">${escapeHtml(item)}</option>`).join("")}
        </select>
      </label>
      <label><span>考试时长（分钟）</span><input name="durationMinutes" type="number" min="1" value="30" required /></label>
      <label><span>及格线</span><input name="passScore" type="number" min="0" max="100" value="60" required /></label>
      <p class="toolbar-note">自动组卷仅从未被现有试卷使用的题目中抽取，当前可用 ${availableQuestions.length} 道。</p>
      <div class="three-column">
        <label><span>单选题数量</span><input name="singleCount" type="number" min="0" value="5" /></label>
        <label><span>多选题数量</span><input name="multipleCount" type="number" min="0" value="5" /></label>
        <label><span>判断题数量</span><input name="judgeCount" type="number" min="0" value="5" /></label>
        <label><span>填空题数量</span><input name="fillCount" type="number" min="0" value="5" /></label>
        <label><span>简答题数量</span><input name="shortCount" type="number" min="0" value="2" /></label>
        <label><span>编程题数量</span><input name="codingCount" type="number" min="0" value="1" /></label>
      </div>
      <button class="primary-btn" type="submit">生成试卷</button>
    </form>
  `);

  document.getElementById("autoPaperForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.target).entries());
    const subject = form.subject;
    const pool = availableQuestions.filter((item) => subject === "all" || item.subject === subject);
    const typeMap = {
      single: Number(form.singleCount || 0),
      multiple: Number(form.multipleCount || 0),
      judge: Number(form.judgeCount || 0),
      fill: Number(form.fillCount || 0),
      short: Number(form.shortCount || 0),
      coding: Number(form.codingCount || 0),
    };

    const selectedIds = [];
    for (const [type, count] of Object.entries(typeMap)) {
      if (count <= 0) continue;
      const candidates = shuffle(pool.filter((item) => item.type === type));
      if (candidates.length < count) {
        alert(`${typeLabel(type)}题数量不足，当前只有 ${candidates.length} 道`);
        return;
      }
      selectedIds.push(...candidates.slice(0, count).map((item) => item.id));
    }

    if (!selectedIds.length) {
      alert("至少需要选择一种题型数量");
      return;
    }

    const selectedQuestions = selectedIds
      .map((id) => state.bootstrap.questions.find((item) => item.id === id))
      .filter(Boolean);
    const totalScore = selectedQuestions.reduce((sum, item) => sum + Number(item.score || 0), 0);
    if (totalScore !== 100) {
      alert(`试卷满分必须为 100 分，当前所选题目合计 ${totalScore} 分，请调整题型数量。`);
      return;
    }
    const record = {
      name: form.name,
      teacherId: state.currentUser.id,
      durationMinutes: Number(form.durationMinutes),
      passScore: Number(form.passScore),
      questionIds: selectedIds,
      totalScore,
    };

    api("/api/entities", {
      method: "POST",
      body: JSON.stringify({ entity: "papers", record }),
    })
      .then(() => {
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
  });
}

function openWrongBookRetrySafe(entryId) {
  const entry = (state.bootstrap.wrongBookEntries || []).find((item) => item.id === entryId);
  if (!entry || !entry.question) {
    alert("错题数据不完整，请刷新后重试");
    return;
  }

  const existing = entry.lastRetryAnswer?.length ? entry.lastRetryAnswer : entry.latestAnswer || [];
  openModal(`
    <div class="panel">
      <div class="section-title">
        <div>
          <h3>错题重做</h3>
          <p class="section-subtitle">${escapeHtml(entry.subject || "-")} / ${escapeHtml(entry.knowledgePoint || "-")} / ${escapeHtml(typeLabel(entry.type))}</p>
        </div>
      </div>
      <span class="tag">${escapeHtml(typeLabel(entry.type))}</span>
      <h3 style="margin-top: 12px;">${escapeHtml(entry.title)}</h3>
      <div style="margin-top: 16px;">${renderQuestionInput(entry.question, existing)}</div>
      <div class="action-row" style="margin-top: 16px;">
        <button class="primary-btn" id="submitWrongBookRetrySafeBtn" type="button">提交重做</button>
        <button class="ghost-btn" id="cancelWrongBookRetrySafeBtn" type="button">取消</button>
      </div>
      <p class="toolbar-note">参考答案：${formatAnswerList(entry.expectedAnswer)}</p>
    </div>
  `);

  document.getElementById("cancelWrongBookRetrySafeBtn").addEventListener("click", () => closeModal({ preserveExamSession: true }));
  document.getElementById("submitWrongBookRetrySafeBtn").addEventListener("click", async () => {
    try {
      const answer = collectQuestionAnswer(entry.question);
      const result = await api(`/api/wrongbook/${entryId}/retry`, {
        method: "POST",
        body: JSON.stringify({ answer }),
      });
      alert(result.entry?.removable ? "重做正确，可自行移出错题本。" : "本次重做未完全正确，已保留在错题本中。");
      closeModal({ preserveExamSession: true });
      await loadBootstrap();
    } catch (error) {
      alert(getFriendlyErrorMessage(error));
    }
  });
}

async function removeWrongBookEntry(entryId) {
  const confirmed = await openConfirmDialog("确认将这道题移出错题本吗？", "移出确认");
  if (!confirmed) {
    return;
  }
  api(`/api/wrongbook/${entryId}/remove`, {
    method: "POST",
    body: JSON.stringify({}),
  })
    .then(() => loadBootstrap())
    .catch((error) => alert(getFriendlyErrorMessage(error)));
}

function openExamMonitor(examId) {
  const exam = state.bootstrap.exams.find((item) => item.id === examId);
  if (!exam) return;
  const targetStudents = state.bootstrap.users.filter((item) => item.role === "student" && exam.targetClassIds.includes(item.classId));
  const rows = targetStudents.map((student) => {
    const submission = state.bootstrap.submissions.find((item) => item.examId === examId && item.studentId === student.id);
    return {
      student,
      submission,
      status: submission?.status || (exam.statusText === "未开始" ? "未开始" : "未作答"),
    };
  });
  const submittedCount = rows.filter((item) => item.submission).length;
  const suspiciousCount = rows.filter((item) => item.submission?.suspicious).length;

  openModal(`
    <h3>考试监控：${escapeHtml(exam.name)}</h3>
    <div class="stats-grid">
      <article class="stat-card"><span>目标考生</span><strong>${rows.length}</strong></article>
      <article class="stat-card"><span>已有答卷</span><strong>${submittedCount}</strong></article>
      <article class="stat-card"><span>异常答卷</span><strong>${suspiciousCount}</strong></article>
    </div>
    <div class="table-wrap" style="margin-top:16px;">
      <table>
        <thead><tr><th>学生</th><th>班级</th><th>状态</th><th>得分</th><th>切屏</th><th>异常</th><th>提交时间</th><th>操作</th></tr></thead>
        <tbody>
          ${rows
            .map((item) => {
              const studentClass = state.bootstrap.classes.find((row) => row.id === item.student.classId);
              return `
                <tr>
                  <td>${escapeHtml(item.student.name)} (${escapeHtml(item.student.username)})</td>
                  <td>${escapeHtml(studentClass?.name || "-")}</td>
                  <td>${escapeHtml(item.status)}</td>
                  <td>${item.submission ? item.submission.finalScore ?? item.submission.autoScore ?? "-" : "-"}</td>
                  <td>${item.submission?.switchCount ?? 0}</td>
                  <td class="${item.submission?.suspicious ? "danger" : "ok"}">${item.submission?.suspicious ? escapeHtml(item.submission.suspiciousReasons?.join("、") || "疑似异常") : "正常"}</td>
                  <td>${item.submission?.submittedAt ? formatDate(item.submission.submittedAt) : "-"}</td>
                  <td>${item.submission?.status === "进行中" ? `<button class="ghost-btn" type="button" data-extend-student="${exam.id}:${item.student.id}">延时</button>` : "-"}</td>
                </tr>
              `;
            })
            .join("")}
        </tbody>
      </table>
    </div>
  `);

  modalContent.querySelectorAll("[data-extend-student]").forEach((button) => {
    button.addEventListener("click", async () => {
      const [, studentId] = button.dataset.extendStudent.split(":");
      const extraMinutes = Number(prompt("请输入要延长的分钟数", "10"));
      if (!Number.isFinite(extraMinutes) || extraMinutes <= 0) {
        return;
      }
      try {
        await api(`/api/exams/${exam.id}/extend-student`, {
          method: "POST",
          body: JSON.stringify({ studentId, extraMinutes }),
        });
        await loadBootstrap();
        openExamMonitor(exam.id);
      } catch (error) {
        alert(getFriendlyErrorMessage(error));
      }
    });
  });
}

function openSubmissionReview(submissionId) {
  const submission = state.bootstrap.submissions.find((item) => item.id === submissionId);
  if (!submission) return;
  const scoreText = submission.finalScore ?? submission.autoScore ?? "-";
  const rankText = submission.rank ? `${submission.rank} / ${submission.finishedCount || submission.rank}` : "待排名";
  const durationText = submission.usedTimeText || (submission.usedMinutes ? `${submission.usedMinutes} 分钟` : "-");
  openModal(`
    <h3>${escapeHtml(submission.examName || "答卷详情")}</h3>
    <p class="muted">考生：${escapeHtml(submission.studentName || state.currentUser.name)} · 状态：${escapeHtml(submission.status)}</p>
    <section class="stats-grid compact-stats" style="margin-top:16px;">
      <article class="stat-card compact-card">
        <span>成绩</span>
        <strong>${scoreText}${submission.totalScore ? ` / ${submission.totalScore}` : ""}</strong>
        <p class="muted">及格线 ${submission.passScore ?? 0} 分</p>
      </article>
      <article class="stat-card compact-card">
        <span>及格状态</span>
        <strong class="${submission.passStatus === "已及格" ? "ok" : submission.passStatus === "未及格" ? "danger" : "warn"}">${submission.passStatus || "待定"}</strong>
        <p class="muted">${renderPassStatusMeta(submission)}</p>
      </article>
      <article class="stat-card compact-card">
        <span>排名</span>
        <strong>${rankText}</strong>
        <p class="muted">${renderRankMeta(submission)}</p>
      </article>
      <article class="stat-card compact-card">
        <span>答题用时</span>
        <strong>${durationText}</strong>
        <p class="muted">${renderDurationMeta(submission)}</p>
      </article>
    </section>
    <div class="table-wrap" style="margin-top:16px;">
      <table>
        <thead><tr><th>题型</th><th>题目</th><th>你的答案</th><th>参考答案</th><th>得分</th></tr></thead>
        <tbody>
          ${(submission.answerDetail || [])
            .map(
              (item) => `
            <tr>
              <td>${escapeHtml(typeLabel(item.type))}</td>
              <td>${escapeHtml(item.title)}</td>
              <td>${formatAnswerList(item.answer)}</td>
              <td>${formatAnswerList(item.expectedAnswer)}</td>
              <td>${item.score ?? 0} / ${item.fullScore ?? 0}</td>
            </tr>`
            )
            .join("")}
        </tbody>
      </table>
    </div>
  `);
}

function openGrading(submissionId) {
  const submission = state.bootstrap.submissions.find((item) => item.id === submissionId);
  if (!submission) return;
  const allQuestions = submission.answerDetail || [];
  const exams = indexBy(state.bootstrap.exams);
  const examName = exams[submission.examId]?.name || submission.examName || "考试";
  const studentName = submission.studentName || "未知学生";
  const scoreText = submission.finalScore ?? submission.autoScore ?? "-";
  const totalScore = submission.totalScore || "-";
  const state_grading = {
    submissionId,
    currentQuestionIndex: 0,
    scores: {},
  };

  // Pre-fill scores from existing data
  allQuestions.forEach((item, index) => {
    state_grading.scores[index] = item.score ?? 0;
  });

  state.gradingSession = state_grading;
  renderGradingModal();
}

function renderGradingModal() {
  const { submissionId, currentQuestionIndex, scores } = state.gradingSession;
  const submission = state.bootstrap.submissions.find((item) => item.id === submissionId);
  if (!submission) return;
  const allQuestions = submission.answerDetail || [];
  const exams = indexBy(state.bootstrap.exams);
  const examName = exams[submission.examId]?.name || submission.examName || "考试";
  const studentName = submission.studentName || "未知学生";
  const totalQuestions = allQuestions.length;
  const scoredCount = Object.keys(scores).filter((k) => scores[k] !== undefined && scores[k] !== null).length;
  const progressPercent = totalQuestions ? Math.round((scoredCount / totalQuestions) * 100) : 0;
  const isFirst = currentQuestionIndex === 0;
  const isLast = currentQuestionIndex === totalQuestions - 1;
  const current = allQuestions[currentQuestionIndex];
  const isSubjective = ["short", "coding", "fill"].includes(current.type);
  const currentScore = scores[currentQuestionIndex] ?? 0;

  openModal(`
    <div class="grading-shell">
      <aside class="grading-sidebar">
        <h3 class="grading-sidebar-title">阅卷评分</h3>
        <p class="grading-sidebar-meta">${escapeHtml(examName)}</p>
        <div class="grading-info-block">
          <div class="grading-info-row">
            <span>考生</span>
            <strong>${escapeHtml(studentName)}</strong>
          </div>
          <div class="grading-info-row">
            <span>当前得分</span>
            <strong>${Object.values(scores).reduce((sum, v) => sum + Number(v || 0), 0)}${submission.totalScore ? " / " + submission.totalScore : ""}</strong>
          </div>
          <div class="grading-info-row">
            <span>状态</span>
            <strong>${escapeHtml(submission.status)}</strong>
          </div>
        </div>
        <div class="grading-progress">
          <div class="grading-progress-label">
            <span>评分进度</span>
            <span>${scoredCount} / ${totalQuestions}</span>
          </div>
          <div class="grading-progress-bar">
            <div class="grading-progress-fill" style="width:${progressPercent}%"></div>
          </div>
        </div>
        <div class="grading-nav">
          ${allQuestions.map((item, index) => {
            const isScored = scores[index] !== undefined;
            const isAuto = !["short", "coding", "fill"].includes(item.type);
            return `<button class="${isScored ? (isAuto ? "auto-scored" : "scored") : ""} ${index === currentQuestionIndex ? "current" : ""}" type="button" data-grading-jump="${index}">${index + 1}</button>`;
          }).join("")}
        </div>
        <div class="grading-sidebar-actions">
          <button class="primary-btn" id="submitGradingBtn" type="button">提交评分</button>
          <button class="ghost-btn" id="closeGradingBtn" type="button">关闭</button>
        </div>
        <p id="gradingMessage" class="message"></p>
      </aside>
      <div class="grading-question-area">
        <div class="grading-question-header">
          <span class="grading-question-number">第 ${currentQuestionIndex + 1} 题</span>
          <span class="grading-question-type">${escapeHtml(typeLabel(current.type))}</span>
          ${!isSubjective ? '<span class="grading-auto-badge">自动评分</span>' : ""}
          <span class="grading-question-title">${escapeHtml(current.title)}</span>
        </div>
        <div class="grading-question-body">
          <div class="grading-answer-section">
            <div class="grading-answer-label">学生答案</div>
            <div class="grading-answer-box student-answer">
              ${current.answer?.length ? formatAnswerList(current.answer) : '<span class="no-answer">未作答</span>'}
            </div>
          </div>
          <div class="grading-answer-section">
            <div class="grading-answer-label">参考答案</div>
            <div class="grading-answer-box reference-answer">
              ${formatAnswerList(current.expectedAnswer)}
            </div>
          </div>
          <div class="grading-score-section">
            <div class="grading-score-row">
              <label>得分</label>
              <input class="grading-score-input" type="number" min="0" max="${current.fullScore}" value="${currentScore}" id="gradingScoreInput" ${!isSubjective ? "readonly" : ""} />
              <span class="grading-score-max">/ ${current.fullScore} 分</span>
              ${!isSubjective ? '<span class="grading-auto-badge">客观题自动评分</span>' : ""}
            </div>
          </div>
        </div>
        <div class="grading-nav-footer">
          <button class="grading-nav-btn prev" type="button" id="gradingPrevBtn" ${isFirst ? "disabled" : ""}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg>
            上一题
          </button>
          <span class="grading-nav-indicator">${currentQuestionIndex + 1} / ${totalQuestions}</span>
          <button class="grading-nav-btn next" type="button" id="gradingNextBtn" ${isLast ? "disabled" : ""}>
            下一题
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"/></svg>
          </button>
        </div>
      </div>
    </div>
  `);

  // Score input change
  const scoreInput = document.getElementById("gradingScoreInput");
  if (scoreInput && isSubjective) {
    scoreInput.addEventListener("input", () => {
      state.gradingSession.scores[currentQuestionIndex] = Number(scoreInput.value) || 0;
    });
    scoreInput.addEventListener("change", () => {
      const val = Math.min(Math.max(Number(scoreInput.value) || 0, 0), current.fullScore);
      scoreInput.value = val;
      state.gradingSession.scores[currentQuestionIndex] = val;
    });
  }

  // Question grid navigation
  modalContent.querySelectorAll("[data-grading-jump]").forEach((button) => {
    button.addEventListener("click", () => {
      persistGradingScore();
      state.gradingSession.currentQuestionIndex = Number(button.dataset.gradingJump);
      renderGradingModal();
    });
  });

  // Prev / Next
  document.getElementById("gradingPrevBtn")?.addEventListener("click", () => {
    if (state.gradingSession.currentQuestionIndex > 0) {
      persistGradingScore();
      state.gradingSession.currentQuestionIndex -= 1;
      renderGradingModal();
    }
  });
  document.getElementById("gradingNextBtn")?.addEventListener("click", () => {
    if (state.gradingSession.currentQuestionIndex < totalQuestions - 1) {
      persistGradingScore();
      state.gradingSession.currentQuestionIndex += 1;
      renderGradingModal();
    }
  });

  // Submit
  document.getElementById("submitGradingBtn").addEventListener("click", () => {
    persistGradingScore();
    const allScores = state.bootstrap.submissions.find((item) => item.id === submissionId)?.answerDetail || [];
    const scoreMap = {};
    allQuestions.forEach((item, index) => {
      scoreMap[item.questionId] = state.gradingSession.scores[index] ?? item.score ?? 0;
    });
    api("/api/submissions/manual-grade", {
      method: "POST",
      body: JSON.stringify({ submissionId, scores: scoreMap }),
    })
      .then(() => {
        state.gradingSession = null;
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
  });

  // Close
  document.getElementById("closeGradingBtn").addEventListener("click", () => {
    state.gradingSession = null;
    closeModal({ preserveExamSession: true });
  });
}

function persistGradingScore() {
  if (!state.gradingSession) return;
  const scoreInput = document.getElementById("gradingScoreInput");
  if (scoreInput) {
    state.gradingSession.scores[state.gradingSession.currentQuestionIndex] = Number(scoreInput.value) || 0;
  }
}

function submitEntityForm(event) {
  event.preventDefault();
  const formData = new FormData(event.target);
  const entity = formData.get("entity");
  const record = Object.fromEntries(formData.entries());
  delete record.entity;

  if (entity === "questions") {
    record.teacherId = state.currentUser.id;
    record.options = record.options ? record.options.split("|").map((item) => item.trim()).filter(Boolean) : [];
    record.answer = record.answer.split("|").map((item) => item.trim()).filter(Boolean);
    record.score = Number(record.score);
  }
  if (entity === "papers") {
    const selected = [...event.target.querySelector('[name="questionIds"]').selectedOptions].map((option) => option.value);
    if (!selected.length) {
      alert("试卷至少选择一道题");
      return;
    }
    const totalScore = selected
      .map((id) => state.bootstrap.questions.find((item) => item.id === id))
      .filter(Boolean)
      .reduce((sum, item) => sum + Number(item.score), 0);
    record.questionIds = selected;
    record.teacherId = state.currentUser.id;
    record.durationMinutes = Number(record.durationMinutes);
    record.passScore = Number(record.passScore);
    record.totalScore = totalScore;
  }
  if (entity === "exams") {
    // Collect multi-class IDs
    const classCheckboxes = event.target.querySelectorAll('input[name="targetClassId"]:checked');
    const targetClassIds = [...classCheckboxes].map((cb) => cb.value);
    if (!targetClassIds.length) {
      alert("请至少选择一个目标班级");
      return;
    }

    // Get form values
    const durationMinutes = Number(record.durationMinutes);
    const passScore = Number(record.passScore);
    const subject = record.subject;
    delete record.subject;
    delete record.targetClassId;
    delete record.singleCount;
    delete record.multipleCount;
    delete record.judgeCount;
    delete record.fillCount;
    delete record.shortCount;
    delete record.codingCount;

    // Auto-generate paper
    const availableQuestions = getSelectablePaperQuestions();
    const pool = availableQuestions.filter((item) => subject === "all" || item.subject === subject);
    const typeMap = {
      single: Number(event.target.querySelector('[name="singleCount"]')?.value || 0),
      multiple: Number(event.target.querySelector('[name="multipleCount"]')?.value || 0),
      judge: Number(event.target.querySelector('[name="judgeCount"]')?.value || 0),
      fill: Number(event.target.querySelector('[name="fillCount"]')?.value || 0),
      short: Number(event.target.querySelector('[name="shortCount"]')?.value || 0),
      coding: Number(event.target.querySelector('[name="codingCount"]')?.value || 0),
    };

    const selectedIds = [];
    for (const [qType, count] of Object.entries(typeMap)) {
      if (count <= 0) continue;
      const candidates = shuffle(pool.filter((item) => item.type === qType));
      if (candidates.length < count) {
        alert(`${typeLabel(qType)}题数量不足，当前只有 ${candidates.length} 道可用`);
        return;
      }
      selectedIds.push(...candidates.slice(0, count).map((item) => item.id));
    }

    if (!selectedIds.length) {
      alert("至少需要选择一种题型数量");
      return;
    }

    // Check for duplicates
    const uniqueIds = new Set(selectedIds);
    if (uniqueIds.size !== selectedIds.length) {
      alert("题目存在重复，请调整组卷设置");
      return;
    }

    const selectedQuestions = selectedIds
      .map((id) => state.bootstrap.questions.find((item) => item.id === id))
      .filter(Boolean);
    const totalScore = selectedQuestions.reduce((sum, item) => sum + Number(item.score || 0), 0);

    // Create paper first
    const paperRecord = {
      name: `${record.name} - 自动组卷`,
      teacherId: state.currentUser.id,
      durationMinutes,
      passScore,
      questionIds: selectedIds,
      totalScore,
    };

    api("/api/entities", {
      method: "POST",
      body: JSON.stringify({ entity: "papers", record: paperRecord }),
    })
      .then((data) => {
        const paperId = data.paper?.id || data.record?.id;
        if (!paperId) {
          alert("组卷失败：无法获取试卷ID");
          return;
        }

        // Calculate end time
        const startTime = new Date(record.startTime);
        const endTime = new Date(startTime.getTime() + durationMinutes * 60000);

        // Create exam
        const examRecord = {
          name: record.name,
          paperId,
          teacherId: state.currentUser.id,
          targetClassIds,
          startTime: startTime.toISOString(),
          endTime: endTime.toISOString(),
          antiCheatLimit: Number(record.antiCheatLimit),
          published: true,
        };

        return api("/api/entities", {
          method: "POST",
          body: JSON.stringify({ entity: "exams", record: examRecord }),
        });
      })
      .then(() => {
        closeModal({ preserveExamSession: true });
        return loadBootstrap();
      })
      .catch((error) => alert(getFriendlyErrorMessage(error)));
    return; // Early return since we handled it asynchronously
  }

  api("/api/entities", { method: "POST", body: JSON.stringify({ entity, record }) })
    .then(() => {
      closeModal({ preserveExamSession: true });
      return loadBootstrap();
    })
    .catch((error) => alert(getFriendlyErrorMessage(error)));
}

async function handleDelete(value) {
  const [entity, id] = value.split(":");
  const labels = {
    users: "用户",
    departments: "院系",
    classes: "班级",
    questions: "题目",
    papers: "试卷",
    exams: "考试",
  };
  const label = labels[entity] || "记录";
  const confirmed = await openConfirmDialog(`确认删除该${label}吗？此操作不可撤销。`);
  if (!confirmed) {
    return;
  }
  api(`/api/entities/${entity}/${id}`, { method: "DELETE" })
    .then(() => loadBootstrap())
    .catch((error) => alert(getFriendlyErrorMessage(error)));
}

function startExam(examId) {
  api(`/api/exams/${examId}/detail`)
    .then((exam) => {
      state.examSession = {
        exam,
        currentQuestionIndex: 0,
        answers: exam.session?.answers || [],
        switchCount: exam.session?.switchCount || 0,
        startedAt: exam.session?.startedAt,
        deadlineAt: exam.session?.deadlineAt,
      };
      renderExamModal();
      startExamTimers();
    })
    .catch((error) => alert(getFriendlyErrorMessage(error)));
}

function renderExamModal() {
  const { exam, currentQuestionIndex, answers, switchCount } = state.examSession;
  const current = exam.questions[currentQuestionIndex];
  const existing = answers.find((item) => item.questionId === current.id)?.answer || [];
  const totalQuestions = exam.questions.length;
  const answeredCount = answers.filter((item) => item.answer?.length > 0).length;
  const progressPercent = totalQuestions ? Math.round((answeredCount / totalQuestions) * 100) : 0;
  const isFirst = currentQuestionIndex === 0;
  const isLast = currentQuestionIndex === totalQuestions - 1;
  const remaining = Math.max(0, new Date(state.examSession.deadlineAt).getTime() - Date.now());
  const isUrgent = remaining > 0 && remaining < 5 * 60 * 1000;

  openModal(`
    <div class="exam-shell">
      <aside class="exam-sidebar">
        <h3 class="exam-sidebar-title">${escapeHtml(exam.name)}</h3>
        <p class="exam-sidebar-meta">${escapeHtml(exam.paper.name)}</p>
        <div class="exam-info-block">
          <div class="exam-info-row">
            <span>倒计时</span>
            <strong id="countdownText" class="exam-countdown${isUrgent ? " urgent" : ""}"></strong>
          </div>
          <div class="exam-info-row">
            <span>切屏</span>
            <strong class="${switchCount > exam.antiCheatLimit ? "exam-switch-danger" : switchCount > 0 ? "exam-switch-warn" : ""}">${switchCount} / ${exam.antiCheatLimit}</strong>
          </div>
        </div>
        <div class="exam-progress">
          <div class="exam-progress-label">
            <span>答题进度</span>
            <span>${answeredCount} / ${totalQuestions}</span>
          </div>
          <div class="exam-progress-bar">
            <div class="exam-progress-fill" style="width:${progressPercent}%"></div>
          </div>
        </div>
        <div class="question-nav">
          ${exam.questions
            .map((item, index) => {
              const answered = answers.find((entry) => entry.questionId === item.id);
              const hasAnswer = answered?.answer?.length > 0;
              return `<button class="${hasAnswer ? "answered" : ""} ${index === currentQuestionIndex ? "current" : ""}" type="button" data-jump-question="${index}">${index + 1}</button>`;
            })
            .join("")}
        </div>
        <div class="exam-sidebar-actions">
          <button class="ghost-btn" id="saveExamBtn" type="button">保存草稿</button>
          <button class="primary-btn" id="submitExamBtn" type="button">交卷</button>
        </div>
        <p id="examMessage" class="message"></p>
      </aside>
      <div class="exam-question-area">
        <div class="exam-question-header">
          <span class="exam-question-number">第 ${currentQuestionIndex + 1} 题</span>
          <span class="exam-question-type">${escapeHtml(typeLabel(current.type))}</span>
          <span class="exam-question-title">${escapeHtml(current.title)}</span>
        </div>
        <div class="exam-question-body">
          ${renderQuestionInput(current, existing)}
        </div>
        <div class="exam-nav-footer">
          <button class="exam-nav-btn prev" type="button" id="examPrevBtn" ${isFirst ? "disabled" : ""}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg>
            上一题
          </button>
          <span class="exam-nav-indicator">${currentQuestionIndex + 1} / ${totalQuestions}</span>
          <button class="exam-nav-btn next" type="button" id="examNextBtn" ${isLast ? "disabled" : ""}>
            下一题
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"/></svg>
          </button>
        </div>
      </div>
    </div>
  `);

  // Question grid navigation
  modalContent.querySelectorAll("[data-jump-question]").forEach((button) => {
    button.addEventListener("click", () => {
      persistCurrentAnswer();
      state.examSession.currentQuestionIndex = Number(button.dataset.jumpQuestion);
      renderExamModal();
    });
  });

  // Prev / Next buttons
  const prevBtn = document.getElementById("examPrevBtn");
  const nextBtn = document.getElementById("examNextBtn");
  if (prevBtn) {
    prevBtn.addEventListener("click", () => {
      if (state.examSession.currentQuestionIndex > 0) {
        persistCurrentAnswer();
        state.examSession.currentQuestionIndex -= 1;
        renderExamModal();
      }
    });
  }
  if (nextBtn) {
    nextBtn.addEventListener("click", () => {
      if (state.examSession.currentQuestionIndex < totalQuestions - 1) {
        persistCurrentAnswer();
        state.examSession.currentQuestionIndex += 1;
        renderExamModal();
      }
    });
  }

  // Save & Submit
  document.getElementById("saveExamBtn").addEventListener("click", () => saveExamDraft({ silent: false }));
  document.getElementById("submitExamBtn").addEventListener("click", submitExamPaper);

  updateCountdown();
}

function renderQuestionInput(question, existing) {
  const letters = ["A", "B", "C", "D", "E", "F", "G", "H"];

  if (question.type === "single" || question.type === "judge") {
    return `<div class="option-list">${question.options
      .map(
        (option, i) => `
      <label class="option-item">
        <input type="radio" name="answer" value="${escapeAttr(option)}" ${existing.includes(option) ? "checked" : ""}/>
        <span class="option-letter">${letters[i] || (i + 1)}</span>
        <span class="option-text">${escapeHtml(option)}</span>
      </label>`
      )
      .join("")}</div>`;
  }
  if (question.type === "multiple") {
    return `<div class="option-list">${question.options
      .map(
        (option, i) => `
      <label class="option-item">
        <input type="checkbox" name="answer" value="${escapeAttr(option)}" ${existing.includes(option) ? "checked" : ""}/>
        <span class="option-letter">${letters[i] || (i + 1)}</span>
        <span class="option-text">${escapeHtml(option)}</span>
      </label>`
      )
      .join("")}</div>`;
  }
  return `<textarea id="textAnswer" class="exam-textarea" placeholder="请输入答案">${escapeHtml(existing[0] || "")}</textarea>`;
}

function collectQuestionAnswer(question) {
  if (question.type === "single" || question.type === "judge") {
    const checked = modalContent.querySelector('input[name="answer"]:checked');
    return checked ? [checked.value] : [];
  }
  if (question.type === "multiple") {
    return [...modalContent.querySelectorAll('input[name="answer"]:checked')].map((item) => item.value);
  }
  const textValue = (document.getElementById("textAnswer")?.value || "").trim();
  return textValue ? [textValue] : [];
}

function persistCurrentAnswer() {
  if (!state.examSession) return;
  const { exam, currentQuestionIndex, answers } = state.examSession;
  const current = exam.questions[currentQuestionIndex];
  const answer = collectQuestionAnswer(current);
  const index = answers.findIndex((item) => item.questionId === current.id);
  const payload = { questionId: current.id, answer };
  if (index >= 0) {
    answers[index] = payload;
  } else {
    answers.push(payload);
  }
}

function saveExamDraft({ silent }) {
  if (!state.examSession) return Promise.resolve();
  persistCurrentAnswer();
  return api("/api/submissions/save", {
    method: "POST",
    body: JSON.stringify({
      examId: state.examSession.exam.id,
      answers: state.examSession.answers,
      switchCount: state.examSession.switchCount,
    }),
  })
    .then((data) => {
      if (data.submission?.deadlineAt) {
        state.examSession.deadlineAt = data.submission.deadlineAt;
      }
      if (!silent) {
        const message = document.getElementById("examMessage");
        if (message) {
          message.textContent = "答卷已保存";
          setTimeout(() => {
            if (message.textContent === "答卷已保存") {
              message.textContent = "";
            }
          }, 2000);
        }
      }
      return loadBootstrap();
    })
    .catch((error) => {
      if (!silent) {
        alert(getFriendlyErrorMessage(error));
      }
    });
}

function submitExamPaper() {
  if (!state.examSession) return;
  persistCurrentAnswer();
  api("/api/submissions/submit", {
    method: "POST",
    body: JSON.stringify({
      examId: state.examSession.exam.id,
      answers: state.examSession.answers,
      switchCount: state.examSession.switchCount,
    }),
  })
    .then((data) => {
      alert(`交卷成功，当前得分：${data.submission.finalScore ?? data.submission.autoScore ?? 0}`);
      state.examSession = null;
      stopExamTimers();
      closeModal({ preserveExamSession: true });
      state.activeMenu = "records";
      return loadBootstrap();
    })
    .catch((error) => alert(getFriendlyErrorMessage(error)));
}

function startExamTimers() {
  stopExamTimers();
  state.examTimer = setInterval(updateCountdown, 1000);
  state.examAutoSaveTimer = setInterval(() => saveExamDraft({ silent: true }), 30000);
}

function stopExamTimers() {
  if (state.examTimer) {
    clearInterval(state.examTimer);
    state.examTimer = null;
  }
  if (state.examAutoSaveTimer) {
    clearInterval(state.examAutoSaveTimer);
    state.examAutoSaveTimer = null;
  }
}

function updateCountdown() {
  const target = document.getElementById("countdownText");
  if (!state.examSession || !target) return;
  const diff = Math.max(0, new Date(state.examSession.deadlineAt).getTime() - Date.now());
  target.textContent = formatDuration(diff);
  if (diff > 0 && diff < 5 * 60 * 1000) {
    target.classList.add("urgent");
  } else {
    target.classList.remove("urgent");
  }
  if (diff === 0) {
    submitExamPaper();
  }
}

function handleVisibilityChange() {
  if (!state.examSession || document.visibilityState !== "hidden") return;
  state.examSession.switchCount += 1;
  saveExamDraft({ silent: true });
  if (state.examSession.switchCount > state.examSession.exam.antiCheatLimit) {
    alert("切屏次数超限，系统自动交卷。");
    submitExamPaper();
    return;
  }
  renderExamModal();
}

function updatePassword(event) {
  event.preventDefault();
  const message = document.getElementById("passwordMessage");
  const body = Object.fromEntries(new FormData(event.target).entries());
  api("/api/user/password", {
    method: "POST",
    body: JSON.stringify(body),
  })
    .then(() => {
      message.textContent = "密码修改成功";
      event.target.reset();
    })
    .catch((error) => {
      message.textContent = getFriendlyErrorMessage(error);
    });
}

function exportUsersCsv() {
  const rows = [["账号", "姓名", "角色", "班级", "专业", "院系"]];
  const classes = indexBy(state.bootstrap.classes);
  const departments = indexBy(state.bootstrap.departments);
  state.bootstrap.users.forEach((item) => {
    rows.push([
      item.username,
      item.name,
      roleLabel(item.role),
      classes[item.classId]?.name || "",
      item.major || "",
      departments[item.departmentId]?.name || "",
    ]);
  });
  downloadFile(`users-${timestampForFile()}.csv`, rows.map(toCsvRow).join("\n"), "text/csv;charset=utf-8");
}

function exportTeacherScores() {
  const exams = indexBy(state.bootstrap.exams);
  const rows = [["考试", "学生", "状态", "自动分", "最终分", "异常", "提交时间"]];
  state.bootstrap.submissions.forEach((item) => {
    rows.push([
      exams[item.examId]?.name || item.examName || item.examId,
      item.studentName,
      item.status,
      item.autoScore ?? "",
      item.finalScore ?? "",
      item.suspicious ? item.suspiciousReasons?.join("、") || "疑似异常" : "正常",
      item.submittedAt ? formatDate(item.submittedAt) : "",
    ]);
  });
  downloadFile(`scores-${timestampForFile()}.csv`, rows.map(toCsvRow).join("\n"), "text/csv;charset=utf-8");
}

function downloadFile(filename, content, mimeType) {
  const bom = mimeType && mimeType.includes("text/csv") ? "\uFEFF" : "";
  const blob = new Blob([bom + content], { type: mimeType });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

function openModal(html) {
  modal.classList.remove("hidden");
  modalContent.innerHTML = html;
  repairRenderedText(modalContent);
}

function openConfirmDialog(message, title = "删除确认") {
  return new Promise((resolve) => {
    const content = `
      <section class="confirm-dialog">
        <h3>${escapeHtml(title)}</h3>
        <p class="confirm-message">${escapeHtml(message)}</p>
        <div class="action-row confirm-actions">
          <button class="ghost-btn" type="button" data-confirm-cancel="1">取消</button>
          <button class="danger-btn" type="button" data-confirm-ok="1">确认删除</button>
        </div>
      </section>
    `;
    openModal(content);
    const done = (result) => {
      closeModal({ preserveExamSession: true });
      resolve(result);
    };
    modalContent.querySelector('[data-confirm-cancel="1"]')?.addEventListener("click", () => done(false));
    modalContent.querySelector('[data-confirm-ok="1"]')?.addEventListener("click", () => done(true));
  });
}

function closeModal({ preserveExamSession }) {
  modal.classList.add("hidden");
  modalContent.innerHTML = "";
  if (!preserveExamSession && state.examSession) {
    saveExamDraft({ silent: true });
    state.examSession = null;
    stopExamTimers();
  }
}

function renderMiniExamList(exams) {
  return `
    <div class="mini-list">
      ${exams
        .map(
          (item) => `
        <div class="mini-item">
          <h4>${escapeHtml(item.name)}</h4>
          <p>${escapeHtml(item.paperName || "")} · ${escapeHtml(item.statusText)} · ${formatDate(item.startTime)}</p>
        </div>`
        )
        .join("")}
    </div>
  `;
}

function renderMiniLogList(logs) {
  const users = indexBy(state.bootstrap.users);
  return `
    <div class="mini-list">
      ${logs
        .map(
          (item) => `
        <div class="mini-item">
          <h4>${escapeHtml(item.action)}</h4>
          <p>${escapeHtml(users[item.actorId]?.name || item.actorId)} · ${formatDate(item.time)}</p>
        </div>`
        )
        .join("")}
    </div>
  `;
}

function renderMiniSubmissionList(rows) {
  const exams = indexBy(state.bootstrap.exams);
  return `
    <div class="mini-list">
      ${rows
        .map(
          (item) => `
        <div class="mini-item">
          <h4>${escapeHtml(exams[item.examId]?.name || item.examName || item.examId)}</h4>
          <p>${escapeHtml(item.studentName || state.currentUser.name)} · ${escapeHtml(item.status)} · ${item.finalScore ?? item.autoScore ?? "-"}</p>
          <p>${renderMiniSubmissionMeta(item)}</p>
        </div>`
        )
        .join("")}
    </div>
  `;
}

function getVisibleQuestionRows() {
  const keyword = state.filters.question_keyword.trim().toLowerCase();
  const type = state.filters.question_type;
  const subject = state.filters.question_subject;
  return state.bootstrap.questions.filter((item) => {
    const passKeyword = !keyword || `${item.title} ${item.knowledgePoint}`.toLowerCase().includes(keyword);
    const passType = type === "all" || item.type === type;
    const passSubject = subject === "all" || item.subject === subject;
    return passKeyword && passType && passSubject;
  });
}

function buildQuestionPageCacheKey() {
  return `${state.questionPage}|${state.QUESTION_PAGE_SIZE}|${state.filters.question_keyword}|${state.filters.question_type}|${state.filters.question_subject}`;
}

function fetchQuestionPage() {
  const cacheKey = buildQuestionPageCacheKey();
  if (cacheKey === state._questionPageCacheKey && state.questionPageData) {
    return Promise.resolve(state.questionPageData);
  }
  const params = new URLSearchParams({
    page: state.questionPage,
    pageSize: state.QUESTION_PAGE_SIZE,
    keyword: state.filters.question_keyword,
    type: state.filters.question_type,
    subject: state.filters.question_subject,
  });
  return api(`/api/questions/page?${params}`).then((data) => {
    state.questionPageData = data;
    state._questionPageCacheKey = cacheKey;
    return data;
  });
}

function invalidateQuestionCache() {
  state._questionPageCacheKey = "";
  state.questionPageData = null;
  state.questionSubjects = null;
  state._cachedQuestionSubjects = null;
}

function fetchQuestionSubjects() {
  if (state.questionSubjects) return Promise.resolve(state.questionSubjects);
  return api("/api/questions/subjects").then((data) => {
    state.questionSubjects = data.subjects || [];
    return state.questionSubjects;
  });
}

function refreshQuestionBankView() {
  const contentArea = dashboardView.querySelector(".teacher-workspace-content");
  if (!contentArea || state.teacherTab !== "questions") return;
  contentArea.innerHTML = `<div class="empty-state">加载中…</div>`;
  const needsSubjects = !state.questionSubjects;
  const fetches = [fetchQuestionPage()];
  if (needsSubjects) fetches.push(fetchQuestionSubjects());
  Promise.all(fetches).then(() => {
    contentArea.innerHTML = renderQuestionsFromServer();
  }).catch((err) => {
    contentArea.innerHTML = `<div class="empty-state">${escapeHtml(getFriendlyErrorMessage(err))}</div>`;
  });
}

const debouncedRefreshQuestionBank = debounce(refreshQuestionBankView, 250);

function renderQuestionImportPreview(parsed) {
  if (!parsed.records.length && !parsed.errors.length) {
    return `<div class="empty-state">等待输入题目内容后自动预校验</div>`;
  }

  const typeCounts = parsed.records.reduce((acc, item) => {
    acc[item.type] = (acc[item.type] || 0) + 1;
    return acc;
  }, {});
  const previewTags = Object.entries(typeCounts)
    .map(([type, count]) => `<span class="tag">${typeLabel(type)} ${count}</span>`)
    .join("");

  return `
    <div class="preview-grid">
      <article class="mini-item">
        <h4>预检结果</h4>
        <p>可导入 ${parsed.records.length} 道，待修正 ${parsed.errors.length} 行。</p>
        <div class="selection-summary">${previewTags || `<span class="tag">暂无可导入题目</span>`}</div>
      </article>
      <article class="mini-item">
        <h4>格式提示</h4>
        <p>选择/判断题需要提供选项；填空、简答、编程题可以留空“选项”列。</p>
      </article>
    </div>
    ${
      parsed.errors.length
        ? `
          <div class="import-error-list">
            ${parsed.errors
              .slice(0, 6)
              .map((item) => `<p>第 ${item.lineNumber} 行：${item.message}</p>`)
              .join("")}
            ${parsed.errors.length > 6 ? `<p>还有 ${parsed.errors.length - 6} 行待处理...</p>` : ""}
          </div>
        `
        : `<p class="toolbar-note">未发现格式错误，可直接提交。</p>`
    }
  `;
}

function downloadQuestionImportTemplate() {
  const template = [
    "type|subject|knowledgePoint|difficulty|score|title|options|answer",
    "single|Java Web|HTTP|易|5|HTTP 默认端口是？|21,80,443,3306|80",
    "multiple|Java Web|Servlet|中|10|MVC 包含哪些层？|Controller,View,Model,Socket|Controller,View,Model",
    "judge|数据库|事务|中|5|ACID 中的 C 表示一致性。|正确,错误|正确",
    "fill|前端开发|JavaScript|中|10|请填写浏览器本地持久化存储 API 名称||localStorage",
    "short|软件工程|系统设计|难|20|说明两个防作弊手段及作用||防切屏监测；切屏次数统计",
  ].join("\n");
  downloadFile(`question-import-template-${timestampForFile()}.txt`, template, "text/plain;charset=utf-8");
}

function rowsFromDelimitedText(payload) {
  return String(payload || "")
    .split(/\r?\n/)
    .map((line) => parseDelimitedLine(line))
    .filter((row) => row.some((cell) => cell.trim()));
}

function parseDelimitedLine(line) {
  const text = String(line || "");
  if (text.includes("\t")) {
    return text.split("\t").map((item) => item.trim());
  }
  if (text.includes("|")) {
    return text.split("|").map((item) => item.trim());
  }
  return parseCsvLine(text).map((item) => item.trim());
}

function parseCsvLine(line) {
  const cells = [];
  let current = "";
  let quoted = false;
  const text = String(line || "");
  for (let index = 0; index < text.length; index++) {
    const char = text[index];
    if (char === '"') {
      if (quoted && text[index + 1] === '"') {
        current += '"';
        index++;
      } else {
        quoted = !quoted;
      }
    } else if (char === "," && !quoted) {
      cells.push(current);
      current = "";
    } else {
      current += char;
    }
  }
  cells.push(current);
  return cells;
}

function parseUserImportRows(role, rows) {
  const isStudent = role === "student";
  const classMap = new Map();
  const departmentMap = new Map();
  state.bootstrap.classes.forEach((item) => {
    classMap.set(normalizeImportKey(item.id), item.id);
    classMap.set(normalizeImportKey(item.name), item.id);
  });
  state.bootstrap.departments.forEach((item) => {
    departmentMap.set(normalizeImportKey(item.id), item.id);
    departmentMap.set(normalizeImportKey(item.name), item.id);
  });

  const records = [];
  const errors = [];
  rows.forEach((row, index) => {
    const lineNumber = index + 1;
    const cells = row.map((item) => String(item ?? "").trim());
    if (!cells.some(Boolean) || isImportHeaderRow(cells)) {
      return;
    }

    if (isStudent) {
      if (cells.length < 2) {
        errors.push({ lineNumber, message: "格式应为：学号|班级，或 学号|姓名|班级|专业|密码" });
        return;
      }
      const username = cells[0];
      const secondIsClass = classMap.has(normalizeImportKey(cells[1]));
      const classText = secondIsClass ? cells[1] : cells[2];
      const name = secondIsClass ? username : cells[1] || username;
      const classId = classMap.get(normalizeImportKey(classText));
      if (!classId) {
        errors.push({ lineNumber, message: `未找到班级：${classText}` });
        return;
      }
      const classRecord = state.bootstrap.classes.find((item) => item.id === classId);
      const major = (secondIsClass ? cells[2] : cells[3]) || classRecord?.major || "";
      const password = (secondIsClass ? cells[3] : cells[4]) || "123456";
      if (!username || !classText) {
        errors.push({ lineNumber, message: "学号、班级不能为空" });
        return;
      }
      records.push({ role: "student", username, name, classId, major, password });
      return;
    }

    if (cells.length < 2) {
      errors.push({ lineNumber, message: "格式应为：账号|院系，或 账号|姓名|院系|密码" });
      return;
    }
    const username = cells[0];
    const secondIsDepartment = departmentMap.has(normalizeImportKey(cells[1]));
    const name = secondIsDepartment ? username : cells[1] || username;
    const departmentText = secondIsDepartment ? cells[1] : cells[2];
    const password = (secondIsDepartment ? cells[2] : cells[3]) || "123456";
    const departmentId = departmentMap.get(normalizeImportKey(departmentText));
    if (!username || !departmentText) {
      errors.push({ lineNumber, message: "账号、院系不能为空" });
      return;
    }
    if (!departmentId) {
      errors.push({ lineNumber, message: `未找到院系：${departmentText}` });
      return;
    }
    records.push({ role: "teacher", username, name, departmentId, password: password || "123456" });
  });

  return { records, errors };
}

function normalizeImportKey(value) {
  return normalizeDisplayText(String(value ?? "")).trim().toLowerCase();
}

function isImportHeaderRow(cells) {
  const joined = normalizeImportKey(cells.join("|"));
  return (
    joined.includes("username") ||
    joined.includes("学号") ||
    joined.includes("账号") ||
    joined.includes("姓名")
  ) && (joined.includes("班级") || joined.includes("院系") || joined.includes("class") || joined.includes("department"));
}

function renderUserImportPreview(role, parsed) {
  const label = role === "student" ? "学生" : "教师";
  if (!parsed.records.length && !parsed.errors.length) {
    return `<div class="empty-state">等待输入${label}数据后自动预校验</div>`;
  }
  return `
    <div class="preview-grid">
      <article class="mini-item">
        <h4>预检结果</h4>
        <p>可导入 ${parsed.records.length} 名${label}，待修正 ${parsed.errors.length} 行。</p>
      </article>
      <article class="mini-item">
        <h4>导入说明</h4>
        <p>${role === "student" ? "班级列支持填写班级 ID 或班级名称。" : "院系列支持填写院系 ID 或院系名称。"}密码为空时默认使用 123456。</p>
      </article>
    </div>
    ${
      parsed.errors.length
        ? `<div class="import-error-list">${parsed.errors
            .slice(0, 6)
            .map((item) => `<p>${escapeHtml(formatImportError(item))}</p>`)
            .join("")}</div>`
        : `<p class="toolbar-note">未发现格式错误，可直接提交。</p>`
    }
  `;
}

function formatImportError(item) {
  return item.lineNumber ? `第 ${item.lineNumber} 行：${item.message}` : item.message;
}

function downloadUserImportTemplate(role) {
  const isStudent = role === "student";
  const template = isStudent
    ? [
        "学号|姓名|班级|专业|密码",
        "2023003|王五|2310|软件工程|123456",
        "2023004|赵六|class-1|软件工程|123456",
      ].join("\n")
    : [
        "账号|姓名|院系|密码",
        "t1001|李老师|dept-1|123456",
        "t1002|周老师|计算机学院|123456",
      ].join("\n");
  downloadFile(`${isStudent ? "student" : "teacher"}-import-template-${timestampForFile()}.csv`, template, "text/csv;charset=utf-8");
}

async function readImportFileRows(file) {
  const name = file.name.toLowerCase();
  if (name.endsWith(".xlsx")) {
    return readXlsxRows(await file.arrayBuffer());
  }
  return rowsFromDelimitedText(await file.text());
}

function rowsToPreviewText(rows) {
  return rows.slice(0, 50).map((row) => row.join("|")).join("\n");
}

async function readXlsxRows(arrayBuffer) {
  const entries = await unzipEntries(arrayBuffer);
  const sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml") || "");
  const sheetPath = getFirstWorksheetPath(entries) || "xl/worksheets/sheet1.xml";
  const sheetXml = entries.get(sheetPath);
  if (!sheetXml) {
    throw new Error("Excel 文件中未找到工作表");
  }
  return parseWorksheetRows(sheetXml, sharedStrings);
}

async function unzipEntries(arrayBuffer) {
  const bytes = new Uint8Array(arrayBuffer);
  const view = new DataView(arrayBuffer);
  const eocdOffset = findZipEndOfCentralDirectory(view);
  if (eocdOffset < 0) {
    throw new Error("不是有效的 .xlsx 文件");
  }
  const entryCount = view.getUint16(eocdOffset + 10, true);
  let offset = view.getUint32(eocdOffset + 16, true);
  const entries = new Map();
  const decoder = new TextDecoder("utf-8");

  for (let index = 0; index < entryCount; index++) {
    if (view.getUint32(offset, true) !== 0x02014b50) {
      break;
    }
    const method = view.getUint16(offset + 10, true);
    const compressedSize = view.getUint32(offset + 20, true);
    const nameLength = view.getUint16(offset + 28, true);
    const extraLength = view.getUint16(offset + 30, true);
    const commentLength = view.getUint16(offset + 32, true);
    const localOffset = view.getUint32(offset + 42, true);
    const name = decoder.decode(bytes.slice(offset + 46, offset + 46 + nameLength)).replace(/^\/+/, "");
    const localNameLength = view.getUint16(localOffset + 26, true);
    const localExtraLength = view.getUint16(localOffset + 28, true);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const compressed = bytes.slice(dataStart, dataStart + compressedSize);
    entries.set(name, await inflateZipEntry(compressed, method));
    offset += 46 + nameLength + extraLength + commentLength;
  }

  return entries;
}

function findZipEndOfCentralDirectory(view) {
  for (let offset = view.byteLength - 22; offset >= Math.max(0, view.byteLength - 66000); offset--) {
    if (view.getUint32(offset, true) === 0x06054b50) {
      return offset;
    }
  }
  return -1;
}

async function inflateZipEntry(bytes, method) {
  if (method === 0) {
    return new TextDecoder("utf-8").decode(bytes);
  }
  if (method !== 8 || typeof DecompressionStream === "undefined") {
    throw new Error("当前浏览器不支持解析该 Excel 压缩格式");
  }
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  return await new Response(stream).text();
}

function parseSharedStrings(xml) {
  if (!xml) {
    return [];
  }
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  return [...doc.getElementsByTagName("si")].map((item) =>
    [...item.getElementsByTagName("t")].map((node) => node.textContent || "").join("")
  );
}

function getFirstWorksheetPath(entries) {
  const workbookXml = entries.get("xl/workbook.xml");
  const relsXml = entries.get("xl/_rels/workbook.xml.rels");
  if (!workbookXml || !relsXml) {
    return [...entries.keys()].find((key) => /^xl\/worksheets\/sheet\d+\.xml$/i.test(key));
  }
  const workbook = new DOMParser().parseFromString(workbookXml, "application/xml");
  const firstSheet = workbook.getElementsByTagName("sheet")[0];
  const relId = firstSheet?.getAttribute("r:id") || firstSheet?.getAttribute("id");
  const rels = new DOMParser().parseFromString(relsXml, "application/xml");
  const relation = [...rels.getElementsByTagName("Relationship")].find((item) => item.getAttribute("Id") === relId);
  const target = relation?.getAttribute("Target");
  if (!target) {
    return null;
  }
  return target.startsWith("/") ? target.slice(1) : normalizeZipPath(`xl/${target}`);
}

function normalizeZipPath(path) {
  const parts = [];
  String(path || "")
    .split("/")
    .forEach((part) => {
      if (!part || part === ".") {
        return;
      }
      if (part === "..") {
        parts.pop();
        return;
      }
      parts.push(part);
    });
  return parts.join("/");
}

function parseWorksheetRows(xml, sharedStrings) {
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  return [...doc.getElementsByTagName("row")]
    .map((row) => {
      const cells = [];
      [...row.getElementsByTagName("c")].forEach((cell) => {
        const ref = cell.getAttribute("r") || "";
        const columnIndex = columnNameToIndex(ref.replace(/[0-9]/g, ""));
        cells[columnIndex] = readWorksheetCell(cell, sharedStrings);
      });
      return cells.map((item) => String(item ?? "").trim());
    })
    .filter((row) => row.some(Boolean));
}

function readWorksheetCell(cell, sharedStrings) {
  const type = cell.getAttribute("t");
  if (type === "inlineStr") {
    return [...cell.getElementsByTagName("t")].map((node) => node.textContent || "").join("");
  }
  const value = cell.getElementsByTagName("v")[0]?.textContent || "";
  return type === "s" ? sharedStrings[Number(value)] || "" : value;
}

function columnNameToIndex(name) {
  let index = 0;
  for (const char of name) {
    index = index * 26 + char.toUpperCase().charCodeAt(0) - 64;
  }
  return Math.max(index - 1, 0);
}

function parseStudentImportPayload(payload) {
  const classMap = new Map();
  state.bootstrap.classes.forEach((item) => {
    classMap.set(item.id, item.id);
    classMap.set(item.name, item.id);
  });

  const records = [];
  const errors = [];
  String(payload)
    .split(/\r?\n/)
    .forEach((rawLine, index) => {
      const line = rawLine.trim();
      if (!line || /^username\|/i.test(line)) {
        return;
      }
      const parts = line.split("|").map((item) => item.trim());
      if (parts.length < 4) {
        errors.push({ lineNumber: index + 1, message: "格式应为：学号|姓名|班级|专业|密码" });
        return;
      }
      const [username, name, classText, major, password = "123456"] = parts;
      const classId = classMap.get(classText);
      if (!classId) {
        errors.push({ lineNumber: index + 1, message: `未找到班级：${classText}` });
        return;
      }
      records.push({
        role: "student",
        username,
        name,
        classId,
        major,
        password,
      });
    });

  return { records, errors };
}

function renderStudentImportPreview(parsed) {
  if (!parsed.records.length && !parsed.errors.length) {
    return `<div class="empty-state">等待输入学生数据后自动预校验</div>`;
  }
  return `
    <div class="preview-grid">
      <article class="mini-item">
        <h4>预检结果</h4>
        <p>可导入 ${parsed.records.length} 名学生，待修正 ${parsed.errors.length} 行。</p>
      </article>
      <article class="mini-item">
        <h4>导入说明</h4>
        <p>班级列支持填写班级 ID 或班级名称；密码为空时默认使用 123456。</p>
      </article>
    </div>
    ${
      parsed.errors.length
        ? `<div class="import-error-list">${parsed.errors
            .slice(0, 6)
            .map((item) => `<p>第 ${item.lineNumber} 行：${item.message}</p>`)
            .join("")}</div>`
        : `<p class="toolbar-note">未发现格式错误，可直接提交。</p>`
    }
  `;
}

function downloadStudentImportTemplate() {
  const template = [
    "username|name|class|major|password",
    "2023003|王五|class-1|软件工程|123456",
    "2023004|赵六|2310|软件工程|123456",
  ].join("\n");
  downloadFile(`student-import-template-${timestampForFile()}.txt`, template, "text/plain;charset=utf-8");
}

function renderMiniSubmissionMeta(item) {
  const parts = [];
  if (item.passStatus && item.passStatus !== "待定") {
    parts.push(item.passStatus);
  }
  if (item.rank) {
    parts.push(`排名 ${item.rank}/${item.finishedCount || item.rank}`);
  }
  if (item.usedTimeText) {
    parts.push(`用时 ${item.usedTimeText}`);
  }
  return parts.join(" · ") || "阅卷完成后显示排名与用时";
}

function buildStudentSubjectStats() {
  const stats = {};
  state.bootstrap.submissions
    .filter((item) => item.status === "已完成")
    .forEach((submission) => {
      (submission.answerDetail || []).forEach((detail) => {
        const subject = detail.subject || "未分类";
        if (!stats[subject]) {
          stats[subject] = { subject, correct: 0, total: 0 };
        }
        stats[subject].total += 1;
        if (detail.correct) {
          stats[subject].correct += 1;
        }
      });
    });

  return Object.values(stats)
    .map((item) => ({
      ...item,
      rate: item.total ? ((item.correct / item.total) * 100).toFixed(1) : "0.0",
    }))
    .sort((a, b) => Number(b.rate) - Number(a.rate));
}

function buildClassComparisonRows() {
  const classes = indexBy(state.bootstrap.classes);
  const stats = {};

  state.bootstrap.submissions
    .filter((item) => item.status === "已完成")
    .forEach((submission) => {
      const student = state.bootstrap.users.find((item) => item.id === submission.studentId);
      if (!student?.classId) return;
      const key = student.classId;
      if (!stats[key]) {
        stats[key] = { className: classes[key]?.name || key, count: 0, passCount: 0, totalScore: 0 };
      }
      const score = Number(submission.finalScore ?? submission.autoScore ?? 0);
      stats[key].count += 1;
      stats[key].totalScore += score;
      if (submission.passStatus === "已及格") {
        stats[key].passCount += 1;
      }
    });

  return Object.values(stats)
    .map((item) => ({
      className: item.className,
      count: item.count,
      avgScore: item.count ? (item.totalScore / item.count).toFixed(1) : "0.0",
      passRate: item.count ? `${((item.passCount / item.count) * 100).toFixed(1)}%` : "0%",
    }))
    .sort((a, b) => Number(b.avgScore) - Number(a.avgScore));
}

function buildQuestionQualityRows() {
  const stats = {};

  state.bootstrap.submissions
    .filter((item) => item.status === "已完成")
    .forEach((submission) => {
      (submission.answerDetail || []).forEach((detail) => {
        if (!stats[detail.questionId]) {
          stats[detail.questionId] = {
            title: detail.title,
            count: 0,
            correctCount: 0,
            totalScore: 0,
          };
        }
        stats[detail.questionId].count += 1;
        stats[detail.questionId].totalScore += Number(detail.score || 0);
        if (detail.correct) {
          stats[detail.questionId].correctCount += 1;
        }
      });
    });

  return Object.values(stats)
    .map((item) => ({
      title: item.title,
      count: item.count,
      correctRate: item.count ? `${((item.correctCount / item.count) * 100).toFixed(1)}%` : "0%",
      avgScore: item.count ? (item.totalScore / item.count).toFixed(1) : "0.0",
    }))
    .sort((a, b) => Number(a.correctRate.replace("%", "")) - Number(b.correctRate.replace("%", "")))
    .slice(0, 8);
}

function buildStudentRecordSummary(rows) {
  const finishedRows = rows.filter((item) => item.status === "已完成");
  const passRows = finishedRows.filter((item) => item.passStatus === "已及格");
  const rankedRows = finishedRows.filter((item) => Number.isFinite(Number(item.rank)) && Number(item.rank) > 0);
  const usedRows = rows.filter((item) => Number(item.usedMs) > 0);
  const scoredRows = finishedRows.filter((item) => Number.isFinite(Number(item.finalScore ?? item.autoScore)));
  const averageUsedMs = usedRows.length ? usedRows.reduce((sum, item) => sum + Number(item.usedMs || 0), 0) / usedRows.length : 0;
  const averageScore =
    scoredRows.length
      ? scoredRows.reduce((sum, item) => sum + Number(item.finalScore ?? item.autoScore ?? 0), 0) / scoredRows.length
      : 0;
  const bestScoreRow = scoredRows.reduce((best, item) => {
    if (!best) return item;
    return Number(item.finalScore ?? item.autoScore ?? 0) > Number(best.finalScore ?? best.autoScore ?? 0) ? item : best;
  }, null);
  const bestRank = rankedRows.length ? Math.min(...rankedRows.map((item) => Number(item.rank))) : null;
  const bestRankRow = bestRank != null ? rankedRows.find((item) => Number(item.rank) === bestRank) : null;

  return {
    finishedCount: finishedRows.length,
    passCount: passRows.length,
    passRate: finishedRows.length ? `${((passRows.length / finishedRows.length) * 100).toFixed(1)}%` : "-",
    passRateMeta: finishedRows.length ? `已完成 ${finishedRows.length} 场，其中通过 ${passRows.length} 场` : "暂无已完成成绩",
    averageScoreText: scoredRows.length ? averageScore.toFixed(1) : "-",
    averageScoreMeta: scoredRows.length ? `基于 ${scoredRows.length} 场已完成考试` : "暂无可统计成绩",
    bestScoreText: bestScoreRow ? `${bestScoreRow.finalScore ?? bestScoreRow.autoScore ?? 0}${bestScoreRow.totalScore ? ` / ${bestScoreRow.totalScore}` : ""}` : "-",
    bestScoreMeta: bestScoreRow ? `${bestScoreRow.examName || "该考试"} · ${bestScoreRow.passStatus || "待定"}` : "暂无最高分记录",
    bestRankText: bestRank != null ? `第 ${bestRank} 名` : "-",
    bestRankMeta: bestRankRow
      ? `${bestRankRow.examName || "该考试"} · 已完成 ${bestRankRow.finishedCount || bestRank} 人`
      : "暂无可计算排名的成绩",
    averageDurationText: averageUsedMs ? formatDurationText(averageUsedMs) : "-",
    averageDurationMeta: usedRows.length ? `基于 ${usedRows.length} 次已提交答卷` : "暂无已提交答卷",
  };
}

function renderPassStatusMeta(item) {
  if (!Number.isFinite(Number(item.passScore))) {
    return "及格线未设置";
  }
  if (item.passDelta == null) {
    return `及格线 ${item.passScore} 分`;
  }
  if (item.passDelta >= 0) {
    return `高于及格线 ${item.passDelta} 分`;
  }
  return `低于及格线 ${Math.abs(item.passDelta)} 分`;
}

function renderRankMeta(item) {
  if (!item.rank) {
    return item.status === "待阅卷" ? "阅卷完成后生成排名" : "提交并完成后生成排名";
  }
  const finishedCount = item.finishedCount || item.rank;
  const totalCount = item.targetStudentCount || item.participantCount || finishedCount;
  return `已完成 ${finishedCount} 人 · 应考 ${totalCount} 人`;
}

function renderDurationMeta(item) {
  if (item.timeUsageRate != null) {
    return `约占考试时长 ${formatPercent(item.timeUsageRate)}`;
  }
  if (item.durationMinutes) {
    return `考试限时 ${item.durationMinutes} 分钟`;
  }
  return "暂无用时统计";
}

function parseQuestionImportLine(line) {
  const parts = String(line)
    .replaceAll("｜", "|")
    .split("|")
    .map((item) => item.trim());
  if (parts.length < 7) {
    throw new Error(`导入格式错误：${line}`);
  }
  const type = normalizeImportQuestionType(parts[0]);
  const subject = parts[1];
  const knowledgePoint = parts[2];
  const difficulty = normalizeImportDifficulty(parts[3]);
  const scoreText = parts[4];
  const title = parts[5];
  const optionsText = parts.length >= 8 ? parts[6] : "";
  const answerText = parts.length >= 8 ? parts.slice(7).join("|") : parts[6];
  const score = Number(scoreText);

  if (!type) {
    throw new Error(`题型不支持：${parts[0]}`);
  }
  if (!subject || !knowledgePoint || !title) {
    throw new Error("科目、知识点和题目不能为空");
  }
  if (!Number.isFinite(score) || score <= 0) {
    throw new Error(`分值无效：${scoreText}`);
  }

  const options = parseDelimitedField(optionsText);
  if (type === "judge" && !options.length) {
    options.push("正确", "错误");
  }
  if (["single", "multiple", "judge"].includes(type) && options.length < 2) {
    throw new Error("选择题或判断题至少需要两个选项");
  }
  const answer = parseDelimitedField(answerText);
  if (!answer.length) {
    throw new Error("答案不能为空");
  }

  return {
    teacherId: state.currentUser.id,
    type,
    subject,
    knowledgePoint,
    difficulty,
    score,
    title,
    options,
    answer,
  };
}

function parseQuestionImportPayload(payload) {
  const records = [];
  const errors = [];

  String(payload)
    .split(/\r?\n/)
    .forEach((rawLine, index) => {
      const line = rawLine.trim();
      if (!line || isQuestionImportHeaderLine(line)) {
        return;
      }
      try {
        records.push(parseQuestionImportLine(line));
      } catch (error) {
        errors.push({
          lineNumber: index + 1,
          message: error.message || "格式错误",
        });
      }
    });

  return { records, errors };
}

function isQuestionImportHeaderLine(line) {
  const normalized = String(line).replaceAll("｜", "|").toLowerCase();
  return (
    normalized === "题型|科目|知识点|难度|分值|题目|选项|答案" ||
    normalized === "type|subject|knowledgepoint|difficulty|score|title|options|answer"
  );
}

function normalizeImportQuestionType(value) {
  const type = String(value || "").trim().toLowerCase();
  return {
    single: "single",
    "单选": "single",
    multiple: "multiple",
    "多选": "multiple",
    judge: "judge",
    "判断": "judge",
    fill: "fill",
    "填空": "fill",
    short: "short",
    "简答": "short",
    coding: "coding",
    "编程": "coding",
  }[type];
}

function normalizeImportDifficulty(value) {
  const difficulty = String(value || "").trim();
  return {
    简单: "易",
    容易: "易",
    普通: "中",
    中等: "中",
    困难: "难",
  }[difficulty] || difficulty || "中";
}

function parseDelimitedField(text) {
  return String(text)
    .split(/[、,，/;；]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function renderEmptyState(message) {
  return `<div class="empty-state">${message}</div>`;
}

function formatDate(value) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function toDateTimeLocalValue(value) {
  const date = new Date(value);
  const pad = (num) => String(num).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatDuration(ms) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const hours = String(Math.floor(totalSeconds / 3600)).padStart(2, "0");
  const minutes = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, "0");
  const seconds = String(totalSeconds % 60).padStart(2, "0");
  return `${hours}:${minutes}:${seconds}`;
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

function formatPercent(value) {
  return value == null || value === "" ? "-" : `${Number(value).toFixed(1)}%`;
}

function timestampForFile() {
  const now = new Date();
  const pad = (num) => String(num).padStart(2, "0");
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
}

function toCsvRow(values) {
  return values
    .map((value) => {
      const text = String(value ?? "");
      return `"${text.replaceAll('"', '""')}"`;
    })
    .join(",");
}

function shuffle(list) {
  const next = [...list];
  for (let i = next.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [next[i], next[j]] = [next[j], next[i]];
  }
  return next;
}

function typeLabel(type) {
  return {
    single: "单选",
    multiple: "多选",
    judge: "判断",
    fill: "填空",
    short: "简答",
    coding: "编程",
  }[type] || type;
}

function indexBy(rows) {
  return rows.reduce((acc, item) => {
    acc[item.id] = item;
    return acc;
  }, {});
}

(function init() {
  const saved = localStorage.getItem("exam-user");
  if (!saved) return;
  try {
    state.currentUser = JSON.parse(saved);
    loadBootstrap();
  } catch (error) {
    localStorage.removeItem("exam-user");
  }
})();
