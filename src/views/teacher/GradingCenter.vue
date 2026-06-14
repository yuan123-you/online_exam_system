<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>阅卷中心</h3>
        <p class="section-subtitle">待阅卷 {{ store.pendingGradeCount }} 份</p>
      </div>
    </div>
    <div class="table-wrap mobile-card-table">
      <table>
        <thead><tr><th>考试</th><th>学生</th><th>状态</th><th>成绩</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="store.mySubmissions.length === 0">
            <td colspan="5" style="text-align:center;color:var(--muted);padding:20px;">暂无待阅卷答卷</td>
          </tr>
          <tr v-for="s in store.mySubmissions" :key="s.id">
            <td data-label="考试">{{ s.examName || '-' }}</td>
            <td data-label="学生"><strong>{{ s.studentName }}</strong></td>
            <td data-label="状态"><span class="tag">{{ s.status }}</span></td>
            <td data-label="成绩"><strong style="color:var(--primary)">{{ s.finalScore ?? s.autoScore ?? 0 }}</strong> / {{ s.totalScore ?? '-' }}</td>
            <td data-label="操作">
              <button class="primary-btn" type="button" @click="store.reviewSubmission(s)">查看 / 阅卷</button>
            </td>
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
