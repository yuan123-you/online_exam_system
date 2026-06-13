<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>试卷管理</h3>
        <p class="section-subtitle">共 {{ store.myPapers.length }} 套试卷</p>
      </div>
      <div class="section-actions">
        <button class="accent-btn" type="button" @click="store.autoGenVisible = true">自动组卷</button>
        <button class="primary-btn" type="button" @click="store.openPaperForm(null)">新增试卷</button>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>试卷名称</th><th>题量</th><th>总分</th><th>及格线</th><th>时长</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="store.myPapers.length === 0">
            <td colspan="6" style="text-align:center;color:var(--muted);padding:32px;">暂无试卷，点击上方按钮新增</td>
          </tr>
          <tr v-for="p in store.myPapers" :key="p.id">
            <td><strong>{{ p.name }}</strong></td>
            <td>{{ p.questionIds.length }}</td>
            <td><strong style="color:var(--primary)">{{ p.totalScore }}</strong></td>
            <td>{{ p.passScore }}</td>
            <td>{{ p.durationMinutes }} 分钟</td>
            <td>
              <div class="action-row">
                <button class="accent-btn" type="button" @click="store.previewPaper(p)">预览</button>
                <button class="ghost-btn" type="button" @click="store.openPaperForm(p)">编辑</button>
                <button class="danger-btn" type="button" @click="store.removeEntity('papers', p.id)">删除</button>
              </div>
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
