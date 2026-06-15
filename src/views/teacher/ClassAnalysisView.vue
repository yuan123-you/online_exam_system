<template>
  <div class="class-analysis-page">
    <header class="page-header">
      <div>
        <h3>班级成绩对比</h3>
        <p class="section-subtitle">按班级维度分析考试成绩，定位薄弱环节</p>
      </div>
      <select v-model="selectedExamId" @change="loadAnalysis" class="monitor-select">
        <option value="">选择考试</option>
        <option v-for="e in store.myExams" :key="e.id" :value="e.id">{{ e.name }}</option>
      </select>
    </header>

    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="!analysisData" class="empty-state">请选择一场考试查看班级分析</div>

    <template v-else>
      <!-- 班级概览卡片 -->
      <div class="class-cards-grid">
        <article
          v-for="cls in analysisData.classes"
          :key="cls.classId"
          class="class-card"
          :class="{ active: selectedClassId === cls.classId }"
          @click="selectedClassId = selectedClassId === cls.classId ? '' : cls.classId"
        >
          <div class="class-card-header">
            <h4>{{ cls.className }}</h4>
            <span class="class-badge">{{ cls.submittedCount }}/{{ cls.totalStudents }}人</span>
          </div>
          <div class="class-card-stats">
            <div class="stat-item">
              <span class="stat-label">平均分</span>
              <strong>{{ cls.avgScore ?? '-' }}</strong>
            </div>
            <div class="stat-item">
              <span class="stat-label">最高分</span>
              <strong style="color:var(--ok)">{{ cls.maxScore ?? '-' }}</strong>
            </div>
            <div class="stat-item">
              <span class="stat-label">最低分</span>
              <strong style="color:var(--danger)">{{ cls.minScore ?? '-' }}</strong>
            </div>
            <div class="stat-item">
              <span class="stat-label">通过率</span>
              <strong :style="{ color: cls.passRate >= 60 ? 'var(--ok)' : 'var(--danger)' }">{{ cls.passRate }}%</strong>
            </div>
          </div>
          <!-- 分数段分布条 -->
          <div class="distribution-bar">
            <div
              class="dist-segment dist-fail"
              :style="{ width: distPct(cls, 'fail') }"
              :title="`不及格: ${cls.distribution.fail}人`"
            ></div>
            <div
              class="dist-segment dist-60"
              :style="{ width: distPct(cls, 'd60') }"
              :title="`60-69: ${cls.distribution.d60}人`"
            ></div>
            <div
              class="dist-segment dist-70"
              :style="{ width: distPct(cls, 'd70') }"
              :title="`70-79: ${cls.distribution.d70}人`"
            ></div>
            <div
              class="dist-segment dist-80"
              :style="{ width: distPct(cls, 'd80') }"
              :title="`80-89: ${cls.distribution.d80}人`"
            ></div>
            <div
              class="dist-segment dist-90"
              :style="{ width: distPct(cls, 'd90') }"
              :title="`90+: ${cls.distribution.d90}人`"
            ></div>
          </div>
        </article>
      </div>

      <!-- 图表区域 -->
      <div class="charts-grid">
        <!-- 班级平均分+通过率对比 -->
        <ChartCard
          title="班级综合对比"
          description="平均分与通过率双维度对比"
          :option="comparisonChartOption"
        />

        <!-- 分数段分布堆叠图 -->
        <ChartCard
          title="分数段分布"
          description="各班级不同分数段人数分布"
          :option="distributionChartOption"
        />
      </div>

      <!-- 选中班级的题目分析 -->
      <div v-if="selectedClassData" class="class-detail-section">
        <h3 class="section-title">{{ selectedClassData.className }} - 题目正确率分析</h3>
        <p class="section-subtitle">定位该班级的薄弱知识点</p>

        <div class="table-wrap mobile-card-table">
          <table>
            <thead>
              <tr>
                <th>题目</th>
                <th>题型</th>
                <th>知识点</th>
                <th>正确率</th>
                <th>作答人数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="q in sortedQuestionStats" :key="q.questionId">
                <td class="cell-title">{{ q.title }}</td>
                <td><span class="tag">{{ q.type }}</span></td>
                <td>{{ q.knowledgePoint }}</td>
                <td>
                  <div class="rate-bar">
                    <div
                      class="rate-fill"
                      :class="q.correctRate >= 60 ? 'rate-ok' : 'rate-low'"
                      :style="{ width: q.correctRate + '%' }"
                    ></div>
                    <span class="rate-text">{{ q.correctRate }}%</span>
                  </div>
                </td>
                <td>{{ q.attemptCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { classAnalysis } from '@/api/client'
import type { ClassAnalysisResult, ClassAnalysisItem } from '@/api/client'
import ChartCard from '@/components/common/ChartCard.vue'

const store = useAppStore()
const selectedExamId = ref('')
const loading = ref(false)
const analysisData = ref<ClassAnalysisResult | null>(null)
const selectedClassId = ref('')

const selectedClassData = computed<ClassAnalysisItem | null>(() => {
  if (!analysisData.value || !selectedClassId.value) return null
  return analysisData.value.classes.find(c => c.classId === selectedClassId.value) ?? null
})

const sortedQuestionStats = computed(() => {
  if (!selectedClassData.value?.questionStats) return []
  return [...selectedClassData.value.questionStats].sort((a, b) => a.correctRate - b.correctRate)
})

function distPct(cls: ClassAnalysisItem, key: string): string {
  const total = cls.submittedCount
  if (total === 0) return '0%'
  const val = (cls.distribution as any)[key] || 0
  return Math.round(val / total * 100) + '%'
}

async function loadAnalysis() {
  if (!selectedExamId.value) {
    analysisData.value = null
    return
  }
  loading.value = true
  try {
    analysisData.value = await classAnalysis(selectedExamId.value)
    selectedClassId.value = ''
  } catch (err: any) {
    store.showToast(err?.message || '加载班级分析失败', 'error')
  } finally {
    loading.value = false
  }
}

// 班级综合对比图表
const comparisonChartOption = computed(() => {
  if (!analysisData.value) return {}
  const classes = analysisData.value.classes
  const names = classes.map(c => c.className)
  const avgScores = classes.map(c => c.avgScore ?? 0)
  const passRates = classes.map(c => c.passRate)
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: ['平均分', '通过率(%)'] },
    xAxis: { type: 'category', data: names },
    yAxis: [
      { type: 'value', name: '分数', min: 0 },
      { type: 'value', name: '通过率', max: 100, axisLabel: { formatter: '{value}%' } }
    ],
    series: [
      { name: '平均分', type: 'bar', data: avgScores, itemStyle: { color: 'var(--primary)', borderRadius: [4, 4, 0, 0] } },
      { name: '通过率(%)', type: 'line', yAxisIndex: 1, data: passRates, itemStyle: { color: '#e8c46e' }, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 8 }
    ],
    grid: { left: 50, right: 50, top: 40, bottom: 40 }
  }
})

// 分数段分布堆叠柱状图
const distributionChartOption = computed(() => {
  if (!analysisData.value) return {}
  const classes = analysisData.value.classes
  const names = classes.map(c => c.className)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, data: ['不及格', '60-69', '70-79', '80-89', '90+'] },
    xAxis: { type: 'category', data: names },
    yAxis: { type: 'value', name: '人数' },
    series: [
      { name: '不及格', type: 'bar', stack: 'total', data: classes.map(c => c.distribution.fail), itemStyle: { color: '#cf5c5c' } },
      { name: '60-69', type: 'bar', stack: 'total', data: classes.map(c => c.distribution.d60), itemStyle: { color: '#d4a844' } },
      { name: '70-79', type: 'bar', stack: 'total', data: classes.map(c => c.distribution.d70), itemStyle: { color: '#7ec8a8' } },
      { name: '80-89', type: 'bar', stack: 'total', data: classes.map(c => c.distribution.d80), itemStyle: { color: '#4db896' } },
      { name: '90+', type: 'bar', stack: 'total', data: classes.map(c => c.distribution.d90), itemStyle: { color: '#2e8a70' } },
    ],
    grid: { left: 50, right: 20, top: 40, bottom: 40 }
  }
})
</script>

<style scoped>
.class-analysis-page {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.class-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.class-card {
  background: var(--panel);
  border-radius: var(--radius);
  padding: 1rem;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.2s var(--ease);
  border: 2px solid transparent;
}

.class-card:hover {
  box-shadow: var(--shadow);
}

.class-card.active {
  border-color: var(--primary);
  box-shadow: var(--shadow-glow);
}

.class-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.class-card-header h4 {
  margin: 0;
  font-size: 1rem;
  color: var(--ink);
}

.class-badge {
  font-size: 0.75rem;
  background: var(--primary-soft);
  color: var(--primary);
  padding: 2px 8px;
  border-radius: 12px;
  font-weight: 500;
}

.class-card-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-item .stat-label {
  font-size: 0.75rem;
  color: var(--muted);
}

.stat-item strong {
  font-size: 1.1rem;
  color: var(--ink);
}

.distribution-bar {
  display: flex;
  height: 6px;
  border-radius: 3px;
  overflow: hidden;
  background: var(--panel-soft);
}

.dist-segment {
  transition: width 0.3s var(--ease);
}

.dist-fail { background: var(--danger); }
.dist-60 { background: var(--warn); }
.dist-70 { background: var(--accent); }
.dist-80 { background: var(--primary); }
.dist-90 { background: var(--primary-dark); }

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 380px), 1fr));
  gap: 1rem;
}

.class-detail-section {
  background: var(--panel);
  border-radius: var(--radius);
  padding: 1.25rem;
  box-shadow: var(--shadow-sm);
}

.rate-bar {
  position: relative;
  height: 20px;
  background: var(--panel-soft);
  border-radius: 4px;
  overflow: hidden;
  min-width: 80px;
}

.rate-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}

.rate-fill.rate-ok { background: var(--primary); }
.rate-fill.rate-low { background: var(--danger); }

.rate-text {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--ink);
}

.cell-title {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .class-cards-grid {
    grid-template-columns: 1fr;
  }
}
</style>
