<template>
  <article class="panel">
    <!-- Quota Progress Bar -->
    <QuotaBar
      label="练习记录"
      :used="practiceQuotaUsed"
      :limit="1000"
      warning-text="练习记录即将满额，最早的记录将被自动归档。"
    />

    <div class="section-title">
      <div>
        <h3>练习记录</h3>
        <p class="section-subtitle">查看历史练习情况与正确率</p>
      </div>
      <button class="ghost-btn" type="button" @click="$router.push('/ai-practice')">AI 助手</button>
    </div>

    <div v-if="practiceRecords.length === 0" class="empty-state">暂无练习记录</div>
    <div v-else class="table-wrap mobile-card-table">
      <table>
        <thead>
          <tr>
            <th>科目</th>
            <th>题型</th>
            <th>题目</th>
            <th>结果</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in pagination.paginatedData.value" :key="r.id">
            <td data-label="科目">{{ r.subject }}</td>
            <td data-label="题型"><span class="tag">{{ typeLabel(r.type as any) }}</span></td>
            <td data-label="题目" class="cell-title">{{ r.title?.slice(0, 30) }}{{ (r.title?.length || 0) > 30 ? '...' : '' }}</td>
            <td data-label="结果">
              <span :class="r.lastRetryCorrect ? 'text-success' : 'text-danger'">
                {{ r.lastRetryCorrect ? '正确' : '错误' }}
              </span>
            </td>
            <td data-label="时间" class="muted">{{ r.lastWrongAt ? formatDate(r.lastWrongAt) : '-' }}</td>
          </tr>
        </tbody>
      </table>
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
import { computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel, formatDate } from '@/utils/format'
import QuotaBar from '@/components/common/QuotaBar.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { useClientPagination } from '@/composables/usePagination'

const store = useAppStore()

onMounted(async () => {
  await store.loadQuotaData()
})

const practiceQuotaUsed = computed(() => {
  if (store.quotaData?.practiceRecords) {
    return store.quotaData.practiceRecords.used
  }
  return 0
})

const practiceRecords = computed(() => {
  return (store.bootstrap?.wrongBookEntries || [])
    .filter(e => e.studentId === store.currentUser?.id && (e as any).status === 'practice')
    .map(e => ({ ...e, lastWrongAt: (e as any).lastWrongAt || (e as any).lastRetryAt || '' }))
})

const pagination = useClientPagination(practiceRecords, { defaultPageSize: 20, pageSizeOptions: [10, 20, 50] })
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

.ghost-btn {
  padding: 6px 12px;
  font-size: 13px;
  white-space: nowrap;
}

.text-success {
  color: #22c55e;
  font-weight: 600;
}

.text-danger {
  color: #ef4444;
  font-weight: 600;
}

.cell-title {
  overflow-wrap: anywhere;
  word-break: break-word;
  max-width: 200px;
}

.tag {
  white-space: nowrap;
  font-size: 11px;
  padding: 2px 8px;
}

.table-wrap {
  overflow: hidden;
  max-width: 100%;
}

.table-wrap table {
  table-layout: auto;
  width: 100%;
  min-width: 0;
}

.table-wrap th,
.table-wrap td {
  padding: 6px 8px;
  font-size: 12px;
  overflow-wrap: break-word;
  word-break: break-word;
  max-width: 160px;
}

.table-wrap td.cell-title {
  max-width: 200px;
}

.muted {
  font-size: 12px;
}

@media (max-width: 767px) {
  .panel {
    padding: 10px;
  }

  .section-title {
    gap: 4px;
    margin-bottom: 4px;
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

  .ghost-btn {
    padding: 5px 10px;
    font-size: 12px;
  }
}
</style>
