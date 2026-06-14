<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>学生管理</h3>
        <p class="section-subtitle">共 {{ store.students.length }} 名学生</p>
      </div>
      <div class="section-actions">
        <button class="accent-btn" type="button" @click="store.showBatchImport('student')">批量导入</button>
        <button class="primary-btn" type="button" @click="store.openEditor('student', null)">新增学生</button>
      </div>
    </div>
    <div class="table-wrap mobile-card-table">
      <table>
        <thead>
          <tr>
            <th>学号</th>
            <th>姓名</th>
            <th>班级</th>
            <th>专业</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.students.length === 0">
            <td colspan="5" style="text-align:center;color:var(--muted);padding:20px;">暂无学生数据</td>
          </tr>
          <tr v-for="user in store.students" :key="user.id">
            <td data-label="学号">{{ user.username }}</td>
            <td data-label="姓名"><strong>{{ user.name }}</strong></td>
            <td data-label="班级">{{ store.className(user.classId) }}</td>
            <td data-label="专业">{{ user.major || '-' }}</td>
            <td data-label="操作">
              <div class="action-row">
                <button class="ghost-btn" type="button" @click="store.openEditor('student', user)">编辑</button>
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
