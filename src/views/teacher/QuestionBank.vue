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
      </div>
    </div>
    <div class="table-wrap mobile-card-table">
      <table>
        <thead><tr><th>题型</th><th>科目</th><th>知识点</th><th>难度</th><th>题目</th><th>分值</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="store.questionsLoading">
            <td colspan="7" style="text-align:center;color:var(--muted);padding:20px;">加载中...</td>
          </tr>
          <tr v-else-if="store.paginatedQuestions.length === 0">
            <td colspan="7" style="text-align:center;color:var(--muted);padding:20px;">暂无题目，点击上方按钮新增或使用 AI 出题</td>
          </tr>
          <tr v-for="(q, idx) in store.paginatedQuestions" :key="idx">
            <td data-label="题型"><span class="tag">{{ typeLabel((q.type as QuestionType) || 'single') }}</span></td>
            <td data-label="科目">{{ q.subject }}</td>
            <td data-label="知识点">{{ q.knowledgePoint }}</td>
            <td data-label="难度">{{ q.difficulty }}</td>
            <td data-label="题目">{{ String(q.title || '').slice(0, 40) }}{{ String(q.title || '').length > 40 ? '...' : '' }}</td>
            <td data-label="分值"><strong style="color:var(--primary)">{{ q.score }}</strong></td>
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

    <!-- Pagination Controls -->
    <div v-if="totalPages > 1" class="pagination-bar">
      <button class="pagination-btn" :disabled="store.currentPage <= 1" @click="goPage(store.currentPage - 1)">‹ 上一页</button>
      <button
        v-for="p in visiblePages"
        :key="p"
        :class="['pagination-btn', { active: p === store.currentPage }]"
        @click="goPage(p)"
      >{{ p }}</button>
      <button class="pagination-btn" :disabled="store.currentPage >= totalPages" @click="goPage(store.currentPage + 1)">下一页 ›</button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import type { QuestionType } from '@/types'

const store = useAppStore()

const totalPages = computed(() => Math.max(1, Math.ceil(store.totalQuestions / store.pageSize)))

const visiblePages = computed(() => {
  const total = totalPages.value
  const current = store.currentPage
  const pages: number[] = []
  let start = Math.max(1, current - 2)
  let end = Math.min(total, current + 2)
  if (end - start < 4) {
    if (start === 1) end = Math.min(total, start + 4)
    else start = Math.max(1, end - 4)
  }
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

function goPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  store.loadQuestionsPage(page)
}

onMounted(() => {
  store.loadQuestionsPage(1)
})
</script>

<style scoped>
.pagination-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  padding: 16px 0;
  flex-wrap: wrap;
}

.pagination-btn {
  min-width: 36px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--card-bg, #fff);
  color: var(--text);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pagination-btn:hover:not(:disabled):not(.active) {
  border-color: var(--primary);
  color: var(--primary);
}

.pagination-btn.active {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
  font-weight: 600;
}

.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
