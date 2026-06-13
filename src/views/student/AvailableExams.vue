<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>待考考试</h3>
        <p class="section-subtitle">{{ store.availableExams.length }} 场考试可参加</p>
      </div>
    </div>
    <div v-if="store.availableExams.length === 0" class="empty-state">暂无待考考试</div>
    <div v-else class="card-grid">
      <article v-for="e in store.availableExams" :key="e.id" class="mini-item">
        <h4>{{ e.name }}</h4>
        <p>{{ formatDate(e.startTime) }} ~ {{ formatDate(e.endTime) }}</p>
        <button class="primary-btn" type="button" @click="store.startExam(e.id)">进入考试</button>
      </article>
    </div>
  </article>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import { formatDate } from '@/utils/format'

const store = useAppStore()
</script>
