<template>
  <div>
    <section class="stats-grid compact-stats">
      <article class="stat-card compact-card">
        <span class="muted">学生</span>
        <strong>{{ store.studentCount }}</strong>
      </article>
      <article class="stat-card compact-card">
        <span class="muted">教师</span>
        <strong>{{ store.teacherCount }}</strong>
      </article>
      <article class="stat-card compact-card">
        <span class="muted">题库</span>
        <strong>{{ store.bootstrap?.questions.length || 0 }}</strong>
      </article>
      <article class="stat-card compact-card">
        <span class="muted">考试</span>
        <strong>{{ store.bootstrap?.exams.length || 0 }}</strong>
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
