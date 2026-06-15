import type { BootstrapData, ExamDetail, SubmissionReview, User, WrongBookEntry } from "../types";
import { normalizeApiData } from "../utils/text";

let currentAuthToken = "";

export function setCurrentAuthToken(token?: string) {
  currentAuthToken = token || "";
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  headers.set("Content-Type", "application/json");
  if (currentAuthToken) {
    headers.set("X-User-Id", currentAuthToken);
  }
  const response = await fetch(url, { ...options, headers });
  const raw = await response.text();
  let payload: T;
  try {
    payload = raw ? normalizeApiData(JSON.parse(raw)) : ({} as T);
  } catch {
    if (!response.ok) throw new Error("请求失败，请稍后重试。");
    throw new Error("服务器返回了无效的响应格式");
  }
  if (!response.ok) {
    const apiMessage = (payload as { message?: string })?.message;
    let errorMessage: string;
    if (apiMessage) {
      errorMessage = apiMessage;
    } else if (response.status === 429) {
      errorMessage = "AI 服务当前请求过多，请稍等片刻再试";
    } else if (response.status === 503) {
      errorMessage = "AI 服务暂时不可用，请稍后重试";
    } else if (response.status === 401) {
      errorMessage = "登录状态已失效，请重新登录。";
    } else {
      errorMessage = "请求失败，请稍后重试。";
    }
    throw new Error(errorMessage);
  }
  return payload as T;
}

export function login(username: string, password: string) {
  return request<{ user: User }>("/api/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function loadBootstrap() {
  return request<BootstrapData>("/api/bootstrap");
}

export function createEntity(entity: string, record: Record<string, unknown>) {
  return request<{ record: unknown }>("/api/entities", {
    method: "POST",
    body: JSON.stringify({ entity, record }),
  });
}

export function updateEntity(entity: string, record: Record<string, unknown>) {
  return request<{ record: unknown }>(`/api/entities/${entity}`, {
    method: "PUT",
    body: JSON.stringify(record),
  });
}

export function deleteEntity(entity: string, id: string) {
  return request<{ success: boolean }>(`/api/entities/${entity}/${id}`, {
    method: "DELETE",
  });
}

export function changePassword(oldPassword: string, newPassword: string) {
  return request<{ success: boolean }>("/api/user/password", {
    method: "POST",
    body: JSON.stringify({ oldPassword, newPassword }),
  });
}

export function resetPassword(userId: string, newPassword: string) {
  return request<{ success: boolean }>("/api/admin/reset-password", {
    method: "POST",
    body: JSON.stringify({ userId, newPassword }),
  });
}

export function loadExamDetail(examId: string) {
  return request<ExamDetail>(`/api/exams/${examId}/detail`);
}

export function saveSubmission(examId: string, answers: Array<{ questionId: string; answer: string[] }>, switchCount: number) {
  return request<{ success: boolean; submission: { deadlineAt?: string } }>("/api/submissions/save", {
    method: "POST",
    body: JSON.stringify({ examId, answers, switchCount }),
  });
}

export function submitSubmission(examId: string, answers: Array<{ questionId: string; answer: string[] }>, switchCount: number) {
  return request<{ submission: SubmissionReview }>("/api/submissions/submit", {
    method: "POST",
    body: JSON.stringify({ examId, answers, switchCount }),
  });
}

export function manualGrade(submissionId: string, scores: Record<string, number>) {
  return request<{ submission: SubmissionReview }>("/api/submissions/manual-grade", {
    method: "POST",
    body: JSON.stringify({ submissionId, scores }),
  });
}

export function extendStudent(examId: string, studentId: string, extraMinutes: number) {
  return request<{ submission: SubmissionReview }>(`/api/exams/${examId}/extend-student`, {
    method: "POST",
    body: JSON.stringify({ studentId, extraMinutes }),
  });
}

export function retryWrongBook(entryId: string, answer: string[]) {
  return request<{ entry: WrongBookEntry }>(`/api/wrongbook/${entryId}/retry`, {
    method: "POST",
    body: JSON.stringify({ answer }),
  });
}

export function removeWrongBook(entryId: string) {
  return request<{ success: boolean }>(`/api/wrongbook/${entryId}/remove`, {
    method: "POST",
    body: JSON.stringify({}),
  });
}

export interface BatchImportResult {
  importedCount: number;
  failedCount: number;
  created: Array<Record<string, unknown>>;
  errors: Array<{ index?: number; title?: string; message: string }>;
}

export interface QuestionsPageResult {
  rows: Array<Record<string, unknown>>;
  total: number;
  page: number;
  pageSize: number;
}

export function loadQuestionsPage(params: {
  page: number;
  pageSize: number;
  keyword?: string;
  type?: string;
  subject?: string;
}) {
  const query = new URLSearchParams({
    page: String(params.page),
    pageSize: String(params.pageSize),
    keyword: params.keyword || '',
    type: params.type || 'all',
    subject: params.subject || 'all',
  });
  return request<QuestionsPageResult>(`/api/questions/page?${query.toString()}`);
}

export function batchImportUsers(records: Array<Record<string, unknown>>) {
  return request<BatchImportResult>("/api/users/batch-import", {
    method: "POST",
    body: JSON.stringify({ records }),
  });
}

export interface MonitorStudent {
  studentId: string;
  studentName: string;
  className: string;
  status: string;
  score: number | null;
  switchCount: number;
  suspicious: boolean;
  suspiciousReasons?: string[];
  startedAt?: string;
  submittedAt?: string;
  usedTimeText?: string | null;
}

export interface MonitorResult {
  students: MonitorStudent[];
  totalCount: number;
  notStartedCount: number;
  runningCount: number;
  submittedCount: number;
  maxScore: number | null;
  minScore: number | null;
  avgScore: number | null;
}

export function monitorExam(examId: string) {
  return request<MonitorResult>(`/api/exams/${examId}/monitor`);
}

export interface ExportScoreRow {
  username: string;
  studentName: string;
  className: string;
  status: string;
  score: number | null;
  totalScore: number;
  passScore: number;
  rank: number | null;
}

export interface ExportScoresResult {
  examName: string;
  rows: ExportScoreRow[];
}

export function exportScores(examId: string) {
  return request<ExportScoresResult>(`/api/exams/${examId}/export-scores`);
}

export function exportExcelScores(examId: string): Promise<void> {
  const headers = new Headers();
  if (currentAuthToken) {
    headers.set("X-User-Id", currentAuthToken);
  }
  return fetch(`/api/exams/${examId}/export-excel`, { headers })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error("导出失败");
      }
      const blob = await response.blob();
      const disposition = response.headers.get("Content-Disposition");
      let filename = "成绩表.xlsx";
      if (disposition) {
        const match = disposition.match(/filename\*?=(?:UTF-8'')?(.+)/i);
        if (match) {
          filename = decodeURIComponent(match[1].replace(/"/g, ""));
        }
      }
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    });
}

export interface QuestionAnalysisItem {
  questionId: string;
  title: string;
  type: string;
  subject: string;
  knowledgePoint: string;
  totalAttempts: number;
  correctCount: number;
  correctRate: number;
}

export interface KnowledgePointAnalysis {
  knowledgePoint: string;
  totalAttempts: number;
  correctCount: number;
  correctRate: number;
}

export interface QuestionAnalysisResult {
  questions: QuestionAnalysisItem[];
  knowledgePointAnalysis: KnowledgePointAnalysis[];
}

export function questionAnalysis(examId: string) {
  return request<QuestionAnalysisResult>(`/api/exams/${examId}/question-analysis`);
}

export interface AutoGenerateRule {
  type: string;
  count: number;
  subject?: string;
  knowledgePoint?: string;
  difficulty?: string;
}

export function autoGeneratePaper(payload: {
  name: string;
  durationMinutes: number;
  passScore: number;
  rules: AutoGenerateRule[];
}) {
  return request<{ record: Record<string, unknown> }>("/api/papers/auto-generate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export interface ScoreTrendItem {
  examName: string;
  score: number;
  totalScore: number;
  passScore: number;
  submittedAt: string;
}

export function scoreTrend() {
  return request<{ trend: ScoreTrendItem[] }>("/api/student/score-trend");
}

export interface SubjectMastery {
  subject: string;
  totalQuestions: number;
  correctQuestions: number;
  mastery: number;
}

export interface KnowledgePointMastery {
  subject: string;
  knowledgePoint: string;
  totalQuestions: number;
  correctQuestions: number;
  mastery: number;
}

export function knowledgeRadar() {
  return request<{ subjectMastery: SubjectMastery[]; knowledgePointMastery: KnowledgePointMastery[] }>("/api/student/knowledge-radar");
}

// ---- Quota API ----

export interface QuotaInfo {
  used: number;
  limit: number;
}

export interface QuotaResult {
  questionBank?: QuotaInfo;
  wrongBook?: QuotaInfo;
  practiceRecords?: QuotaInfo;
}

export function loadQuotas() {
  return request<QuotaResult>("/api/user/quotas");
}

// ---- Batch operations ----

export interface BatchDeleteResult {
  deletedCount: number;
  failedCount: number;
  deletedIds: string[];
  errors: Array<{ id: string; message: string }>;
}

export function batchDeleteQuestions(ids: string[]) {
  return request<BatchDeleteResult>("/api/questions/batch-delete", {
    method: "POST",
    body: JSON.stringify({ ids }),
  });
}

export interface BatchRestoreResult {
  restoredCount: number;
  failedCount: number;
  restoredIds: string[];
  errors: Array<{ id: string; message: string }>;
}

export function batchRestoreQuestions(ids: string[]) {
  return request<BatchRestoreResult>("/api/questions/batch-restore", {
    method: "POST",
    body: JSON.stringify({ ids }),
  });
}

export interface BatchRemoveWrongBookResult {
  removedCount: number;
  removedIds: string[];
}

export function batchRemoveWrongBook(ids: string[]) {
  return request<BatchRemoveWrongBookResult>("/api/wrongbook/batch-remove", {
    method: "POST",
    body: JSON.stringify({ ids }),
  });
}

// ========== Generic Pagination API ==========

export interface PageResult {
  rows: Array<Record<string, unknown>>;
  total: number;
  page: number;
  pageSize: number;
}

export function loadUsersPage(params: {
  page: number;
  pageSize: number;
  keyword?: string;
  role?: string;
  classId?: string;
  departmentId?: string;
}) {
  const query = new URLSearchParams({
    page: String(params.page),
    pageSize: String(params.pageSize),
    keyword: params.keyword || '',
    role: params.role || '',
    classId: params.classId || '',
    departmentId: params.departmentId || '',
  });
  return request<PageResult>(`/api/users/page?${query.toString()}`);
}

export function loadLogsPage(params: {
  page: number;
  pageSize: number;
  keyword?: string;
  action?: string;
}) {
  const query = new URLSearchParams({
    page: String(params.page),
    pageSize: String(params.pageSize),
    keyword: params.keyword || '',
    action: params.action || '',
  });
  return request<PageResult>(`/api/logs/page?${query.toString()}`);
}

export function loadWrongBookPage(params: {
  page: number;
  pageSize: number;
  subject?: string;
  status?: string;
}) {
  const query = new URLSearchParams({
    page: String(params.page),
    pageSize: String(params.pageSize),
    subject: params.subject || '',
    status: params.status || '',
  });
  return request<PageResult>(`/api/wrongbook/page?${query.toString()}`);
}

// ========== AI API Functions ==========

/**
 * SSE streaming callback types
 */
export interface SseChunk {
  type: 'reasoning' | 'content';
  text: string;
}

export interface SseComplete {
  content: string;
  reasoning: string;
  done: boolean;
}

export interface SseError {
  error: string;
}

/**
 * Shared SSE streaming engine — handles fetch, SSE parsing, auto-retry on 429/503.
 * All three AI streaming functions delegate to this to avoid code duplication.
 */
function sseStream(
  endpoint: string,
  body: Record<string, unknown>,
  onChunk: (chunk: SseChunk) => void,
  onComplete: (data: SseComplete) => void,
  onError: (error: string) => void,
  onFinally?: () => void,
  maxRetries: number = 2,
): AbortController {
  const controller = new AbortController();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (currentAuthToken) headers['X-User-Id'] = currentAuthToken;

  const HEARTBEAT_TIMEOUT_MS = 30_000;
  let heartbeatTimer: ReturnType<typeof setTimeout> | null = null;

  const clearHeartbeat = () => {
    if (heartbeatTimer !== null) {
      clearTimeout(heartbeatTimer);
      heartbeatTimer = null;
    }
  };

  const resetHeartbeat = () => {
    clearHeartbeat();
    heartbeatTimer = setTimeout(() => {
      console.log('[SSE] 心跳超时（30秒无数据），自动断开连接');
      controller.abort();
    }, HEARTBEAT_TIMEOUT_MS);
  };

  const replaceCircuitBreakerMsg = (msg: string): string => {
    if (msg.includes('熔断保护')) {
      return 'AI 服务正在恢复中，请等待约30秒后重试';
    }
    return msg;
  };

  const doFetch = async (attempt: number): Promise<void> => {
    try {
      resetHeartbeat();
      const response = await fetch(endpoint, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      // Auto-retry on 429 (rate limit) or 503 (service unavailable)
      // 增加抖动退避，避免雷群效应
      if (!response.ok && (response.status === 429 || response.status === 503) && attempt < maxRetries) {
        const baseWait = 2000 * Math.pow(2, attempt);
        const jitter = baseWait * 0.5 * (Math.random() - 0.5);
        const waitMs = Math.max(1000, baseWait + jitter);
        console.log(`[SSE] 收到 ${response.status}，${Math.round(waitMs)}ms 后重试 (第${attempt + 1}次)`);
        await new Promise(r => setTimeout(r, waitMs));
        if (controller.signal.aborted) return;
        return doFetch(attempt + 1);
      }

      if (!response.ok) {
        const text = await response.text();
        // Try to extract a meaningful error message from JSON body
        let friendlyMsg = `请求失败 (HTTP ${response.status})`;
        try {
          const json = JSON.parse(text);
          if (json.message) friendlyMsg = json.message;
          else if (json.error) friendlyMsg = json.error;
        } catch {
          // Extract from raw text if not JSON
          const clean = text.replace(/<[^>]+>/g, '').trim();
          if (clean && clean.length < 200) friendlyMsg = clean;
        }
        if (response.status === 429) friendlyMsg = 'AI 服务当前请求过多，请稍等片刻再试';
        else if (response.status === 503) friendlyMsg = 'AI 服务暂时不可用，请稍后重试';
        friendlyMsg = replaceCircuitBreakerMsg(friendlyMsg);
        onError(friendlyMsg);
        clearHeartbeat();
        onFinally?.();
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        onError('响应数据不可读取');
        clearHeartbeat();
        onFinally?.();
        return;
      }

      const decoder = new TextDecoder();
      let buffer = '';

      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          resetHeartbeat();

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          let currentEvent = '';
          let currentData = '';

          for (const line of lines) {
            if (line.startsWith('event: ')) {
              currentEvent = line.substring(7).trim();
            } else if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim();
            } else if (line.startsWith('data: ')) {
              currentData += (currentData ? '\n' : '') + line.substring(6).trim()
            } else if (line.startsWith('data:')) {
              currentData += (currentData ? '\n' : '') + line.substring(5).trim();
            } else if (line === '') {
              if (currentData) {
                try {
                  const parsed = JSON.parse(currentData);
                  if (currentEvent === 'chunk') {
                    onChunk(parsed as SseChunk);
                  } else if (currentEvent === 'complete') {
                    onComplete(parsed as SseComplete);
                  } else if (currentEvent === 'error') {
                    onError(replaceCircuitBreakerMsg(parsed.error || 'AI 请求出错'));
                  }
                } catch { /* skip malformed JSON */ }
              }
              currentEvent = '';
              currentData = '';
            }
          }
        }
      } catch (err: unknown) {
        // Silently ignore abort errors (e.g., user navigated away or clicked stop)
        if ((err as Error)?.name === 'AbortError') {
          clearHeartbeat();
          return;
        }
        onError(String(err));
      } finally {
        clearHeartbeat();
        try { reader.releaseLock(); } catch { /* ignore */ }
        onFinally?.();
      }
    } catch (err: unknown) {
      // Silently ignore abort errors from fetch() itself
      if ((err as Error)?.name === 'AbortError') {
        clearHeartbeat();
        onFinally?.();
        return;
      }
      // Retry on network errors
      if (attempt < maxRetries) {
        const waitMs = 1000 * (attempt + 1);
        console.log(`[SSE] 网络错误，${waitMs}ms 后重试 (第${attempt + 1}次)`);
        await new Promise(r => setTimeout(r, waitMs));
        if (!controller.signal.aborted) return doFetch(attempt + 1);
      }
      clearHeartbeat();
      onError('网络连接失败，请检查网络后重试');
      onFinally?.();
    }
  };

  doFetch(0);
  return controller;
}

/**
 * 流式调用 AI 练习（学生端）
 */
export function aiPracticeQuestionsStream(
  params: {
    customPrompt?: string;
    subject: string;
    type: string;
    difficulty: string;
    count: number;
    deepThinking?: boolean;
  },
  onChunk: (chunk: SseChunk) => void,
  onComplete: (data: SseComplete) => void,
  onError: (error: string) => void,
  onFinally?: () => void
): AbortController {
  return sseStream('/api/ai/practice-questions/stream', params, onChunk, onComplete, onError, onFinally);
}

/**
 * 流式调用 AI 出题（教师端）
 */
export function aiGenerateQuestionsStream(
  params: {
    subject: string;
    knowledgePoint?: string;
    type: string;
    difficulty: string;
    count: number;
    deepThinking?: boolean;
  },
  onChunk: (chunk: SseChunk) => void,
  onComplete: (data: SseComplete) => void,
  onError: (error: string) => void,
  onFinally?: () => void
): AbortController {
  return sseStream('/api/ai/generate-questions/stream', params, onChunk, onComplete, onError, onFinally);
}

export interface AiQuestion {
  id: string;
  title: string;
  type: string;
  options: string[];
  answer: string[];
  score: number;
  explanation: string;
  subject: string;
  knowledgePoint: string;
  difficulty: string;
  sourceTag?: string;
}

export interface AiGenerateResult {
  questions: AiQuestion[];
  aiUsed: boolean;
  totalCount: number;
  existingCount: number;
  remainingQuota: number;
}

export interface AiImportResult {
  importedCount: number;
  errors: Array<{ index?: number; title?: string; message: string }>;
  totalCount: number;
  remainingQuota: number;
}

export interface AiGradeDetail {
  questionId: string;
  title: string;
  type: string;
  score: number;
  fullScore: number;
  aiScore: number;
  aiComment: string;
  answer: string[];
  expectedAnswer: string[];
}

export interface AiGradeResult {
  submissionId: string;
  aiScore: number;
  manualScore: number;
  details: AiGradeDetail[];
  message: string;
}

export interface AiExplainResult {
  isCorrect: boolean;
  explanation: string;
  tips: string;
}

export function aiGenerateQuestions(params: {
  subject: string;
  knowledgePoint?: string;
  type: string;
  difficulty: string;
  count: number;
}) {
  return request<AiGenerateResult>("/api/ai/generate-questions", {
    method: "POST",
    body: JSON.stringify(params),
  });
}

export function aiImportQuestions(questions: AiQuestion[]) {
  return request<AiImportResult>("/api/ai/import-questions", {
    method: "POST",
    body: JSON.stringify({ questions }),
  });
}

export function aiGenerateQuestionsStructured(params: {
  subject: string;
  knowledgePoint?: string;
  type: string;
  difficulty: string;
  count: number;
}) {
  return request<AiGenerateResult>("/api/ai/generate-questions-structured", {
    method: "POST",
    body: JSON.stringify(params),
  });
}

export function aiGenerateQuestionsStructuredStream(
  params: {
    subject: string;
    knowledgePoint?: string;
    type: string;
    difficulty: string;
    count: number;
    deepThinking?: boolean;
  },
  onChunk: (chunk: SseChunk) => void,
  onComplete: (data: SseComplete) => void,
  onError: (error: string) => void,
  onFinally?: () => void
): AbortController {
  return sseStream('/api/ai/generate-questions-structured/stream', params, onChunk, onComplete, onError, onFinally);
}

export interface QuestionBackup {
  id: string;
  teacherId: string;
  questionCount: number;
  createdAt: string;
}

export function backupQuestionBank() {
  return request<{ backupId: string; questionCount: number }>("/api/ai/backup-question-bank", {
    method: "POST",
  });
}

export function listQuestionBackups() {
  return request<{ backups: QuestionBackup[] }>("/api/ai/backups");
}

export function restoreQuestionBackup(backupId: string) {
  return request<{ restoredCount: number; backupId: string }>("/api/ai/restore-backup", {
    method: "POST",
    body: JSON.stringify({ backupId }),
  });
}

export function aiGradeSubmission(submissionId: string) {
  return request<AiGradeResult>("/api/ai/grade-submission", {
    method: "POST",
    body: JSON.stringify({ submissionId }),
  });
}

export function aiPracticeQuestions(params: {
  customPrompt?: string;
  subject: string;
  type: string;
  difficulty: string;
  count: number;
}) {
  return request<{ questions: AiQuestion[]; totalCount: number }>("/api/ai/practice-questions", {
    method: "POST",
    body: JSON.stringify(params),
  });
}

export function aiExplainAnswer(params: {
  question: string | { title: string };
  studentAnswer: string[];
  correctAnswer: string[];
}) {
  return request<AiExplainResult>("/api/ai/explain-answer", {
    method: "POST",
    body: JSON.stringify(params),
  });
}

export function savePracticeRecords(records: Array<Record<string, unknown>>) {
  return request<{ savedCount: number }>("/api/practice/records", {
    method: "POST",
    body: JSON.stringify({ records }),
  });
}

// ========== AI Chat API ==========

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  reasoning?: string;  // 深度思考内容，流式结束后保留供查阅
  duration?: number;   // AI回答耗时(秒)
}

export interface ChatResult {
  content: string;
  role: string;
}

export function aiChat(params: {
  message: string;
  messages?: ChatMessage[];
}) {
  return request<ChatResult>("/api/ai/chat", {
    method: "POST",
    body: JSON.stringify({
      message: params.message,
      messages: params.messages || [],
    }),
  });
}

/**
 * 流式 AI 对话
 */
export function aiChatStream(
  params: {
    message: string;
    messages?: ChatMessage[];
    deepThinking?: boolean;
  },
  onChunk: (chunk: SseChunk) => void,
  onComplete: (data: SseComplete) => void,
  onError: (error: string) => void,
  onFinally?: () => void,
): AbortController {
  return sseStream('/api/ai/chat/stream', {
    message: params.message,
    messages: params.messages || [],
    deepThinking: params.deepThinking || false,
  }, onChunk, onComplete, onError, onFinally);
}

// ========== Chat History API ==========

export interface Conversation {
  id: string;
  title: string;
  role: string;
  sessionType?: string; // 'chat' | 'practice'
  createdAt: string;
  updatedAt: string;
}

export function listConversations() {
  return request<{ conversations: Conversation[] }>('/api/chat/conversations');
}

export function getConversationMessages(conversationId: string) {
  return request<{ messages: ChatMessage[] }>(`/api/chat/conversations/${conversationId}`);
}

export function createConversation(title?: string, role?: string, sessionType?: string) {
  return request<Conversation>('/api/chat/conversations', {
    method: 'POST',
    body: JSON.stringify({ title: title || '新对话', role: role || 'student', sessionType: sessionType || 'chat' }),
  });
}

export function appendConversationMessages(conversationId: string, messages: ChatMessage[], sessionType?: string) {
  return request<{ saved: number }>(`/api/chat/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ messages, sessionType: sessionType || '' }),
  });
}

export function deleteConversation(conversationId: string) {
  return request<{ deleted: boolean }>(`/api/chat/conversations/${conversationId}`, {
    method: 'DELETE',
  });
}

/** 搜索历史对话 */
export interface SearchResultConversation extends Conversation {
  snippets?: string[];
}

export function searchConversations(keyword: string) {
  return request<{ conversations: SearchResultConversation[]; keyword: string }>(
    `/api/chat/conversations/search?keyword=${encodeURIComponent(keyword)}`
  );
}

/** 获取用户学习偏好分析 */
export interface UserPreference {
  subjects: Array<{ name: string; score: number }>;
  interests: string[];
  suggestions: Array<{ text: string; category: string; type: string }>;
  practiceTopics: Array<{ icon: string; label: string; prompt: string }>;
  recentThemes: string[];
  messageCount: number;
  knowledgeGaps?: Array<{ knowledgePoint: string; wrongCount: number; severity: string }>;
  learningPreferences?: {
    difficultyPreference: string;
    learningStyle: string;
    activityLevel: string;
    preferredTypes?: string[];
  };
  aiSummary?: string;
}

export function getUserPreferences() {
  return request<UserPreference>('/api/chat/preferences');
}

// ========== 个性化推荐 API ==========

/** 推荐项 */
export interface RecommendationItem {
  type: string; // knowledge_gap | subject_review | study_plan | chat | wrongbook_retry | explore
  priority: string; // high | medium | low
  title: string;
  description: string;
  action: string; // practice | chat | wrongbook
  prompt?: string;
  knowledgePoint?: string;
  severity?: string;
  subject?: string;
  trend?: string;
  category?: string;
}

/** 获取个性化推荐列表 */
export function getRecommendations() {
  return request<{ recommendations: RecommendationItem[]; cached: boolean }>('/api/recommendations');
}

/** 用户画像 */
export interface UserProfile {
  userId: string;
  generatedAt: string;
  subjectAffinity: Array<{
    subject: string;
    score: number;
    trend: string;
    chatInterest: number;
    examAccuracy: number;
    weaknessLevel: number;
    practiceFrequency: number;
  }>;
  knowledgeGaps: Array<{
    knowledgePoint: string;
    wrongCount: number;
    examWeakness: number;
    severity: string;
  }>;
  learningPreferences: {
    difficultyPreference: string;
    learningStyle: string;
    activityLevel: string;
    preferredTypes: string[];
  };
  aiSummary: string;
}

/** 获取用户画像 */
export function getUserProfile() {
  return request<UserProfile>('/api/recommendations/profile');
}

/** 记录用户行为日志 */
export function logBehavior(params: {
  action: string;
  targetType?: string;
  targetId?: string;
  detail?: Record<string, unknown>;
  durationMs?: number;
}) {
  return request<{ logged: boolean }>('/api/recommendations/behavior', {
    method: 'POST',
    body: JSON.stringify(params),
  });
}

/** 提交推荐反馈 */
export function submitRecommendationFeedback(params: {
  recommendationType: string;
  recommendationContent?: Record<string, unknown>;
  feedbackType: string; // helpful | not_helpful | irrelevant | too_easy | too_hard | bookmark
  feedbackDetail?: string;
}) {
  return request<{ submitted: boolean; message: string }>('/api/recommendations/feedback', {
    method: 'POST',
    body: JSON.stringify(params),
  });
}

// ========== Practice Session API (练习会话持久化) ==========

/** 练习会话 */
export interface PracticeSession {
  id: string;
  userId: string;
  conversationId: string;
  subject: string;
  questionCount: number;
  correctCount: number;
  totalScore: number;
  earnedScore: number;
  status: 'active' | 'submitted' | 'abandoned';
  createdAt: string;
  updatedAt: string;
  submittedAt?: string;
  questions?: PracticeSessionQuestion[];
}

/** 练习会话中的题目（含答题状态） */
export interface PracticeSessionQuestion {
  id: string;
  sessionId: string;
  questionIndex: number;
  questionData: AiQuestion;
  userAnswer: string[] | null;
  isCorrect: boolean | null;
  isSubmitted: boolean;
  createdAt: string;
  updatedAt: string;
  submittedAt?: string;
}

/** 创建新的练习会话 */
export function createPracticeSession(params: { subject?: string; conversationId?: string }) {
  return request<PracticeSession>('/api/practice/sessions', {
    method: 'POST',
    body: JSON.stringify(params),
  });
}

/** 获取用户的练习会话列表 */
export function listPracticeSessions() {
  return request<{ sessions: PracticeSession[] }>('/api/practice/sessions');
}

/** 获取指定练习会话的完整状态 */
export function getPracticeSession(sessionId: string) {
  return request<PracticeSession>(`/api/practice/sessions/${sessionId}`);
}

/** 获取用户当前活跃的练习会话（用于页面刷新恢复） */
export function getActivePracticeSession() {
  return request<{ session: PracticeSession | null }>('/api/practice/sessions/active');
}

/** 保存/更新练习会话的题目列表 */
export function savePracticeSessionQuestions(sessionId: string, questions: AiQuestion[]) {
  return request<{ savedCount: number; sessionId: string }>(`/api/practice/sessions/${sessionId}/questions`, {
    method: 'PUT',
    body: JSON.stringify({ questions }),
  });
}

/** 批量保存用户答案 */
export function savePracticeSessionAnswers(sessionId: string, answers: Array<{ questionIndex: number; answer: string[] | string }>) {
  return request<{ savedCount: number }>(`/api/practice/sessions/${sessionId}/answers`, {
    method: 'PUT',
    body: JSON.stringify({ answers }),
  });
}

/** 保存单道题的用户答案 */
export function savePracticeQuestionAnswer(questionId: string, answer: string[] | string) {
  return request<{ saved: boolean; questionId: string }>(`/api/practice/questions/${questionId}/answer`, {
    method: 'PUT',
    body: JSON.stringify({ answer }),
  });
}

/** 提交整个练习会话的所有答案 */
export function submitPracticeSession(sessionId: string, answers?: Array<{ questionIndex: number; answer: string[] | string }>) {
  return request<{ sessionId: string; correctCount: number; totalScore: number; earnedScore: number; status: string }>(`/api/practice/sessions/${sessionId}/submit`, {
    method: 'POST',
    body: JSON.stringify(answers ? { answers } : {}),
  });
}

/** 删除练习会话 */
export function deletePracticeSession(sessionId: string) {
  return request<{ deleted: boolean }>(`/api/practice/sessions/${sessionId}`, {
    method: 'DELETE',
  });
}

// ---- Class Analysis ----
export interface ClassDistribution {
  fail: number;
  d60: number;
  d70: number;
  d80: number;
  d90: number;
}

export interface ClassQuestionStat {
  questionId: string;
  title: string;
  type: string;
  knowledgePoint: string;
  correctRate: number;
  attemptCount: number;
}

export interface ClassAnalysisItem {
  classId: string;
  className: string;
  totalStudents: number;
  submittedCount: number;
  notStartedCount: number;
  runningCount: number;
  avgScore: number | null;
  maxScore: number | null;
  minScore: number | null;
  passRate: number;
  distribution: ClassDistribution;
  questionStats: ClassQuestionStat[];
}

export interface ClassAnalysisResult {
  examName: string;
  totalScore: number;
  passScore: number;
  classes: ClassAnalysisItem[];
}

export function classAnalysis(examId: string) {
  return request<ClassAnalysisResult>(`/api/exams/${examId}/class-analysis`);
}

// ---- Notifications ----
export interface Notification {
  id: string;
  senderId: string;
  senderName: string;
  title: string;
  content: string;
  type: string;
  targetRole: string;
  targetClassId: string;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationListResult {
  notifications: Notification[];
  unreadCount: number;
}

export function loadNotifications() {
  return request<NotificationListResult>("/api/notifications");
}

export function markNotificationRead(id: string) {
  return request<{ success: boolean }>(`/api/notifications/${id}/read`, { method: "POST" });
}

export function markAllNotificationsRead() {
  return request<{ success: boolean; count: number }>("/api/notifications/read-all", { method: "POST" });
}

export function createNotification(data: { title: string; content: string; type?: string; targetRole?: string; targetClassId?: string }) {
  return request<{ notification: Notification }>("/api/notifications", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

