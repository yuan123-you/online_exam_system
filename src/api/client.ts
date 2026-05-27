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
    headers.set("Authorization", `Bearer ${currentAuthToken}`);
  }
  const response = await fetch(url, { ...options, headers });
  const raw = await response.text();
  const payload = raw ? normalizeApiData(JSON.parse(raw)) : ({} as T);
  if (!response.ok) {
    const errorMessage =
      (payload as { message?: string })?.message ||
      (response.status === 401 ? "登录状态已失效，请重新登录。" : "请求失败，请稍后重试。");
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

export function batchImportUsers(records: Array<Record<string, unknown>>) {
  return request<BatchImportResult>("/api/users/batch-import", {
    method: "POST",
    body: JSON.stringify({ records }),
  });
}
