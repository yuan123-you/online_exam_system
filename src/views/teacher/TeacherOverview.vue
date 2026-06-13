<template>
  <div>
    <section class="stats-grid compact-stats">
      <article class="stat-card compact-card">
        <span class="muted">题库</span>
        <strong>{{ store.myQuestions.length }}</strong>
      </article>
      <article class="stat-card compact-card">
        <span class="muted">试卷</span>
        <strong>{{ store.myPapers.length }}</strong>
      </article>
      <article class="stat-card compact-card">
        <span class="muted">考试</span>
        <strong>{{ store.myExams.length }}</strong>
      </article>
      <article class="stat-card compact-card">
        <span class="muted">待阅卷</span>
        <strong>{{ store.pendingGradeCount }}</strong>
      </article>
    </section>
    <ChartCard
      title="题库分布"
      description="按科目统计题目数量"
      :option="store.subjectChartOption"
    />
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import ChartCard from '@/components/common/ChartCard.vue'

const store = useAppStore()
</script>
