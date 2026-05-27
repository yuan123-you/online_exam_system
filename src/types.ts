export type Role = "admin" | "teacher" | "student";
export type QuestionType = "single" | "multiple" | "judge" | "fill" | "short" | "coding";

export interface Department {
  id: string;
  name: string;
}

export interface ClassRoom {
  id: string;
  name: string;
  major: string;
  departmentId: string;
}

export interface User {
  id: string;
  role: Role;
  username: string;
  name: string;
  password?: string;
  classId?: string;
  departmentId?: string;
  major?: string;
}

export interface Question {
  id: string;
  teacherId: string;
  subject: string;
  knowledgePoint: string;
  difficulty: string;
  type: QuestionType;
  title: string;
  options: string[];
  answer: string[];
  score: number;
  sourceTag?: string;
}

export interface Paper {
  id: string;
  teacherId: string;
  name: string;
  durationMinutes: number;
  totalScore: number;
  passScore: number;
  questionIds: string[];
  paperType?: string;
  sourceTag?: string;
}

export interface Exam {
  id: string;
  teacherId: string;
  paperId: string;
  name: string;
  targetClassIds: string[];
  startTime: string;
  endTime: string;
  antiCheatLimit: number;
  published: boolean;
  statusText?: string;
  durationMinutes?: number;
  totalScore?: number;
  passScore?: number;
  paperName?: string;
}

export interface AnswerPayload {
  questionId: string;
  answer: string[];
}

export interface AnswerDetail extends AnswerPayload {
  title: string;
  subject: string;
  knowledgePoint?: string;
  type: QuestionType;
  score: number;
  fullScore: number;
  correct: boolean | null;
  expectedAnswer: string[];
}

export interface SubmissionReview {
  id: string;
  examId: string;
  studentId: string;
  studentName: string;
  answers: AnswerPayload[];
  switchCount: number;
  status: string;
  startedAt?: string;
  deadlineAt?: string;
  updatedAt?: string;
  submittedAt?: string;
  suspicious?: boolean;
  suspiciousReasons?: string[];
  answerDetail?: AnswerDetail[];
  autoScore?: number;
  finalScore?: number;
  examName?: string;
  paperName?: string;
  totalScore?: number;
  durationMinutes?: number;
  passScore?: number;
  passStatus?: string;
  usedMinutes?: string | null;
  usedTimeText?: string | null;
  timeUsageRate?: number | null;
  passDelta?: number | null;
  scoreRate?: number | null;
  participantCount?: number;
  targetStudentCount?: number;
  rank?: number | null;
  finishedCount?: number;
  gradedBy?: string;
}

export interface WrongBookEntry {
  id: string;
  studentId: string;
  studentName?: string;
  questionId: string;
  subject: string;
  knowledgePoint: string;
  type: QuestionType;
  title: string;
  expectedAnswer: string[];
  latestAnswer: string[];
  lastRetryAnswer?: string[];
  wrongCount: number;
  retryCount: number;
  lastRetryCorrect: boolean;
  removable: boolean;
  statusText?: string;
  question?: Question;
}

export interface LogEntry {
  id: string;
  actorId: string;
  action: string;
  detail: string;
  time: string;
}

export interface BootstrapData {
  currentUser: User;
  departments: Department[];
  classes: ClassRoom[];
  users: User[];
  questions: Question[];
  papers: Paper[];
  exams: Exam[];
  submissions: SubmissionReview[];
  wrongBookEntries: WrongBookEntry[];
  logs: LogEntry[];
}

export interface ExamSessionState {
  submissionId: string;
  startedAt: string;
  deadlineAt: string;
  switchCount: number;
  answers: AnswerPayload[];
  remainingMs: number;
}

export interface ExamDetail extends Exam {
  paper: Paper;
  questions: Array<Question & { order: number }>;
  session?: ExamSessionState;
}

export interface MenuItem {
  key: string;
  label: string;
  description: string;
}
