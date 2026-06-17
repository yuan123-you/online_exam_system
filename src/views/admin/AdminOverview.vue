<template>
  <div class="admin-overview">
    <!-- 欢迎横幅 -->
    <section class="overview-welcome">
      <div class="welcome-content">
        <h2>欢迎回来，{{ store.currentUser?.name }}</h2>
        <p class="muted">在线考试系统管理面板 · {{ currentDate }}</p>
      </div>
      <div class="welcome-actions">
        <button class="primary-btn" type="button" @click="handleRefresh" :disabled="refreshing">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
          {{ refreshing ? '刷新中...' : '刷新数据' }}
        </button>
      </div>
    </section>

    <!-- 核心指标卡片 -->
    <section class="stats-grid overview-stats">
      <article class="stat-card overview-stat-card" @click="navigateTo('students')">
        <div class="stat-icon stat-icon--student">
          <svg viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-label">学生总数</span>
          <strong class="stat-value">{{ store.studentCount }}</strong>
        </div>
        <div class="stat-arrow">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </div>
      </article>

      <article class="stat-card overview-stat-card" @click="navigateTo('teachers')">
        <div class="stat-icon stat-icon--teacher">
          <svg viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-label">教师总数</span>
          <strong class="stat-value">{{ store.teacherCount }}</strong>
        </div>
        <div class="stat-arrow">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </div>
      </article>

      <article class="stat-card overview-stat-card">
        <div class="stat-icon stat-icon--question">
          <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-label">题库总量</span>
          <strong class="stat-value">{{ store.bootstrap?.questions.length || 0 }}</strong>
        </div>
      </article>

      <article class="stat-card overview-stat-card">
        <div class="stat-icon stat-icon--exam">
          <svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-label">考试总数</span>
          <strong class="stat-value">{{ store.bootstrap?.exams.length || 0 }}</strong>
        </div>
      </article>

      <article class="stat-card overview-stat-card" @click="navigateTo('org')">
        <div class="stat-icon stat-icon--dept">
          <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-label">学院 / 班级</span>
          <strong class="stat-value">{{ deptCount }} / {{ classCount }}</strong>
        </div>
        <div class="stat-arrow">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </div>
      </article>

      <article class="stat-card overview-stat-card" @click="navigateTo('logs')">
        <div class="stat-icon stat-icon--log">
          <svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-label">操作日志</span>
          <strong class="stat-value">{{ store.bootstrap?.logs.length || 0 }}</strong>
        </div>
        <div class="stat-arrow">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </div>
      </article>
    </section>

    <!-- 图表区域 -->
    <section class="overview-charts">
      <ChartCard
        title="题库科目分布"
        description="按科目统计题目数量"
        :option="store.subjectChartOption"
        height="320px"
      />
      <article class="panel chart-panel exam-status-panel">
        <div class="section-head">
          <div>
            <h3 style="font-size:17px;font-weight:700;letter-spacing:-0.02em;">考试状态概览</h3>
            <p class="muted" style="font-size:13px;margin-top:2px;">当前各状态考试分布</p>
          </div>
        </div>
        <div class="exam-status-grid">
          <div class="exam-status-item status-upcoming">
            <div class="status-dot"></div>
            <div class="status-info">
              <span class="status-label">待开始</span>
              <strong class="status-count">{{ examStatusCounts.upcoming }}</strong>
            </div>
          </div>
          <div class="exam-status-item status-ongoing">
            <div class="status-dot"></div>
            <div class="status-info">
              <span class="status-label">进行中</span>
              <strong class="status-count">{{ examStatusCounts.ongoing }}</strong>
            </div>
          </div>
          <div class="exam-status-item status-ended">
            <div class="status-dot"></div>
            <div class="status-info">
              <span class="status-label">已结束</span>
              <strong class="status-count">{{ examStatusCounts.ended }}</strong>
            </div>
          </div>
        </div>
        <div class="exam-status-bar">
          <div class="bar-segment bar-upcoming" :style="{ width: barWidth('upcoming') }"></div>
          <div class="bar-segment bar-ongoing" :style="{ width: barWidth('ongoing') }"></div>
          <div class="bar-segment bar-ended" :style="{ width: barWidth('ended') }"></div>
        </div>
      </article>
    </section>

    <!-- 快捷操作 + 最近日志 -->
    <section class="overview-bottom">
      <article class="panel quick-actions-panel">
        <div class="section-title">
          <h3>快捷操作</h3>
        </div>
        <div class="quick-actions-grid">
          <button class="quick-action-btn" type="button" @click="store.openEditor('student', null)">
            <div class="qa-icon qa-icon--student">
              <svg viewBox="0 0 24 24"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="23" y1="11" x2="17" y2="11"/></svg>
            </div>
            <span>新增学生</span>
          </button>
          <button class="quick-action-btn" type="button" @click="store.openEditor('teacher', null)">
            <div class="qa-icon qa-icon--teacher">
              <svg viewBox="0 0 24 24"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="23" y1="11" x2="17" y2="11"/></svg>
            </div>
            <span>新增教师</span>
          </button>
          <button class="quick-action-btn" type="button" @click="store.openEditor('department', null)">
            <div class="qa-icon qa-icon--dept">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/></svg>
            </div>
            <span>新增学院</span>
          </button>
          <button class="quick-action-btn" type="button" @click="store.openEditor('class', null)">
            <div class="qa-icon qa-icon--class">
              <svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/></svg>
            </div>
            <span>新增班级</span>
          </button>
          <button class="quick-action-btn" type="button" @click="store.showBatchImport('student')">
            <div class="qa-icon qa-icon--import">
              <svg viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            </div>
            <span>批量导入学生</span>
          </button>
          <button class="quick-action-btn" type="button" @click="store.showBatchImport('teacher')">
            <div class="qa-icon qa-icon--import">
              <svg viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            </div>
            <span>批量导入教师</span>
          </button>
        </div>
      </article>

      <article class="panel recent-logs-panel">
        <div class="section-title">
          <div>
            <h3>最近操作</h3>
            <p class="section-subtitle">最近 5 条系统日志</p>
          </div>
          <button class="ghost-btn" type="button" @click="navigateTo('logs')">查看全部</button>
        </div>
        <div class="recent-logs-list">
          <div v-if="recentLogs.length === 0" class="empty-state" style="padding:24px;">暂无操作记录</div>
          <div v-for="log in recentLogs" :key="log.id" class="recent-log-item">
            <div class="log-icon" :class="getLogIconClass(log.action)">
              <svg v-if="log.action.includes('创建') || log.action.includes('新增') || log.action.includes('导入')" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              <svg v-else-if="log.action.includes('删除')" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
              <svg v-else-if="log.action.includes('更新') || log.action.includes('编辑')" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            </div>
            <div class="log-content">
              <div class="log-main">
                <span class="log-action">{{ log.action }}</span>
                <span class="log-detail">{{ log.detail }}</span>
              </div>
              <div class="log-meta">
                <span class="log-actor">{{ log.actorId }}</span>
                <span class="log-time">{{ formatRelativeTime(log.time) }}</span>
              </div>
            </div>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import ChartCard from '@/components/common/ChartCard.vue'
import { formatDate } from '@/utils/format'

const store = useAppStore()
const router = useRouter()
const refreshing = ref(false)

const currentDate = computed(() => {
  const now = new Date()
  const weekdays = ['日', '一', '二', '三', '四', '五', '六']
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${weekdays[now.getDay()]}`
})

const deptCount = computed(() => store.bootstrap?.departments.length || 0)
const classCount = computed(() => store.bootstrap?.classes.length || 0)

const recentLogs = computed(() => store.sortedLogs.slice(0, 5))

const examStatusCounts = computed(() => {
  const now = Date.now()
  const exams = store.bootstrap?.exams || []
  let upcoming = 0, ongoing = 0, ended = 0
  exams.forEach(e => {
    const start = new Date(e.startTime).getTime()
    const end = new Date(e.endTime).getTime()
    if (now < start) upcoming++
    else if (now >= start && now <= end) ongoing++
    else ended++
  })
  return { upcoming, ongoing, ended }
})

function barWidth(status: 'upcoming' | 'ongoing' | 'ended') {
  const total = (store.bootstrap?.exams.length || 0)
  if (total === 0) return '0%'
  return (examStatusCounts.value[status] / total * 100) + '%'
}

function navigateTo(key: string) {
  router.push('/' + key)
}

async function handleRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await store.loadData()
    // 同步刷新通知数据，保持与全局刷新行为一致
    await store.loadNotificationData()
    // 通知分页视图重新加载（保留当前筛选状态）
    store.triggerRefresh()
    store.showToast('数据已刷新', 'success')
  } catch {
    store.showToast('刷新失败，请重试', 'error')
  } finally {
    refreshing.value = false
  }
}

function getLogIconClass(action: string): string {
  if (action.includes('创建') || action.includes('新增') || action.includes('导入')) return 'log-icon--create'
  if (action.includes('删除')) return 'log-icon--delete'
  if (action.includes('更新') || action.includes('编辑')) return 'log-icon--update'
  return 'log-icon--info'
}

function formatRelativeTime(time: string): string {
  const now = Date.now()
  const then = new Date(time).getTime()
  const diff = now - then
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  return formatDate(time)
}

// 数据加载保护：若 bootstrap 为空（如刷新后 API 异常），尝试重新加载
onMounted(() => {
  if (!store.bootstrap) {
    store.loadData().catch(() => { /* 静默处理，路由守卫会引导到登录页 */ })
  }
})
</script>

<style scoped>
.admin-overview {
  display: grid;
  gap: 16px;
}

/* 欢迎横幅 */
.overview-welcome {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  border-radius: var(--radius-xl);
  color: #fff;
  box-shadow: 0 8px 32px rgba(61, 153, 128, 0.25);
}

.overview-welcome h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.overview-welcome .muted {
  color: rgba(255, 255, 255, 0.75);
  font-size: 13px;
  margin-top: 4px;
}

.welcome-actions .primary-btn {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #fff;
  box-shadow: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  backdrop-filter: blur(8px);
}

.welcome-actions .primary-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.3);
  box-shadow: none;
  transform: none;
}

/* 统计卡片 */
.overview-stats {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.overview-stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  cursor: default;
  transition: transform var(--duration) var(--ease), box-shadow var(--duration) var(--ease);
}

.overview-stat-card[style*="cursor"] {
  cursor: pointer;
}

.overview-stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 22px;
  height: 22px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.stat-icon--student {
  background: #e0f2ec;
  color: var(--primary);
}

.stat-icon--teacher {
  background: #e8e0f2;
  color: #7c3aed;
}

.stat-icon--question {
  background: #fdf5e0;
  color: #d4a844;
}

.stat-icon--exam {
  background: #e0ecf2;
  color: #2563eb;
}

.stat-icon--dept {
  background: #fce8e8;
  color: #cf5c5c;
}

.stat-icon--log {
  background: #f0f7f4;
  color: var(--ink-secondary);
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-weight: 500;
}

.stat-value {
  display: block;
  font-size: 26px;
  font-weight: 700;
  color: var(--ink);
  line-height: 1.2;
  margin-top: 2px;
}

.stat-arrow {
  color: var(--muted-light);
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--duration) var(--ease);
}

.overview-stat-card:hover .stat-arrow {
  opacity: 1;
}

/* 图表区域 */
.overview-charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* 考试状态面板 */
.exam-status-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.exam-status-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border-radius: var(--radius);
  background: var(--panel-soft);
  border: 1px solid var(--line-soft);
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-upcoming .status-dot { background: #2563eb; }
.status-ongoing .status-dot { background: #16a34a; }
.status-ended .status-dot { background: #9ca3af; }

.status-info {
  flex: 1;
}

.status-label {
  display: block;
  font-size: 12px;
  color: var(--muted);
}

.status-count {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
  margin-top: 2px;
}

.exam-status-bar {
  display: flex;
  height: 8px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--panel-soft);
}

.bar-segment {
  height: 100%;
  transition: width 0.6s var(--ease);
}

.bar-upcoming { background: #2563eb; }
.bar-ongoing { background: #16a34a; }
.bar-ended { background: #9ca3af; }

/* 底部区域 */
.overview-bottom {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* 快捷操作 */
.quick-actions-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.quick-action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 12px;
  border-radius: var(--radius);
  background: var(--panel-soft);
  border: 1.5px solid var(--line-soft);
  color: var(--ink);
  font-size: 13px;
  font-weight: 500;
  transition: all var(--duration) var(--ease);
}

.quick-action-btn:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.qa-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qa-icon svg {
  width: 20px;
  height: 20px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.qa-icon--student { background: #e0f2ec; color: var(--primary); }
.qa-icon--teacher { background: #e8e0f2; color: #7c3aed; }
.qa-icon--dept { background: #fce8e8; color: #cf5c5c; }
.qa-icon--class { background: #e0ecf2; color: #2563eb; }
.qa-icon--import { background: #fdf5e0; color: #d4a844; }

/* 最近日志 */
.recent-logs-list {
  display: grid;
  gap: 8px;
}

.recent-log-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius);
  background: var(--panel-soft);
  border: 1px solid var(--line-soft);
  transition: border-color var(--duration) var(--ease);
}

.recent-log-item:hover {
  border-color: var(--line);
}

.log-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

.log-icon--create { background: #e0f2ec; color: var(--primary); }
.log-icon--delete { background: #fce8e8; color: #cf5c5c; }
.log-icon--update { background: #e0ecf2; color: #2563eb; }
.log-icon--info { background: #f0f7f4; color: var(--ink-secondary); }

.log-content {
  flex: 1;
  min-width: 0;
}

.log-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.log-action {
  font-weight: 600;
  font-size: 13px;
  color: var(--ink);
}

.log-detail {
  font-size: 12px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.log-meta {
  display: flex;
  gap: 8px;
  margin-top: 3px;
  font-size: 11px;
  color: var(--muted-light);
}

/* 响应式 */
@media (max-width: 1023px) {
  .overview-charts {
    grid-template-columns: 1fr;
  }
  .overview-bottom {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .overview-welcome {
    flex-direction: column;
    gap: 12px;
    text-align: center;
    padding: 16px;
  }
  .overview-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .quick-actions-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .exam-status-grid {
    grid-template-columns: 1fr;
  }
  .stat-value {
    font-size: 22px;
  }
}

/* ---- Dark mode overrides ---- */
[data-theme="dark"] .stat-icon--student { background: rgba(16, 185, 129, 0.15); }
[data-theme="dark"] .stat-icon--teacher { background: rgba(124, 58, 237, 0.15); color: #c4b5fd; }
[data-theme="dark"] .stat-icon--question { background: rgba(245, 158, 11, 0.15); color: #fcd34d; }
[data-theme="dark"] .stat-icon--exam { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
[data-theme="dark"] .stat-icon--dept { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
[data-theme="dark"] .stat-icon--log { background: rgba(148, 163, 184, 0.15); }
[data-theme="dark"] .qa-icon--student { background: rgba(16, 185, 129, 0.15); }
[data-theme="dark"] .qa-icon--teacher { background: rgba(124, 58, 237, 0.15); color: #c4b5fd; }
[data-theme="dark"] .qa-icon--dept { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
[data-theme="dark"] .qa-icon--class { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
[data-theme="dark"] .qa-icon--import { background: rgba(245, 158, 11, 0.15); color: #fcd34d; }
[data-theme="dark"] .log-icon--create { background: rgba(16, 185, 129, 0.15); }
[data-theme="dark"] .log-icon--delete { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
[data-theme="dark"] .log-icon--update { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
[data-theme="dark"] .log-icon--info { background: rgba(148, 163, 184, 0.15); }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .stat-icon--student { background: rgba(16, 185, 129, 0.15); }
  :root:not([data-theme="light"]) .stat-icon--teacher { background: rgba(124, 58, 237, 0.15); color: #c4b5fd; }
  :root:not([data-theme="light"]) .stat-icon--question { background: rgba(245, 158, 11, 0.15); color: #fcd34d; }
  :root:not([data-theme="light"]) .stat-icon--exam { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
  :root:not([data-theme="light"]) .stat-icon--dept { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
  :root:not([data-theme="light"]) .stat-icon--log { background: rgba(148, 163, 184, 0.15); }
  :root:not([data-theme="light"]) .qa-icon--student { background: rgba(16, 185, 129, 0.15); }
  :root:not([data-theme="light"]) .qa-icon--teacher { background: rgba(124, 58, 237, 0.15); color: #c4b5fd; }
  :root:not([data-theme="light"]) .qa-icon--dept { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
  :root:not([data-theme="light"]) .qa-icon--class { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
  :root:not([data-theme="light"]) .qa-icon--import { background: rgba(245, 158, 11, 0.15); color: #fcd34d; }
  :root:not([data-theme="light"]) .log-icon--create { background: rgba(16, 185, 129, 0.15); }
  :root:not([data-theme="light"]) .log-icon--delete { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
  :root:not([data-theme="light"]) .log-icon--update { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
  :root:not([data-theme="light"]) .log-icon--info { background: rgba(148, 163, 184, 0.15); }
}
</style>
