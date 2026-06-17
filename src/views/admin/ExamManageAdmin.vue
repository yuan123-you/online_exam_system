<template>
  <article class="panel manage-panel">
    <div class="section-title">
      <div>
        <h3>考试管理</h3>
        <p class="section-subtitle">共 {{ pagination.total.value }} 场考试{{ hasFilter ? '（已筛选）' : '' }}</p>
      </div>
      <div class="section-actions">
        <div class="search-box">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchQuery" type="text" placeholder="搜索考试名称..." class="search-input" />
          <button v-if="searchQuery" class="search-clear" type="button" @click="searchQuery = ''">&times;</button>
        </div>
        <select v-model="statusFilter" class="filter-select">
          <option value="">全部状态</option>
          <option value="upcoming">待开始</option>
          <option value="ongoing">进行中</option>
          <option value="ended">已结束</option>
        </select>
      </div>
    </div>

    <!-- 状态概览 -->
    <div class="exam-status-summary">
      <div class="status-chip status-chip--upcoming" :class="{ active: statusFilter === 'upcoming' }" @click="toggleStatusFilter('upcoming')">
        <span class="status-chip-dot"></span>
        待开始 {{ statusCounts.upcoming }}
      </div>
      <div class="status-chip status-chip--ongoing" :class="{ active: statusFilter === 'ongoing' }" @click="toggleStatusFilter('ongoing')">
        <span class="status-chip-dot"></span>
        进行中 {{ statusCounts.ongoing }}
      </div>
      <div class="status-chip status-chip--ended" :class="{ active: statusFilter === 'ended' }" @click="toggleStatusFilter('ended')">
        <span class="status-chip-dot"></span>
        已结束 {{ statusCounts.ended }}
      </div>
    </div>

    <div class="table-wrap mobile-card-table">
      <table>
        <thead>
          <tr>
            <th>考试名称</th>
            <th>试卷</th>
            <th>班级</th>
            <th>开始时间</th>
            <th>结束时间</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="pagination.paginatedData.value.length === 0">
            <td colspan="6" class="empty-cell">
              <div class="empty-state-inline">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="var(--muted-light)" stroke-width="1.5" stroke-linecap="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                <p>{{ hasFilter ? '没有匹配的考试' : '暂无考试数据' }}</p>
              </div>
            </td>
          </tr>
          <tr v-for="exam in pagination.paginatedData.value" :key="exam.id">
            <td data-label="考试名称"><strong>{{ exam.name }}</strong></td>
            <td data-label="试卷">{{ getPaperName(exam.paperId) }}</td>
            <td data-label="班级">
              <span class="tag">{{ getClassName(exam) }}</span>
            </td>
            <td data-label="开始时间" class="time-cell">{{ formatDate(exam.startTime) }}</td>
            <td data-label="结束时间" class="time-cell">{{ formatDate(exam.endTime) }}</td>
            <td data-label="状态">
              <span class="exam-status-badge" :class="'status-' + getExamStatus(exam)">
                {{ getExamStatusLabel(exam) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
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
import { computed, ref, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { formatDate } from '@/utils/format'
import type { Exam } from '@/types'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { useClientPagination } from '@/composables/usePagination'

const store = useAppStore()

const searchQuery = ref('')
const statusFilter = ref('')
const hasFilter = computed(() => !!(searchQuery.value.trim() || statusFilter.value))

const allExams = computed(() => store.bootstrap?.exams || [])

function getExamStatus(exam: Exam): string {
  const now = Date.now()
  const start = new Date(exam.startTime).getTime()
  const end = new Date(exam.endTime).getTime()
  if (now < start) return 'upcoming'
  if (now >= start && now <= end) return 'ongoing'
  return 'ended'
}

function getExamStatusLabel(exam: Exam): string {
  const status = getExamStatus(exam)
  const map: Record<string, string> = { upcoming: '待开始', ongoing: '进行中', ended: '已结束' }
  return map[status] || '未知'
}

const statusCounts = computed(() => {
  let upcoming = 0, ongoing = 0, ended = 0
  allExams.value.forEach(e => {
    const s = getExamStatus(e)
    if (s === 'upcoming') upcoming++
    else if (s === 'ongoing') ongoing++
    else ended++
  })
  return { upcoming, ongoing, ended }
})

const filteredExams = computed(() => {
  let list = allExams.value
  if (statusFilter.value) {
    list = list.filter(e => getExamStatus(e) === statusFilter.value)
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter(e => e.name.toLowerCase().includes(q))
  }
  return list
})

const pagination = useClientPagination(filteredExams, { defaultPageSize: 15, pageSizeOptions: [10, 15, 20, 50] })

watch([searchQuery, statusFilter], () => {
  pagination.resetPage()
})

function toggleStatusFilter(status: string) {
  statusFilter.value = statusFilter.value === status ? '' : status
}

function getPaperName(paperId: string): string {
  return store.bootstrap?.papers.find(p => p.id === paperId)?.name || '-'
}

function getClassName(exam: Exam): string {
  const classIds = (exam as any).targetClassIds || []
  if (classIds.length === 0) return '-'
  return classIds.map((id: string) => store.className(id)).filter(Boolean).join('、') || '-'
}
</script>

<style scoped>
.manage-panel {
  padding: 20px;
}

.section-actions {
  flex-wrap: wrap;
  gap: 8px;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 200px;
}

.search-box svg {
  position: absolute;
  left: 10px;
  color: var(--muted);
  pointer-events: none;
}

.search-input {
  padding-left: 34px !important;
  padding-right: 28px !important;
  background: var(--panel-soft) !important;
  border: 1.5px solid var(--line-soft) !important;
  border-radius: var(--radius) !important;
  font-size: 13px;
  height: 38px;
}

.search-input:focus {
  background: var(--input-focus-bg) !important;
  border-color: var(--primary) !important;
  box-shadow: var(--shadow-glow) !important;
}

.search-clear {
  position: absolute;
  right: 6px;
  background: none;
  border: none;
  color: var(--muted);
  font-size: 18px;
  padding: 2px 6px;
  cursor: pointer;
  line-height: 1;
}

.search-clear:hover {
  color: var(--ink);
}

.filter-select {
  width: auto !important;
  min-width: 120px;
  max-width: 160px;
  height: 38px;
  font-size: 13px;
  padding: 0 28px 0 10px;
  background: var(--panel-soft);
  border: 1.5px solid var(--line-soft);
  border-radius: var(--radius);
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236b8f84' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
}

/* 状态概览 */
.exam-status-summary {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: 1.5px solid var(--line-soft);
  background: var(--panel-soft);
  color: var(--ink-secondary);
  transition: all var(--duration) var(--ease);
}

.status-chip:hover {
  border-color: var(--line);
}

.status-chip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-chip--upcoming .status-chip-dot { background: #2563eb; }
.status-chip--ongoing .status-chip-dot { background: #16a34a; }
.status-chip--ended .status-chip-dot { background: #9ca3af; }

.status-chip--upcoming.active { background: #e0ecf2; border-color: #2563eb; color: #2563eb; }
.status-chip--ongoing.active { background: #e0f2ec; border-color: #16a34a; color: #16a34a; }
.status-chip--ended.active { background: #f3f4f6; border-color: #9ca3af; color: #6b7280; }

/* 考试状态标签 */
.exam-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.exam-status-badge.status-upcoming {
  background: #e0ecf2;
  color: #2563eb;
}

.exam-status-badge.status-ongoing {
  background: #e0f2ec;
  color: #16a34a;
}

.exam-status-badge.status-ended {
  background: #f3f4f6;
  color: #6b7280;
}

.time-cell {
  white-space: nowrap;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--ink-secondary);
}

.empty-cell {
  text-align: center !important;
  padding: 40px 20px !important;
}

.empty-state-inline {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--muted);
}

.empty-state-inline p {
  margin: 0;
  font-size: 14px;
}

@media (max-width: 767px) {
  .search-box {
    min-width: 100%;
  }
  .search-input {
    width: 100% !important;
  }
  .filter-select {
    width: 100% !important;
    max-width: none;
  }
}

/* ---- Dark mode overrides for status chips & badges ---- */
[data-theme="dark"] .status-chip--upcoming.active {
  background: rgba(37, 99, 235, 0.15);
  color: #60a5fa;
}
[data-theme="dark"] .status-chip--ongoing.active {
  background: rgba(22, 163, 74, 0.15);
  color: #4ade80;
}
[data-theme="dark"] .status-chip--ended.active {
  background: rgba(156, 163, 175, 0.15);
  color: #d1d5db;
}
[data-theme="dark"] .exam-status-badge.status-upcoming {
  background: rgba(37, 99, 235, 0.15);
  color: #60a5fa;
}
[data-theme="dark"] .exam-status-badge.status-ongoing {
  background: rgba(22, 163, 74, 0.15);
  color: #4ade80;
}
[data-theme="dark"] .exam-status-badge.status-ended {
  background: rgba(156, 163, 175, 0.15);
  color: #d1d5db;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .status-chip--upcoming.active {
    background: rgba(37, 99, 235, 0.15);
    color: #60a5fa;
  }
  :root:not([data-theme="light"]) .status-chip--ongoing.active {
    background: rgba(22, 163, 74, 0.15);
    color: #4ade80;
  }
  :root:not([data-theme="light"]) .status-chip--ended.active {
    background: rgba(156, 163, 175, 0.15);
    color: #d1d5db;
  }
  :root:not([data-theme="light"]) .exam-status-badge.status-upcoming {
    background: rgba(37, 99, 235, 0.15);
    color: #60a5fa;
  }
  :root:not([data-theme="light"]) .exam-status-badge.status-ongoing {
    background: rgba(22, 163, 74, 0.15);
    color: #4ade80;
  }
  :root:not([data-theme="light"]) .exam-status-badge.status-ended {
    background: rgba(156, 163, 175, 0.15);
    color: #d1d5db;
  }
}
</style>
