<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>考试监控</h3>
        <p class="section-subtitle">实时查看考生答题状态</p>
      </div>
      <div class="section-actions">
        <select v-model="store.monitorExamId" @change="store.loadMonitorData(store.monitorExamId)" class="monitor-select">
          <option value="">选择考试</option>
          <option v-for="e in store.myExams" :key="e.id" :value="e.id">{{ e.name }}</option>
        </select>
        <label class="auto-refresh-toggle">
          <input type="checkbox" v-model="autoRefresh" />
          <span class="toggle-slider"></span>
          <span class="toggle-label">自动刷新</span>
        </label>
        <button v-if="store.monitorExamId" class="accent-btn" type="button" @click="store.handleExportScores()" :disabled="store.exportLoading">
          {{ store.exportLoading ? '导出中...' : '导出成绩' }}
        </button>
        <button v-if="store.monitorExamId" class="ghost-btn" type="button" @click="store.loadQuestionAnalysisData(store.monitorExamId)">错题分析</button>
      </div>
    </div>

    <template v-if="store.monitorResult">
      <!-- 统计网格 -->
      <section class="stats-grid">
        <article class="stat-card">
          <span class="stat-label">总人数</span>
          <strong class="stat-value">{{ store.monitorResult.totalCount }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">未开始</span>
          <strong class="stat-value stat-value--muted">{{ store.monitorResult.notStartedCount }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">进行中</span>
          <strong class="stat-value stat-value--active">{{ store.monitorResult.runningCount }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">已交卷</span>
          <strong class="stat-value stat-value--submitted">{{ store.monitorResult.submittedCount }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">最高分</span>
          <strong class="stat-value stat-value--primary">{{ store.monitorResult.maxScore ?? '-' }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">最低分</span>
          <strong class="stat-value stat-value--danger">{{ store.monitorResult.minScore ?? '-' }}</strong>
        </article>
        <article class="stat-card">
          <span class="stat-label">平均分</span>
          <strong class="stat-value">{{ store.monitorResult.avgScore ?? '-' }}</strong>
        </article>
      </section>

      <!-- 学生表格 -->
      <div class="table-wrap mobile-card-table">
        <table v-if="store.monitorResult.students && store.monitorResult.students.length > 0">
          <thead>
            <tr>
              <th>学生</th>
              <th>班级</th>
              <th>状态</th>
              <th>得分</th>
              <th>切屏</th>
              <th>用时</th>
              <th>可疑</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="s in store.monitorResult.students"
              :key="s.studentId"
              :class="{ 'row--suspicious': s.suspicious }"
            >
              <td data-label="学生"><strong>{{ s.studentName }}</strong></td>
              <td data-label="班级">{{ s.className }}</td>
              <td data-label="状态"><span class="badge" :class="studentStatusClass(s.status)">{{ s.status }}</span></td>
              <td data-label="得分"><strong class="score-primary">{{ s.score ?? '-' }}</strong></td>
              <td data-label="切屏">
                <span :class="{ 'switch-warn': s.switchCount > 3 }">{{ s.switchCount }}</span>
              </td>
              <td data-label="用时">{{ s.usedTimeText || '-' }}</td>
              <td data-label="可疑">
                <span v-if="s.suspicious" class="badge badge--danger" :title="(s.suspiciousReasons || []).join(', ')">可疑</span>
                <span v-else class="badge badge--ok">正常</span>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <div class="empty-icon">👥</div>
          <p class="empty-title">暂无考生数据</p>
          <p class="empty-desc">等待考生进入考试后将显示实时状态</p>
        </div>
      </div>

      <!-- 错题分析 -->
      <template v-if="store.questionAnalysisResult">
        <h4 class="analysis-heading">题目正确率分析</h4>
        <ChartCard title="题目正确率" description="每道题的正确率统计" :option="store.questionAnalysisChartOption" />
        <ChartCard title="知识点掌握分析" description="各知识点正确率" :option="store.kpAnalysisChartOption" />
        <div class="table-wrap mobile-card-table" style="margin-top:1rem">
          <table>
            <thead>
              <tr>
                <th>题号</th>
                <th>题型</th>
                <th>知识点</th>
                <th>作答人数</th>
                <th>正确人数</th>
                <th>正确率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(q, i) in store.questionAnalysisResult.questions" :key="q.questionId">
                <td data-label="题号">{{ i + 1 }}</td>
                <td data-label="题型"><span class="badge badge--blue">{{ typeLabel(q.type as any) }}</span></td>
                <td data-label="知识点">{{ q.knowledgePoint }}</td>
                <td data-label="作答人数">{{ q.totalAttempts }}</td>
                <td data-label="正确人数">{{ q.correctCount }}</td>
                <td data-label="正确率">
                  <span :class="q.correctRate < 60 ? 'rate-low' : 'rate-ok'">{{ q.correctRate }}%</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>

    <!-- 无数据空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">📊</div>
      <p class="empty-title">请选择一场考试</p>
      <p class="empty-desc">选择考试后可查看实时监控数据</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useAppStore } from '@/stores/app'
import ChartCard from '@/components/common/ChartCard.vue'
import { typeLabel } from '@/utils/format'

const store = useAppStore()
const autoRefresh = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    if (store.monitorExamId) {
      store.loadMonitorData(store.monitorExamId)
    }
  }, 30000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

watch(autoRefresh, (val) => {
  if (val) startAutoRefresh()
  else stopAutoRefresh()
})

function studentStatusClass(status: string) {
  if (status === '进行中') return 'badge--green'
  if (status === '已交卷') return 'badge--blue'
  return 'badge--gray'
}

onMounted(() => {
  if (!store.monitorExamId && store.myExams.length > 0) {
    store.monitorExamId = store.myExams[0].id
    store.loadMonitorData(store.monitorExamId)
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
/* 自动刷新开关 */
.auto-refresh-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--text, #333);
  user-select: none;
}

.auto-refresh-toggle input {
  display: none;
}

.toggle-slider {
  position: relative;
  width: 36px;
  height: 20px;
  background: var(--border, #d1d5db);
  border-radius: 999px;
  transition: background 0.2s;
}

.toggle-slider::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

.auto-refresh-toggle input:checked + .toggle-slider {
  background: var(--primary, #4f46e5);
}

.auto-refresh-toggle input:checked + .toggle-slider::after {
  transform: translateX(16px);
}

.toggle-label {
  color: var(--muted, #888);
}

/* 统计网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 0.75rem;
  margin-bottom: 1.25rem;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  padding: 0.75rem 0.5rem;
  border-radius: 10px;
  background: var(--card-bg, #f9fafb);
  border: 1px solid var(--border, #e5e7eb);
  text-align: center;
}

.stat-label {
  font-size: 0.78rem;
  color: var(--muted, #888);
}

.stat-value {
  font-size: 1.25rem;
  color: var(--text, #333);
}

.stat-value--primary {
  color: var(--primary, #4f46e5);
}

.stat-value--active {
  color: #16a34a;
}

.stat-value--submitted {
  color: #2563eb;
}

.stat-value--muted {
  color: #6b7280;
}

.stat-value--danger {
  color: var(--danger, #dc2626);
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

.badge--blue {
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.badge--green {
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
}

.badge--gray {
  background: rgba(156, 163, 175, 0.15);
  color: #6b7280;
}

.badge--danger {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}

.badge--ok {
  background: rgba(34, 197, 94, 0.08);
  color: #16a34a;
}

/* 可疑行高亮 */
.row--suspicious {
  background: rgba(239, 68, 68, 0.05) !important;
  border-left: 3px solid #dc2626;
}

.score-primary {
  color: var(--primary, #4f46e5);
}

.switch-warn {
  color: var(--danger, #dc2626);
  font-weight: 600;
}

.rate-low {
  color: var(--danger, #dc2626);
  font-weight: 600;
}

.rate-ok {
  color: var(--primary, #4f46e5);
}

.analysis-heading {
  margin: 1.5rem 0 0.5rem;
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
  .section-actions {
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .monitor-select {
    width: 100%;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 0.5rem;
  }

  .stat-card {
    padding: 0.6rem 0.4rem;
  }

  .stat-value {
    font-size: 1.05rem;
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

  .row--suspicious {
    border-left: none;
    border: 2px solid rgba(239, 68, 68, 0.4);
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .table-wrap {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
}
</style>
