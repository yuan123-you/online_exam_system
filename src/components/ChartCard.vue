<template>
  <article class="panel chart-panel">
    <div class="section-head">
      <div>
        <h3 style="font-size:17px;font-weight:700;letter-spacing:-0.02em;">{{ title }}</h3>
        <p v-if="description" class="muted" style="font-size:13px;margin-top:2px;">{{ description }}</p>
      </div>
    </div>
    <div ref="container" class="chart-surface" :style="{ height }"></div>
  </article>
</template>

<script setup lang="ts">
import * as echarts from "echarts";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { EChartsOption } from "echarts";

const props = withDefaults(
  defineProps<{
    title: string;
    option: EChartsOption;
    description?: string;
    height?: string;
  }>(),
  {
    height: "300px",
  }
);

const container = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let observer: ResizeObserver | null = null;

function renderChart() {
  if (!container.value) return;
  chart ??= echarts.init(container.value);
  chart.setOption(props.option, true);
}

onMounted(() => {
  renderChart();
  if (container.value) {
    observer = new ResizeObserver(() => {
      chart?.resize();
    });
    observer.observe(container.value);
  }
});

watch(
  () => props.option,
  () => renderChart(),
  { deep: true }
);

onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
