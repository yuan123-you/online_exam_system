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
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
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
// 防止重复重试初始化
let initRetryRafId = 0;
// 标记是否已成功初始化，避免在容器尺寸为 0 时反复 setOption
let initialized = false;

function renderChart() {
  if (!container.value) return;
  // 容器尺寸为 0 时延迟初始化，等 ResizeObserver 触发后再初始化
  if (!chart) {
    const width = container.value.clientWidth;
    const height = container.value.clientHeight;
    if (width === 0 || height === 0) {
      // 延迟到下一帧重试，等待布局完成
      if (initRetryRafId) cancelAnimationFrame(initRetryRafId);
      initRetryRafId = requestAnimationFrame(() => {
        initRetryRafId = 0;
        renderChart();
      });
      return;
    }
    chart = echarts.init(container.value);
    initialized = true;
  }
  chart.setOption(props.option, true);
  // setOption 后主动 resize，确保画布尺寸与容器一致
  chart.resize();
}

onMounted(() => {
  // 使用 nextTick 确保 DOM 布局完成后再初始化图表
  nextTick(() => {
    renderChart();
  });
  if (container.value) {
    observer = new ResizeObserver(() => {
      // 容器尺寸变化时：若图表尚未初始化则尝试初始化，否则仅 resize
      if (!chart) {
        renderChart();
      } else {
        chart.resize();
      }
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
  if (initRetryRafId) {
    cancelAnimationFrame(initRetryRafId);
    initRetryRafId = 0;
  }
  observer?.disconnect();
  observer = null;
  chart?.dispose();
  chart = null;
  initialized = false;
});
</script>
