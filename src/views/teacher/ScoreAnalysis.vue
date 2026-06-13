<template>
  <section class="two-column">
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
  </section>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import ChartCard from '@/components/common/ChartCard.vue'

const store = useAppStore()
</script>
