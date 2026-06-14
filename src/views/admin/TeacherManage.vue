<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>教师管理</h3>
        <p class="section-subtitle">共 {{ store.teachers.length }} 名教师</p>
      </div>
      <div class="section-actions">
        <button class="accent-btn" type="button" @click="store.showBatchImport('teacher')">批量导入</button>
        <button class="primary-btn" type="button" @click="store.openEditor('teacher', null)">新增教师</button>
      </div>
    </div>
    <div class="table-wrap mobile-card-table">
      <table>
        <thead>
          <tr>
            <th>账号</th>
            <th>姓名</th>
            <th>学院</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.teachers.length === 0">
            <td colspan="4" style="text-align:center;color:var(--muted);padding:20px;">暂无教师数据</td>
          </tr>
          <tr v-for="user in store.teachers" :key="user.id">
            <td data-label="账号">{{ user.username }}</td>
            <td data-label="姓名"><strong>{{ user.name }}</strong></td>
            <td data-label="学院">{{ store.departmentName(user.departmentId) }}</td>
            <td data-label="操作">
              <div class="action-row">
                <button class="ghost-btn" type="button" @click="store.openEditor('teacher', user)">编辑</button>
                <button class="danger-btn" type="button" @click="store.removeEntity('users', user.id)">删除</button>
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
