<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>考试管理</h3>
        <p class="section-subtitle">共 {{ store.myExams.length }} 场考试，{{ activeCount }} 场进行中</p>
      </div>
      <button class="primary-btn" type="button" @click="store.openEditor('exam', null)">发布考试</button>
    </div>

    <!-- 快速统计 -->
    <section class="quick-stats">
      <div class="stat-chip">
        <span class="stat-chip-label">总考试</span>
        <strong class="stat-chip-value">{{ store.myExams.length }}</strong>
      </div>
      <div class="stat-chip stat-chip--active">
        <span class="stat-chip-label">进行中</span>
        <strong class="stat-chip-value">{{ activeCount }}</strong>
      </div>
      <div class="stat-chip stat-chip--ended">
        <span class="stat-chip-label">已结束</span>
        <strong class="stat-chip-value">{{ endedCount }}</strong>
      </div>
    </section>

    <!-- 搜索 -->
    <div class="search-bar">
      <input
        v-model="searchQuery"
        type="text"
        class="search-input"
        placeholder="搜索考试名称..."
      />
    </div>

    <!-- 表格 -->
    <div class="table-wrap mobile-card-table">
      <table v-if="filteredExams.length > 0">
        <thead>
          <tr>
            <th>考试名称</th>
            <th>试卷</th>
            <th>时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in pagination.paginatedData.value" :key="e.id">
            <td data-label="考试名称"><strong>{{ e.name }}</strong></td>
            <td data-label="试卷">{{ e.paperName || '-' }}</td>
            <td data-label="时间">
              <div class="time-cell">
                <span class="time-main">{{ formatDate(e.startTime) }}</span>
                <span class="time-relative" :class="relativeTimeClass(e)">{{ relativeTimeText(e) }}</span>
              </div>
            </td>
            <td data-label="状态">
              <span class="badge" :class="statusBadgeClass(e)">{{ e.statusText || '已发布' }}</span>
            </td>
            <td data-label="操作">
              <div class="action-row">
                <button class="accent-btn" type="button" @click="handleMonitor(e.id)">监控</button>
                <button class="ghost-btn" type="button" @click="store.openEditor('exam', e)">编辑</button>
                <button class="danger-btn" type="button" @click="store.removeEntity('exams', e.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">📋</div>
        <p class="empty-title">{{ searchQuery ? '未找到匹配的考试' : '暂无考试' }}</p>
        <p class="empty-desc">{{ searchQuery ? '请尝试其他关键词' : '点击上方「发布考试」按钮创建第一场考试' }}</p>
      </div>
    </div>

    <PaginationBar
      :total="pagination.total.value"
      :current-page="pagination.currentPage.value"
      :page-size="pagination.pageSize.value"
      :page-size-options="pagination.pageSizeOptions"
      @page-change="pagination.goToPage"
      @page-size-change="pagination.changePageSize"
    />
  </article>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { formatDate } from '@/utils/format'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { useClientPagination } from '@/composables/usePagination'

const store = useAppStore()
const router = useRouter()
const searchQuery = ref('')

const filteredExams = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return store.myExams
  return store.myExams.filter(e => e.name.toLowerCase().includes(q))
})

const pagination = useClientPagination(filteredExams, { defaultPageSize: 15, pageSizeOptions: [10, 15, 20, 50] })

const activeCount = computed(() =>
  store.myExams.filter(e => (e.statusText || '已发布') === '进行中').length
)

const endedCount = computed(() =>
  store.myExams.filter(e => (e.statusText || '已发布') === '已结束').length
)

function statusBadgeClass(e: { statusText?: string }) {
  const status = e.statusText || '已发布'
  if (status === '进行中') return 'badge--green'
  if (status === '已结束') return 'badge--gray'
  return 'badge--blue'
}

function relativeTimeText(e: { startTime?: string; endTime?: string; statusText?: string }) {
  const status = e.statusText || '已发布'
  if (status === '已结束') return '已结束'
  const now = Date.now()
  const start = e.startTime ? new Date(e.startTime).getTime() : 0
  const end = e.endTime ? new Date(e.endTime).getTime() : 0
  if (status === '进行中' || (start <= now && end > now)) return '进行中'
  if (start > now) {
    const diff = start - now
    const hours = Math.floor(diff / 3600000)
    const minutes = Math.floor((diff % 3600000) / 60000)
    if (hours >= 24) return `${Math.floor(hours / 24)}天后开始`
    if (hours > 0) return `${hours}小时后开始`
    if (minutes > 0) return `${minutes}分钟后开始`
    return '即将开始'
  }
  return ''
}

function relativeTimeClass(e: { startTime?: string; endTime?: string; statusText?: string }) {
  const status = e.statusText || '已发布'
  if (status === '已结束') return 'time-relative--ended'
  if (status === '进行中') return 'time-relative--active'
  const now = Date.now()
  const start = e.startTime ? new Date(e.startTime).getTime() : 0
  if (start > now) return 'time-relative--upcoming'
  return ''
}

async function handleMonitor(examId: string) {
  await store.loadMonitorData(examId)
  if (store.monitorResult) {
    router.push('/monitor')
  }
}
</script>

<style scoped>
.quick-stats {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.stat-chip {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  background: var(--card-bg, #f5f5f5);
  border: 1px solid var(--border, #e0e0e0);
  font-size: 0.85rem;
}

.stat-chip--active {
  background: rgba(34, 197, 94, 0.1);
  border-color: rgba(34, 197, 94, 0.3);
}

.stat-chip--active .stat-chip-value {
  color: #16a34a;
}

.stat-chip--ended {
  background: rgba(156, 163, 175, 0.1);
  border-color: rgba(156, 163, 175, 0.3);
}

.stat-chip--ended .stat-chip-value {
  color: #6b7280;
}

.stat-chip-label {
  color: var(--muted, #888);
}

.stat-chip-value {
  color: var(--text, #333);
}

.search-bar {
  margin-bottom: 1rem;
}

.search-input {
  width: 100%;
  max-width: 360px;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--border, #e0e0e0);
  border-radius: 8px;
  font-size: 0.9rem;
  background: var(--card-bg, #fff);
  color: var(--text, #333);
  outline: none;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: var(--primary, #4f46e5);
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.15);
}

.time-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.time-main {
  font-size: 0.85rem;
}

.time-relative {
  font-size: 0.75rem;
  font-weight: 500;
}

.time-relative--active {
  color: #16a34a;
}

.time-relative--upcoming {
  color: var(--primary, #4f46e5);
}

.time-relative--ended {
  color: #6b7280;
}

/* 状态徽章 */
.badge {
  display: inline-block;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
}

.badge--blue {
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.badge--green {
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
}

.badge--gray {
  background: rgba(156, 163, 175, 0.15);
  color: #6b7280;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 3rem 1rem;
}

.empty-icon {
  font-size: 2.5rem;
  margin-bottom: 0.75rem;
}

.empty-title {
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--text, #333);
  margin: 0 0 0.35rem;
}

.empty-desc {
  font-size: 0.85rem;
  color: var(--muted, #888);
  margin: 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .quick-stats {
    gap: 0.5rem;
  }

  .stat-chip {
    font-size: 0.78rem;
    padding: 0.25rem 0.6rem;
  }

  .search-input {
    max-width: 100%;
  }

  .mobile-card-table table thead {
    display: none;
  }

  .mobile-card-table table tbody tr {
    display: block;
    margin-bottom: 0.75rem;
    border: 1px solid var(--border, #e0e0e0);
    border-radius: 10px;
    padding: 0.75rem;
    background: var(--card-bg, #fff);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  }

  .mobile-card-table table tbody td {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.35rem 0;
    border: none;
  }

  .mobile-card-table table tbody td::before {
    content: attr(data-label);
    font-weight: 600;
    font-size: 0.8rem;
    color: var(--muted, #888);
    flex-shrink: 0;
    margin-right: 0.75rem;
  }

  .action-row {
    flex-wrap: wrap;
    gap: 0.35rem;
  }

  .time-cell {
    text-align: right;
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .table-wrap {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
}

/* ---- Dark mode overrides ---- */
[data-theme="dark"] .stat-chip--ended .stat-chip-value,
[data-theme="dark"] .time-relative--ended,
[data-theme="dark"] .badge--gray {
  color: #9ca3af;
}
[data-theme="dark"] .badge--blue {
  background: rgba(96, 165, 250, 0.18);
  color: #93c5fd;
}
[data-theme="dark"] .badge--green {
  background: rgba(34, 197, 94, 0.18);
  color: #86efac;
}
[data-theme="dark"] .stat-chip--active .stat-chip-value,
[data-theme="dark"] .time-relative--active {
  color: #4ade80;
}
[data-theme="dark"] .time-relative--upcoming {
  color: #a5b4fc;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .stat-chip--ended .stat-chip-value,
  :root:not([data-theme="light"]) .time-relative--ended,
  :root:not([data-theme="light"]) .badge--gray {
    color: #9ca3af;
  }
  :root:not([data-theme="light"]) .badge--blue {
    background: rgba(96, 165, 250, 0.18);
    color: #93c5fd;
  }
  :root:not([data-theme="light"]) .badge--green {
    background: rgba(34, 197, 94, 0.18);
    color: #86efac;
  }
  :root:not([data-theme="light"]) .stat-chip--active .stat-chip-value,
  :root:not([data-theme="light"]) .time-relative--active {
    color: #4ade80;
  }
  :root:not([data-theme="light"]) .time-relative--upcoming {
    color: #a5b4fc;
  }
}
</style>
