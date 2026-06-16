import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import type {
  BootstrapData,
  Exam,
  ExamDetail,
  MenuItem,
  Paper,
  Question,
  SubmissionReview,
  WrongBookEntry,
} from '@/types'
import {
  login as apiLogin,
  loadBootstrap as apiLoadBootstrap,
  setCurrentAuthToken,
  createEntity,
  updateEntity,
  deleteEntity,
  changePassword,
  loadExamDetail,
  manualGrade,
  retryWrongBook,
  removeWrongBook,
  monitorExam,
  exportScores,
  exportExcelScores,
  questionAnalysis,
  autoGeneratePaper,
  scoreTrend,
  knowledgeRadar,
  aiGenerateQuestions,
  aiImportQuestions,
  aiGradeSubmission,
  aiPracticeQuestions,
  aiPracticeQuestionsStream,
  aiGenerateQuestionsStream,
  aiExplainAnswer,
  savePracticeRecords,
  loadQuotas,
  batchDeleteQuestions,
  batchRestoreQuestions,
  batchRemoveWrongBook,
  loadQuestionsPage as apiLoadQuestionsPage,
  loadUsersPage as apiLoadUsersPage,
  loadLogsPage as apiLoadLogsPage,
  loadWrongBookPage as apiLoadWrongBookPage,
  aiChat as apiAiChat,
  aiChatStream as apiAiChatStream,
  listConversations,
  getConversationMessages,
  createConversation,
  appendConversationMessages,
  deleteConversation,
  searchConversations,
  getUserPreferences,
  getRecommendations,
  getUserProfile,
  logBehavior,
  submitRecommendationFeedback,
  createPracticeSession,
  getActivePracticeSession,
  savePracticeSessionQuestions,
  savePracticeSessionAnswers,
  submitPracticeSession,
  deletePracticeSession as apiDeletePracticeSession,
  loadNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  createNotification,
} from '@/api/client'
import type {
  MonitorResult,
  QuestionAnalysisResult,
  ScoreTrendItem,
  SubjectMastery,
  KnowledgePointMastery,
  AiQuestion,
  AiGenerateResult,
  AiImportResult,
  AiGradeResult,
  AiExplainResult,
  QuotaResult,
  QuestionsPageResult,
  PageResult,
  Conversation,
  ChatMessage,
  ChatResult,
  UserPreference,
  SearchResultConversation,
  RecommendationItem,
  UserProfile,
  PracticeSession,
  Notification,
  NotificationListResult,
} from '@/api/client'
import { useToast } from '@/composables/useToast'
export type { Toast } from '@/composables/useToast'

/**
 * 检测文本是否为 AI 思考/推理过程输出
 * 通过匹配多个思考模式来避免误判
 */
function looksLikeThinking(text: string): boolean {
  if (!text || text.length < 20) return false
  const patterns = [
    /分析/, /让我/, /好的[，，]?\s*我/, /我将/, /我们需要/,
    /检查/, /起草/, /构建/, /组装/, /最终/, /自检/,
    /步骤/, /考虑/, /优化/, /重读/, /尝试/, /看看/,
    /约束/, /格式.*要求/, /示例.*输出/, /题\s*\d/,
    /角色/, /任务/, /内容.*涵盖/, /严格遵循/,
  ]
  let matches = 0
  for (const p of patterns) {
    if (p.test(text)) matches++
  }
  return matches >= 2
}

/**
 * 从 AI 响应内容中剥离思考/推理文本
 * AI 模型有时会将思考过程输出到 content 字段而非 reasoning_content
 * 此函数检测并剥离这些文本，将其移入 reasoning 字段
 */
function stripThinkingFromContent(raw: string): { content: string; thinking: string } {
  if (!raw || raw.length < 30) return { content: raw, thinking: '' }

  // 策略1：JSON 数组前有思考文本 — 剥离到第一个 [ 之前
  const arrIdx = raw.search(/\[\s*\{/)
  if (arrIdx > 0) {
    const before = raw.substring(0, arrIdx).trim()
    if (looksLikeThinking(before)) {
      return { content: raw.substring(arrIdx), thinking: before }
    }
  }

  // 策略2：JSON 对象前有思考文本 — 剥离到第一个 { 之前
  const objIdx = raw.search(/\{\s*"/)
  if (objIdx > 0) {
    const before = raw.substring(0, objIdx).trim()
    if (looksLikeThinking(before)) {
      return { content: raw.substring(objIdx), thinking: before }
    }
  }

  // 策略3：代码块前有思考文本 — 剥离到 ``` 之前
  const codeIdx = raw.search(/```(?:json)?\s*\n/)
  if (codeIdx > 0) {
    const before = raw.substring(0, codeIdx).trim()
    if (looksLikeThinking(before)) {
      return { content: raw.substring(codeIdx), thinking: before }
    }
  }

  return { content: raw, thinking: '' }
}

export const useAppStore = defineStore('app', () => {
  // Toast notification system
  const { toasts, show: showToastRaw, success: toastSuccess, error: toastError, warning: toastWarning, info: toastInfo } = useToast()

  function showToast(message: string, type: 'success' | 'error' | 'info' | 'warning' = 'info') {
    showToastRaw(message, type)
  }

  // Confirm dialog system (replaces native confirm())
  const confirmState = ref<{
    visible: boolean
    title: string
    message: string
    confirmText: string
    danger: boolean
    resolve: ((value: boolean) => void) | null
  }>({
    visible: false,
    title: '确认操作',
    message: '',
    confirmText: '确定',
    danger: false,
    resolve: null,
  })

  function confirmDialog(message: string, options?: { title?: string; confirmText?: string; danger?: boolean }): Promise<boolean> {
    return new Promise((resolve) => {
      confirmState.value = {
        visible: true,
        title: options?.title || '确认操作',
        message,
        confirmText: options?.confirmText || '确定',
        danger: options?.danger ?? true,
        resolve,
      }
    })
  }

  function handleConfirmOk() {
    const resolve = confirmState.value.resolve
    confirmState.value.visible = false
    confirmState.value.resolve = null
    resolve?.(true)
  }

  function handleConfirmCancel() {
    const resolve = confirmState.value.resolve
    confirmState.value.visible = false
    confirmState.value.resolve = null
    resolve?.(false)
  }

  // Core state
  const bootstrap = ref<BootstrapData | null>(null)
  const loginLoading = ref(false)
  const loginMessage = ref('')
  const authReady = ref(false) // true once initial auth check is done

  // Editor state
  const editorState = ref({
    visible: false,
    kind: 'student' as 'student' | 'teacher' | 'department' | 'class' | 'question' | 'exam',
    model: null as any,
  })

  // Modal visibility
  const paperFormVisible = ref(false)
  const paperModel = ref<Paper | null>(null)
  const previewPaperModel = ref<Paper | null>(null)
  const reviewingSubmission = ref<SubmissionReview | null>(null)
  const retryingEntry = ref<WrongBookEntry | null>(null)
  const batchImportRole = ref<'student' | 'teacher' | null>(null)
  const activeExam = ref<ExamDetail | null>(null)

  // Enhanced features state
  const monitorResult = ref<MonitorResult | null>(null)
  const monitorExamId = ref('')
  const exportLoading = ref(false)
  const questionAnalysisResult = ref<QuestionAnalysisResult | null>(null)
  const questionAnalysisExamId = ref('')
  const scoreTrendData = ref<ScoreTrendItem[]>([])
  const subjectMasteryData = ref<SubjectMastery[]>([])
  const kpMasteryData = ref<KnowledgePointMastery[]>([])
  const wrongBookSubjectFilter = ref('')
  const autoGenVisible = ref(false)

  // Question bank pagination state
  const paginatedQuestions = ref<Array<Record<string, unknown>>>([])
  const totalQuestions = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(50)
  const questionsLoading = ref(false)

  // Users pagination state (admin)
  const paginatedUsers = ref<Array<Record<string, unknown>>>([])
  const totalUsers = ref(0)
  const usersCurrentPage = ref(1)
  const usersPageSize = ref(20)
  const usersLoading = ref(false)

  // Logs pagination state (admin)
  const paginatedLogs = ref<Array<Record<string, unknown>>>([])
  const totalLogs = ref(0)
  const logsCurrentPage = ref(1)
  const logsPageSize = ref(20)
  const logsLoading = ref(false)

  // Wrong book pagination state (student)
  const paginatedWrongBook = ref<Array<Record<string, unknown>>>([])
  const totalWrongBook = ref(0)
  const wrongBookCurrentPage = ref(1)
  const wrongBookPageSize = ref(20)
  const wrongBookLoading = ref(false)

  // AI Features state
  const aiQuestions = ref<AiQuestion[]>([])
  const aiLoading = ref(false)
  const aiQuotaRemaining = ref(5000)
  const aiQuotaUsed = ref(0)
  const aiGradeResult = ref<AiGradeResult | null>(null)
  const aiGradeLoading = ref(false)
  const practiceQuestions = ref<AiQuestion[]>([])
  const practiceLoading = ref(false)
  const streamingContent = ref('')
  const streamingReasoning = ref('')
  const streamingActive = ref(false)

  // Chat state (general conversation session)
  const chatMessages = ref<ChatMessage[]>([])
  const chatLoading = ref(false)
  const chatStreamingContent = ref('')
  const chatStreamingReasoning = ref('')
  const chatStreamingActive = ref(false)
  const chatStreamStartTime = ref(0)
  const chatStreamingHint = ref('')
  const chatDisplayContent = ref('')
  // Typewriter effect: displayed content appears character-by-character
  const chatTypewriterContent = ref('')
  // Feedback system: pre-output and in-output messages
  const chatFeedbackMessage = ref('')
  const chatFeedbackVisible = ref(false)
  let chatDisplayRafId = 0
  let chatAbortController: AbortController | null = null
  let chatTimeoutId: ReturnType<typeof setTimeout> | null = null
  // Typewriter timer
  let chatTypewriterTimerId: ReturnType<typeof setTimeout> | null = null
  let chatTypewriterIndex = 0
  // In-output feedback timer
  let chatFeedbackTimerId: ReturnType<typeof setInterval> | null = null

  // Practice state (question generation session — separate from chat)
  const practiceMessages = ref<ChatMessage[]>([])
  const practSessionLoading = ref(false)
  const practiceStreamingContent = ref('')
  const practiceStreamingReasoning = ref('')
  const practiceStreamingActive = ref(false)
  const practiceStreamStartTime = ref(0)
  const practiceStreamingHint = ref('')
  const practiceDisplayContent = ref('')
  // Question-by-question streaming: parsed questions appear one by one
  const practiceStreamingQuestions = ref<Array<Record<string, unknown>>>([])
  // Feedback system for practice mode
  const practiceFeedbackMessage = ref('')
  const practiceFeedbackVisible = ref(false)
  let practiceDisplayRafId = 0
  let practiceAbortController: AbortController | null = null
  let practiceTimeoutId: ReturnType<typeof setTimeout> | null = null
  // In-output feedback timer for practice
  let practiceFeedbackTimerId: ReturnType<typeof setInterval> | null = null

  // Chat history state
  const conversations = ref<Conversation[]>([])
  const activeConversationId = ref<string | null>(null)
  const conversationsLoading = ref(false)
  // Track how many messages have been persisted per conversation to avoid re-saving duplicates
  const persistedCount: Record<string, number> = {}

  // User preference state (personalized recommendations)
  const userPreferences = ref<UserPreference | null>(null)
  const preferencesLoading = ref(false)

  // Recommendation state
  const recommendations = ref<RecommendationItem[]>([])
  const recommendationsLoading = ref(false)
  const userProfile = ref<UserProfile | null>(null)

  // Deep thinking toggle
  const deepThinkingEnabled = ref(false)

  // Current session tab ('chat' | 'practice')
  const activeTab = ref<'chat' | 'practice'>('chat')

  // Practice session persistence state
  const activePracticeSession = ref<PracticeSession | null>(null)
  const practiceSessionLoading = ref(false)
  let practiceAnswerSaveTimer: ReturnType<typeof setTimeout> | null = null

  // Quota state
  const quotaData = ref<QuotaResult | null>(null)

  // Notification state
  const notifications = ref<Notification[]>([])
  const unreadNotificationCount = ref(0)

  // Computed: User role
  const role = computed(() => bootstrap.value?.currentUser.role || 'student')
  const isAdmin = computed(() => role.value === 'admin')
  const isTeacher = computed(() => role.value === 'teacher')
  const isStudent = computed(() => role.value === 'student')
  const currentUser = computed(() => bootstrap.value?.currentUser)

  // Computed: Data filters
  const students = computed(() => bootstrap.value?.users.filter((u) => u.role === 'student') || [])
  const teachers = computed(() => bootstrap.value?.users.filter((u) => u.role === 'teacher') || [])
  const studentCount = computed(() => students.value.length)
  const teacherCount = computed(() => teachers.value.length)

  const myQuestions = computed(() => {
    const bs = bootstrap.value
    if (!bs) return []
    return bs.questions.filter((q) => q.teacherId === bs.currentUser.id)
  })

  const myPapers = computed(() => {
    const bs = bootstrap.value
    if (!bs) return []
    return bs.papers.filter((p) => p.teacherId === bs.currentUser.id)
  })

  const myExams = computed(() => {
    const bs = bootstrap.value
    if (!bs) return []
    return bs.exams.filter((e) => e.teacherId === bs.currentUser.id)
  })

  const mySubmissions = computed(() =>
    bootstrap.value?.submissions.filter((s) => {
      const exam = bootstrap.value?.exams.find((e) => e.id === s.examId)
      return exam?.teacherId === bootstrap.value?.currentUser.id
    }) || []
  )

  const pendingGradeCount = computed(() => mySubmissions.value.filter((s) => s.status === '待阅卷').length)

  const availableExams = computed(() =>
    bootstrap.value?.exams.filter((e) => {
      const user = bootstrap.value?.currentUser
      if (!user) return false
      const classId = user.classId || ''
      return (classId ? e.targetClassIds.includes(classId) : false) && e.published
    }) || []
  )

  const mySubmissionRecords = computed(() =>
    bootstrap.value?.submissions.filter((s) => s.studentId === bootstrap.value?.currentUser.id) || []
  )

  const sortedLogs = computed(() =>
    [...(bootstrap.value?.logs || [])].sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime())
  )

  const wrongBookSubjects = computed(() =>
    [...new Set((bootstrap.value?.wrongBookEntries || []).map((e) => e.subject))].sort()
  )

  const filteredWrongBookEntries = computed(() => {
    const entries = bootstrap.value?.wrongBookEntries || []
    if (!wrongBookSubjectFilter.value) return entries
    return entries.filter((e) => e.subject === wrongBookSubjectFilter.value)
  })

  // Chart options
  const subjectChartOption = computed<EChartsOption>(() => {
    const subjects = new Map<string, number>();
    (bootstrap.value?.questions || []).forEach((q: Question) => {
      subjects.set(q.subject, (subjects.get(q.subject) || 0) + 1)
    })
    return {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: [...subjects.keys()] },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: [...subjects.values()], itemStyle: { color: '#2563eb', borderRadius: [6, 6, 0, 0] } }],
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
    }
  })

  const scoreTrendOption = computed<EChartsOption>(() => ({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: scoreTrendData.value.map((d) => d.examName), axisLabel: { rotate: 15 } },
    yAxis: { type: 'value', name: '分数' },
    series: [
      { name: '得分', type: 'line', data: scoreTrendData.value.map((d) => d.score), smooth: true, itemStyle: { color: '#2563eb' } },
      { name: '总分', type: 'line', data: scoreTrendData.value.map((d) => d.totalScore), lineStyle: { type: 'dashed' }, itemStyle: { color: '#aaa' } },
      { name: '及格线', type: 'line', data: scoreTrendData.value.map((d) => d.passScore), lineStyle: { type: 'dotted' }, itemStyle: { color: '#dc2626' } },
    ],
    legend: { top: 0 },
    grid: { left: 50, right: 20, top: 40, bottom: 60 },
  }))

  const knowledgeRadarOption = computed<EChartsOption>(() => ({
    tooltip: {},
    radar: {
      indicator: subjectMasteryData.value.map((d) => ({ name: d.subject, max: 100 })),
      shape: 'polygon',
    },
    series: [{
      type: 'radar',
      data: [{ value: subjectMasteryData.value.map((d) => d.mastery), name: '掌握率 (%)', areaStyle: { opacity: 0.15 } }],
      itemStyle: { color: '#2563eb' },
    }],
  }))

  const scoreDistOption = computed<EChartsOption>(() => {
    const buckets = { '0-59': 0, '60-69': 0, '70-79': 0, '80-89': 0, '90-100': 0 }
    mySubmissions.value.forEach((s) => {
      const score = s.finalScore ?? s.autoScore ?? 0
      if (score < 60) buckets['0-59']++
      else if (score < 70) buckets['60-69']++
      else if (score < 80) buckets['70-79']++
      else if (score < 90) buckets['80-89']++
      else buckets['90-100']++
    })
    return {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: Object.keys(buckets) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ type: 'bar', data: Object.values(buckets), itemStyle: { color: '#2563eb', borderRadius: [6, 6, 0, 0] } }],
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
    }
  })

  const passRateOption = computed<EChartsOption>(() => {
    const examMap = new Map<string, { pass: number; total: number }>()
    mySubmissions.value.forEach((s) => {
      const key = s.examName || s.examId
      if (!examMap.has(key)) examMap.set(key, { pass: 0, total: 0 })
      const item = examMap.get(key)!
      item.total++
      if (s.passStatus === '通过' || (s.finalScore ?? s.autoScore ?? 0) >= (s.passScore ?? 60)) item.pass++
    })
    const names = [...examMap.keys()]
    const rates = names.map((k) => {
      const v = examMap.get(k)!
      return v.total > 0 ? Math.round((v.pass / v.total) * 100) : 0
    })
    return {
      tooltip: { trigger: 'axis', formatter: '{b}: {c}%' },
      xAxis: { type: 'category', data: names },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{ type: 'bar', data: rates, itemStyle: { color: '#2563eb', borderRadius: [6, 6, 0, 0] } }],
      grid: { left: 50, right: 20, top: 20, bottom: 40 },
    }
  })

  const classComparisonOption = computed<EChartsOption>(() => {
    const classScores = new Map<string, { scores: number[]; count: number }>()
    mySubmissions.value.forEach((s) => {
      if (s.status !== '已完成') return
      const student = bootstrap.value?.users.find((u) => u.id === s.studentId)
      if (!student?.classId) return
      const cls = bootstrap.value?.classes.find((c) => c.id === student.classId)
      const className = cls?.name || '未知班级'
      if (!classScores.has(className)) classScores.set(className, { scores: [], count: 0 })
      const data = classScores.get(className)!
      data.scores.push(s.finalScore ?? s.autoScore ?? 0)
      data.count++
    })
    const names = [...classScores.keys()]
    const avgScores = names.map((k) => {
      const d = classScores.get(k)!
      return d.scores.length > 0 ? Math.round(d.scores.reduce((a, b) => a + b, 0) / d.scores.length * 10) / 10 : 0
    })
    const maxScores = names.map((k) => {
      const d = classScores.get(k)!
      return d.scores.length > 0 ? Math.max(...d.scores) : 0
    })
    return {
      tooltip: { trigger: 'axis' },
      legend: { top: 0 },
      xAxis: { type: 'category', data: names },
      yAxis: { type: 'value', name: '分数' },
      series: [
        { name: '平均分', type: 'bar', data: avgScores, itemStyle: { color: '#2563eb', borderRadius: [4, 4, 0, 0] } },
        { name: '最高分', type: 'bar', data: maxScores, itemStyle: { color: '#60a5fa', borderRadius: [4, 4, 0, 0] } },
      ],
      grid: { left: 50, right: 20, top: 40, bottom: 40 },
    }
  })

  const questionAnalysisChartOption = computed<EChartsOption>(() => {
    if (!questionAnalysisResult.value) return {}
    const data = questionAnalysisResult.value.questions
    return {
      tooltip: { trigger: 'axis', formatter: (params: any) => {
        const p = params[0]; return `${p.name}<br/>正确率: ${p.value}%`
      }},
      xAxis: { type: 'category', data: data.map((_, i) => `第${i + 1}题`), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{
        type: 'bar',
        data: data.map((d) => ({
          value: d.correctRate,
          itemStyle: { color: d.correctRate >= 60 ? '#2563eb' : '#dc2626', borderRadius: [4, 4, 0, 0] },
        })),
      }],
      grid: { left: 50, right: 20, top: 20, bottom: 50 },
    }
  })

  const kpAnalysisChartOption = computed<EChartsOption>(() => {
    if (!questionAnalysisResult.value) return {}
    const kpData = questionAnalysisResult.value.knowledgePointAnalysis
    return {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: kpData.map((d) => d.knowledgePoint), axisLabel: { rotate: 25 } },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{
        type: 'bar',
        data: kpData.map((d) => ({
          value: d.correctRate,
          itemStyle: { color: d.correctRate >= 60 ? '#2563eb' : '#dc2626', borderRadius: [4, 4, 0, 0] },
        })),
      }],
      grid: { left: 50, right: 20, top: 20, bottom: 60 },
    }
  })

  // Menu items based on role
  const menuItems = computed<MenuItem[]>(() => {
    if (isAdmin.value) {
      return [
        { key: 'overview', label: '数据总览', description: '全局统计与概览' },
        { key: 'students', label: '学生管理', description: '维护学生账号' },
        { key: 'teachers', label: '教师管理', description: '维护教师账号' },
        { key: 'org', label: '组织管理', description: '学院与班级' },
        { key: 'admin-exams', label: '考试管理', description: '查看所有考试' },
        { key: 'logs', label: '系统日志', description: '操作记录' },
        { key: 'profile', label: '个人信息', description: '修改密码' },
      ]
    }
    if (isTeacher.value) {
      return [
        { key: 'overview', label: '教学看板', description: '教学数据概览' },
        { key: 'questions', label: '题库管理', description: '维护题目' },
        { key: 'ai-questions', label: 'AI 出题', description: 'AI 智能出题' },
        { key: 'papers', label: '试卷管理', description: '组卷与管理' },
        { key: 'exams', label: '考试管理', description: '发布考试' },
        { key: 'monitor', label: '考试监控', description: '实时查看考生状态' },
        { key: 'grading', label: '阅卷中心', description: '评阅答卷' },
        { key: 'analysis', label: '成绩分析', description: '统计与图表' },
        { key: 'class-analysis', label: '班级对比', description: '按班级维度分析成绩' },
        { key: 'profile', label: '个人信息', description: '修改密码' },
      ]
    }
    return [
      { key: 'available-exams', label: '待考考试', description: '查看可参加的考试' },
      { key: 'my-exams', label: '考试记录', description: '历史成绩' },
      { key: 'ai-practice', label: 'AI 助手', description: 'AI 智能服务' },
      { key: 'wrong-book', label: '错题本', description: '错题回顾与重做' },
      { key: 'grades', label: '成绩单', description: '成绩趋势与知识掌握' },
      { key: 'practice-records', label: '练习记录', description: '历史练习与正确率' },
      { key: 'profile', label: '个人信息', description: '修改密码' },
    ]
  })

  // Helper functions
  function className(classId?: string) {
    return classId ? bootstrap.value?.classes.find((c) => c.id === classId)?.name || '-' : '-'
  }

  function departmentName(deptId?: string) {
    return deptId ? bootstrap.value?.departments.find((d) => d.id === deptId)?.name || '-' : '-'
  }

  // Actions
  async function initAuth() {
    const savedToken = localStorage.getItem('auth_token')
    if (savedToken) {
      setCurrentAuthToken(savedToken)
      try {
        const data = await apiLoadBootstrap()
        // 后端未登录时返回 200 + currentUser 为空字符串，需检测并清除 token
        if (!data.currentUser || !data.currentUser.id) {
          setCurrentAuthToken('')
          localStorage.removeItem('auth_token')
          bootstrap.value = null
        } else {
          bootstrap.value = data
          handleLoadConversations() // load conversation history in background
          handleLoadPreferences()   // load user preferences for personalization
          loadRecommendations()     // load personalized recommendations
          loadNotificationData()    // load notification data
        }
      } catch (err: any) {
        // 仅在 401（未授权）时清除 token，服务端 500 等错误不应登出用户
        if (err?.message?.includes('登录状态已失效') || err?.message?.includes('401')) {
          setCurrentAuthToken('')
          localStorage.removeItem('auth_token')
          bootstrap.value = null
        }
        // 其他错误（如 500）保留 token，用户刷新页面可重试
      }
    }
    authReady.value = true
  }

  async function login(payload: { username: string; password: string }) {
    loginLoading.value = true
    loginMessage.value = ''
    try {
      const result = await apiLogin(payload.username, payload.password)
      setCurrentAuthToken(result.user.id)
      localStorage.setItem('auth_token', result.user.id)
      bootstrap.value = null // clear stale data from previous user immediately
      try {
        await loadData()       // ensure fresh data before UI renders
      } catch (loadErr: any) {
        // loadData 失败意味着 bootstrap 数据不可用，用户无法正常使用系统
        // 必须清除 token 并报告登录失败，确保提示与实际状态一致
        setCurrentAuthToken('')
        localStorage.removeItem('auth_token')
        bootstrap.value = null
        loginMessage.value = loadErr?.message || '登录成功但加载用户数据失败，请稍后重试。'
        return false
      }
      loadNotificationData() // load notifications for new user
      showToast('登录成功，欢迎回来！', 'success')
      return true
    } catch (err: any) {
      loginMessage.value = err?.message || '登录失败，请检查账号密码。'
      return false
    } finally {
      loginLoading.value = false
    }
  }

  async function loadData() {
    try {
      bootstrap.value = await apiLoadBootstrap()
    } catch (err: any) {
      if (err?.message?.includes('登录状态已失效') || err?.message?.includes('401')) {
        // 仅 401 时清除 token
        setCurrentAuthToken('')
        localStorage.removeItem('auth_token')
        bootstrap.value = null
      }
      // 始终向上抛出异常，让调用方决定如何处理
      // （login 时需要知道 loadData 失败以避免误报"登录成功"）
      throw err
    }
  }

  async function loadQuestionsPage(page: number = 1, keyword?: string, type?: string, subject?: string) {
    questionsLoading.value = true
    currentPage.value = page
    try {
      const result = await apiLoadQuestionsPage({
        page,
        pageSize: pageSize.value,
        keyword,
        type,
        subject,
      })
      paginatedQuestions.value = result.rows
      totalQuestions.value = result.total
    } catch (err: any) {
      showToast(err?.message || '加载题目失败', 'error')
    } finally {
      questionsLoading.value = false
    }
  }

  async function loadUsersPage(page: number = 1, keyword?: string, role?: string, classId?: string, departmentId?: string) {
    usersLoading.value = true
    usersCurrentPage.value = page
    try {
      const result = await apiLoadUsersPage({
        page,
        pageSize: usersPageSize.value,
        keyword,
        role,
        classId,
        departmentId,
      })
      paginatedUsers.value = result.rows
      totalUsers.value = result.total
    } catch (err: any) {
      showToast(err?.message || '加载用户列表失败', 'error')
    } finally {
      usersLoading.value = false
    }
  }

  async function loadLogsPage(page: number = 1, keyword?: string, action?: string) {
    logsLoading.value = true
    logsCurrentPage.value = page
    try {
      const result = await apiLoadLogsPage({
        page,
        pageSize: logsPageSize.value,
        keyword,
        action,
      })
      paginatedLogs.value = result.rows
      totalLogs.value = result.total
    } catch (err: any) {
      showToast(err?.message || '加载日志失败', 'error')
    } finally {
      logsLoading.value = false
    }
  }

  async function loadWrongBookPage(page: number = 1, subject?: string, status?: string) {
    wrongBookLoading.value = true
    wrongBookCurrentPage.value = page
    try {
      const result = await apiLoadWrongBookPage({
        page,
        pageSize: wrongBookPageSize.value,
        subject,
        status,
      })
      paginatedWrongBook.value = result.rows
      totalWrongBook.value = result.total
    } catch (err: any) {
      showToast(err?.message || '加载错题本失败', 'error')
    } finally {
      wrongBookLoading.value = false
    }
  }

  function logout() {
    closeAllModals()
    setCurrentAuthToken('')
    localStorage.removeItem('auth_token')
    bootstrap.value = null
    loginMessage.value = ''
    // Clear AI chat state so next user doesn't see previous user's conversations
    chatMessages.value = []
    practiceMessages.value = []
    chatStreamingContent.value = ''
    chatStreamingActive.value = false
    chatStreamingReasoning.value = ''
    chatStreamStartTime.value = 0
    chatStreamingHint.value = ''
    chatDisplayContent.value = ''
    chatTypewriterContent.value = ''
    chatFeedbackMessage.value = ''
    chatFeedbackVisible.value = false
    stopChatTypewriter()
    stopChatFeedbackTimer()
    practiceStreamingContent.value = ''
    practiceStreamingActive.value = false
    practiceStreamingReasoning.value = ''
    practiceStreamStartTime.value = 0
    practiceStreamingHint.value = ''
    practiceDisplayContent.value = ''
    practiceStreamingQuestions.value = []
    practiceFeedbackMessage.value = ''
    practiceFeedbackVisible.value = false
    stopPracticeFeedbackTimer()
    conversations.value = []
    activeConversationId.value = null
    conversationsLoading.value = false
    // Clear persisted-count tracking
    for (const k of Object.keys(persistedCount)) delete persistedCount[k]
    userPreferences.value = null
    preferencesLoading.value = false
    deepThinkingEnabled.value = false
    // 清理练习会话状态
    activePracticeSession.value = null
    practiceSessionLoading.value = false
    if (practiceAnswerSaveTimer) { clearTimeout(practiceAnswerSaveTimer); practiceAnswerSaveTimer = null }
    // 清理通知状态
    notifications.value = []
    unreadNotificationCount.value = 0
  }

  /** 关闭所有弹窗（页面切换时调用） */
  function closeAllModals() {
    editorState.value.visible = false
    paperFormVisible.value = false
    previewPaperModel.value = null
    reviewingSubmission.value = null
    retryingEntry.value = null
    batchImportRole.value = null
    activeExam.value = null
    autoGenVisible.value = false
  }

  function openEditor(kind: typeof editorState.value.kind, model: any) {
    editorState.value.kind = kind
    editorState.value.model = model
    editorState.value.visible = true
  }

  async function handleEntitySubmit(payload: { entity: string; record: Record<string, unknown> }) {
    try {
      if (payload.record.id) {
        await updateEntity(payload.entity, payload.record)
        showToast('更新成功', 'success')
      } else {
        await createEntity(payload.entity, payload.record)
        showToast('创建成功', 'success')
      }
      editorState.value.visible = false
      await loadData()
    } catch (err: any) {
      showToast(err?.message || '操作失败', 'error')
    }
  }

  async function removeEntity(entity: string, id: string) {
    let msg = '确定要删除该记录吗？'
    if (entity === 'departments') msg = '确定要删除该学院吗？学院下有班级或师生时将无法删除。'
    else if (entity === 'classes') msg = '确定要删除该班级吗？班级下有学生时将无法删除，关联考试的目标班级将被自动清理。'
    const ok = await confirmDialog(msg, { title: '删除确认', confirmText: '删除', danger: true })
    if (!ok) return
    try {
      await deleteEntity(entity, id)
      showToast('删除成功', 'success')
      await loadData()
    } catch (err: any) {
      showToast(err?.message || '删除失败', 'error')
    }
  }

  function openPaperForm(model: Paper | null) {
    paperModel.value = model
    paperFormVisible.value = true
  }

  async function handlePaperSubmit(payload: { entity: string; record: Record<string, unknown> }) {
    try {
      if (payload.record.id) {
        await updateEntity(payload.entity, payload.record)
        showToast('试卷更新成功', 'success')
      } else {
        await createEntity(payload.entity, payload.record)
        showToast('试卷创建成功', 'success')
      }
      paperFormVisible.value = false
      await loadData()
    } catch (err: any) {
      showToast(err?.message || '保存失败', 'error')
    }
  }

  function previewPaper(paper: Paper) {
    previewPaperModel.value = paper
  }

  function reviewSubmission(submission: SubmissionReview) {
    reviewingSubmission.value = submission
  }

  async function handleGrade(payload: { submissionId: string; scores: Record<string, number> }) {
    try {
      await manualGrade(payload.submissionId, payload.scores)
      reviewingSubmission.value = null
      showToast('阅卷完成', 'success')
      await loadData()
    } catch (err: any) {
      showToast(err?.message || '阅卷失败', 'error')
    }
  }

  async function startExam(examId: string) {
    try {
      const detail = await loadExamDetail(examId)
      activeExam.value = detail
    } catch (err: any) {
      showToast(err?.message || '无法进入考试', 'error')
    }
  }

  function handleExamSubmitted() {
    activeExam.value = null
    showToast('交卷成功！', 'success')
    loadData().catch(() => { /* 静默处理刷新失败 */ })
  }

  function retryWrongEntry(entry: WrongBookEntry) {
    retryingEntry.value = entry
  }

  async function handleWrongRetry(payload: { entryId: string; answer: string[] }) {
    try {
      await retryWrongBook(payload.entryId, payload.answer)
      retryingEntry.value = null
      showToast('重做提交成功', 'success')
      await loadData()
    } catch (err: any) {
      showToast(err?.message || '重做失败', 'error')
    }
  }

  async function removeWrongEntry(entryId: string) {
    const ok = await confirmDialog('确定从错题本中移除吗？', { title: '移除确认', confirmText: '移除', danger: true })
    if (!ok) return
    try {
      await removeWrongBook(entryId)
      showToast('已从错题本移除', 'success')
      await loadData()
    } catch (err: any) {
      showToast(err?.message || '移除失败', 'error')
    }
  }

  async function handlePasswordChange(payload: { oldPassword: string; newPassword: string }) {
    try {
      await changePassword(payload.oldPassword, payload.newPassword)
      showToast('密码修改成功', 'success')
    } catch (err: any) {
      showToast(err?.message || '修改失败', 'error')
    }
  }

  function showBatchImport(role: 'student' | 'teacher') {
    batchImportRole.value = role
  }

  async function loadMonitorData(examId: string) {
    if (!examId) { monitorResult.value = null; return }
    try {
      monitorResult.value = await monitorExam(examId)
      monitorExamId.value = examId
    } catch (err: any) {
      showToast(err?.message || '加载监控数据失败', 'error')
    }
  }

  async function handleExportScores() {
    if (!monitorExamId.value) return
    exportLoading.value = true
    try {
      await exportExcelScores(monitorExamId.value)
      showToast('成绩导出成功（Excel）', 'success')
    } catch (err: any) {
      showToast(err?.message || '导出失败', 'error')
    } finally {
      exportLoading.value = false
    }
  }

  async function loadQuestionAnalysisData(examId: string) {
    if (!examId) { questionAnalysisResult.value = null; return }
    try {
      questionAnalysisResult.value = await questionAnalysis(examId)
      questionAnalysisExamId.value = examId
    } catch (err: any) {
      showToast(err?.message || '加载分析数据失败', 'error')
    }
  }

  async function handleAutoGenerate(payload: { name: string; durationMinutes: number; passScore: number; rules: Array<{ type: string; count: number; subject?: string; knowledgePoint?: string; difficulty?: string }> }) {
    try {
      await autoGeneratePaper(payload)
      autoGenVisible.value = false
      await loadData()
      showToast('自动组卷成功！', 'success')
    } catch (err: any) {
      showToast(err?.message || '自动组卷失败', 'error')
    }
  }

  async function loadStudentGrades() {
    try {
      const [trendRes, radarRes] = await Promise.all([scoreTrend(), knowledgeRadar()])
      scoreTrendData.value = trendRes.trend
      subjectMasteryData.value = radarRes.subjectMastery
      kpMasteryData.value = radarRes.knowledgePointMastery
    } catch {
      // silently fail
    }
  }

  // ========== AI Actions ==========

  async function handleAiGenerateQuestions(params: {
    subject: string
    knowledgePoint?: string
    type: string
    difficulty: string
    count: number
  }) {
    aiLoading.value = true
    try {
      const result = await aiGenerateQuestions(params)
      aiQuestions.value = result.questions
      aiQuotaRemaining.value = result.remainingQuota
      aiQuotaUsed.value = result.existingCount
      showToast(`AI 已生成 ${result.totalCount} 道题目，请预览后导入`, 'success')
      return result
    } catch (err: any) {
      showToast(err?.message || 'AI 出题失败', 'error')
      return null
    } finally {
      aiLoading.value = false
    }
  }

  async function handleAiImportQuestions(questions: AiQuestion[]) {
    aiLoading.value = true
    try {
      const result = await aiImportQuestions(questions)
      aiQuotaRemaining.value = result.remainingQuota
      aiQuotaUsed.value = result.totalCount
      aiQuestions.value = []
      await loadData()
      showToast(`成功导入 ${result.importedCount} 道题目`, 'success')
      if (result.errors.length > 0) {
        showToast(`${result.errors.length} 道题目导入失败`, 'error')
      }
      return result
    } catch (err: any) {
      showToast(err?.message || '导入失败', 'error')
      return null
    } finally {
      aiLoading.value = false
    }
  }

  async function handleAiGradeSubmission(submissionId: string) {
    aiGradeLoading.value = true
    try {
      const result = await aiGradeSubmission(submissionId)
      aiGradeResult.value = result
      showToast('AI 预评分完成（仅供参考）', 'success')
      return result
    } catch (err: any) {
      showToast(err?.message || 'AI 评分失败', 'error')
      throw err
    } finally {
      aiGradeLoading.value = false
    }
  }

  async function handleAiPracticeQuestions(params: {
    customPrompt?: string
    subject: string
    type: string
    difficulty: string
    count: number
  }) {
    practiceLoading.value = true
    try {
      const result = await aiPracticeQuestions(params)
      practiceQuestions.value = result.questions
      showToast(`AI 已生成 ${result.totalCount} 条内容`, 'success')
      return result
    } catch (err: any) {
      showToast(err?.message || 'AI 助手请求失败', 'error')
      return null
    } finally {
      practiceLoading.value = false
    }
  }

  /**
   * 流式版 AI 练习（学生端）—— 实时显示思考和生成内容
   */
  function handleAiPracticeQuestionsStream(params: {
    customPrompt?: string
    subject: string
    type: string
    difficulty: string
    count: number
  }) {
    streamingContent.value = ''
    streamingReasoning.value = ''
    streamingActive.value = true
    practiceLoading.value = true

    return aiPracticeQuestionsStream(
      params,
      // onChunk
      (chunk) => {
        if (chunk.type === 'reasoning') {
          streamingReasoning.value += chunk.text
        } else {
          streamingContent.value += chunk.text
        }
      },
      // onComplete
      (data) => {
        streamingActive.value = false
        practiceLoading.value = false
        // Try to parse the generated content as questions
        if (data.content) {
          try {
            const cleaned = data.content.replace(/^```[a-z]*\n?/i, '').replace(/\n?```$/i, '').trim()
            const parsed = JSON.parse(cleaned)
            if (Array.isArray(parsed)) {
              practiceQuestions.value = parsed as AiQuestion[]
              showToast(`AI 已生成 ${parsed.length} 道题目`, 'success')
              return
            }
          } catch {
            // Not valid JSON — keep as raw content
          }
          // Fallback: treat each line as a question
          practiceQuestions.value = [{
            id: 'stream-result',
            title: data.content,
            type: 'single',
            options: [],
            answer: [],
            score: 5,
            explanation: '',
            subject: params.subject,
            knowledgePoint: 'AI生成',
            difficulty: params.difficulty,
          }]
        }
      },
      // onError
      (error) => {
        streamingActive.value = false
        practiceLoading.value = false
        showToast(error || 'AI 流式请求失败', 'error')
      }
    )
  }

  /**
   * 流式版 AI 出题（教师端）
   */
  function handleAiGenerateQuestionsStream(params: {
    subject: string
    knowledgePoint?: string
    type: string
    difficulty: string
    count: number
  }) {
    streamingContent.value = ''
    streamingReasoning.value = ''
    streamingActive.value = true
    aiLoading.value = true

    return aiGenerateQuestionsStream(
      params,
      // onChunk
      (chunk) => {
        if (chunk.type === 'reasoning') {
          streamingReasoning.value += chunk.text
        } else {
          streamingContent.value += chunk.text
        }
      },
      // onComplete
      (data) => {
        streamingActive.value = false
        aiLoading.value = false
        if (data.content) {
          try {
            const cleaned = data.content.replace(/^```[a-z]*\n?/i, '').replace(/\n?```$/i, '').trim()
            const parsed = JSON.parse(cleaned)
            if (Array.isArray(parsed)) {
              aiQuestions.value = parsed as AiQuestion[]
              showToast(`AI 已生成 ${parsed.length} 道题目`, 'success')
              return
            }
          } catch {
            // Not valid JSON
          }
          aiQuestions.value = [{
            id: 'stream-result',
            title: data.content,
            type: params.type as any,
            options: [],
            answer: [],
            score: 5,
            explanation: '',
            subject: params.subject,
            knowledgePoint: params.knowledgePoint || 'AI生成',
            difficulty: params.difficulty,
          }]
        }
      },
      // onError
      (error) => {
        streamingActive.value = false
        aiLoading.value = false
        showToast(error || 'AI 流式请求失败', 'error')
      }
    )
  }

  // ========== Conversation Actions ==========

  /** Load conversation list from server */
  async function handleLoadConversations() {
    if (!currentUser.value) return
    conversationsLoading.value = true
    try {
      const result = await listConversations()
      conversations.value = result.conversations
    } catch (err: any) {
      showToast(err?.message || '加载历史会话失败', 'error')
    } finally {
      conversationsLoading.value = false
    }
  }

  /** Load user preferences for personalized recommendations */
  async function handleLoadPreferences() {
    if (!currentUser.value || preferencesLoading.value) return
    preferencesLoading.value = true
    try {
      userPreferences.value = await getUserPreferences()
    } catch (err) {
      console.warn('[Preferences] Failed to load user preferences:', err)
    } finally {
      preferencesLoading.value = false
    }
  }

  /** Load personalized recommendations and user profile */
  async function loadRecommendations(forceRefresh = false) {
    if (!currentUser.value) return
    // Allow force refresh even if a previous load is in progress
    if (recommendationsLoading.value && !forceRefresh) return
    recommendationsLoading.value = true
    try {
      const recResult = await getRecommendations(forceRefresh)
      recommendations.value = recResult.recommendations
    } catch (err) {
      console.warn('[Recommendations] Failed to load recommendations:', err)
    }
    try {
      userProfile.value = await getUserProfile()
    } catch (err) {
      console.warn('[Recommendations] Failed to load user profile:', err)
    }
    recommendationsLoading.value = false
  }

  /** Submit feedback on a recommendation */
  async function submitFeedback(
    recommendationType: string,
    feedbackType: string,
    detail?: string,
    recommendationContent?: Record<string, unknown>,
  ) {
    try {
      await submitRecommendationFeedback({
        recommendationType,
        feedbackType,
        feedbackDetail: detail,
        recommendationContent,
      })
      const label = feedbackType === 'helpful' ? '感谢反馈，我们会继续推荐类似内容' : '感谢反馈，我们会优化推荐内容'
      showToast(label, 'success')
      // Reload recommendations so feedback takes effect immediately
      loadRecommendations(true)
    } catch (err) {
      console.warn('[Recommendations] Failed to submit feedback:', err)
      showToast('反馈提交失败，请稍后重试', 'error')
    }
  }

  /** Track user behavior (fire-and-forget) */
  function trackBehavior(
    action: string,
    targetType?: string,
    targetId?: string,
    detail?: Record<string, unknown>,
    durationMs?: number,
  ) {
    logBehavior({ action, targetType, targetId, detail, durationMs }).catch(() => {
      // silently ignore behavior logging errors
    })
  }

  /** Search conversations by keyword */
  async function handleSearchConversations(keyword: string): Promise<SearchResultConversation[]> {
    if (!keyword.trim()) {
      await handleLoadConversations()
      return conversations.value as SearchResultConversation[]
    }
    try {
      const result = await searchConversations(keyword.trim())
      return result.conversations
    } catch {
      return []
    }
  }

  /** Create a new conversation and switch to it, tagged with current tab */
  async function handleNewConversation(sessionType?: string) {
    if (!currentUser.value) return
    try {
      const type = sessionType || activeTab.value
      const conv = await createConversation('新对话', undefined, type)
      conv.sessionType = type
      conversations.value.unshift(conv)
      activeConversationId.value = conv.id
      persistedCount[`chat:${conv.id}`] = 0
      persistedCount[`practice:${conv.id}`] = 0
      chatMessages.value = []
      practiceMessages.value = []
    } catch (err: any) {
      showToast(err?.message || '创建会话失败', 'error')
    }
  }

  /** Switch to a conversation and load its messages, auto-switch tab based on sessionType */
  async function handleSwitchConversation(conversationId: string) {
    if (!currentUser.value || activeConversationId.value === conversationId) return
    conversationsLoading.value = true
    try {
      // Find conversation metadata to determine session type
      const conv = conversations.value.find(c => c.id === conversationId)
      if (conv?.sessionType) {
        activeTab.value = conv.sessionType as 'chat' | 'practice'
      }

      const result = await getConversationMessages(conversationId)
      activeConversationId.value = conversationId
      // Deduplicate by message id from DB (content-based dedup was too aggressive —
      // it removed legitimate messages that happened to share the same text)
      const seenIds = new Set<string>()
      const deduped = result.messages.filter(m => {
        if (!m.id) return true // no id → keep
        if (seenIds.has(m.id)) return false
        seenIds.add(m.id)
        return true
      })
      // Load into the appropriate message array based on session type
      const target = activeTab.value === 'practice' ? practiceMessages : chatMessages
      target.value = deduped.map(m => ({
        role: m.role as 'user' | 'assistant',
        content: m.content,
        reasoning: m.reasoning,
      }))
      // Mark all loaded messages as persisted
      persistedCount[`chat:${conversationId}`] = activeTab.value === 'chat' ? target.value.length : 0
      persistedCount[`practice:${conversationId}`] = activeTab.value === 'practice' ? target.value.length : 0
      // Clear the other array
      if (activeTab.value === 'practice') {
        chatMessages.value = []
      } else {
        practiceMessages.value = []
      }
    } catch (err: any) {
      showToast(err?.message || '加载会话失败', 'error')
    } finally {
      conversationsLoading.value = false
    }
  }

  /** Save current messages to the active conversation.
   *  Auto-creates a conversation if none exists yet.
   *  Only saves new messages since last persist (delta) to avoid duplicates.
   *  Pass optional messages array for practice mode (chat and practice track separately). */
  async function handleSaveMessages(messages?: ChatMessage[]) {
    const msgs = messages || chatMessages.value
    if (msgs.length === 0) return

    // Auto-create conversation if needed (first message in a session)
    if (!activeConversationId.value) {
      try {
        if (!currentUser.value) return
        const conv = await createConversation('新对话', undefined, activeTab.value)
        conv.sessionType = activeTab.value
        conversations.value.unshift(conv)
        activeConversationId.value = conv.id
      } catch (err: any) {
        console.error('Failed to auto-create conversation:', err)
        return
      }
    }

    const convId = activeConversationId.value
    if (!convId) return

    // Use separate tracking for chat vs practice since they have independent message arrays
    const modeKey = messages ? `practice:${convId}` : `chat:${convId}`

    // Delta: only send messages that haven't been persisted yet
    const alreadySaved = persistedCount[modeKey] || 0
    const newMessages = msgs.slice(alreadySaved)
    if (newMessages.length === 0) return

    try {
      await appendConversationMessages(convId, newMessages, activeTab.value)
      // Update persisted count for this mode+conversation
      persistedCount[modeKey] = msgs.length
      // Refresh conversation list to update title/time
      handleLoadConversations()
    } catch (err: any) {
      console.error('Failed to save messages:', err)
    }
  }

  /** Delete a conversation */
  async function handleDeleteConversation(conversationId: string) {
    try {
      await deleteConversation(conversationId)
      conversations.value = conversations.value.filter(c => c.id !== conversationId)
      delete persistedCount[`chat:${conversationId}`]
      delete persistedCount[`practice:${conversationId}`]
      if (activeConversationId.value === conversationId) {
        activeConversationId.value = null
        chatMessages.value = []
        practiceMessages.value = []
      }
      showToast('会话已删除', 'info')
    } catch (err: any) {
      showToast(err?.message || '删除失败', 'error')
    }
  }

  // ========== Typewriter & Feedback Helpers ==========

  /** Pre-output feedback messages for chat mode */
  const CHAT_PRE_FEEDBACK = [
    '我正在为你准备详细的回答 ✨',
    '让我认真思考一下你的问题 🤔',
    '好问题！让我为你详细解答 💡',
    '正在构思最佳答案，请稍候... 📝',
  ]
  /** In-output feedback messages for chat mode */
  const CHAT_IN_FEEDBACK = [
    '继续完善回答中...',
    '正在补充更多细节...',
    '马上就好，正在整理思路...',
    '快完成了，请稍候...',
  ]
  /** Pre-output feedback messages for practice mode */
  const PRACTICE_PRE_FEEDBACK = [
    '正在为你精心出题，请稍候 📝',
    '让我为你准备高质量的练习题 ✨',
    '好题目值得等待，正在生成中... 🎯',
  ]
  /** In-output feedback messages for practice mode */
  const PRACTICE_IN_FEEDBACK = [
    '题目生成中，已接近完成...',
    '正在完善最后一道题...',
    '快好了，正在添加解析...',
  ]

  /** Pick a random message from pool, avoiding consecutive repeats */
  function pickFeedback(pool: string[], lastIndex: number): { message: string; index: number } {
    let idx: number
    do { idx = Math.floor(Math.random() * pool.length) } while (idx === lastIndex && pool.length > 1)
    return { message: pool[idx], index: idx }
  }

  let chatPreFeedbackIdx = -1
  let chatInFeedbackIdx = -1
  let practicePreFeedbackIdx = -1
  let practiceInFeedbackIdx = -1

  /** Typewriter tick: advance one character with natural delay */
  function chatTypewriterTick() {
    const target = chatStreamingContent.value
    if (chatTypewriterIndex >= target.length) {
      // All current content displayed; wait for more or complete
      if (!chatStreamingActive.value) {
        // Streaming done — finish
        chatTypewriterContent.value = target
      }
      return
    }

    // Advance 1-3 characters (faster for ASCII)
    let step = 1
    const ch = target[chatTypewriterIndex]
    if (/[a-zA-Z0-9\s]/.test(ch)) {
      step = Math.random() < 0.3 ? 2 : 1
    }
    chatTypewriterIndex = Math.min(chatTypewriterIndex + step, target.length)
    chatTypewriterContent.value = target.substring(0, chatTypewriterIndex)

    // Natural delay with punctuation pauses
    const baseInterval = 80 // ~12.5 chars/sec
    const jitter = baseInterval * 0.3 * (Math.random() * 2 - 1)
    let delay = baseInterval + jitter
    // Punctuation pauses
    if (chatTypewriterIndex > 0) {
      const prevChar = target[chatTypewriterIndex - 1]
      const pauseMap: Record<string, number> = {
        '。': 180, '？': 180, '！': 180, '.': 150, '?': 150, '!': 150,
        '，': 100, '；': 100, '：': 100, ',': 80, ';': 80, ':': 80,
        '\n': 120, '…': 200,
      }
      delay += pauseMap[prevChar] || 0
    }
    delay = Math.min(Math.max(delay, 30), 200)

    chatTypewriterTimerId = setTimeout(chatTypewriterTick, delay)
  }

  /** Start the typewriter effect */
  function startChatTypewriter() {
    stopChatTypewriter()
    chatTypewriterIndex = 0
    chatTypewriterContent.value = ''
    chatTypewriterTick()
  }

  /** Stop the typewriter effect */
  function stopChatTypewriter() {
    if (chatTypewriterTimerId !== null) {
      clearTimeout(chatTypewriterTimerId)
      chatTypewriterTimerId = null
    }
  }

  /** Finish typewriter immediately (skip animation) */
  function finishChatTypewriter() {
    stopChatTypewriter()
    chatTypewriterContent.value = chatStreamingContent.value
    chatTypewriterIndex = chatStreamingContent.value.length
  }

  /** Start in-output feedback timer for chat */
  function startChatFeedbackTimer() {
    stopChatFeedbackTimer()
    chatFeedbackTimerId = setInterval(() => {
      if (chatStreamingActive.value && chatStreamingContent.value.length > 500) {
        const { message, index } = pickFeedback(CHAT_IN_FEEDBACK, chatInFeedbackIdx)
        chatInFeedbackIdx = index
        chatFeedbackMessage.value = message
        chatFeedbackVisible.value = true
        // Auto-hide after 3 seconds
        setTimeout(() => {
          if (chatFeedbackMessage.value === message) {
            chatFeedbackVisible.value = false
          }
        }, 3000)
      }
    }, 8000)
  }

  /** Stop in-output feedback timer for chat */
  function stopChatFeedbackTimer() {
    if (chatFeedbackTimerId !== null) {
      clearInterval(chatFeedbackTimerId)
      chatFeedbackTimerId = null
    }
  }

  /** Start in-output feedback timer for practice */
  function startPracticeFeedbackTimer() {
    stopPracticeFeedbackTimer()
    practiceFeedbackTimerId = setInterval(() => {
      if (practiceStreamingActive.value && practiceStreamingContent.value.length > 300) {
        const { message, index } = pickFeedback(PRACTICE_IN_FEEDBACK, practiceInFeedbackIdx)
        practiceInFeedbackIdx = index
        practiceFeedbackMessage.value = message
        practiceFeedbackVisible.value = true
        setTimeout(() => {
          if (practiceFeedbackMessage.value === message) {
            practiceFeedbackVisible.value = false
          }
        }, 3000)
      }
    }, 8000)
  }

  /** Stop in-output feedback timer for practice */
  function stopPracticeFeedbackTimer() {
    if (practiceFeedbackTimerId !== null) {
      clearInterval(practiceFeedbackTimerId)
      practiceFeedbackTimerId = null
    }
  }

  /** Try to parse complete questions from streaming content for question-by-question display */
  function tryParseStreamingQuestions(content: string): Array<Record<string, unknown>> {
    const result: Array<Record<string, unknown>> = []
    // Extract complete JSON objects from the streaming content
    const objectPattern = /\{\s*(?:"[^"]*"\s*:\s*(?:"[^"]*"|\[[^\]]*\]|\d+|true|false)\s*,?\s*)+\}/g
    let match
    while ((match = objectPattern.exec(content)) !== null) {
      try {
        const obj = JSON.parse(match[0])
        if (obj.title && obj.type) {
          const exists = result.some(q => q.title === obj.title)
          if (!exists) {
            // Mark as new if not already in the current streaming questions list
            const alreadySeen = practiceStreamingQuestions.value.some(q => q.title === obj.title)
            result.push({ ...obj, _isNew: !alreadySeen })
          }
        }
      } catch { /* skip */ }
    }
    return result
  }

  // ========== Chat Actions ==========

  /** Send a chat message and get streaming response */
  function handleChatSend(message: string) {
    if (!message.trim() || chatLoading.value) return

    // Add user message
    chatMessages.value.push({ role: 'user', content: message })

    // Create a placeholder for the AI response
    const aiMsgIndex = chatMessages.value.length
    const startTime = Date.now()
    chatMessages.value.push({ role: 'assistant', content: '', _startedAt: startTime } as any)

    chatStreamingContent.value = ''
    chatStreamingReasoning.value = ''
    chatStreamingActive.value = true
    chatStreamStartTime.value = startTime
    chatLoading.value = true

    // Pre-output feedback: show encouraging message immediately
    const { message: preMsg, index: preIdx } = pickFeedback(CHAT_PRE_FEEDBACK, chatPreFeedbackIdx)
    chatPreFeedbackIdx = preIdx
    chatFeedbackMessage.value = preMsg
    chatFeedbackVisible.value = true

    // Build context: only use chat messages (no cross-tab contamination from practice)
    let contextMessages = chatMessages.value.slice(0, -1)

    // Timeout warning: if no output after 5s, show slow hint
    if (chatTimeoutId) clearTimeout(chatTimeoutId)
    chatTimeoutId = setTimeout(() => {
      if (chatStreamingActive.value && !chatStreamingContent.value && !chatStreamingReasoning.value) {
        chatStreamingHint.value = '⏳ 正在思考中，请稍候...'
      }
    }, 5000)
    // Second timeout: more encouraging message
    const chatTimeout2 = setTimeout(() => {
      if (chatStreamingActive.value && !chatStreamingContent.value) {
        chatStreamingHint.value = '⏳ 好答案值得等待，AI正在认真构思...'
      }
    }, 15000)

    // Start in-output feedback timer
    startChatFeedbackTimer()

    chatAbortController = apiAiChatStream(
      { message, messages: contextMessages, deepThinking: deepThinkingEnabled.value },
      (chunk) => {
        if (chunk.type === 'reasoning') {
          chatStreamingReasoning.value += chunk.text
        } else {
          chatStreamingContent.value += chunk.text
          // Hide pre-output feedback once content starts arriving
          if (chatFeedbackVisible.value && chatStreamingContent.value.length > 0) {
            chatFeedbackVisible.value = false
          }
          // Start typewriter on first content chunk
          if (chatTypewriterIndex === 0 && chatStreamingContent.value.length > 0) {
            startChatTypewriter()
          }
          // RAF-batched display update — coalesce multiple chunks per frame
          if (!chatDisplayRafId) {
            chatDisplayRafId = requestAnimationFrame(() => {
              chatDisplayContent.value = chatStreamingContent.value
              chatDisplayRafId = 0
            })
          }
        }
      },
      (data) => {
        chatStreamingHint.value = ''
        chatFeedbackVisible.value = false
        stopChatFeedbackTimer()
        // Flush any pending RAF display update
        if (chatDisplayRafId) { cancelAnimationFrame(chatDisplayRafId); chatDisplayRafId = 0 }
        chatDisplayContent.value = ''
        if (chatTimeoutId) { clearTimeout(chatTimeoutId); chatTimeoutId = null }
        clearTimeout(chatTimeout2)
        chatStreamingActive.value = false
        chatLoading.value = false
        // Finish typewriter immediately on complete
        finishChatTypewriter()
        if (data.content) {
          // 剥离 AI 思考文本，将其移入 reasoning 字段
          const { content, thinking } = stripThinkingFromContent(data.content)
          chatMessages.value[aiMsgIndex].content = content
          if (thinking) {
            const existing = data.reasoning || chatStreamingReasoning.value || ''
            chatMessages.value[aiMsgIndex].reasoning = existing
              ? thinking + '\n\n' + existing
              : thinking
          }
        }
        // Save reasoning for later review (collapsed fold)
        if (data.reasoning && !chatMessages.value[aiMsgIndex].reasoning) {
          chatMessages.value[aiMsgIndex].reasoning = data.reasoning
        } else if (!chatMessages.value[aiMsgIndex].reasoning && chatStreamingReasoning.value) {
          chatMessages.value[aiMsgIndex].reasoning = chatStreamingReasoning.value
        }
        // Compute response duration
        const msg = chatMessages.value[aiMsgIndex] as any
        if (msg._startedAt) {
          msg.duration = (Date.now() - msg._startedAt) / 1000
          delete msg._startedAt
        }
        // Persist to conversation history
        handleSaveMessages()
      },
      (error) => {
        chatStreamingHint.value = ''
        chatFeedbackVisible.value = false
        stopChatFeedbackTimer()
        stopChatTypewriter()
        if (chatDisplayRafId) { cancelAnimationFrame(chatDisplayRafId); chatDisplayRafId = 0 }
        chatDisplayContent.value = ''
        if (chatTimeoutId) { clearTimeout(chatTimeoutId); chatTimeoutId = null }
        clearTimeout(chatTimeout2)
        chatStreamingActive.value = false
        chatLoading.value = false
        const cleanError = (error || 'AI 请求失败，请稍后重试').replace(/^HTTP \d+: /, '')
        chatMessages.value[aiMsgIndex].content = '❌ ' + cleanError
        ;(chatMessages.value[aiMsgIndex] as any)._retryMessage = message
        showToast(cleanError, 'error')
      },
      () => {
        chatStreamingHint.value = ''
        chatFeedbackVisible.value = false
        stopChatFeedbackTimer()
        stopChatTypewriter()
        if (chatDisplayRafId) { cancelAnimationFrame(chatDisplayRafId); chatDisplayRafId = 0 }
        chatDisplayContent.value = ''
        if (chatTimeoutId) { clearTimeout(chatTimeoutId); chatTimeoutId = null }
        clearTimeout(chatTimeout2)
        chatStreamingActive.value = false
        chatLoading.value = false
      }
    )
  }

  /** Stop the ongoing chat stream */
  function handleChatStop() {
    if (chatAbortController) {
      chatAbortController.abort()
      chatAbortController = null
    }
    if (chatTimeoutId) { clearTimeout(chatTimeoutId); chatTimeoutId = null }
    stopChatTypewriter()
    stopChatFeedbackTimer()
    chatFeedbackVisible.value = false
    chatStreamingActive.value = false
    chatLoading.value = false
  }

  /** Clear chat history */
  function clearChatMessages() {
    chatMessages.value = []
    chatStreamingContent.value = ''
    chatStreamingReasoning.value = ''
    chatStreamingActive.value = false
    chatStreamingHint.value = ''
    chatDisplayContent.value = ''
    chatTypewriterContent.value = ''
    chatFeedbackMessage.value = ''
    chatFeedbackVisible.value = false
    stopChatTypewriter()
    stopChatFeedbackTimer()
  }

  /** Retry a failed AI message — removes the error message and re-sends */
  function handleRetryMessage(msgIndex: number, tab: 'chat' | 'practice') {
    const messages = tab === 'chat' ? chatMessages.value : practiceMessages.value
    const failedMsg = messages[msgIndex] as any
    const retryText = failedMsg?._retryMessage
    if (!retryText) return

    // Remove the failed assistant message and the user message before it
    const removed = msgIndex > 0 && messages[msgIndex - 1]?.role === 'user' ? 2 : 1
    if (msgIndex > 0 && messages[msgIndex - 1]?.role === 'user') {
      messages.splice(msgIndex - 1, 2)
    } else {
      messages.splice(msgIndex, 1)
    }

    // Adjust persisted count: removed messages are no longer in the array
    const convId = activeConversationId.value
    const modeKey = tab === 'practice' ? `practice:${convId}` : `chat:${convId}`
    if (convId && persistedCount[modeKey] != null) {
      persistedCount[modeKey] = Math.max(0, persistedCount[modeKey] - removed)
    }

    // Re-send
    if (tab === 'chat') {
      handleChatSend(retryText)
    } else {
      handlePracticeSend(retryText)
    }
  }

  // ========== Practice (Question Generation) Actions ==========

  /** Send a practice prompt and get streaming question generation */
  function handlePracticeSend(message: string) {
    if (!message.trim() || practSessionLoading.value) return

    practiceMessages.value.push({ role: 'user', content: message })
    const aiMsgIndex = practiceMessages.value.length
    const startTime = Date.now()
    practiceMessages.value.push({ role: 'assistant', content: '', _startedAt: startTime } as any)

    practiceStreamingContent.value = ''
    practiceStreamingReasoning.value = ''
    practiceStreamingActive.value = true
    practiceStreamStartTime.value = startTime
    practSessionLoading.value = true
    // Reset question-by-question streaming
    practiceStreamingQuestions.value = []

    // Pre-output feedback: show encouraging message immediately
    const { message: preMsg, index: preIdx } = pickFeedback(PRACTICE_PRE_FEEDBACK, practicePreFeedbackIdx)
    practicePreFeedbackIdx = preIdx
    practiceFeedbackMessage.value = preMsg
    practiceFeedbackVisible.value = true

    // Practice mode: use the user's message directly (no cross-tab contamination from chat)
    let enrichedPrompt = message

    if (practiceTimeoutId) clearTimeout(practiceTimeoutId)
    practiceTimeoutId = setTimeout(() => {
      if (practiceStreamingActive.value && !practiceStreamingContent.value && !practiceStreamingReasoning.value) {
        practiceStreamingHint.value = '⏳ 正在为你精心出题，请稍候...'
      }
    }, 5000)
    // Second timeout: show more encouraging message
    const practiceTimeout2 = setTimeout(() => {
      if (practiceStreamingActive.value && !practiceStreamingContent.value) {
        practiceStreamingHint.value = '⏳ 题目生成中，好题值得等待...'
      }
    }, 15000)

    // Start in-output feedback timer
    startPracticeFeedbackTimer()

    // Smart extraction from user message: infer type/subject/difficulty/count
    const inferredType = /多选/.test(message) ? 'multiple'
      : /判断/.test(message) ? 'judge'
      : /填空/.test(message) ? 'fill'
      : /简答/.test(message) ? 'short'
      : /编程|代码/.test(message) ? 'coding'
      : /单选/.test(message) ? 'single'
      : ''
    const inferredDifficulty = /简单|基础|入门/.test(message) ? 'easy'
      : /困难|较难|高难|挑战/.test(message) ? 'hard'
      : ''
    const countMatch = message.match(/(\d+)\s*道/)
    const inferredCount = countMatch ? Math.min(parseInt(countMatch[1]), 20) : 0
    // Extract subject from user message for practice session metadata
    const subjectPatterns: [RegExp, string][] = [
      [/高等数学|微积分|线性代数|高数/, '高等数学'],
      [/大学语文|语文/, '大学语文'],
      [/马克思主义|马原|马哲/, '马克思主义'],
      [/政治|毛概|中特/, '政治'],
      [/中国近现代史|近现代史|近代史/, '中国近现代史'],
      [/大学英语|英语/, '大学英语'],
      [/大学物理|物理/, '大学物理'],
      [/计算机基础|计算机/, '计算机基础'],
      [/计算机网络|网络/, '计算机网络'],
      [/数据结构/, '数据结构'],
      [/操作系统/, '操作系统'],
      [/数据库/, '数据库'],
      [/JavaScript|JS/i, 'JavaScript'],
      [/Python/i, 'Python'],
      [/Java(?!Script)/i, 'Java'],
    ]
    let inferredSubject = ''
    for (const [pattern, subject] of subjectPatterns) {
      if (pattern.test(message)) { inferredSubject = subject; break }
    }

    practiceAbortController = aiPracticeQuestionsStream(
      {
        customPrompt: enrichedPrompt,
        subject: '',
        type: inferredType || 'single',
        difficulty: inferredDifficulty || 'medium',
        count: inferredCount || 5,
        deepThinking: false, // practice mode skips deep thinking — generate cards directly
      },
      (chunk) => {
        if (chunk.type === 'reasoning') {
          practiceStreamingReasoning.value += chunk.text
        } else {
          practiceStreamingContent.value += chunk.text
          // Hide pre-output feedback once content starts arriving
          if (practiceFeedbackVisible.value && practiceStreamingContent.value.length > 0) {
            practiceFeedbackVisible.value = false
          }
          // Try to parse complete questions for question-by-question display
          const parsed = tryParseStreamingQuestions(practiceStreamingContent.value)
          if (parsed.length > practiceStreamingQuestions.value.length) {
            practiceStreamingQuestions.value = parsed
          }
          // RAF-batched display update
          if (!practiceDisplayRafId) {
            practiceDisplayRafId = requestAnimationFrame(() => {
              practiceDisplayContent.value = practiceStreamingContent.value
              practiceDisplayRafId = 0
            })
          }
        }
      },
      (data) => {
        practiceStreamingHint.value = ''
        practiceFeedbackVisible.value = false
        stopPracticeFeedbackTimer()
        if (practiceDisplayRafId) { cancelAnimationFrame(practiceDisplayRafId); practiceDisplayRafId = 0 }
        practiceDisplayContent.value = ''
        if (practiceTimeoutId) { clearTimeout(practiceTimeoutId); practiceTimeoutId = null }
        clearTimeout(practiceTimeout2)
        practiceStreamingActive.value = false
        practSessionLoading.value = false
        if (data.content) {
          // 剥离 AI 思考文本，将其移入 reasoning 字段
          const { content, thinking } = stripThinkingFromContent(data.content)
          practiceMessages.value[aiMsgIndex].content = content
          if (thinking) {
            const existing = data.reasoning || practiceStreamingReasoning.value || ''
            practiceMessages.value[aiMsgIndex].reasoning = existing
              ? thinking + '\n\n' + existing
              : thinking
          }
        }
        // Save reasoning for later review (collapsed fold)
        if (data.reasoning && !practiceMessages.value[aiMsgIndex].reasoning) {
          practiceMessages.value[aiMsgIndex].reasoning = data.reasoning
        } else if (!practiceMessages.value[aiMsgIndex].reasoning && practiceStreamingReasoning.value) {
          practiceMessages.value[aiMsgIndex].reasoning = practiceStreamingReasoning.value
        }
        // Compute response duration
        const msg = practiceMessages.value[aiMsgIndex] as any
        if (msg._startedAt) {
          msg.duration = (Date.now() - msg._startedAt) / 1000
          delete msg._startedAt
        }
        // Persist practice messages to conversation history
        handleSaveMessages(practiceMessages.value)

        // 自动创建练习会话并保存生成的题目
        try {
          const cleaned = (data.content || '').replace(/^```[a-z]*\n?/i, '').replace(/\n?```$/i, '').trim()
          const parsed = JSON.parse(cleaned)
          if (Array.isArray(parsed) && parsed.length > 0) {
            handleCreatePracticeSession(parsed, inferredSubject || undefined)
          }
        } catch {
          // Not valid JSON — try with the full content
          try {
            const content = data.content || ''
            const arrStart = content.indexOf('[')
            const arrEnd = content.lastIndexOf(']')
            if (arrStart >= 0 && arrEnd > arrStart) {
              const jsonStr = content.substring(arrStart, arrEnd + 1)
              const parsed = JSON.parse(jsonStr)
              if (Array.isArray(parsed) && parsed.length > 0) {
                handleCreatePracticeSession(parsed, inferredSubject || undefined)
              }
            }
          } catch { /* not parseable, skip session creation */ }
        }
      },
      (error) => {
        practiceStreamingHint.value = ''
        practiceFeedbackVisible.value = false
        stopPracticeFeedbackTimer()
        if (practiceDisplayRafId) { cancelAnimationFrame(practiceDisplayRafId); practiceDisplayRafId = 0 }
        practiceDisplayContent.value = ''
        if (practiceTimeoutId) { clearTimeout(practiceTimeoutId); practiceTimeoutId = null }
        clearTimeout(practiceTimeout2)
        practiceStreamingActive.value = false
        practSessionLoading.value = false
        const cleanError = (error || 'AI 请求失败，请稍后重试').replace(/^HTTP \d+: /, '')
        practiceMessages.value[aiMsgIndex].content = '❌ ' + cleanError
        ;(practiceMessages.value[aiMsgIndex] as any)._retryMessage = message
        showToast(cleanError, 'error')
      },
      () => {
        practiceStreamingHint.value = ''
        practiceFeedbackVisible.value = false
        stopPracticeFeedbackTimer()
        if (practiceDisplayRafId) { cancelAnimationFrame(practiceDisplayRafId); practiceDisplayRafId = 0 }
        practiceDisplayContent.value = ''
        if (practiceTimeoutId) { clearTimeout(practiceTimeoutId); practiceTimeoutId = null }
        clearTimeout(practiceTimeout2)
        practiceStreamingActive.value = false
        practSessionLoading.value = false
      }
    )
  }

  /** Stop the ongoing practice stream */
  function handlePracticeStop() {
    if (practiceAbortController) {
      practiceAbortController.abort()
      practiceAbortController = null
    }
    if (practiceTimeoutId) { clearTimeout(practiceTimeoutId); practiceTimeoutId = null }
    stopPracticeFeedbackTimer()
    practiceFeedbackVisible.value = false
    practiceStreamingActive.value = false
    practSessionLoading.value = false
  }

  /** Clear practice history */
  function clearPracticeMessages() {
    practiceMessages.value = []
    practiceStreamingContent.value = ''
    practiceStreamingReasoning.value = ''
    practiceStreamingActive.value = false
    practiceStreamingHint.value = ''
    practiceDisplayContent.value = ''
    practiceStreamingQuestions.value = []
    practiceFeedbackMessage.value = ''
    practiceFeedbackVisible.value = false
    stopPracticeFeedbackTimer()
  }

  // ========== Practice Session Persistence Actions ==========

  /** 恢复用户活跃的练习会话（页面刷新时调用） */
  async function restorePracticeSession() {
    if (!currentUser.value) return
    practiceSessionLoading.value = true
    try {
      const result = await getActivePracticeSession()
      if (result.session) {
        activePracticeSession.value = result.session
        // 恢复练习消息：从会话的题目数据重建 AI 消息
        if (result.session.questions && result.session.questions.length > 0) {
          // 重建用户消息（从 conversation 获取，或使用占位符）
          const userMsg = result.session.subject
            ? `帮我出关于「${result.session.subject}」的练习题`
            : '帮我出练习题'

          // 重建 AI 消息内容：将题目数据序列化为 JSON
          const questionsData = result.session.questions.map(q => q.questionData)
          const aiContent = JSON.stringify(questionsData)

          practiceMessages.value = [
            { role: 'user', content: userMsg },
            { role: 'assistant', content: aiContent },
          ]

          // 标记已持久化的消息数
          if (activeConversationId.value) {
            persistedCount[`practice:${activeConversationId.value}`] = 2
          }
        }
      }
    } catch (err) {
      console.warn('[PracticeSession] Failed to restore session:', err)
    } finally {
      practiceSessionLoading.value = false
    }
  }

  /** 创建新的练习会话并保存题目 */
  async function handleCreatePracticeSession(questions: AiQuestion[], subject?: string) {
    if (!currentUser.value) return
    try {
      // 如果已有活跃会话，先将其标记为 abandoned
      if (activePracticeSession.value && activePracticeSession.value.status === 'active') {
        // 删除旧会话（未提交的）
        try {
          await apiDeletePracticeSession(activePracticeSession.value.id)
        } catch { /* ignore */ }
      }

      // 创建新会话
      const session = await createPracticeSession({
        subject: subject || 'AI练习',
        conversationId: activeConversationId.value || undefined,
      })
      activePracticeSession.value = session

      // 保存题目到会话
      if (questions.length > 0) {
        await savePracticeSessionQuestions(session.id, questions)
        activePracticeSession.value.questionCount = questions.length
      }
    } catch (err) {
      console.error('[PracticeSession] Failed to create session:', err)
    }
  }

  /** 自动保存用户答案（防抖，2秒延迟） */
  function scheduleAnswerSave(sessionId: string, answers: Array<{ questionIndex: number; answer: string[] | string }>) {
    if (practiceAnswerSaveTimer) clearTimeout(practiceAnswerSaveTimer)
    practiceAnswerSaveTimer = setTimeout(async () => {
      try {
        await savePracticeSessionAnswers(sessionId, answers)
      } catch (err) {
        console.warn('[PracticeSession] Failed to auto-save answers:', err)
      }
    }, 2000)
  }

  /** 提交练习会话 */
  async function handleSubmitPracticeSession(
    sessionId: string,
    answers: Array<{ questionIndex: number; answer: string[] | string }>,
    records: Array<Record<string, unknown>>,
  ) {
    try {
      const result = await submitPracticeSession(sessionId, answers)
      // 同时保存练习记录到 wrong_book_entry（兼容现有逻辑）
      if (records.length > 0) {
        try { await savePracticeRecords(records) } catch { /* ignore */ }
      }
      // 更新本地状态
      if (activePracticeSession.value?.id === sessionId) {
        activePracticeSession.value.status = 'submitted'
        activePracticeSession.value.correctCount = result.correctCount
        activePracticeSession.value.totalScore = result.totalScore
        activePracticeSession.value.earnedScore = result.earnedScore
      }
      await loadData()
      return result
    } catch (err: any) {
      showToast(err?.message || '提交练习失败', 'error')
      throw err
    }
  }

  async function handleAiExplainAnswer(params: {
    question: string | { title: string }
    studentAnswer: string[]
    correctAnswer: string[]
  }) {
    try {
      return await aiExplainAnswer(params)
    } catch (err: any) {
      showToast(err?.message || '获取 AI 解析失败', 'error')
      return { isCorrect: false, explanation: 'AI 解析暂不可用', tips: '请稍后重试' }
    }
  }

  async function handleSavePracticeRecords(records: Array<Record<string, unknown>>) {
    try {
      const result = await savePracticeRecords(records)
      await loadData()
      showToast(`已保存 ${result.savedCount} 条练习记录`, 'success')
      return result
    } catch (err: any) {
      showToast(err?.message || '保存练习记录失败', 'error')
      throw err
    }
  }

  function clearAiQuestions() {
    aiQuestions.value = []
  }

  function clearAiGradeResult() {
    aiGradeResult.value = null
  }

  function clearPracticeQuestions() {
    practiceQuestions.value = []
  }

  // ========== Quota & Batch Actions ==========

  async function loadQuotaData() {
    try {
      quotaData.value = await loadQuotas()
    } catch {
      // silently fail
    }
  }

  // ========== Notification Actions ==========

  async function loadNotificationData() {
    try {
      const result = await loadNotifications()
      notifications.value = result.notifications
      unreadNotificationCount.value = result.unreadCount
    } catch { /* silent */ }
  }

  async function handleMarkNotificationRead(id: string) {
    try {
      await markNotificationRead(id)
      const notif = notifications.value.find(n => n.id === id)
      if (notif) notif.isRead = true
      unreadNotificationCount.value = Math.max(0, unreadNotificationCount.value - 1)
    } catch (err: any) {
      showToast(err?.message || '标记失败', 'error')
    }
  }

  async function handleMarkAllNotificationsRead() {
    try {
      await markAllNotificationsRead()
      notifications.value.forEach(n => { n.isRead = true })
      unreadNotificationCount.value = 0
    } catch (err: any) {
      showToast(err?.message || '操作失败', 'error')
    }
  }

  async function handleCreateNotification(data: { title: string; content: string; type?: string; targetRole?: string; targetClassId?: string }) {
    try {
      await createNotification(data)
      showToast('通知发布成功', 'success')
    } catch (err: any) {
      showToast(err?.message || '发布失败', 'error')
    }
  }

  async function handleBatchDeleteQuestions(ids: string[]) {
    try {
      const result = await batchDeleteQuestions(ids)
      await loadData()
      await loadQuotaData()
      showToast(`成功删除 ${result.deletedCount} 道题目`, 'success')
      if (result.failedCount > 0) {
        showToast(`${result.failedCount} 道题目删除失败`, 'error')
      }
      return result
    } catch (err: any) {
      showToast(err?.message || '批量删除失败', 'error')
      throw err
    }
  }

  async function handleBatchRestoreQuestions(ids: string[]) {
    try {
      const result = await batchRestoreQuestions(ids)
      await loadData()
      await loadQuotaData()
      showToast(`成功恢复 ${result.restoredCount} 道题目`, 'success')
      return result
    } catch (err: any) {
      showToast(err?.message || '批量恢复失败', 'error')
      throw err
    }
  }

  async function handleBatchRemoveWrongBook(ids: string[]) {
    try {
      const result = await batchRemoveWrongBook(ids)
      await loadData()
      await loadQuotaData()
      showToast(`成功移除 ${result.removedCount} 条错题记录`, 'success')
      return result
    } catch (err: any) {
      showToast(err?.message || '批量移除失败', 'error')
      throw err
    }
  }

  return {
    // State
    bootstrap,
    loginLoading,
    loginMessage,
    authReady,
    editorState,
    paperFormVisible,
    paperModel,
    previewPaperModel,
    reviewingSubmission,
    retryingEntry,
    batchImportRole,
    activeExam,
    monitorResult,
    monitorExamId,
    exportLoading,
    questionAnalysisResult,
    questionAnalysisExamId,
    scoreTrendData,
    subjectMasteryData,
    kpMasteryData,
    wrongBookSubjectFilter,
    autoGenVisible,
    confirmState,
    aiQuestions,
    aiLoading,
    aiQuotaRemaining,
    aiQuotaUsed,
    aiGradeResult,
    aiGradeLoading,
    practiceQuestions,
    practiceLoading,
    quotaData,
    toasts,

    // Computed
    role,
    isAdmin,
    isTeacher,
    isStudent,
    currentUser,
    students,
    teachers,
    studentCount,
    teacherCount,
    myQuestions,
    myPapers,
    myExams,
    mySubmissions,
    pendingGradeCount,
    availableExams,
    mySubmissionRecords,
    sortedLogs,
    wrongBookSubjects,
    filteredWrongBookEntries,
    menuItems,
    subjectChartOption,
    scoreTrendOption,
    knowledgeRadarOption,
    scoreDistOption,
    passRateOption,
    classComparisonOption,
    questionAnalysisChartOption,
    kpAnalysisChartOption,

    // Helpers
    className,
    departmentName,
    showToast,
    confirmDialog,
    handleConfirmOk,
    handleConfirmCancel,

    // Paginated question bank
    paginatedQuestions,
    totalQuestions,
    currentPage,
    pageSize,
    questionsLoading,
    loadQuestionsPage,

    // Paginated users (admin)
    paginatedUsers,
    totalUsers,
    usersCurrentPage,
    usersPageSize,
    usersLoading,
    loadUsersPage,

    // Paginated logs (admin)
    paginatedLogs,
    totalLogs,
    logsCurrentPage,
    logsPageSize,
    logsLoading,
    loadLogsPage,

    // Paginated wrong book (student)
    paginatedWrongBook,
    totalWrongBook,
    wrongBookCurrentPage,
    wrongBookPageSize,
    wrongBookLoading,
    loadWrongBookPage,

    // Actions
    initAuth,
    login,
    loadData,
    logout,
    closeAllModals,
    openEditor,
    handleEntitySubmit,
    removeEntity,
    openPaperForm,
    handlePaperSubmit,
    previewPaper,
    reviewSubmission,
    handleGrade,
    startExam,
    handleExamSubmitted,
    retryWrongEntry,
    handleWrongRetry,
    removeWrongEntry,
    handlePasswordChange,
    showBatchImport,
    loadMonitorData,
    handleExportScores,
    loadQuestionAnalysisData,
    handleAutoGenerate,
    loadStudentGrades,
    streamingContent,
    streamingReasoning,
    streamingActive,

    // Chat
    chatMessages,
    chatLoading,
    chatStreamingContent,
    chatStreamingReasoning,
    chatStreamingActive,
    chatStreamStartTime,
    chatStreamingHint,
    chatDisplayContent,
    chatTypewriterContent,
    chatFeedbackMessage,
    chatFeedbackVisible,
    handleChatSend,
    handleChatStop,
    clearChatMessages,
    handleRetryMessage,

    // Deep thinking toggle
    deepThinkingEnabled,

    // Current session tab
    activeTab,

    // Conversation history
    conversations,
    activeConversationId,
    conversationsLoading,
    handleLoadConversations,
    handleNewConversation,
    handleSwitchConversation,
    handleSaveMessages,
    handleDeleteConversation,
    handleSearchConversations,

    // User preferences (personalized recommendations)
    userPreferences,
    preferencesLoading,
    handleLoadPreferences,

    // Recommendations
    recommendations,
    recommendationsLoading,
    userProfile,
    loadRecommendations,
    submitFeedback,
    trackBehavior,

    // Practice (separate question generation session)
    practiceMessages,
    practSessionLoading,
    practiceStreamingContent,
    practiceStreamingReasoning,
    practiceStreamingActive,
    practiceStreamStartTime,
    practiceStreamingHint,
    practiceDisplayContent,
    practiceStreamingQuestions,
    practiceFeedbackMessage,
    practiceFeedbackVisible,
    handlePracticeSend,
    handlePracticeStop,
    clearPracticeMessages,

    // Practice session persistence
    activePracticeSession,
    practiceSessionLoading,
    restorePracticeSession,
    handleCreatePracticeSession,
    scheduleAnswerSave,
    handleSubmitPracticeSession,

    handleAiGenerateQuestions,
    handleAiGenerateQuestionsStream,
    handleAiImportQuestions,
    handleAiGradeSubmission,
    handleAiPracticeQuestions,
    handleAiPracticeQuestionsStream,
    handleAiExplainAnswer,
    handleSavePracticeRecords,
    clearAiQuestions,
    clearAiGradeResult,
    clearPracticeQuestions,
    loadQuotaData,
    handleBatchDeleteQuestions,
    handleBatchRestoreQuestions,
    handleBatchRemoveWrongBook,

    // Notifications
    notifications,
    unreadNotificationCount,
    loadNotificationData,
    handleMarkNotificationRead,
    handleMarkAllNotificationsRead,
    handleCreateNotification,
  }
})
