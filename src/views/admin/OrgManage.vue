<template>
  <div class="org-manage">
    <!-- 学院管理 -->
    <article class="panel org-panel">
      <div class="section-title">
        <div>
          <h3>学院管理</h3>
          <p class="section-subtitle">{{ departments.length }} 个学院</p>
        </div>
        <button class="primary-btn" type="button" @click="store.openEditor('department', null)">新增学院</button>
      </div>
      <div class="org-list">
        <div v-if="departments.length === 0" class="empty-state" style="padding:24px;">暂无学院数据</div>
        <div v-for="dept in departments" :key="dept.id" class="org-item">
          <div class="org-item-main">
            <div class="org-item-icon dept-icon">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/></svg>
            </div>
            <div class="org-item-info">
              <strong>{{ dept.name }}</strong>
              <span class="org-item-meta">{{ getDeptClassCount(dept.id) }} 个班级 · {{ getDeptStudentCount(dept.id) }} 名学生</span>
            </div>
          </div>
          <div class="org-item-actions">
            <button class="ghost-btn" type="button" @click="store.openEditor('department', dept)">编辑</button>
            <button class="danger-btn" type="button" @click="store.removeEntity('departments', dept.id)">删除</button>
          </div>
        </div>
      </div>
    </article>

    <!-- 班级管理 -->
    <article class="panel org-panel">
      <div class="section-title">
        <div>
          <h3>班级管理</h3>
          <p class="section-subtitle">{{ classes.length }} 个班级</p>
        </div>
        <div class="section-actions">
          <select v-model="classDeptFilter" class="filter-select">
            <option value="">全部学院</option>
            <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
          <button class="primary-btn" type="button" @click="store.openEditor('class', null)">新增班级</button>
        </div>
      </div>
      <div class="org-list">
        <div v-if="filteredClasses.length === 0" class="empty-state" style="padding:24px;">暂无班级数据</div>
        <div v-for="cls in filteredClasses" :key="cls.id" class="org-item">
          <div class="org-item-main">
            <div class="org-item-icon class-icon">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/></svg>
            </div>
            <div class="org-item-info">
              <strong>{{ cls.name }}</strong>
              <span class="org-item-meta">{{ cls.major || '-' }} · {{ store.departmentName(cls.departmentId) }} · {{ getClassStudentCount(cls.id) }} 名学生</span>
            </div>
          </div>
          <div class="org-item-actions">
            <button class="ghost-btn" type="button" @click="store.openEditor('class', cls)">编辑</button>
            <button class="danger-btn" type="button" @click="store.removeEntity('classes', cls.id)">删除</button>
          </div>
        </div>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const classDeptFilter = ref('')

const departments = computed(() => store.bootstrap?.departments || [])
const classes = computed(() => store.bootstrap?.classes || [])

const filteredClasses = computed(() => {
  if (!classDeptFilter.value) return classes.value
  return classes.value.filter(c => c.departmentId === classDeptFilter.value)
})

function getDeptClassCount(deptId: string): number {
  return classes.value.filter(c => c.departmentId === deptId).length
}

function getDeptStudentCount(deptId: string): number {
  const classIds = classes.value.filter(c => c.departmentId === deptId).map(c => c.id)
  return store.students.filter(s => classIds.includes(s.classId || '')).length
}

function getClassStudentCount(classId: string): number {
  return store.students.filter(s => s.classId === classId).length
}
</script>

<style scoped>
.org-manage {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.org-panel {
  padding: 20px;
}

.org-panel .section-title {
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line-soft);
}

.org-panel .section-actions {
  flex-wrap: wrap;
  gap: 8px;
}

.filter-select {
  width: auto !important;
  min-width: 120px;
  max-width: 160px;
  height: 38px;
  font-size: 13px;
  padding: 0 28px 0 10px;
  background: var(--panel-soft);
  border: 1.5px solid var(--line-soft);
  border-radius: var(--radius);
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236b8f84' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
}

/* 组织列表 */
.org-list {
  display: grid;
  gap: 8px;
}

.org-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  background: var(--panel-soft);
  transition: border-color var(--duration) var(--ease), background var(--duration) var(--ease);
}

.org-item:hover {
  border-color: var(--line);
  background: rgba(255, 255, 255, 0.6);
}

.org-item-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.org-item-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.dept-icon {
  background: #fce8e8;
  color: #cf5c5c;
}

.class-icon {
  background: #e0ecf2;
  color: #2563eb;
}

.org-item-info {
  min-width: 0;
}

.org-item-info strong {
  display: block;
  font-size: 14px;
  color: var(--ink);
}

.org-item-meta {
  display: block;
  font-size: 12px;
  color: var(--muted);
  margin-top: 2px;
}

.org-item-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

/* 响应式 */
@media (max-width: 1023px) {
  .org-manage {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .org-item {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .org-item-actions {
    justify-content: flex-end;
  }
  .filter-select {
    width: 100% !important;
    max-width: none;
  }
}
</style>
