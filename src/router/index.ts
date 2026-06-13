import { createRouter, createWebHistory } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AppShell from '@/components/layout/AppShell.vue'

// Admin views
import AdminOverview from '@/views/admin/AdminOverview.vue'
import StudentManage from '@/views/admin/StudentManage.vue'
import TeacherManage from '@/views/admin/TeacherManage.vue'
import OrgManage from '@/views/admin/OrgManage.vue'
import SystemLogs from '@/views/admin/SystemLogs.vue'

// Teacher views
import TeacherOverview from '@/views/teacher/TeacherOverview.vue'
import QuestionBank from '@/views/teacher/QuestionBank.vue'
import PaperManage from '@/views/teacher/PaperManage.vue'
import ExamManage from '@/views/teacher/ExamManage.vue'
import ExamMonitor from '@/views/teacher/ExamMonitor.vue'
import GradingCenter from '@/views/teacher/GradingCenter.vue'
import ScoreAnalysis from '@/views/teacher/ScoreAnalysis.vue'
import AiQuestionGen from '@/views/teacher/AiQuestionGen.vue'

// Student views
import AvailableExams from '@/views/student/AvailableExams.vue'
import MyExams from '@/views/student/MyExams.vue'
import Grades from '@/views/student/Grades.vue'
import WrongBook from '@/views/student/WrongBook.vue'
import AiPractice from '@/views/student/AiPractice.vue'
import PracticeRecords from '@/views/student/PracticeRecords.vue'

// Common views
import ProfileView from '@/views/ProfileView.vue'
import LoginView from '@/views/LoginView.vue'

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
            return store.isAdmin ? AdminOverview : TeacherOverview
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
          meta: { roles: ['teacher'] },
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
