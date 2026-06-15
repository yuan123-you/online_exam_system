<template>
  <div class="score-analysis">
    <!-- Page Header -->
    <header class="page-header">
      <h1 class="page-title">成绩分析</h1>
      <p class="page-subtitle">多维度成绩数据统计与可视化</p>
    </header>

    <!-- Exam Selector -->
    <div class="exam-selector">
      <label class="selector-label" for="exam-select">选择考试</label>
      <select id="exam-select" v-model="selectedExamId" class="selector-input">
        <option value="">全部考试</option>
        <option v-for="exam in store.myExams" :key="exam.id" :value="exam.id">
          {{ exam.name }}
        </option>
      </select>
    </div>

    <!-- Stats Summary Row -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon avg-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ avgScore }}</span>
          <span class="stat-label">平均分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon max-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ maxScore }}</span>
          <span class="stat-label">最高分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon min-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ minScore }}</span>
          <span class="stat-label">最低分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon pass-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ passRate }}%</span>
          <span class="stat-label">通过率</span>
        </div>
      </div>
    </div>

    <!-- Chart Grid -->
    <div class="chart-grid">
      <ChartCard
        title="成绩分布"
        description="各分数段人数统计"
        :option="store.scoreDistOption"
      />
      <ChartCard
        title="考试通过率"
        description="各场考试的通过比例"
        :option="store.passRateOption"
      />
      <ChartCard
        v-if="store.bootstrap && store.bootstrap.classes.length > 1"
        title="班级成绩对比"
        description="各班级平均分与最高分对比"
        :option="store.classComparisonOption"
      />
    </div>

    <!-- Data Table -->
    <div class="table-section">
      <h2 class="section-title">成绩明细</h2>
      <div class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>学生</th>
              <th>考试</th>
              <th>得分</th>
              <th>总分</th>
              <th>及格线</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in filteredSubmissions" :key="s.id">
              <td data-label="学生">{{ s.studentName }}</td>
              <td data-label="考试">{{ s.examName || '-' }}</td>
              <td data-label="得分" class="score-cell">{{ s.finalScore ?? s.autoScore ?? '-' }}</td>
              <td data-label="总分">{{ s.totalScore ?? '-' }}</td>
              <td data-label="及格线">{{ s.passScore ?? '-' }}</td>
              <td data-label="状态">
                <span :class="['status-badge', passStatusClass(s)]">{{ passStatusText(s) }}</span>
              </td>
            </tr>
            <tr v-if="filteredSubmissions.length === 0">
              <td colspan="6" class="empty-row">暂无成绩数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import ChartCard from '@/components/common/ChartCard.vue'

const store = useAppStore()
const selectedExamId = ref('')

const filteredSubmissions = computed(() => {
  const subs = store.mySubmissions
  if (!selectedExamId.value) return subs
  return subs.filter((s) => s.examId === selectedExamId.value)
})

const scores = computed(() =>
  filteredSubmissions.value
    .filter((s) => s.status === '已完成')
    .map((s) => s.finalScore ?? s.autoScore ?? 0)
)

const avgScore = computed(() => {
  if (scores.value.length === 0) return '-'
  return (scores.value.reduce((a, b) => a + b, 0) / scores.value.length).toFixed(1)
})

const maxScore = computed(() => {
  if (scores.value.length === 0) return '-'
  return Math.max(...scores.value)
})

const minScore = computed(() => {
  if (scores.value.length === 0) return '-'
  return Math.min(...scores.value)
})

const passRate = computed(() => {
  const subs = filteredSubmissions.value.filter((s) => s.status === '已完成')
  if (subs.length === 0) return '-'
  const passed = subs.filter(
    (s) => s.passStatus === '通过' || (s.finalScore ?? s.autoScore ?? 0) >= (s.passScore ?? 60)
  ).length
  return Math.round((passed / subs.length) * 100)
})

function passStatusText(s: any): string {
  if (s.status !== '已完成') return s.status
  return (s.finalScore ?? s.autoScore ?? 0) >= (s.passScore ?? 60) ? '通过' : '未通过'
}

function passStatusClass(s: any): string {
  if (s.status !== '已完成') return 'status-pending'
  return (s.finalScore ?? s.autoScore ?? 0) >= (s.passScore ?? 60) ? 'status-pass' : 'status-fail'
}
</script>

<style scoped>
.score-analysis {
  --color-primary: #2563eb;
  --color-primary-light: #60a5fa;
  --color-success: #16a34a;
  --color-danger: #dc2626;
  --color-warning: #f59e0b;
  --color-bg: #f8fafc;
  --color-card: #ffffff;
  --color-text: #1e293b;
  --color-text-muted: #64748b;
  --color-border: #e2e8f0;
  --radius: 12px;
  --shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.07), 0 2px 4px rgba(0, 0, 0, 0.04);

  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

/* Page Header */
.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.02em;
  margin: 0;
}

.page-subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 4px 0 0;
}

/* Exam Selector */
.exam-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.selector-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
}

.selector-input {
  flex: 1;
  max-width: 320px;
  padding: 8px 12px;
  font-size: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-card);
  color: var(--color-text);
  outline: none;
  transition: border-color 0.2s;
}

.selector-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  transition: box-shadow 0.2s;
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 22px;
  height: 22px;
}

.avg-icon {
  background: #eff6ff;
  color: var(--color-primary);
}

.max-icon {
  background: #f0fdf4;
  color: var(--color-success);
}

.min-icon {
  background: #fef3c7;
  color: var(--color-warning);
}

.pass-icon {
  background: #f0fdf4;
  color: var(--color-success);
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

/* Chart Grid */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

/* Table Section */
.table-section {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.section-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
  padding: 20px 20px 0;
}

.table-wrapper {
  overflow-x: auto;
  padding: 16px 20px 20px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.data-table th {
  text-align: left;
  padding: 10px 12px;
  font-weight: 600;
  color: var(--color-text-muted);
  border-bottom: 2px solid var(--color-border);
  white-space: nowrap;
}

.data-table td {
  padding: 10px 12px;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
}

.data-table tbody tr:hover {
  background: var(--color-bg);
}

.score-cell {
  font-weight: 600;
}

.empty-row {
  text-align: center;
  color: var(--color-text-muted);
  padding: 32px 12px !important;
}

.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.status-pass {
  background: #dcfce7;
  color: var(--color-success);
}

.status-fail {
  background: #fef2f2;
  color: var(--color-danger);
}

.status-pending {
  background: #f1f5f9;
  color: var(--color-text-muted);
}

/* Responsive: Tablet */
@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* Responsive: Mobile */
@media (max-width: 640px) {
  .score-analysis {
    padding: 16px;
  }

  .page-title {
    font-size: 20px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }

  .stat-card {
    padding: 14px;
    gap: 10px;
  }

  .stat-icon {
    width: 38px;
    height: 38px;
  }

  .stat-icon svg {
    width: 18px;
    height: 18px;
  }

  .stat-value {
    font-size: 18px;
  }

  .chart-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .selector-input {
    max-width: 100%;
  }

  /* Mobile card-table pattern */
  .data-table thead {
    display: none;
  }

  .data-table tbody tr {
    display: block;
    padding: 14px 0;
    border-bottom: 1px solid var(--color-border);
  }

  .data-table tbody td {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 0;
    border-bottom: none;
  }

  .data-table tbody td::before {
    content: attr(data-label);
    font-weight: 600;
    color: var(--color-text-muted);
    font-size: 13px;
  }

  .empty-row {
    display: block !important;
    text-align: center;
  }
}
</style>
