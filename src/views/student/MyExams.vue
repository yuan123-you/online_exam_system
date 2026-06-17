<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>考试记录</h3>
        <p class="section-subtitle">共 {{ store.mySubmissionRecords.length }} 条记录</p>
      </div>
    </div>
    <div v-if="store.mySubmissionRecords.length === 0" class="empty-state">暂无考试记录</div>
    <div v-else class="table-wrap mobile-card-table">
      <table>
        <thead><tr><th>考试</th><th>状态</th><th>成绩</th><th>排名</th><th>耗时</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="s in pagination.paginatedData.value" :key="s.id">
            <td data-label="考试"><strong>{{ s.examName || '-' }}</strong></td>
            <td data-label="状态"><span class="tag">{{ s.status }}</span></td>
            <td data-label="成绩"><strong style="color:var(--primary)">{{ s.finalScore != null ? s.finalScore : (s.autoScore != null ? s.autoScore : '待出分') }}</strong> / {{ s.totalScore ?? '-' }}</td>
            <td data-label="排名">{{ s.rank ? `${s.rank} / ${s.finishedCount}` : '-' }}</td>
            <td data-label="耗时">{{ s.usedTimeText || '-' }}</td>
            <td data-label="操作">
              <button
                v-if="s.answerDetail && s.answerDetail.length > 0"
                class="ghost-btn"
                type="button"
                @click="store.reviewSubmission(s)"
              >查看详情</button>
              <span v-else class="muted">-</span>
            </td>
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
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { useClientPagination } from '@/composables/usePagination'

const store = useAppStore()

const myRecords = computed(() => store.mySubmissionRecords as any[])
const pagination = useClientPagination(myRecords, { defaultPageSize: 20, pageSizeOptions: [10, 20, 50] })
</script>
