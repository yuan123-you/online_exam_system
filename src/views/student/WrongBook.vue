<template>
  <article class="panel">
    <!-- Quota Progress Bar -->
    <QuotaBar
      label="错题本"
      :used="wrongBookQuotaUsed"
      :limit="1000"
      warning-text="错题本已满，最早的记录将被自动归档"
    />

    <div class="section-title">
      <div>
        <h3>错题本</h3>
        <p class="section-subtitle">共 {{ store.filteredWrongBookEntries.length }} 道错题{{ store.wrongBookSubjectFilter ? ` (筛选: ${store.wrongBookSubjectFilter})` : '' }}</p>
      </div>
      <div class="section-actions">
        <select v-model="store.wrongBookSubjectFilter" class="monitor-select">
          <option value="">全部科目</option>
          <option v-for="s in store.wrongBookSubjects" :key="s" :value="s">{{ s }}</option>
        </select>
        <button
          v-if="selectedIds.size > 0"
          class="danger-btn"
          type="button"
          @click="handleBatchRemove"
        >批量清理 ({{ selectedIds.size }})</button>
        <button class="ghost-btn" type="button" @click="selectMastered">选择已掌握</button>
      </div>
    </div>

    <!-- Selection info bar -->
    <div v-if="selectedIds.size > 0" class="selection-bar">
      <span>已选择 {{ selectedIds.size }} 条错题记录</span>
      <button class="ghost-btn" type="button" @click="clearSelection">取消选择</button>
    </div>

    <div v-if="store.filteredWrongBookEntries.length === 0 && !loading" class="empty-state">暂无错题记录</div>
    <div v-else-if="loading" class="empty-state">加载中...</div>
    <div v-else class="table-wrap mobile-card-table">
      <table>
        <thead>
          <tr>
            <th class="checkbox-col">
              <input
                type="checkbox"
                :checked="allSelected"
                :indeterminate="someSelected && !allSelected"
                @change="toggleSelectAll"
              />
            </th>
            <th>科目</th><th>知识点</th><th>题型</th><th>题目</th><th class="hide-mobile">错误次数</th><th class="hide-mobile">重做次数</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in (store.paginatedWrongBook as any)"
            :key="entry.id"
            :class="{ 'row-selected': selectedIds.has(entry.id) }"
          >
            <td class="checkbox-col" data-label="选择">
              <input
                type="checkbox"
                :checked="selectedIds.has(entry.id)"
                @change="toggleSelect(entry.id)"
              />
            </td>
            <td data-label="科目">{{ entry.subject }}</td>
            <td data-label="知识点">{{ entry.knowledgePoint }}</td>
            <td data-label="题型"><span class="tag">{{ typeLabel(entry.type) }}</span></td>
            <td data-label="题目" class="cell-title">{{ (entry.title || '').slice(0, 40) }}{{ (entry.title || '').length > 40 ? '...' : '' }}</td>
            <td data-label="错误次数" class="hide-mobile"><strong style="color:var(--danger)">{{ entry.wrongCount }}</strong></td>
            <td data-label="重做次数" class="hide-mobile">{{ entry.retryCount }}</td>
            <td data-label="状态">
              <span :class="entry.lastRetryCorrect ? 'status-ok' : 'status-pending'">
                {{ entry.lastRetryCorrect ? '已掌握' : '待重做' }}
              </span>
            </td>
            <td data-label="操作" class="mobile-action-cell">
              <div class="action-row">
                <button class="primary-btn" type="button" @click="store.retryWrongEntry(entry)">重做</button>
                <button v-if="entry.removable" class="ghost-btn" type="button" @click="store.removeWrongEntry(entry.id)">移除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <PaginationBar
      :total="store.totalWrongBook"
      :current-page="store.wrongBookCurrentPage"
      :page-size="store.wrongBookPageSize"
      :page-size-options="[10, 20, 50]"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    />
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import QuotaBar from '@/components/common/QuotaBar.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'

const store = useAppStore()
const selectedIds = ref<Set<string>>(new Set())
const loading = ref(false)

// Load quota data and first page on mount
onMounted(async () => {
  await store.loadQuotaData()
  loading.value = true
  try {
    await store.loadWrongBookPage(1)
  } finally {
    loading.value = false
  }
})

// Watch subject filter changes to reload data
watch(() => store.wrongBookSubjectFilter, async () => {
  loading.value = true
  try {
    await store.loadWrongBookPage(1, store.wrongBookSubjectFilter || undefined)
  } finally {
    loading.value = false
  }
})

// 全局刷新时重新加载当前页（保留科目筛选状态）
watch(() => store.refreshTrigger, async (n) => {
  if (n > 0) {
    loading.value = true
    try {
      await store.loadWrongBookPage(store.wrongBookCurrentPage, store.wrongBookSubjectFilter || undefined)
    } finally {
      loading.value = false
    }
  }
})

async function handlePageChange(page: number) {
  loading.value = true
  try {
    await store.loadWrongBookPage(page, store.wrongBookSubjectFilter || undefined)
  } finally {
    loading.value = false
  }
}

async function handlePageSizeChange(size: number) {
  store.wrongBookPageSize = size
  loading.value = true
  try {
    await store.loadWrongBookPage(1, store.wrongBookSubjectFilter || undefined)
  } finally {
    loading.value = false
  }
}

// Quota: use real API data if available, fallback to local count
const wrongBookQuotaUsed = computed(() => {
  if (store.quotaData?.wrongBook) {
    return store.quotaData.wrongBook.used
  }
  return store.filteredWrongBookEntries.length
})

// Selection logic — operate on current page data only
const currentPageEntries = computed(() => store.paginatedWrongBook as any[])

const allSelected = computed(() =>
  currentPageEntries.value.length > 0 &&
  currentPageEntries.value.every((e: any) => selectedIds.value.has(e.id))
)

const someSelected = computed(() => selectedIds.value.size > 0)

function toggleSelect(id: string) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}

function toggleSelectAll() {
  const pageIds = currentPageEntries.value.map((e: any) => e.id)
  const allPageSelected = pageIds.length > 0 && pageIds.every((id: string) => selectedIds.value.has(id))
  if (allPageSelected) {
    // Deselect only current page items
    const next = new Set(selectedIds.value)
    pageIds.forEach((id: string) => next.delete(id))
    selectedIds.value = next
  } else {
    // Select all current page items
    const next = new Set(selectedIds.value)
    pageIds.forEach((id: string) => next.add(id))
    selectedIds.value = next
  }
}

function clearSelection() {
  selectedIds.value = new Set()
}

function selectMastered() {
  const masteredIds = currentPageEntries.value
    .filter((e: any) => e.lastRetryCorrect)
    .map((e: any) => e.id)
  if (masteredIds.length === 0) {
    store.showToast('没有已掌握的错题可选择', 'info')
    return
  }
  selectedIds.value = new Set(masteredIds)
  store.showToast(`已选择 ${masteredIds.length} 条已掌握的错题`, 'info')
}

async function handleBatchRemove() {
  const count = selectedIds.value.size
  if (count === 0) return
  const ok = await store.confirmDialog(`确定要移除选中的 ${count} 条错题记录吗？`, { title: '批量移除确认', confirmText: '移除', danger: true })
  if (!ok) return
  try {
    await store.handleBatchRemoveWrongBook(Array.from(selectedIds.value))
    selectedIds.value = new Set()
  } catch {
    // error toast already shown by store action
  }
}
</script>

<style scoped>
.panel {
  max-width: 100%;
  overflow: hidden;
}

.section-title {
  gap: 6px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.section-subtitle {
  margin-top: 2px;
  font-size: 12px;
}

.section-actions {
  display: flex;
  gap: 4px;
  align-items: center;
  flex-wrap: wrap;
}

.section-actions .monitor-select {
  padding: 5px 8px;
  font-size: 12px;
  min-width: 90px;
  max-width: 140px;
}

.section-actions button {
  padding: 5px 10px;
  font-size: 12px;
  white-space: nowrap;
}

.checkbox-col {
  width: 32px;
  text-align: center;
}

.row-selected {
  background: var(--primary-soft, #e0f2ec);
}

.selection-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 8px;
  margin-bottom: 4px;
  background: var(--primary-soft, #e0f2ec);
  border-radius: 6px;
  font-size: 0.8rem;
  color: var(--primary, #3d9980);
  font-weight: 500;
  overflow-wrap: break-word;
  gap: 4px;
}

.selection-bar .ghost-btn {
  padding: 3px 8px;
  font-size: 12px;
}

.status-ok {
  color: var(--ok, #3d9980);
  font-weight: 500;
  font-size: 0.8rem;
  white-space: nowrap;
}

.status-pending {
  color: var(--warn, #d4a844);
  font-weight: 500;
  font-size: 0.8rem;
  white-space: nowrap;
}

.cell-title {
  max-width: 180px;
  overflow-wrap: break-word;
  word-break: break-word;
  overflow: hidden;
}

.tag {
  white-space: nowrap;
  font-size: 11px;
  padding: 2px 8px;
}

/* Ensure table cells don't overflow their containers */
.table-wrap {
  overflow: auto;
}

.table-wrap table {
  table-layout: auto;
  width: 100%;
  min-width: 0;
}

.table-wrap th,
.table-wrap td {
  overflow-wrap: break-word;
  word-break: break-word;
  max-width: 140px;
  padding: 5px 6px;
  font-size: 12px;
}

.table-wrap td.cell-title {
  max-width: 180px;
}

.action-row {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.action-row button {
  padding: 4px 10px;
  font-size: 12px;
  white-space: nowrap;
}

/* Desktop: hide mobile-only class */
.hide-mobile {
  /* visible by default on desktop */
}

/* Mobile card-table: hide less important columns */
@media (max-width: 767px) {
  .panel {
    padding: 10px;
  }

  .section-title {
    gap: 4px;
    margin-bottom: 4px;
  }

  .checkbox-col {
    width: auto;
  }

  .hide-mobile {
    display: none !important;
  }

  .selection-bar {
    flex-wrap: wrap;
    gap: 4px;
    font-size: 0.75rem;
    padding: 4px 6px;
  }

  .cell-title {
    max-width: none;
    font-size: 12px;
  }

  .table-wrap th,
  .table-wrap td {
    max-width: none;
    padding: 4px 6px;
  }

  .section-actions {
    gap: 4px;
  }

  .section-actions .monitor-select {
    min-width: 80px;
    max-width: 120px;
    padding: 4px 6px;
    font-size: 12px;
  }

  .section-actions button {
    padding: 4px 8px;
    font-size: 11px;
  }

  .action-row button {
    padding: 4px 8px;
    font-size: 11px;
  }
}

/* Tablet */
@media (min-width: 768px) and (max-width: 1023px) {
  .table-wrap th,
  .table-wrap td {
    padding: 5px 7px;
    font-size: 12px;
  }

  .cell-title {
    max-width: 150px;
  }

  .table-wrap th,
  .table-wrap td {
    max-width: 120px;
  }
}
</style>
