<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>考试记录</h3>
        <p class="section-subtitle">共 {{ store.mySubmissionRecords.length }} 条记录</p>
      </div>
    </div>
    <div v-if="store.mySubmissionRecords.length === 0" class="empty-state">暂无考试记录</div>
    <div v-else class="table-wrap mobile-card-table">
      <table>
        <thead><tr><th>考试</th><th>状态</th><th>成绩</th><th>排名</th><th>耗时</th></tr></thead>
        <tbody>
          <tr v-for="s in store.mySubmissionRecords" :key="s.id">
            <td data-label="考试"><strong>{{ s.examName || '-' }}</strong></td>
            <td data-label="状态"><span class="tag">{{ s.status }}</span></td>
            <td data-label="成绩"><strong style="color:var(--primary)">{{ s.finalScore ?? s.autoScore ?? 0 }}</strong> / {{ s.totalScore ?? '-' }}</td>
            <td data-label="排名">{{ s.rank ? `${s.rank} / ${s.finishedCount}` : '-' }}</td>
            <td data-label="耗时">{{ s.usedTimeText || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const store = useAppStore()
</script>
