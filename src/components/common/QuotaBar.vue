<template>
  <div class="quota-bar">
    <div class="quota-info">
      <span class="quota-label">{{ label }}</span>
      <span
        :class="{
          'quota-warn': percentage >= 70 && percentage < 90,
          'quota-danger': percentage >= 90,
        }"
        class="quota-text"
      >
        {{ used }} / {{ limit }} ({{ percentage }}%)
      </span>
    </div>
    <div class="quota-track">
      <div
        class="quota-fill"
        :class="{
          'quota-fill-warn': percentage >= 70 && percentage < 90,
          'quota-fill-danger': percentage >= 90,
        }"
        :style="{ width: clampedPercentage + '%' }"
      ></div>
    </div>
    <p v-if="percentage >= 90" class="quota-alert">{{ warningText || '存储空间即将用尽，请及时清理。' }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  used: number
  limit: number
  warningText?: string
}>()

const percentage = computed(() => {
  if (props.limit <= 0) return 0
  return Math.round((props.used / props.limit) * 100)
})

const clampedPercentage = computed(() => Math.min(100, percentage.value))
</script>

<style scoped>
.quota-bar {
  margin-bottom: 12px;
}

.quota-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 0.875rem;
}

.quota-label {
  font-weight: 600;
  color: var(--ink, #1a3a34);
}

.quota-text {
  color: var(--ok, #3d9980);
  font-weight: 500;
}

.quota-warn {
  color: var(--warn, #d4a844) !important;
}

.quota-danger {
  color: var(--danger, #cf5c5c) !important;
}

.quota-track {
  height: 8px;
  background: var(--line-soft, rgba(200, 221, 214, 0.6));
  border-radius: 4px;
  overflow: hidden;
}

.quota-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--ok, #3d9980);
  transition: width 0.4s ease, background-color 0.3s ease;
}

.quota-fill-warn {
  background: var(--warn, #d4a844) !important;
}

.quota-fill-danger {
  background: var(--danger, #cf5c5c) !important;
}

.quota-alert {
  margin-top: 6px;
  font-size: 0.8rem;
  color: var(--danger, #cf5c5c);
  font-weight: 500;
}
</style>
