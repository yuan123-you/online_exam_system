<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>阅卷中心</h3>
        <p class="section-subtitle">待阅卷 {{ store.pendingGradeCount }} 份</p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-tabs">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          class="filter-tab"
          :class="{ 'filter-tab--active': statusFilter === opt.value }"
          type="button"
          @click="statusFilter = opt.value"
        >
          {{ opt.label }}
          <span v-if="opt.count !== undefined" class="filter-count">{{ opt.count }}</span>
        </button>
      </div>
      <input
        v-model="searchQuery"
        type="text"
        class="search-input"
        placeholder="搜索学生姓名..."
      />
    </div>

    <!-- 表格 -->
    <div class="table-wrap mobile-card-table">
      <table v-if="filteredSubmissions.length > 0">
        <thead>
          <tr>
            <th>考试</th>
            <th>学生</th>
            <th>状态</th>
            <th>成绩</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in pagination.paginatedData.value" :key="s.id">
            <td data-label="考试">{{ s.examName || '-' }}</td>
            <td data-label="学生"><strong>{{ s.studentName }}</strong></td>
            <td data-label="状态">
              <span class="badge" :class="s.status === '待阅卷' ? 'badge--amber' : 'badge--green'">
                {{ s.status }}
              </span>
            </td>
            <td data-label="成绩">
              <span class="score" :class="scoreClass(s)">{{ s.finalScore ?? s.autoScore ?? 0 }}</span>
              <span class="score-total"> / {{ s.totalScore ?? '-' }}</span>
            </td>
            <td data-label="操作">
              <div class="action-row">
                <button class="primary-btn" type="button" @click="store.reviewSubmission(s)">查看 / 阅卷</button>
                <button
                  v-if="s.status === '待阅卷'"
                  class="ai-btn"
                  type="button"
                  :disabled="aiLoadingId === s.id"
                  @click="handleAiGrade(s.id)"
                >
                  {{ aiLoadingId === s.id ? '评分中...' : 'AI 阅卷' }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">📝</div>
        <p class="empty-title">{{ hasActiveFilter ? '没有匹配的答卷' : '暂无待阅卷答卷' }}</p>
        <p class="empty-desc">{{ hasActiveFilter ? '请尝试调整筛选条件' : '学生提交答卷后将在此显示' }}</p>
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
import { useAppStore } from '@/stores/app'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { useClientPagination } from '@/composables/usePagination'

const store = useAppStore()
const statusFilter = ref<'all' | 'pending' | 'graded'>('all')
const searchQuery = ref('')
const aiLoadingId = ref<string | null>(null)

const statusOptions = computed(() => [
  { label: '全部', value: 'all' as const, count: store.mySubmissions.length },
  { label: '待阅卷', value: 'pending' as const, count: store.pendingGradeCount },
  { label: '已阅卷', value: 'graded' as const, count: store.mySubmissions.filter(s => s.status === '已完成' || s.status === '已阅卷').length },
])

const filteredSubmissions = computed(() => {
  let list = store.mySubmissions
  if (statusFilter.value === 'pending') list = list.filter(s => s.status === '待阅卷')
  if (statusFilter.value === 'graded') list = list.filter(s => s.status === '已完成' || s.status === '已阅卷')
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(s => s.studentName.toLowerCase().includes(q))
  return list
})

const pagination = useClientPagination(filteredSubmissions, { defaultPageSize: 15, pageSizeOptions: [10, 15, 20, 50] })

const hasActiveFilter = computed(() => statusFilter.value !== 'all' || searchQuery.value.trim() !== '')

function scoreClass(s: { finalScore?: number | null; autoScore?: number | null; totalScore?: number | null; status?: string }) {
  if (s.status !== '已阅卷' && s.status !== '已完成') return ''
  const score = s.finalScore ?? s.autoScore ?? 0
  const total = s.totalScore ?? 100
  return score >= total * 0.6 ? 'score--pass' : 'score--fail'
}

async function handleAiGrade(id: string) {
  aiLoadingId.value = id
  try {
    await store.handleAiGradeSubmission(id)
  } finally {
    aiLoadingId.value = null
  }
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.filter-tabs {
  display: flex;
  gap: 0.35rem;
  background: var(--card-bg, #f5f5f5);
  border: 1px solid var(--border, #e0e0e0);
  border-radius: 8px;
  padding: 0.2rem;
}

.filter-tab {
  padding: 0.35rem 0.75rem;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--muted, #888);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.filter-tab:hover {
  color: var(--text, #333);
  background: rgba(0, 0, 0, 0.04);
}

.filter-tab--active {
  background: var(--primary, #4f46e5);
  color: #fff;
}

.filter-tab--active:hover {
  background: var(--primary, #4f46e5);
  color: #fff;
}

.filter-count {
  font-size: 0.72rem;
  background: rgba(0, 0, 0, 0.1);
  padding: 0.05rem 0.35rem;
  border-radius: 999px;
  line-height: 1.3;
}

.filter-tab--active .filter-count {
  background: rgba(255, 255, 255, 0.25);
}

.search-input {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--border, #e0e0e0);
  border-radius: 8px;
  font-size: 0.9rem;
  background: var(--card-bg, #fff);
  color: var(--text, #333);
  outline: none;
  transition: border-color 0.2s;
  min-width: 180px;
}

.search-input:focus {
  border-color: var(--primary, #4f46e5);
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.15);
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

.badge--amber {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

.badge--green {
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
}

/* 分数颜色 */
.score {
  font-weight: 700;
  font-size: 1rem;
}

.score--pass {
  color: #16a34a;
}

.score--fail {
  color: var(--danger, #dc2626);
}

.score-total {
  color: var(--muted, #888);
  font-size: 0.85rem;
}

/* AI 阅卷按钮 */
.ai-btn {
  padding: 0.3rem 0.7rem;
  border: 1px solid rgba(139, 92, 246, 0.4);
  border-radius: 6px;
  background: rgba(139, 92, 246, 0.08);
  color: #7c3aed;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s;
}

.ai-btn:hover:not(:disabled) {
  background: rgba(139, 92, 246, 0.18);
  border-color: rgba(139, 92, 246, 0.6);
}

.ai-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-tabs {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .search-input {
    min-width: 0;
    width: 100%;
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
}

@media (min-width: 769px) and (max-width: 1024px) {
  .table-wrap {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
}
</style>
