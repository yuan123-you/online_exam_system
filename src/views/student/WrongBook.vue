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

    <div v-if="store.filteredWrongBookEntries.length === 0" class="empty-state">暂无错题记录</div>
    <div v-else class="table-wrap">
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
            <th>科目</th><th>知识点</th><th>题型</th><th>题目</th><th>错误次数</th><th>重做次数</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in store.filteredWrongBookEntries"
            :key="entry.id"
            :class="{ 'row-selected': selectedIds.has(entry.id) }"
          >
            <td class="checkbox-col">
              <input
                type="checkbox"
                :checked="selectedIds.has(entry.id)"
                @change="toggleSelect(entry.id)"
              />
            </td>
            <td>{{ entry.subject }}</td>
            <td>{{ entry.knowledgePoint }}</td>
            <td><span class="tag">{{ typeLabel(entry.type) }}</span></td>
            <td>{{ entry.title.slice(0, 40) }}{{ entry.title.length > 40 ? '...' : '' }}</td>
            <td><strong style="color:var(--danger)">{{ entry.wrongCount }}</strong></td>
            <td>{{ entry.retryCount }}</td>
            <td>
              <span :class="entry.lastRetryCorrect ? 'status-ok' : 'status-pending'">
                {{ entry.lastRetryCorrect ? '已掌握' : '待重做' }}
              </span>
            </td>
            <td>
              <div class="action-row">
                <button class="primary-btn" type="button" @click="store.retryWrongEntry(entry)">重做</button>
                <button v-if="entry.removable" class="ghost-btn" type="button" @click="store.removeWrongEntry(entry.id)">移除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import QuotaBar from '@/components/common/QuotaBar.vue'

const store = useAppStore()
const selectedIds = ref<Set<string>>(new Set())

// Load quota data on mount
onMounted(async () => {
  await store.loadQuotaData()
})

// Quota: use real API data if available, fallback to local count
const wrongBookQuotaUsed = computed(() => {
  if (store.quotaData?.wrongBook) {
    return store.quotaData.wrongBook.used
  }
  return store.filteredWrongBookEntries.length
})

// Selection logic
const allSelected = computed(() =>
  store.filteredWrongBookEntries.length > 0 &&
  selectedIds.value.size === store.filteredWrongBookEntries.length
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
  if (allSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(store.filteredWrongBookEntries.map((e) => e.id))
  }
}

function clearSelection() {
  selectedIds.value = new Set()
}

function selectMastered() {
  const masteredIds = store.filteredWrongBookEntries
    .filter((e) => e.lastRetryCorrect)
    .map((e) => e.id)
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
  if (!confirm(`确定要移除选中的 ${count} 条错题记录吗？`)) return
  try {
    await store.handleBatchRemoveWrongBook(Array.from(selectedIds.value))
    selectedIds.value = new Set()
  } catch {
    // error toast already shown by store action
  }
}
</script>

<style scoped>
.section-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.checkbox-col {
  width: 40px;
  text-align: center;
}

.row-selected {
  background: var(--primary-soft, #e0f2ec);
}

.selection-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: var(--primary-soft, #e0f2ec);
  border-radius: 6px;
  font-size: 0.875rem;
  color: var(--primary, #3d9980);
  font-weight: 500;
}

.status-ok {
  color: var(--ok, #3d9980);
  font-weight: 500;
  font-size: 0.85rem;
}

.status-pending {
  color: var(--warn, #d4a844);
  font-weight: 500;
  font-size: 0.85rem;
}
</style>
