<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>系统日志</h3>
        <p class="section-subtitle">最近 {{ store.bootstrap?.logs.length || 0 }} 条操作记录</p>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>详情</th></tr></thead>
        <tbody>
          <tr v-if="store.sortedLogs.length === 0">
            <td colspan="4" style="text-align:center;color:var(--muted);padding:32px;">暂无日志记录</td>
          </tr>
          <tr v-for="log in store.sortedLogs" :key="log.id">
            <td>{{ formatDate(log.time) }}</td>
            <td>{{ log.actorName || log.actorId }}</td>
            <td><span class="tag">{{ log.action }}</span></td>
            <td class="cell-sub">{{ log.detail }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import { formatDate } from '@/utils/format'

const store = useAppStore()
</script>
