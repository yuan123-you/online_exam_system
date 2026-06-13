<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>阅卷中心</h3>
        <p class="section-subtitle">待阅卷 {{ store.pendingGradeCount }} 份</p>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>考试</th><th>学生</th><th>状态</th><th>成绩</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="store.mySubmissions.length === 0">
            <td colspan="5" style="text-align:center;color:var(--muted);padding:32px;">暂无待阅卷答卷</td>
          </tr>
          <tr v-for="s in store.mySubmissions" :key="s.id">
            <td>{{ s.examName || '-' }}</td>
            <td><strong>{{ s.studentName }}</strong></td>
            <td><span class="tag">{{ s.status }}</span></td>
            <td><strong style="color:var(--primary)">{{ s.finalScore ?? s.autoScore ?? 0 }}</strong> / {{ s.totalScore ?? '-' }}</td>
            <td>
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
