<template>
  <div class="teacher-overview">
    <!-- Stats Grid -->
    <section class="stats-grid">
      <article class="stat-card overview-stat" style="--accent-color: var(--primary)">
        <div class="stat-icon-wrap" style="background: var(--primary-soft)">
          <svg viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25"/></svg>
        </div>
        <div class="stat-body">
          <span class="stat-label">题库题目</span>
          <strong class="stat-value">{{ store.myQuestions.length }}</strong>
          <span class="stat-sub">道题目</span>
        </div>
      </article>

      <article class="stat-card overview-stat" style="--accent-color: #6366f1">
        <div class="stat-icon-wrap" style="background: rgba(99,102,241,0.1)">
          <svg viewBox="0 0 24 24" fill="none" stroke="#6366f1" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z"/></svg>
        </div>
        <div class="stat-body">
          <span class="stat-label">试卷数量</span>
          <strong class="stat-value">{{ store.myPapers.length }}</strong>
          <span class="stat-sub">份试卷</span>
        </div>
      </article>

      <article class="stat-card overview-stat" style="--accent-color: #f59e0b">
        <div class="stat-icon-wrap" style="background: rgba(245,158,11,0.1)">
          <svg viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
        </div>
        <div class="stat-body">
          <span class="stat-label">进行中考试</span>
          <strong class="stat-value">{{ activeExamCount }}</strong>
          <span class="stat-sub">场考试</span>
        </div>
      </article>

      <article class="stat-card overview-stat" style="--accent-color: #ef4444">
        <div class="stat-icon-wrap" style="background: rgba(239,68,68,0.1)">
          <svg viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10"/></svg>
        </div>
        <div class="stat-body">
          <span class="stat-label">待阅卷</span>
          <strong class="stat-value">{{ store.pendingGradeCount }}</strong>
          <span class="stat-sub">份答卷</span>
        </div>
      </article>
    </section>

    <!-- Quick Actions -->
    <section class="quick-actions">
      <h3 class="section-heading">快捷操作</h3>
      <div class="action-grid">
        <button class="action-btn action-btn--ai" @click="router.push('/ai-questions')">
          <span class="action-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z"/></svg>
          </span>
          <span class="action-text">AI 一键出题</span>
        </button>
        <button class="action-btn action-btn--add" @click="store.openEditor('question', null)">
          <span class="action-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4.5v15m7.5-7.5h-15"/></svg>
          </span>
          <span class="action-text">新增题目</span>
        </button>
        <button class="action-btn action-btn--publish" @click="store.openEditor('exam', null)">
          <span class="action-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5"/></svg>
          </span>
          <span class="action-text">发布考试</span>
        </button>
        <button class="action-btn action-btn--auto" @click="store.autoGenVisible = true">
          <span class="action-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 010 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 010-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281z"/><path d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
          </span>
          <span class="action-text">自动组卷</span>
        </button>
        <button class="action-btn action-btn--notify" @click="showPublisher = true">
          <span class="action-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          </span>
          <span class="action-text">发布通知</span>
        </button>
      </div>
    </section>

    <!-- Charts Section -->
    <section class="charts-grid">
      <ChartCard
        title="题库分布"
        description="按科目统计题目数量"
        :option="store.subjectChartOption"
      />
      <ChartCard
        v-if="hasScoreData"
        title="成绩分布"
        description="学生成绩分段统计"
        :option="store.scoreDistOption"
      />
    </section>

    <!-- Recent Activity -->
    <section class="recent-activity">
      <h3 class="section-heading">最近动态</h3>
      <div v-if="recentItems.length === 0" class="empty-state">暂无动态</div>
      <ul v-else class="activity-list">
        <li v-for="item in recentItems" :key="item.key" class="activity-item">
          <span class="activity-badge" :class="item.badgeClass">{{ item.badge }}</span>
          <div class="activity-content">
            <span class="activity-title">{{ item.title }}</span>
            <span class="activity-meta">{{ item.meta }}</span>
          </div>
        </li>
      </ul>
    </section>

    <!-- 通知发布弹窗 -->
    <NotificationPublisher v-if="showPublisher" @close="showPublisher = false" @submitted="onNotificationSubmitted" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import ChartCard from '@/components/common/ChartCard.vue'
import NotificationPublisher from '@/components/common/NotificationPublisher.vue'

const store = useAppStore()
const router = useRouter()
const showPublisher = ref(false)

function onNotificationSubmitted() {
  store.showToast('通知发布成功', 'success')
}

const activeExamCount = computed(() =>
  store.myExams.filter(e => e.statusText === '进行中').length
)

const hasScoreData = computed(() => store.mySubmissions.length > 0)

interface RecentItem {
  key: string
  badge: string
  badgeClass: string
  title: string
  meta: string
  time: number
}

/** 从试卷 ID 中安全提取时间戳，失败时返回 0 */
function safePaperTime(id: string | undefined): number {
  if (!id || typeof id !== 'string' || !id.includes('-')) return 0
  try {
    const tail = id.split('-').pop() || '0'
    const parsed = parseInt(tail, 16)
    return Number.isFinite(parsed) ? parsed * 1000 : 0
  } catch {
    return 0
  }
}

const recentItems = computed<RecentItem[]>(() => {
  const items: RecentItem[] = []

  store.myExams.forEach(e => {
    items.push({
      key: `exam-${e.id}`,
      badge: '考试',
      badgeClass: 'badge-exam',
      title: e.name || '未命名考试',
      meta: e.statusText || '已发布',
      time: new Date(e.startTime || 0).getTime(),
    })
  })

  store.myPapers.forEach(p => {
    items.push({
      key: `paper-${p.id}`,
      badge: '试卷',
      badgeClass: 'badge-paper',
      title: p.name || '未命名试卷',
      meta: `${p.questionIds?.length || 0} 题`,
      time: safePaperTime(p.id),
    })
  })

  store.mySubmissions
    .filter(s => s.status === '待阅卷')
    .forEach(s => {
      items.push({
        key: `grade-${s.id}`,
        badge: '待阅',
        badgeClass: 'badge-grade',
        title: s.examName || s.examId,
        meta: s.studentName || s.studentId,
        time: new Date(s.submittedAt || s.updatedAt || 0).getTime(),
      })
    })

  return items.sort((a, b) => b.time - a.time).slice(0, 5)
})

// 数据加载保护：若 bootstrap 为空（如刷新后 API 异常），尝试重新加载
onMounted(() => {
  if (!store.bootstrap) {
    store.loadData().catch(() => { /* 静默处理，路由守卫会引导到登录页 */ })
  }
})
</script>

<style scoped>
.teacher-overview {
  display: grid;
  gap: 16px;
  overflow-wrap: break-word;
}

/* ---- Stats Grid ---- */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.overview-stat {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border-left: 3px solid var(--accent-color, var(--primary));
  min-height: auto;
}

.overview-stat:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.stat-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-wrap svg {
  width: 22px;
  height: 22px;
}

.stat-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat-label {
  font-size: 12px;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-weight: 500;
}

.stat-value {
  font-size: 26px;
  line-height: 1.15;
  margin-top: 2px;
  color: var(--ink);
}

.stat-sub {
  font-size: 11px;
  color: var(--muted-light);
  margin-top: 1px;
}

/* ---- Quick Actions ---- */
.quick-actions {
  background: var(--panel);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  padding: 16px;
  box-shadow: var(--shadow-sm);
}

.section-heading {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
  margin: 0 0 12px;
  letter-spacing: -0.01em;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--radius);
  border: 1.5px solid var(--line-soft);
  background: var(--panel-soft);
  color: var(--ink);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration) var(--ease);
  white-space: nowrap;
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow);
}

.action-btn:active {
  transform: translateY(0);
}

.action-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-icon svg {
  width: 18px;
  height: 18px;
}

.action-btn--ai {
  border-color: rgba(99, 102, 241, 0.25);
  background: rgba(99, 102, 241, 0.06);
  color: #4f46e5;
}

.action-btn--ai:hover {
  background: rgba(99, 102, 241, 0.12);
  border-color: rgba(99, 102, 241, 0.4);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.15);
}

.action-btn--add {
  border-color: rgba(61, 153, 128, 0.25);
  background: var(--primary-soft);
  color: var(--primary-dark);
}

.action-btn--add:hover {
  background: #d2ece4;
  border-color: var(--primary);
  box-shadow: 0 4px 14px rgba(61, 153, 128, 0.15);
}

.action-btn--publish {
  border-color: rgba(245, 158, 11, 0.25);
  background: rgba(245, 158, 11, 0.06);
  color: #b45309;
}

.action-btn--publish:hover {
  background: rgba(245, 158, 11, 0.12);
  border-color: rgba(245, 158, 11, 0.4);
  box-shadow: 0 4px 14px rgba(245, 158, 11, 0.15);
}

.action-btn--auto {
  border-color: rgba(139, 92, 246, 0.25);
  background: rgba(139, 92, 246, 0.06);
  color: #7c3aed;
}

.action-btn--auto:hover {
  background: rgba(139, 92, 246, 0.12);
  border-color: rgba(139, 92, 246, 0.4);
  box-shadow: 0 4px 14px rgba(139, 92, 246, 0.15);
}

.action-btn--notify {
  border-color: rgba(234, 88, 12, 0.25);
  background: rgba(234, 88, 12, 0.06);
  color: #c2410c;
}

.action-btn--notify:hover {
  background: rgba(234, 88, 12, 0.12);
  border-color: rgba(234, 88, 12, 0.4);
  box-shadow: 0 4px 14px rgba(234, 88, 12, 0.15);
}

/* ---- Charts Grid ---- */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

/* ---- Recent Activity ---- */
.recent-activity {
  background: var(--panel);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  padding: 16px;
  box-shadow: var(--shadow-sm);
}

.activity-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius);
  border: 1px solid var(--line-soft);
  background: var(--panel-soft);
  transition: border-color var(--duration) var(--ease),
              background var(--duration) var(--ease);
}

.activity-item:hover {
  border-color: var(--accent);
  background: #eaf5f0;
}

.activity-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 10px;
  border-radius: 100px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
  min-width: 40px;
}

.badge-exam {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.badge-paper {
  background: rgba(99, 102, 241, 0.1);
  color: #4f46e5;
}

.badge-grade {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

.activity-content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.activity-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-meta {
  font-size: 11px;
  color: var(--muted);
  margin-top: 2px;
}

/* ---- Responsive: Tablet (768px - 1023px) ---- */
@media (max-width: 1023px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .action-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .charts-grid {
    grid-template-columns: 1fr;
  }
}

/* ---- Responsive: Mobile (<768px) ---- */
@media (max-width: 767px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }

  .overview-stat {
    padding: 12px;
    gap: 10px;
  }

  .stat-icon-wrap {
    width: 36px;
    height: 36px;
    border-radius: 8px;
  }

  .stat-icon-wrap svg {
    width: 18px;
    height: 18px;
  }

  .stat-value {
    font-size: 22px;
  }

  .stat-label {
    font-size: 11px;
  }

  .stat-sub {
    font-size: 10px;
  }

  .action-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }

  .action-btn {
    padding: 8px 10px;
    font-size: 12px;
    gap: 6px;
  }

  .charts-grid {
    grid-template-columns: 1fr;
  }

  .activity-item {
    padding: 8px 10px;
  }
}

/* ---- Dark mode overrides ---- */
[data-theme="dark"] .action-btn--auto { color: #c4b5fd; }
[data-theme="dark"] .action-btn--notify { color: #fb923c; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .action-btn--auto { color: #c4b5fd; }
  :root:not([data-theme="light"]) .action-btn--notify { color: #fb923c; }
}
</style>
