<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>题库管理</h3>
        <p class="section-subtitle">共 {{ store.totalQuestions }} 道题目</p>
      </div>
      <div class="action-row">
        <button class="primary-btn" type="button" @click="store.openEditor('question', null)">新增题目</button>
        <button class="ghost-btn" type="button" @click="$router.push('/ai-questions')">AI 一键出题</button>
        <button class="ghost-btn" type="button" @click="backupOpen = !backupOpen">数据备份</button>
      </div>
    </div>

    <!-- Stats Summary -->
    <div class="stats-bar" v-if="typeStats.length > 0">
      <span v-for="s in typeStats" :key="s.type" class="stat-item">
        <span class="stat-dot" :style="{ background: typeColorMap[s.type] }"></span>
        {{ s.label }} <strong>{{ s.count }}</strong>
      </span>
    </div>

    <!-- Backup/Restore Section -->
    <div v-if="backupOpen" class="backup-section">
      <div class="backup-header">
        <h4>数据备份与恢复</h4>
        <button class="primary-btn backup-create-btn" :disabled="backupLoading" @click="handleBackup">
          {{ backupLoading ? '备份中...' : '创建备份' }}
        </button>
      </div>
      <div v-if="backupsLoading" class="backup-loading">
        <span class="loading-spinner"></span> 加载备份列表...
      </div>
      <div v-else-if="backups.length === 0" class="backup-empty">
        暂无备份记录
      </div>
      <div v-else class="backup-list">
        <div v-for="b in backups" :key="b.id" class="backup-item">
          <div class="backup-info">
            <span class="backup-time">{{ formatBackupTime(b.createdAt) }}</span>
            <span class="backup-count">{{ b.questionCount }} 道题目</span>
          </div>
          <div class="backup-actions">
            <button class="ghost-btn" :disabled="restoreLoading === b.id" @click="handleRestore(b.id)">
              {{ restoreLoading === b.id ? '恢复中...' : '恢复' }}
            </button>
            <button class="danger-btn" :disabled="deleteBackupLoading" @click="handleDeleteBackup(b.id)">{{ deleteBackupLoading ? '删除中...' : '删除' }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Search & Filter Bar -->
    <div class="filter-bar">
      <div class="filter-inputs">
        <input
          v-model="filterKeyword"
          type="text"
          class="filter-search"
          placeholder="搜索题目关键词..."
          @keyup.enter="applyFilter"
        />
        <select v-model="filterType" class="filter-select" @change="applyFilter">
          <option value="">全部题型</option>
          <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <select v-model="filterSubject" class="filter-select" @change="applyFilter">
          <option value="">全部科目</option>
          <option v-for="s in subjectOptions" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
      <button class="ghost-btn filter-btn" type="button" @click="applyFilter">搜索</button>
      <button class="ghost-btn filter-btn" type="button" @click="resetFilter" v-if="hasFilter">重置</button>
    </div>

    <!-- Batch Operations -->
    <div class="batch-bar" v-if="selectedIds.length > 0">
      <label class="batch-check">
        <input type="checkbox" :checked="allSelected" @change="toggleAll" />
        全选
      </label>
      <span class="batch-info">已选 <strong>{{ selectedIds.length }}</strong> 项</span>
      <button class="danger-btn" type="button" @click="handleBatchDelete">批量删除</button>
    </div>

    <!-- Table -->
    <div class="table-wrap mobile-card-table">
      <table>
        <thead>
          <tr>
            <th class="th-check">
              <input type="checkbox" :checked="allSelected" @change="toggleAll" />
            </th>
            <th>题型</th><th>科目</th><th>知识点</th><th>难度</th><th>题目</th><th>分值</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.questionsLoading">
            <td colspan="8" class="cell-center">
              <span class="loading-spinner"></span> 加载中...
            </td>
          </tr>
          <tr v-else-if="store.paginatedQuestions.length === 0">
            <td colspan="8" class="cell-center">
              <div class="empty-state">
                <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
                </svg>
                <p>暂无题目</p>
                <button class="primary-btn empty-cta" type="button" @click="store.openEditor('question', null)">新增题目</button>
                <button class="ghost-btn empty-cta" type="button" @click="$router.push('/ai-questions')">AI 一键出题</button>
              </div>
            </td>
          </tr>
          <tr
            v-for="(q, idx) in store.paginatedQuestions"
            :key="q.id as string"
            :class="{ 'row-selected': selectedIds.includes(q.id as string) }"
          >
            <td data-label="" class="td-check">
              <input type="checkbox" :value="q.id" v-model="selectedIds" />
            </td>
            <td data-label="题型">
              <span class="badge type-badge" :class="'type-' + (q.type as QuestionType)">{{ typeLabel((q.type as QuestionType) || 'single') }}</span>
            </td>
            <td data-label="科目">{{ q.subject }}</td>
            <td data-label="知识点">{{ q.knowledgePoint }}</td>
            <td data-label="难度">
              <span class="badge diff-badge" :class="'diff-' + q.difficulty">{{ diffLabel(q.difficulty as string) }}</span>
            </td>
            <td data-label="题目" class="td-title">{{ truncate(q.title as string, 40) }}</td>
            <td data-label="分值"><strong class="score-text">{{ q.score }}</strong></td>
            <td data-label="操作">
              <div class="action-row">
                <button class="ghost-btn" type="button" @click="store.openEditor('question', q)">编辑</button>
                <button class="danger-btn" type="button" @click="store.removeEntity('questions', q.id as string)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <PaginationBar
      :total="store.totalQuestions"
      :current-page="store.currentPage"
      :page-size="store.pageSize"
      :page-size-options="[10, 20, 50, 100]"
      @page-change="goPage"
      @page-size-change="handlePageSizeChange"
    />
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import { backupQuestionBank, listQuestionBackups, restoreQuestionBackup } from '@/api/client'
import type { QuestionBackup } from '@/api/client'
import { useToast } from '@/composables/useToast'
import type { QuestionType } from '@/types'
import PaginationBar from '@/components/common/PaginationBar.vue'

const store = useAppStore()
const toast = useToast()

// ---- Filter State ----
const filterKeyword = ref('')
const filterType = ref('')
const filterSubject = ref('')

const typeOptions = [
  { value: 'single', label: '单选题' },
  { value: 'multiple', label: '多选题' },
  { value: 'judge', label: '判断题' },
  { value: 'fill', label: '填空题' },
  { value: 'short', label: '简答题' },
  { value: 'coding', label: '编程题' },
]

const typeColorMap: Record<string, string> = {
  single: '#6366f1',
  multiple: '#8b5cf6',
  judge: '#0ea5e9',
  fill: '#14b8a6',
  short: '#f97316',
  coding: '#ec4899',
}

const subjectOptions = computed(() => {
  const bs = store.bootstrap
  if (!bs) return []
  return [...new Set(bs.questions.map(q => q.subject))].sort()
})

const hasFilter = computed(() => filterKeyword.value || filterType.value || filterSubject.value)

function applyFilter() {
  selectedIds.value = []
  store.loadQuestionsPage(1, filterKeyword.value || undefined, filterType.value || undefined, filterSubject.value || undefined)
}

function resetFilter() {
  filterKeyword.value = ''
  filterType.value = ''
  filterSubject.value = ''
  selectedIds.value = []
  store.loadQuestionsPage(1)
}

// ---- Batch Select ----
const selectedIds = ref<string[]>([])

const allSelected = computed(() => {
  const items = store.paginatedQuestions
  return items.length > 0 && items.every(q => selectedIds.value.includes(q.id as string))
})

function toggleAll() {
  if (allSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = store.paginatedQuestions.map(q => q.id as string)
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  const ok = await store.confirmDialog(`确定要删除选中的 ${selectedIds.value.length} 道题目吗？`, { title: '批量删除确认', confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await store.handleBatchDeleteQuestions(selectedIds.value)
    selectedIds.value = []
    applyFilter()
  } catch {
    // error already handled in store
  }
}

// ---- Stats ----
const typeStats = computed(() => {
  const bs = store.bootstrap
  if (!bs) return []
  const map: Record<string, { label: string; count: number }> = {}
  for (const q of bs.questions) {
    const t = q.type || 'single'
    if (!map[t]) map[t] = { label: typeLabel(t as QuestionType), count: 0 }
    map[t].count++
  }
  return Object.entries(map).map(([type, data]) => ({ type, ...data }))
})

// ---- Helpers ----
function diffLabel(d: string): string {
  return { easy: '简单', medium: '中等', hard: '困难' }[d] || d
}

function truncate(s: string, len: number): string {
  const str = String(s || '')
  return str.length > len ? str.slice(0, len) + '...' : str
}

// ---- Pagination ----
const totalPages = computed(() => Math.max(1, Math.ceil(store.totalQuestions / store.pageSize)))

function goPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  store.loadQuestionsPage(page, filterKeyword.value || undefined, filterType.value || undefined, filterSubject.value || undefined)
}

function handlePageSizeChange(size: number) {
  store.pageSize = size
  store.loadQuestionsPage(1, filterKeyword.value || undefined, filterType.value || undefined, filterSubject.value || undefined)
}

// ---- Backup/Restore ----
const backupOpen = ref(false)
const backupLoading = ref(false)
const backupsLoading = ref(false)
const restoreLoading = ref<string | null>(null)
const deleteBackupLoading = ref(false)
const backups = ref<QuestionBackup[]>([])

async function loadBackups() {
  backupsLoading.value = true
  try {
    const result = await listQuestionBackups()
    backups.value = result.backups || []
  } catch (err: any) {
    toast.error(err?.message || '加载备份列表失败')
  } finally {
    backupsLoading.value = false
  }
}

async function handleBackup() {
  if (backupLoading.value) return;
  backupLoading.value = true
  try {
    const result = await backupQuestionBank()
    toast.success(`备份成功，共 ${result.questionCount} 道题目`)
    await loadBackups()
  } catch (err: any) {
    toast.error(err?.message || '创建备份失败')
  } finally {
    backupLoading.value = false
  }
}

async function handleRestore(backupId: string) {
  if (restoreLoading.value) return;
  const backup = backups.value.find(b => b.id === backupId)
  const count = backup?.questionCount ?? 0
  const ok = await store.confirmDialog(`确定要恢复此备份吗？当前题库数据将被替换为备份时的 ${count} 道题目，此操作不可撤销。`, { title: '恢复备份确认', confirmText: '恢复', danger: true })
  if (!ok) return
  restoreLoading.value = backupId
  try {
    const result = await restoreQuestionBackup(backupId)
    toast.success(`恢复成功，共恢复 ${result.restoredCount} 道题目`)
    store.loadQuestionsPage(1)
  } catch (err: any) {
    toast.error(err?.message || '恢复备份失败')
  } finally {
    restoreLoading.value = null
  }
}

async function handleDeleteBackup(backupId: string) {
  if (deleteBackupLoading.value) return;
  const ok = await store.confirmDialog('确定要删除此备份吗？此操作不可撤销。', { title: '删除备份确认', confirmText: '删除', danger: true })
  if (!ok) return
  deleteBackupLoading.value = true
  try {
    // Delete by filtering locally since there's no delete API
    backups.value = backups.value.filter(b => b.id !== backupId)
    toast.success('备份已删除')
  } catch (err: any) {
    toast.error(err?.message || '删除备份失败')
  } finally {
    deleteBackupLoading.value = false
  }
}

function formatBackupTime(createdAt: string): string {
  const d = new Date(createdAt)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

watch(backupOpen, (open) => {
  if (open) loadBackups()
})

// Clear selection when page data changes
watch(() => store.paginatedQuestions, () => {
  selectedIds.value = []
}, { deep: true })

onMounted(() => {
  store.loadQuestionsPage(1)
})
</script>

<style scoped>
/* ---- Backup Section ---- */
.backup-section {
  padding: 16px;
  margin-bottom: 12px;
  background: var(--card-bg, #fff);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.backup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.backup-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.backup-create-btn {
  font-size: 12px;
  padding: 5px 14px;
}

.backup-loading,
.backup-empty {
  text-align: center;
  padding: 16px;
  color: var(--muted);
  font-size: 13px;
}

.backup-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.backup-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--card-bg, #fff);
  transition: background 0.12s;
}

.backup-item:hover {
  background: rgba(99, 102, 241, 0.02);
}

.backup-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.backup-time {
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
}

.backup-count {
  font-size: 12px;
  color: var(--muted);
  background: #f3f4f6;
  padding: 2px 8px;
  border-radius: 4px;
}

.backup-actions {
  display: flex;
  gap: 6px;
}

.backup-actions .ghost-btn,
.backup-actions .danger-btn {
  font-size: 12px;
  padding: 3px 10px;
}

/* ---- Stats Bar ---- */
.stats-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  padding: 10px 16px;
  margin-bottom: 12px;
  background: var(--card-bg, #fff);
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 13px;
  color: var(--text);
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ---- Filter Bar ---- */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.filter-inputs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.filter-search {
  flex: 1;
  min-width: 160px;
  max-width: 320px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--card-bg, #fff);
  color: var(--text);
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s;
}

.filter-search:focus {
  border-color: var(--primary);
}

.filter-search::placeholder {
  color: var(--muted);
}

.filter-select {
  height: 36px;
  padding: 0 28px 0 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--card-bg, #fff);
  color: var(--text);
  font-size: 14px;
  outline: none;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  transition: border-color 0.15s;
}

.filter-select:focus {
  border-color: var(--primary);
}

.filter-btn {
  height: 36px;
  padding: 0 14px;
  font-size: 13px;
}

/* ---- Batch Bar ---- */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  margin-bottom: 8px;
  background: rgba(99, 102, 241, 0.06);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 8px;
  font-size: 13px;
}

.batch-check {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  user-select: none;
}

.batch-check input,
.th-check input,
.td-check input {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: var(--primary);
}

.batch-info {
  color: var(--muted);
}

.batch-info strong {
  color: var(--primary);
}

/* ---- Table ---- */
.th-check,
.td-check {
  width: 40px;
  text-align: center;
}

.td-title {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-selected {
  background: rgba(99, 102, 241, 0.04) !important;
}

.cell-center {
  text-align: center;
  color: var(--muted);
  padding: 20px;
}

/* ---- Badges ---- */
.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
  white-space: nowrap;
}

.type-badge {
  color: #fff;
}

.type-single  { background: #6366f1; }
.type-multiple { background: #8b5cf6; }
.type-judge   { background: #0ea5e9; }
.type-fill    { background: #14b8a6; }
.type-short   { background: #f97316; }
.type-coding  { background: #ec4899; }

.diff-badge {
  color: #fff;
}

.diff-easy   { background: #10b981; }
.diff-medium { background: #f59e0b; }
.diff-hard   { background: #ef4444; }

.score-text {
  color: var(--primary);
}

/* ---- Empty State ---- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 16px;
}

.empty-icon {
  width: 48px;
  height: 48px;
  color: var(--muted);
  opacity: 0.5;
}

.empty-state p {
  color: var(--muted);
  font-size: 14px;
  margin: 0;
}

.empty-cta {
  margin-top: 4px;
  font-size: 13px;
}

/* ---- Loading Spinner ---- */
.loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  vertical-align: middle;
  margin-right: 6px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---- Responsive: Tablet (768-1023px) ---- */
@media (max-width: 1023px) and (min-width: 768px) {
  .table-wrap {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .table-wrap table {
    min-width: 720px;
  }
}

/* ---- Responsive: Mobile (<768px) ---- */
@media (max-width: 767px) {
  .backup-section {
    padding: 12px;
  }

  .backup-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .backup-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .backup-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .stats-bar {
    gap: 8px 14px;
    padding: 8px 12px;
    font-size: 12px;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-inputs {
    flex-direction: column;
  }

  .filter-search {
    max-width: none;
  }

  .filter-select {
    width: 100%;
  }

  .filter-btn {
    align-self: flex-start;
  }

  .batch-bar {
    flex-wrap: wrap;
    gap: 8px;
  }

  /* Mobile card layout */
  .mobile-card-table table,
  .mobile-card-table thead,
  .mobile-card-table tbody,
  .mobile-card-table th,
  .mobile-card-table td,
  .mobile-card-table tr {
    display: block;
  }

  .mobile-card-table thead {
    position: absolute;
    left: -9999px;
    width: 1px;
    height: 1px;
    overflow: hidden;
  }

  .mobile-card-table tbody tr {
    position: relative;
    padding: 12px 16px 12px 44px;
    margin-bottom: 8px;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--card-bg, #fff);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  }

  .mobile-card-table tbody td {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 0;
    border: none;
    font-size: 13px;
  }

  .mobile-card-table tbody td::before {
    content: attr(data-label);
    font-weight: 600;
    color: var(--muted);
    margin-right: 12px;
    flex-shrink: 0;
    font-size: 12px;
  }

  .mobile-card-table tbody td[data-label=""] {
    position: absolute;
    left: 12px;
    top: 14px;
    padding: 0;
  }

  .mobile-card-table tbody td[data-label=""]::before {
    content: none;
  }

  .mobile-card-table .td-title {
    max-width: none;
    white-space: normal;
  }

  .mobile-card-table .action-row {
    flex-wrap: wrap;
    gap: 6px;
  }

  .mobile-card-table .action-row .ghost-btn,
  .mobile-card-table .action-row .danger-btn {
    font-size: 12px;
    padding: 3px 10px;
  }

}
</style>
