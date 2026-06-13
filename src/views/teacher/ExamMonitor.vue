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
        <button v-if="store.monitorExamId" class="accent-btn" type="button" @click="store.handleExportScores()" :disabled="store.exportLoading">导出成绩</button>
        <button v-if="store.monitorExamId" class="ghost-btn" type="button" @click="store.loadQuestionAnalysisData(store.monitorExamId)">错题分析</button>
      </div>
    </div>
    <template v-if="store.monitorResult">
      <section class="stats-grid compact-stats">
        <article class="stat-card compact-card"><span class="muted">总人数</span><strong>{{ store.monitorResult.totalCount }}</strong></article>
        <article class="stat-card compact-card"><span class="muted">未开始</span><strong>{{ store.monitorResult.notStartedCount }}</strong></article>
        <article class="stat-card compact-card"><span class="muted">进行中</span><strong>{{ store.monitorResult.runningCount }}</strong></article>
        <article class="stat-card compact-card"><span class="muted">已交卷</span><strong>{{ store.monitorResult.submittedCount }}</strong></article>
        <article class="stat-card compact-card"><span class="muted">最高分</span><strong style="color:var(--primary)">{{ store.monitorResult.maxScore ?? '-' }}</strong></article>
        <article class="stat-card compact-card"><span class="muted">最低分</span><strong>{{ store.monitorResult.minScore ?? '-' }}</strong></article>
        <article class="stat-card compact-card"><span class="muted">平均分</span><strong>{{ store.monitorResult.avgScore ?? '-' }}</strong></article>
      </section>
      <div class="table-wrap">
        <table>
          <thead><tr><th>学生</th><th>班级</th><th>状态</th><th>得分</th><th>切屏</th><th>用时</th><th>可疑</th></tr></thead>
          <tbody>
            <tr v-for="s in store.monitorResult.students" :key="s.studentId">
              <td><strong>{{ s.studentName }}</strong></td>
              <td>{{ s.className }}</td>
              <td><span class="tag">{{ s.status }}</span></td>
              <td><strong style="color:var(--primary)">{{ s.score ?? '-' }}</strong></td>
              <td>{{ s.switchCount }}</td>
              <td>{{ s.usedTimeText || '-' }}</td>
              <td>
                <span v-if="s.suspicious" class="tag danger-tag" :title="(s.suspiciousReasons || []).join(', ')">可疑</span>
                <span v-else>-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template v-if="store.questionAnalysisResult">
        <h4 style="margin:1.5rem 0 0.5rem">题目正确率分析</h4>
        <ChartCard title="题目正确率" description="每道题的正确率统计" :option="store.questionAnalysisChartOption" />
        <ChartCard title="知识点掌握分析" description="各知识点正确率" :option="store.kpAnalysisChartOption" />
        <div class="table-wrap" style="margin-top:1rem">
          <table>
            <thead><tr><th>题号</th><th>题型</th><th>知识点</th><th>作答人数</th><th>正确人数</th><th>正确率</th></tr></thead>
            <tbody>
              <tr v-for="(q, i) in store.questionAnalysisResult.questions" :key="q.questionId">
                <td>{{ i + 1 }}</td>
                <td><span class="tag">{{ typeLabel(q.type as any) }}</span></td>
                <td>{{ q.knowledgePoint }}</td>
                <td>{{ q.totalAttempts }}</td>
                <td>{{ q.correctCount }}</td>
                <td :style="{ color: q.correctRate < 60 ? 'var(--danger)' : 'var(--primary)' }">{{ q.correctRate }}%</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>
    <div v-else class="empty-state">请选择一场考试查看监控数据</div>
  </article>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import ChartCard from '@/components/common/ChartCard.vue'
import { typeLabel } from '@/utils/format'

const store = useAppStore()

onMounted(() => {
  if (!store.monitorExamId && store.myExams.length > 0) {
    store.monitorExamId = store.myExams[0].id
    store.loadMonitorData(store.monitorExamId)
  }
})
</script>
