<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>考试管理</h3>
        <p class="section-subtitle">共 {{ store.myExams.length }} 场考试</p>
      </div>
      <button class="primary-btn" type="button" @click="store.openEditor('exam', null)">发布考试</button>
    </div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>考试名称</th><th>试卷</th><th>时间</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="store.myExams.length === 0">
            <td colspan="5" style="text-align:center;color:var(--muted);padding:32px;">暂无考试，点击上方按钮发布</td>
          </tr>
          <tr v-for="e in store.myExams" :key="e.id">
            <td><strong>{{ e.name }}</strong></td>
            <td>{{ e.paperName || '-' }}</td>
            <td>{{ formatDate(e.startTime) }}</td>
            <td><span class="tag">{{ e.statusText || '已发布' }}</span></td>
            <td>
              <div class="action-row">
                <button class="accent-btn" type="button" @click="handleMonitor(e.id)">监控</button>
                <button class="ghost-btn" type="button" @click="store.openEditor('exam', e)">编辑</button>
                <button class="danger-btn" type="button" @click="store.removeEntity('exams', e.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { formatDate } from '@/utils/format'

const store = useAppStore()
const router = useRouter()

function handleMonitor(examId: string) {
  store.loadMonitorData(examId)
  router.push('/monitor')
}
</script>
