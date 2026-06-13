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
  aiChat as apiAiChat,
  aiChatStream as apiAiChatStream,
  listConversations,
  getConversationMessages,
  createConversation,
  appendConversationMessages,
  deleteConversation,
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
  Conversation,
  ChatMessage,
  ChatResult,
} from '@/api/client'

export interface Toast {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
}

export const useAppStore = defineStore('app', () => {
  // Toast notification system
  let toastId = 0
  const toasts = ref<Toast[]>([])

  function showToast(message: string, type: 'success' | 'error' | 'info' = 'info') {
    const id = ++toastId
    toasts.value.push({ id, message, type })
    setTimeout(() => {
      toasts.value = toasts.value.filter((t) => t.id !== id)
    }, 3000)
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
  let chatAbortController: AbortController | null = null
  let chatTimeoutId: ReturnType<typeof setTimeout> | null = null

  // Practice state (question generation session — separate from chat)
  const practiceMessages = ref<ChatMessage[]>([])
  const practSessionLoading = ref(false)
  const practiceStreamingContent = ref('')
  const practiceStreamingReasoning = ref('')
  const practiceStreamingActive = ref(false)
  let practiceAbortController: AbortController | null = null
  let practiceTimeoutId: ReturnType<typeof setTimeout> | null = null

  // Chat history state
  const conversations = ref<Conversation[]>([])
  const activeConversationId = ref<string | null>(null)
  const conversationsLoading = ref(false)

  // Deep thinking toggle
  const deepThinkingEnabled = ref(false)

  // Quota state
  const quotaData = ref<QuotaResult | null>(null)

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
      const user = bootstrap.value!.currentUser
      return e.targetClassIds.includes(user.classId || '') && e.published
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
        { key: 'profile', label: '个人信息', description: '修改密码' },
      ]
    }
    return [
      { key: 'available-exams', label: '待考考试', description: '查看可参加的考试' },
      { key: 'my-exams', label: '考试记录', description: '历史成绩' },
      { key: 'grades', label: '成绩单', description: '成绩趋势与知识掌握' },
      { key: 'wrong-book', label: '错题本', description: '错题回顾与重做' },
      { key: 'ai-practice', label: 'AI 助手', description: 'AI 智能服务' },
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
        bootstrap.value = await apiLoadBootstrap()
      } catch {
        setCurrentAuthToken('')
        localStorage.removeItem('auth_token')
        bootstrap.value = null
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
      loadData() // fire-and-forget — don't block login on full data load
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
      if (bootstrap.value) {
        // silent refresh failure
      } else {
        setCurrentAuthToken('')
        localStorage.removeItem('auth_token')
        bootstrap.value = null
      }
    }
  }

  async function loadQuestionsPage(page: number = 1) {
    questionsLoading.value = true
    currentPage.value = page
    try {
      const result = await apiLoadQuestionsPage({
        page,
        pageSize: pageSize.value,
      })
      paginatedQuestions.value = result.rows
      totalQuestions.value = result.total
    } catch (err: any) {
      showToast(err?.message || '加载题目失败', 'error')
    } finally {
      questionsLoading.value = false
    }
  }

  function logout() {
    setCurrentAuthToken('')
    localStorage.removeItem('auth_token')
    bootstrap.value = null
    loginMessage.value = ''
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
    if (!confirm(msg)) return
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
    loadData()
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
    if (!confirm('确定从错题本中移除吗？')) return
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
      const result = await exportScores(monitorExamId.value)
      const headers = ['学号', '姓名', '班级', '状态', '得分', '总分', '及格线', '排名']
      const rows = result.rows.map((r) => [r.username, r.studentName, r.className, r.status, r.score ?? '', r.totalScore, r.passScore, r.rank ?? ''].join(','))
      const csv = '\uFEFF' + [headers.join(','), ...rows].join('\n')
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${result.examName}_成绩表.csv`
      a.click()
      URL.revokeObjectURL(url)
      showToast('成绩导出成功', 'success')
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

  /** Create a new conversation and switch to it */
  async function handleNewConversation() {
    if (!currentUser.value) return
    try {
      const conv = await createConversation('新对话')
      conversations.value.unshift(conv)
      activeConversationId.value = conv.id
      chatMessages.value = []
      practiceMessages.value = []
    } catch (err: any) {
      showToast(err?.message || '创建会话失败', 'error')
    }
  }

  /** Switch to a conversation and load its messages */
  async function handleSwitchConversation(conversationId: string) {
    if (!currentUser.value || activeConversationId.value === conversationId) return
    conversationsLoading.value = true
    try {
      const result = await getConversationMessages(conversationId)
      activeConversationId.value = conversationId
      chatMessages.value = result.messages.map(m => ({
        role: m.role,
        content: m.content,
        reasoning: m.reasoning,
      }))
      practiceMessages.value = []
    } catch (err: any) {
      showToast(err?.message || '加载会话失败', 'error')
    } finally {
      conversationsLoading.value = false
    }
  }

  /** Save current chat messages to the active conversation.
   *  Auto-creates a conversation if none exists yet. */
  async function handleSaveMessages() {
    if (chatMessages.value.length === 0) return
    // Auto-create conversation if needed (first message in a session)
    if (!activeConversationId.value) {
      try {
        await handleNewConversation()
      } catch (err: any) {
        console.error('Failed to auto-create conversation:', err)
        return
      }
    }
    if (!activeConversationId.value) return
    try {
      await appendConversationMessages(activeConversationId.value, chatMessages.value)
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
    chatLoading.value = true

    // Timeout warning: if no output after 20s, show slow hint
    if (chatTimeoutId) clearTimeout(chatTimeoutId)
    chatTimeoutId = setTimeout(() => {
      if (chatStreamingActive.value && !chatStreamingContent.value && !chatStreamingReasoning.value) {
        chatMessages.value[aiMsgIndex].content = '⏳ AI 响应较慢，请耐心等待...'
      }
    }, 8000)

    chatAbortController = apiAiChatStream(
      { message, messages: chatMessages.value.slice(0, -1), deepThinking: deepThinkingEnabled.value },
      (chunk) => {
        if (chunk.type === 'reasoning') {
          chatStreamingReasoning.value += chunk.text
        } else {
          chatStreamingContent.value += chunk.text
          // Update the AI message in real-time
          chatMessages.value[aiMsgIndex].content = chatStreamingContent.value
        }
      },
      (data) => {
        if (chatTimeoutId) { clearTimeout(chatTimeoutId); chatTimeoutId = null }
        chatStreamingActive.value = false
        chatLoading.value = false
        if (data.content) {
          chatMessages.value[aiMsgIndex].content = data.content
        }
        // Save reasoning for later review (collapsed fold)
        if (data.reasoning) {
          chatMessages.value[aiMsgIndex].reasoning = data.reasoning
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
        if (chatTimeoutId) { clearTimeout(chatTimeoutId); chatTimeoutId = null }
        chatStreamingActive.value = false
        chatLoading.value = false
        chatMessages.value[aiMsgIndex].content = '❌ ' + (error || 'AI 请求失败，请稍后重试')
        showToast(error || 'AI 请求失败', 'error')
      },
      () => {
        if (chatTimeoutId) { clearTimeout(chatTimeoutId); chatTimeoutId = null }
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
    chatStreamingActive.value = false
    chatLoading.value = false
  }

  /** Clear chat history */
  function clearChatMessages() {
    chatMessages.value = []
    chatStreamingContent.value = ''
    chatStreamingReasoning.value = ''
    chatStreamingActive.value = false
  }

  // ========== Practice (Question Generation) Actions ==========

  /** Send a practice prompt and get streaming question generation */
  function handlePracticeSend(message: string) {
    if (!message.trim() || practSessionLoading.value) return

    practiceMessages.value.push({ role: 'user', content: message })
    const aiMsgIndex = practiceMessages.value.length
    practiceMessages.value.push({ role: 'assistant', content: '' })

    practiceStreamingContent.value = ''
    practiceStreamingReasoning.value = ''
    practiceStreamingActive.value = true
    practSessionLoading.value = true

    if (practiceTimeoutId) clearTimeout(practiceTimeoutId)
    practiceTimeoutId = setTimeout(() => {
      if (practiceStreamingActive.value && !practiceStreamingContent.value && !practiceStreamingReasoning.value) {
        practiceMessages.value[aiMsgIndex].content = '⏳ AI 响应较慢，请耐心等待...'
      }
    }, 8000)

    practiceAbortController = aiPracticeQuestionsStream(
      {
        customPrompt: message,
        subject: '',
        type: 'single',
        difficulty: 'medium',
        count: 5,
        deepThinking: deepThinkingEnabled.value,
      },
      (chunk) => {
        if (chunk.type === 'reasoning') {
          practiceStreamingReasoning.value += chunk.text
        } else {
          practiceStreamingContent.value += chunk.text
          practiceMessages.value[aiMsgIndex].content = practiceStreamingContent.value
        }
      },
      (data) => {
        if (practiceTimeoutId) { clearTimeout(practiceTimeoutId); practiceTimeoutId = null }
        practiceStreamingActive.value = false
        practSessionLoading.value = false
        if (data.content) {
          practiceMessages.value[aiMsgIndex].content = data.content
        }
        // Save reasoning for later review (collapsed fold)
        if (data.reasoning) {
          practiceMessages.value[aiMsgIndex].reasoning = data.reasoning
        }
      },
      (error) => {
        if (practiceTimeoutId) { clearTimeout(practiceTimeoutId); practiceTimeoutId = null }
        practiceStreamingActive.value = false
        practSessionLoading.value = false
        practiceMessages.value[aiMsgIndex].content = '❌ ' + (error || 'AI 请求失败，请稍后重试')
        showToast(error || 'AI 请求失败', 'error')
      },
      () => {
        if (practiceTimeoutId) { clearTimeout(practiceTimeoutId); practiceTimeoutId = null }
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
    practiceStreamingActive.value = false
    practSessionLoading.value = false
  }

  /** Clear practice history */
  function clearPracticeMessages() {
    practiceMessages.value = []
    practiceStreamingContent.value = ''
    practiceStreamingReasoning.value = ''
    practiceStreamingActive.value = false
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

    // Paginated question bank
    paginatedQuestions,
    totalQuestions,
    currentPage,
    pageSize,
    questionsLoading,
    loadQuestionsPage,

    // Actions
    initAuth,
    login,
    loadData,
    logout,
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
    handleChatSend,
    handleChatStop,
    clearChatMessages,

    // Deep thinking toggle
    deepThinkingEnabled,

    // Conversation history
    conversations,
    activeConversationId,
    conversationsLoading,
    handleLoadConversations,
    handleNewConversation,
    handleSwitchConversation,
    handleSaveMessages,
    handleDeleteConversation,

    // Practice (separate question generation session)
    practiceMessages,
    practSessionLoading,
    practiceStreamingContent,
    practiceStreamingReasoning,
    practiceStreamingActive,
    handlePracticeSend,
    handlePracticeStop,
    clearPracticeMessages,

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
  }
})
