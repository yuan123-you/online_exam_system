<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>考试记录</h3>
        <p class="section-subtitle">共 {{ store.mySubmissionRecords.length }} 条记录</p>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>考试</th><th>状态</th><th>成绩</th><th>排名</th><th>耗时</th></tr></thead>
        <tbody>
          <tr v-if="store.mySubmissionRecords.length === 0">
            <td colspan="5" style="text-align:center;color:var(--muted);padding:32px;">暂无考试记录</td>
          </tr>
          <tr v-for="s in store.mySubmissionRecords" :key="s.id">
            <td><strong>{{ s.examName || '-' }}</strong></td>
            <td><span class="tag">{{ s.status }}</span></td>
            <td><strong style="color:var(--primary)">{{ s.finalScore ?? s.autoScore ?? 0 }}</strong> / {{ s.totalScore ?? '-' }}</td>
            <td>{{ s.rank ? `${s.rank} / ${s.finishedCount}` : '-' }}</td>
            <td>{{ s.usedTimeText || '-' }}</td>
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
