<template>
  <section class="grades-page">
    <article class="panel" style="margin-bottom:1rem">
      <div class="section-title">
        <div>
          <h3>成绩单</h3>
          <p class="section-subtitle">个人成绩趋势与知识点掌握分析</p>
        </div>
      </div>
      <div v-if="store.scoreTrendData.length === 0" class="empty-state">暂无已完成考试</div>
      <template v-else>
        <section class="stats-grid compact-stats">
          <article class="stat-card compact-card"><span class="muted">考试次数</span><strong>{{ store.scoreTrendData.length }}</strong></article>
          <article class="stat-card compact-card"><span class="muted">最高分</span><strong style="color:var(--primary)">{{ Math.max(...store.scoreTrendData.map(d => d.score)) }}</strong></article>
          <article class="stat-card compact-card"><span class="muted">平均分</span><strong>{{ Math.round(store.scoreTrendData.reduce((s, d) => s + d.score, 0) / store.scoreTrendData.length) }}</strong></article>
        </section>
      </template>
    </article>
    <div class="grades-charts">
      <ChartCard
        v-if="store.scoreTrendData.length > 0"
        title="成绩趋势"
        description="历次考试得分变化"
        :option="store.scoreTrendOption"
      />
      <ChartCard
        v-if="store.subjectMasteryData.length > 0"
        title="知识点掌握"
        description="各科目掌握率雷达图"
        :option="store.knowledgeRadarOption"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import ChartCard from '@/components/common/ChartCard.vue'

const store = useAppStore()

onMounted(() => {
  store.loadStudentGrades()
})
</script>

<style scoped>
.grades-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.grades-charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
  gap: 1rem;
}

/* Mobile: stats grid 3 columns for compact cards */
@media (max-width: 767px) {
  .grades-charts {
    grid-template-columns: 1fr;
    gap: 0.75rem;
  }
}

/* Tablet: 2 columns for charts */
@media (min-width: 768px) and (max-width: 1023px) {
  .grades-charts {
    grid-template-columns: 1fr;
  }
}
</style>
