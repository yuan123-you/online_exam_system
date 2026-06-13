<template>
  <section class="org-column">
    <article class="panel org-panel">
      <div class="section-title">
        <div>
          <h3>学院管理</h3>
          <p class="section-subtitle">{{ store.bootstrap?.departments.length || 0 }} 个学院</p>
        </div>
        <button class="primary-btn" type="button" @click="store.openEditor('department', null)">新增学院</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>名称</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="(store.bootstrap?.departments || []).length === 0">
              <td colspan="2" style="text-align:center;color:var(--muted);padding:32px;">暂无学院数据</td>
            </tr>
            <tr v-for="dept in store.bootstrap?.departments || []" :key="dept.id">
              <td><strong>{{ dept.name }}</strong></td>
              <td>
                <div class="action-row">
                  <button class="ghost-btn" type="button" @click="store.openEditor('department', dept)">编辑</button>
                  <button class="danger-btn" type="button" @click="store.removeEntity('departments', dept.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
    <article class="panel org-panel">
      <div class="section-title">
        <div>
          <h3>班级管理</h3>
          <p class="section-subtitle">{{ store.bootstrap?.classes.length || 0 }} 个班级</p>
        </div>
        <button class="primary-btn" type="button" @click="store.openEditor('class', null)">新增班级</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>名称</th><th>专业</th><th>学院</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="(store.bootstrap?.classes || []).length === 0">
              <td colspan="4" style="text-align:center;color:var(--muted);padding:32px;">暂无班级数据</td>
            </tr>
            <tr v-for="cls in store.bootstrap?.classes || []" :key="cls.id">
              <td><strong>{{ cls.name }}</strong></td>
              <td>{{ cls.major }}</td>
              <td>{{ store.departmentName(cls.departmentId) }}</td>
              <td>
                <div class="action-row">
                  <button class="ghost-btn" type="button" @click="store.openEditor('class', cls)">编辑</button>
                  <button class="danger-btn" type="button" @click="store.removeEntity('classes', cls.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const store = useAppStore()
</script>
