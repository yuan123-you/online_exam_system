<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>试卷管理</h3>
        <p class="section-subtitle">共 {{ store.myPapers.length }} 套试卷</p>
      </div>
      <div class="section-actions">
        <button class="accent-btn" type="button" @click="store.autoGenVisible = true">自动组卷</button>
        <button class="primary-btn" type="button" @click="store.openPaperForm(null)">新增试卷</button>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="stats-bar">
      <div class="stat-item">
        <span class="stat-value">{{ store.myPapers.length }}</span>
        <span class="stat-label">试卷总数</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ totalQuestions }}</span>
        <span class="stat-label">题目总数</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ averageScore }}</span>
        <span class="stat-label">平均分</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8" />
        <line x1="21" y1="21" x2="16.65" y2="16.65" />
      </svg>
      <input
        v-model="searchQuery"
        type="text"
        class="search-input"
        placeholder="搜索试卷名称..."
      />
      <button v-if="searchQuery" class="clear-btn" type="button" @click="searchQuery = ''">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>

    <!-- 表格区域 -->
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>试卷名称</th>
            <th>题量</th>
            <th>总分</th>
            <th>及格线</th>
            <th>时长</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="filteredPapers.length === 0 && store.myPapers.length > 0" class="empty-row">
            <td colspan="6">
              <div class="empty-state">
                <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                  <line x1="8" y1="11" x2="14" y2="11" />
                </svg>
                <p>未找到匹配「{{ searchQuery }}」的试卷</p>
              </div>
            </td>
          </tr>
          <tr v-if="store.myPapers.length === 0" class="empty-row">
            <td colspan="6">
              <div class="empty-state">
                <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                  <polyline points="14 2 14 8 20 8" />
                  <line x1="16" y1="13" x2="8" y2="13" />
                  <line x1="16" y1="17" x2="8" y2="17" />
                </svg>
                <p>暂无试卷，点击上方按钮新增</p>
              </div>
            </td>
          </tr>
          <tr v-for="p in filteredPapers" :key="p.id">
            <td data-label="试卷名称"><span class="paper-name">{{ p.name }}</span></td>
            <td data-label="题量"><span class="badge">{{ p.questionIds.length }} 题</span></td>
            <td data-label="总分"><span class="score-value">{{ p.totalScore }}</span></td>
            <td data-label="及格线">{{ p.passScore }}</td>
            <td data-label="时长">
              <span class="duration-cell">
                <svg class="clock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                {{ p.durationMinutes }} 分钟
              </span>
            </td>
            <td data-label="操作">
              <div class="action-row">
                <button class="accent-btn" type="button" @click="store.previewPaper(p)">预览</button>
                <button class="ghost-btn" type="button" @click="store.openPaperForm(p)">编辑</button>
                <button class="danger-btn" type="button" @click="store.removeEntity('papers', p.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const searchQuery = ref('')

const filteredPapers = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return store.myPapers
  return store.myPapers.filter(p => p.name.toLowerCase().includes(q))
})

const totalQuestions = computed(() =>
  store.myPapers.reduce((sum, p) => sum + p.questionIds.length, 0)
)

const averageScore = computed(() => {
  if (store.myPapers.length === 0) return 0
  const total = store.myPapers.reduce((sum, p) => sum + p.totalScore, 0)
  return Math.round(total / store.myPapers.length * 10) / 10
})
</script>

<style scoped>
/* ---- CSS Custom Properties ---- */
:root {
  --pm-radius: 8px;
  --pm-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  --pm-transition: 0.2s ease;
}

/* ---- Stats Bar ---- */
.stats-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 12px;
  background: var(--bg, #fff);
  border: 1px solid var(--border, #e5e7eb);
  border-radius: var(--pm-radius);
  box-shadow: var(--pm-shadow);
  transition: transform var(--pm-transition);
}

.stat-item:hover {
  transform: translateY(-2px);
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary, #4f46e5);
  line-height: 1.2;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--muted, #9ca3af);
  margin-top: 4px;
}

/* ---- Search Bar ---- */
.search-bar {
  position: relative;
  margin-bottom: 20px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  color: var(--muted, #9ca3af);
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 10px 40px 10px 38px;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: var(--pm-radius);
  background: var(--bg, #fff);
  color: var(--fg, #111827);
  font-size: 0.9rem;
  outline: none;
  transition: border-color var(--pm-transition), box-shadow var(--pm-transition);
  box-sizing: border-box;
}

.search-input::placeholder {
  color: var(--muted, #9ca3af);
}

.search-input:focus {
  border-color: var(--primary, #4f46e5);
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);
}

.clear-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: var(--muted, #9ca3af);
  cursor: pointer;
  padding: 4px;
  border-radius: 50%;
  transition: color var(--pm-transition), background var(--pm-transition);
}

.clear-btn:hover {
  color: var(--fg, #111827);
  background: rgba(0, 0, 0, 0.06);
}

/* ---- Table ---- */
.table-wrap {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-radius: var(--pm-radius);
  border: 1px solid var(--border, #e5e7eb);
}

table {
  width: 100%;
  border-collapse: collapse;
}

thead th {
  background: var(--bg, #f9fafb);
  padding: 12px 16px;
  text-align: left;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--muted, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 2px solid var(--border, #e5e7eb);
  white-space: nowrap;
}

tbody td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border, #e5e7eb);
  vertical-align: middle;
  font-size: 0.9rem;
}

tbody tr:last-child td {
  border-bottom: none;
}

tbody tr:hover {
  background: rgba(79, 70, 229, 0.03);
}

/* ---- Paper Name ---- */
.paper-name {
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--fg, #111827);
}

/* ---- Badge ---- */
.badge {
  display: inline-block;
  padding: 3px 10px;
  font-size: 0.78rem;
  font-weight: 600;
  border-radius: 999px;
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary, #4f46e5);
  white-space: nowrap;
}

/* ---- Score Value ---- */
.score-value {
  font-weight: 700;
  font-size: 1rem;
  color: var(--primary, #4f46e5);
}

/* ---- Duration Cell ---- */
.duration-cell {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.clock-icon {
  width: 15px;
  height: 15px;
  color: var(--muted, #9ca3af);
  flex-shrink: 0;
}

/* ---- Action Row ---- */
.action-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

/* ---- Empty State ---- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--muted, #9ca3af);
}

.empty-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-state p {
  margin: 0;
  font-size: 0.9rem;
}

/* ---- Responsive: Tablet (<=1024px) ---- */
@media (max-width: 1024px) {
  .stats-bar {
    gap: 10px;
  }

  .stat-item {
    padding: 10px 8px;
  }

  .stat-value {
    font-size: 1.25rem;
  }
}

/* ---- Responsive: Mobile (<=768px) ---- */
@media (max-width: 768px) {
  .stats-bar {
    gap: 8px;
  }

  .stat-value {
    font-size: 1.1rem;
  }

  /* Card layout on mobile */
  .table-wrap {
    border: none;
  }

  table,
  thead,
  tbody,
  th,
  td,
  tr {
    display: block;
  }

  thead {
    position: absolute;
    left: -9999px;
    width: 1px;
    height: 1px;
    overflow: hidden;
  }

  tbody tr {
    position: relative;
    background: var(--bg, #fff);
    border: 1px solid var(--border, #e5e7eb);
    border-radius: var(--pm-radius);
    margin-bottom: 12px;
    padding: 14px 16px;
    box-shadow: var(--pm-shadow);
  }

  tbody tr:hover {
    background: var(--bg, #fff);
  }

  tbody td {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  }

  tbody td:last-child {
    border-bottom: none;
    justify-content: flex-start;
    padding-top: 12px;
  }

  tbody td::before {
    content: attr(data-label);
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--muted, #6b7280);
    text-transform: uppercase;
    letter-spacing: 0.03em;
    flex-shrink: 0;
    margin-right: 12px;
  }

  tbody td:last-child::before {
    display: none;
  }

  .empty-row td {
    display: block;
  }

  .empty-row td::before {
    display: none;
  }

  .action-row {
    width: 100%;
  }
}
</style>
