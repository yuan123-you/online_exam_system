import { createRouter, createWebHistory } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AppShell from '@/components/layout/AppShell.vue'

// Lazy-loaded views — 按路由懒加载，减少首屏体积
const AdminOverview = () => import('@/views/admin/AdminOverview.vue')
const StudentManage = () => import('@/views/admin/StudentManage.vue')
const TeacherManage = () => import('@/views/admin/TeacherManage.vue')
const OrgManage = () => import('@/views/admin/OrgManage.vue')
const SystemLogs = () => import('@/views/admin/SystemLogs.vue')
const ExamManageAdmin = () => import('@/views/admin/ExamManageAdmin.vue')

const TeacherOverview = () => import('@/views/teacher/TeacherOverview.vue')
const QuestionBank = () => import('@/views/teacher/QuestionBank.vue')
const PaperManage = () => import('@/views/teacher/PaperManage.vue')
const ExamManage = () => import('@/views/teacher/ExamManage.vue')
const ExamMonitor = () => import('@/views/teacher/ExamMonitor.vue')
const GradingCenter = () => import('@/views/teacher/GradingCenter.vue')
const ScoreAnalysis = () => import('@/views/teacher/ScoreAnalysis.vue')
const AiQuestionGen = () => import('@/views/teacher/AiQuestionGen.vue')
const ClassAnalysisView = () => import('@/views/teacher/ClassAnalysisView.vue')

const AvailableExams = () => import('@/views/student/AvailableExams.vue')
const MyExams = () => import('@/views/student/MyExams.vue')
const Grades = () => import('@/views/student/Grades.vue')
const WrongBook = () => import('@/views/student/WrongBook.vue')
const AiPractice = () => import('@/views/student/AiPractice.vue')
const PracticeRecords = () => import('@/views/student/PracticeRecords.vue')

const ProfileView = () => import('@/views/ProfileView.vue')
const LoginView = () => import('@/views/LoginView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/',
      component: AppShell,
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/home',
        },
        {
          path: 'home',
          name: 'home',
          redirect: () => {
            const store = useAppStore()
            const firstKey = store.menuItems[0]?.key || 'overview'
            return '/' + firstKey
          },
        },
        // Admin routes
        {
          path: 'overview',
          name: 'overview',
          component: () => {
            const store = useAppStore()
            // AdminOverview / TeacherOverview 本身是 () => import(...) 懒加载函数，
            // 必须调用它们以返回组件 Promise，否则 Promise.resolve(函数) 会把函数当作组件渲染为 [object Promise]
            return store.isAdmin ? AdminOverview() : TeacherOverview()
          },
          meta: { roles: ['admin', 'teacher'] },
        },
        {
          path: 'students',
          name: 'students',
          component: StudentManage,
          meta: { roles: ['admin'] },
        },
        {
          path: 'teachers',
          name: 'teachers',
          component: TeacherManage,
          meta: { roles: ['admin'] },
        },
        {
          path: 'org',
          name: 'org',
          component: OrgManage,
          meta: { roles: ['admin'] },
        },
        {
          path: 'logs',
          name: 'logs',
          component: SystemLogs,
          meta: { roles: ['admin'] },
        },
        {
          path: 'admin-exams',
          name: 'admin-exams',
          component: ExamManageAdmin,
          meta: { roles: ['admin'] },
        },
        // Teacher routes
        {
          path: 'questions',
          name: 'questions',
          component: QuestionBank,
          meta: { roles: ['teacher'] },
        },
        {
          path: 'ai-questions',
          name: 'ai-questions',
          component: AiQuestionGen,
          meta: { roles: ['teacher', 'admin'] },
        },
        {
          path: 'papers',
          name: 'papers',
          component: PaperManage,
          meta: { roles: ['teacher'] },
        },
        {
          path: 'exams',
          name: 'exams',
          component: ExamManage,
          meta: { roles: ['teacher'] },
        },
        {
          path: 'monitor',
          name: 'monitor',
          component: ExamMonitor,
          meta: { roles: ['teacher'] },
        },
        {
          path: 'grading',
          name: 'grading',
          component: GradingCenter,
          meta: { roles: ['teacher'] },
        },
        {
          path: 'analysis',
          name: 'analysis',
          component: ScoreAnalysis,
          meta: { roles: ['teacher'] },
        },
        {
          path: 'class-analysis',
          name: 'class-analysis',
          component: ClassAnalysisView,
          meta: { roles: ['teacher'] },
        },
        // Student routes
        {
          path: 'available-exams',
          name: 'available-exams',
          component: AvailableExams,
          meta: { roles: ['student'] },
        },
        {
          path: 'my-exams',
          name: 'my-exams',
          component: MyExams,
          meta: { roles: ['student'] },
        },
        {
          path: 'grades',
          name: 'grades',
          component: Grades,
          meta: { roles: ['student'] },
        },
        {
          path: 'wrong-book',
          name: 'wrong-book',
          component: WrongBook,
          meta: { roles: ['student'] },
        },
        {
          path: 'ai-practice',
          name: 'ai-practice',
          component: AiPractice,
          meta: { roles: ['student'] },
        },
        {
          path: 'practice-records',
          name: 'practice-records',
          component: PracticeRecords,
          meta: { roles: ['student'] },
        },
        // Common routes
        {
          path: 'profile',
          name: 'profile',
          component: ProfileView,
          meta: { roles: ['admin', 'teacher', 'student'] },
        },
      ],
    },
  ],
})

router.beforeEach((to, from, next) => {
  const store = useAppStore()

  // 页面切换时关闭所有弹窗，防止弹窗残留
  // 但如果正在考试中，不关闭考试弹窗（刷新恢复场景）
  if (from.path !== to.path) {
    if (!store.activeExam) {
      store.closeAllModals()
    }
  }

  // 等待认证初始化完成（main.ts 中 await initAuth() 后 authReady 为 true）
  // 防御性逻辑：即使 main.ts 未 await，首次导航也不会误判
  if (!store.authReady) {
    return next()
  }

  // 公开页面（如 /login）
  if (to.meta.public) {
    // 已登录用户访问登录页 → 跳转到首页
    if (to.name === 'login' && store.bootstrap) {
      const firstMenuItem = store.menuItems[0]
      return next('/' + (firstMenuItem?.key || 'overview'))
    }
    return next()
  }

  // 需要认证的页面
  if (to.matched.some((record) => record.meta.requiresAuth)) {
    // 未登录 → 跳转到登录页
    if (!store.bootstrap) {
      return next('/login')
    }

    // 角色权限检查
    const requiredRoles = to.meta.roles as string[] | undefined
    if (requiredRoles && !requiredRoles.includes(store.role)) {
      // 当前角色无权限访问此页面 → 跳转到第一个可用菜单
      const firstMenuItem = store.menuItems[0]
      if (firstMenuItem) {
        return next('/' + firstMenuItem.key)
      }
      return next('/login')
    }
  }

  next()
})

export default router
