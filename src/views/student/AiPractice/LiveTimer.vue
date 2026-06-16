<!-- Isolated live timer component — prevents 1s interval from causing full message list re-renders -->
<template>
  <span :class="['live-timer-display', { active: active }]">{{ displayText }}</span>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  startTime: number
  active: boolean
}>()

const displayText = ref('0s')
let intervalId: ReturnType<typeof setInterval> | null = null

function updateDisplay() {
  if (!props.startTime) {
    displayText.value = '0s'
    return
  }
  const elapsed = Math.floor((Date.now() - props.startTime) / 1000)
  if (elapsed < 60) {
    displayText.value = elapsed + 's'
  } else {
    const m = Math.floor(elapsed / 60)
    const s = elapsed % 60
    displayText.value = m + 'm ' + s + 's'
  }
}

function startTimer() {
  updateDisplay()
  if (intervalId) clearInterval(intervalId)
  intervalId = setInterval(updateDisplay, 1000)
}

function stopTimer() {
  if (intervalId) {
    clearInterval(intervalId)
    intervalId = null
  }
}

watch(() => props.active, (active) => {
  if (active) {
    startTimer()
  } else {
    stopTimer()
  }
}, { immediate: true })

// When startTime changes while active (shouldn't normally happen), refresh display
watch(() => props.startTime, () => {
  if (props.active) {
    updateDisplay()
  }
})

// Handle visibility change — browser throttles setInterval in background tabs.
// When tab becomes visible again, immediately recalculate the timer from the
// stored absolute start time so the display is accurate without any "reset".
function handleVisibilityChange() {
  if (document.visibilityState === 'visible' && props.active) {
    updateDisplay()
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  stopTimer()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.live-timer-display {
  font-size: 11px;
  color: #9ca3af;
}
.live-timer-display.active {
  color: #6366f1;
  font-weight: 500;
  animation: timer-pulse 2s ease-in-out infinite;
}
@keyframes timer-pulse {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}
</style>
