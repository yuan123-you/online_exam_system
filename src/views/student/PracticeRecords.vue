<template>
  <article class="panel">
    <!-- Quota Progress Bar -->
    <QuotaBar
      label="练习记录"
      :used="practiceQuotaUsed"
      :limit="1000"
      warning-text="练习记录即将满额，最早的记录将被自动归档。"
    />

    <div class="section-title">
      <div>
        <h3>练习记录</h3>
        <p class="section-subtitle">查看历史练习情况与正确率</p>
      </div>
      <button class="ghost-btn" type="button" @click="$router.push('/ai-practice')">AI 助手</button>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>科目</th>
            <th>题型</th>
            <th>题目</th>
            <th>结果</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="practiceRecords.length === 0">
            <td colspan="5" style="text-align:center;color:var(--muted);padding:32px;">暂无练习记录</td>
          </tr>
          <tr v-for="r in practiceRecords" :key="r.id">
            <td>{{ r.subject }}</td>
            <td><span class="tag">{{ typeLabel(r.type as any) }}</span></td>
            <td>{{ r.title?.slice(0, 30) }}{{ (r.title?.length || 0) > 30 ? '...' : '' }}</td>
            <td>
              <span :class="r.lastRetryCorrect ? 'text-success' : 'text-danger'">
                {{ r.lastRetryCorrect ? '正确' : '错误' }}
              </span>
            </td>
            <td class="muted">{{ r.lastWrongAt ? formatDate(r.lastWrongAt) : '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel, formatDate } from '@/utils/format'
import QuotaBar from '@/components/common/QuotaBar.vue'

const store = useAppStore()

onMounted(async () => {
  await store.loadQuotaData()
})

const practiceQuotaUsed = computed(() => {
  if (store.quotaData?.practiceRecords) {
    return store.quotaData.practiceRecords.used
  }
  return 0
})

const practiceRecords = computed(() => {
  return (store.bootstrap?.wrongBookEntries || [])
    .filter(e => e.studentId === store.currentUser?.id && (e as any).status === 'practice')
    .map(e => ({ ...e, lastWrongAt: (e as any).lastWrongAt || (e as any).lastRetryAt || '' }))
})
</script>

<style scoped>
.text-success {
  color: #22c55e;
  font-weight: 600;
}

.text-danger {
  color: #ef4444;
  font-weight: 600;
}
</style>
