<template>
  <article class="panel manage-panel">
    <div class="section-title">
      <div>
        <h3>教师管理</h3>
        <p class="section-subtitle">共 {{ filteredTeachers.length }} 名教师{{ searchQuery ? '（已筛选）' : '' }}</p>
      </div>
      <div class="section-actions">
        <div class="search-box">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchQuery" type="text" placeholder="搜索账号、姓名..." class="search-input" />
          <button v-if="searchQuery" class="search-clear" type="button" @click="searchQuery = ''">&times;</button>
        </div>
        <select v-model="deptFilter" class="filter-select">
          <option value="">全部学院</option>
          <option v-for="dept in store.bootstrap?.departments || []" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
        </select>
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
          <tr v-if="paginatedTeachers.length === 0">
            <td colspan="4" class="empty-cell">
              <div class="empty-state-inline">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="var(--muted-light)" stroke-width="1.5" stroke-linecap="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                <p>{{ searchQuery || deptFilter ? '没有匹配的教师' : '暂无教师数据' }}</p>
                <button v-if="!searchQuery && !deptFilter" class="primary-btn" type="button" @click="store.openEditor('teacher', null)">添加第一个教师</button>
              </div>
            </td>
          </tr>
          <tr v-for="user in paginatedTeachers" :key="user.id">
            <td data-label="账号"><code class="user-code">{{ user.username }}</code></td>
            <td data-label="姓名"><strong>{{ user.name }}</strong></td>
            <td data-label="学院">
              <span class="tag">{{ store.departmentName(user.departmentId) }}</span>
            </td>
            <td data-label="操作">
              <div class="action-row">
                <button class="ghost-btn" type="button" @click="store.openEditor('teacher', user)">编辑</button>
                <button class="ghost-btn reset-pwd-btn" type="button" @click="handleResetPassword(user)">重置密码</button>
                <button class="danger-btn" type="button" @click="store.removeEntity('users', user.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="pagination-bar">
      <button class="ghost-btn" type="button" :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
      <span class="pagination-info">{{ currentPage }} / {{ totalPages }}</span>
      <button class="ghost-btn" type="button" :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { resetPassword } from '@/api/client'
import type { User } from '@/types'

const store = useAppStore()

const searchQuery = ref('')
const deptFilter = ref('')
const currentPage = ref(1)
const pageSize = 20

const filteredTeachers = computed(() => {
  let list = store.teachers
  if (deptFilter.value) {
    list = list.filter(u => u.departmentId === deptFilter.value)
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter(u =>
      u.username.toLowerCase().includes(q) ||
      u.name.toLowerCase().includes(q)
    )
  }
  return list
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredTeachers.value.length / pageSize)))

const paginatedTeachers = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredTeachers.value.slice(start, start + pageSize)
})

watch([searchQuery, deptFilter], () => {
  currentPage.value = 1
})

async function handleResetPassword(user: User) {
  if (!confirm(`确定要重置 ${user.name} 的密码为 123456 吗？`)) return
  try {
    await resetPassword(user.id, '123456')
    store.showToast(`已重置 ${user.name} 的密码`, 'success')
  } catch (err: any) {
    store.showToast(err?.message || '重置密码失败', 'error')
  }
}
</script>

<style scoped>
.manage-panel {
  padding: 20px;
}

.section-actions {
  flex-wrap: wrap;
  gap: 8px;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 200px;
}

.search-box svg {
  position: absolute;
  left: 10px;
  color: var(--muted);
  pointer-events: none;
}

.search-input {
  padding-left: 34px !important;
  padding-right: 28px !important;
  background: var(--panel-soft) !important;
  border: 1.5px solid var(--line-soft) !important;
  border-radius: var(--radius) !important;
  font-size: 13px;
  height: 38px;
}

.search-input:focus {
  background: #fff !important;
  border-color: var(--primary) !important;
  box-shadow: var(--shadow-glow) !important;
}

.search-clear {
  position: absolute;
  right: 6px;
  background: none;
  border: none;
  color: var(--muted);
  font-size: 18px;
  padding: 2px 6px;
  cursor: pointer;
  line-height: 1;
}

.search-clear:hover {
  color: var(--ink);
}

.filter-select {
  width: auto !important;
  min-width: 120px;
  max-width: 180px;
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

.filter-select:focus {
  border-color: var(--primary);
  box-shadow: var(--shadow-glow);
}

.user-code {
  font-family: "Cascadia Code", "Fira Code", "Consolas", monospace;
  font-size: 12px;
  background: var(--panel-soft);
  padding: 2px 8px;
  border-radius: 4px;
  color: var(--ink-secondary);
  border: 1px solid var(--line-soft);
}

.reset-pwd-btn {
  color: #d4a844;
  border-color: rgba(212, 168, 68, 0.2);
  background: var(--warn-soft);
}

.reset-pwd-btn:hover {
  background: #fdf5e0;
  border-color: rgba(212, 168, 68, 0.4);
}

.empty-cell {
  text-align: center !important;
  padding: 40px 20px !important;
}

.empty-state-inline {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--muted);
}

.empty-state-inline p {
  margin: 0;
  font-size: 14px;
}

@media (max-width: 767px) {
  .search-box {
    min-width: 100%;
  }
  .search-input {
    width: 100% !important;
  }
  .filter-select {
    width: 100% !important;
    max-width: none;
  }
}
</style>
