<template>
  <article class="panel logs-panel">
    <div class="section-title">
      <div>
        <h3>系统日志</h3>
        <p class="section-subtitle">共 {{ store.totalLogs }} 条操作记录</p>
      </div>
      <div class="section-actions">
        <div class="search-box">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchInput" type="text" placeholder="搜索操作人、动作、详情..." class="search-input" />
          <button v-if="searchInput" class="search-clear" type="button" @click="setSearch('')">&times;</button>
        </div>
        <select v-model="actionFilter" class="filter-select">
          <option value="">全部操作</option>
          <option v-for="action in uniqueActions" :key="action" :value="action">{{ action }}</option>
        </select>
        <button v-if="hasFilter" class="ghost-btn" type="button" @click="clearFilters">清除筛选</button>
      </div>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>动作</th>
            <th>详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.logsLoading">
            <td colspan="4" class="empty-cell">
              <span class="loading-spinner"></span> 加载中...
            </td>
          </tr>
          <tr v-if="!store.logsLoading && (store.paginatedLogs as any[]).length === 0">
            <td colspan="4" class="empty-cell">
              <div class="empty-state-inline">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="var(--muted-light)" stroke-width="1.5" stroke-linecap="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                <p>{{ hasFilter ? '没有匹配的日志记录' : '暂无日志记录' }}</p>
              </div>
            </td>
          </tr>
          <tr v-for="log in (store.paginatedLogs as any[])" :key="(log as any).id">
            <td data-label="时间" class="log-time-cell">{{ formatDate((log as any).time) }}</td>
            <td data-label="操作人">
              <div class="actor-cell">
                <div class="actor-avatar">{{ ((log as any).actorId || '?').charAt(0) }}</div>
                <span>{{ (log as any).actorId }}</span>
              </div>
            </td>
            <td data-label="动作">
              <span class="tag" :class="getActionTagClass((log as any).action)">{{ (log as any).action }}</span>
            </td>
            <td data-label="详情" class="cell-sub">{{ (log as any).detail }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <PaginationBar
      :total="store.totalLogs"
      :current-page="store.logsCurrentPage"
      :page-size="store.logsPageSize"
      :page-size-options="[10, 20, 50, 100]"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    />
  </article>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { useDebouncedRef } from '@/composables/useDebounce'
import { formatDate } from '@/utils/format'
import PaginationBar from '@/components/common/PaginationBar.vue'

const store = useAppStore()

const { debouncedValue: searchQuery, inputValue: searchInput, setValue: setSearch } = useDebouncedRef('')
const actionFilter = ref('')

const hasFilter = computed(() => !!(searchQuery.value.trim() || actionFilter.value))

// Get unique actions from bootstrap data for the filter dropdown
const uniqueActions = computed(() => {
  const actions = new Set<string>()
  store.sortedLogs.forEach(log => actions.add(log.action))
  return [...actions].sort()
})

watch([searchQuery, actionFilter], () => {
  store.loadLogsPage(1, searchQuery.value || undefined, actionFilter.value || undefined)
})

// 全局刷新时重新加载当前页（保留搜索/筛选状态）
watch(() => store.refreshTrigger, (n) => {
  if (n > 0) {
    store.loadLogsPage(store.logsCurrentPage, searchQuery.value || undefined, actionFilter.value || undefined)
  }
})

function handlePageChange(page: number) {
  store.loadLogsPage(page, searchQuery.value || undefined, actionFilter.value || undefined)
}

function handlePageSizeChange(size: number) {
  store.logsPageSize = size
  store.loadLogsPage(1, searchQuery.value || undefined, actionFilter.value || undefined)
}

function clearFilters() {
  setSearch('')
  actionFilter.value = ''
  store.loadLogsPage(1)
}

function getActionTagClass(action: string): string {
  if (action.includes('创建') || action.includes('新增') || action.includes('导入')) return 'tag-create'
  if (action.includes('删除')) return 'tag-delete'
  if (action.includes('更新') || action.includes('编辑')) return 'tag-update'
  if (action.includes('登录')) return 'tag-login'
  return 'tag-default'
}

onMounted(() => {
  store.loadLogsPage(1)
})
</script>

<style scoped>
.logs-panel {
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

.filter-select:focus {
  border-color: var(--primary);
  box-shadow: var(--shadow-glow);
}

/* 日志时间列 */
.log-time-cell {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: var(--ink-secondary);
}

/* 操作人单元格 */
.actor-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.actor-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

/* 操作标签颜色 */
.tag-create {
  background: #e0f2ec;
  color: var(--primary-dark);
}

.tag-delete {
  background: #fce8e8;
  color: #cf5c5c;
}

.tag-update {
  background: #e0ecf2;
  color: #2563eb;
}

.tag-login {
  background: #fdf5e0;
  color: #d4a844;
}

.tag-default {
  background: var(--panel-soft);
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

.loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid var(--border, var(--line-soft));
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  vertical-align: middle;
  margin-right: 6px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
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

/* ---- Dark mode overrides ---- */
[data-theme="dark"] .tag-create { background: rgba(16, 185, 129, 0.15); color: #6ee7b7; }
[data-theme="dark"] .tag-delete { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
[data-theme="dark"] .tag-update { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
[data-theme="dark"] .tag-login { background: rgba(245, 158, 11, 0.15); color: #fcd34d; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .tag-create { background: rgba(16, 185, 129, 0.15); color: #6ee7b7; }
  :root:not([data-theme="light"]) .tag-delete { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
  :root:not([data-theme="light"]) .tag-update { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
  :root:not([data-theme="light"]) .tag-login { background: rgba(245, 158, 11, 0.15); color: #fcd34d; }
}
</style>
