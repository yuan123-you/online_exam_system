<template>
  <article class="panel manage-panel">
    <div class="section-title">
      <div>
        <h3>学生管理</h3>
        <p class="section-subtitle">共 {{ store.totalUsers }} 名学生{{ searchQuery ? '（已筛选）' : '' }}</p>
      </div>
      <div class="section-actions">
        <div class="search-box">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchQuery" type="text" placeholder="搜索学号、姓名、专业..." class="search-input" />
          <button v-if="searchQuery" class="search-clear" type="button" @click="searchQuery = ''">&times;</button>
        </div>
        <select v-model="classFilter" class="filter-select">
          <option value="">全部班级</option>
          <option v-for="cls in store.bootstrap?.classes || []" :key="cls.id" :value="cls.id">{{ cls.name }}</option>
        </select>
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
          <tr v-if="store.usersLoading">
            <td colspan="5" class="empty-cell">
              <span class="loading-spinner"></span> 加载中...
            </td>
          </tr>
          <tr v-if="!store.usersLoading && store.paginatedUsers.length === 0">
            <td colspan="5" class="empty-cell">
              <div class="empty-state-inline">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="var(--muted-light)" stroke-width="1.5" stroke-linecap="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                <p>{{ searchQuery || classFilter ? '没有匹配的学生' : '暂无学生数据' }}</p>
                <button v-if="!searchQuery && !classFilter" class="primary-btn" type="button" @click="store.openEditor('student', null)">添加第一个学生</button>
              </div>
            </td>
          </tr>
          <tr v-for="user in (store.paginatedUsers as any)" :key="(user as any).id">
            <td data-label="学号"><code class="user-code">{{ (user as any).username }}</code></td>
            <td data-label="姓名"><strong>{{ (user as any).name }}</strong></td>
            <td data-label="班级">
              <span class="tag">{{ store.className((user as any).classId) }}</span>
            </td>
            <td data-label="专业">{{ (user as any).major || '-' }}</td>
            <td data-label="操作">
              <div class="action-row">
                <button class="ghost-btn" type="button" @click="store.openEditor('student', user)">编辑</button>
                <button class="ghost-btn reset-pwd-btn" type="button" @click="handleResetPassword(user as any)">重置密码</button>
                <button class="danger-btn" type="button" @click="store.removeEntity('users', (user as any).id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <PaginationBar
      :total="store.totalUsers"
      :current-page="store.usersCurrentPage"
      :page-size="store.usersPageSize"
      :page-size-options="[10, 20, 50]"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    />
  </article>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { resetPassword } from '@/api/client'
import type { User } from '@/types'
import PaginationBar from '@/components/common/PaginationBar.vue'

const store = useAppStore()

const searchQuery = ref('')
const classFilter = ref('')

// 搜索或筛选变化时重新查询（带防抖）
let searchTimer: ReturnType<typeof setTimeout> | null = null
watch([searchQuery, classFilter], () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.loadUsersPage(1, searchQuery.value || undefined, 'student', classFilter.value || undefined)
  }, 300)
})

function handlePageChange(page: number) {
  store.loadUsersPage(page, searchQuery.value || undefined, 'student', classFilter.value || undefined)
}

function handlePageSizeChange(size: number) {
  store.usersPageSize = size
  store.loadUsersPage(1, searchQuery.value || undefined, 'student', classFilter.value || undefined)
}

async function handleResetPassword(user: User) {
  const ok = await store.confirmDialog(`确定要重置 ${user.name} 的密码为 123456 吗？`, { title: '重置密码确认', confirmText: '重置', danger: true })
  if (!ok) return
  try {
    await resetPassword(user.id, '123456')
    store.showToast(`已重置 ${user.name} 的密码`, 'success')
  } catch (err: any) {
    store.showToast(err?.message || '重置密码失败', 'error')
  }
}

onMounted(() => {
  store.loadUsersPage(1, undefined, 'student')
})
</script>

<style scoped>
.manage-panel {
  padding: 20px;
}

.section-actions {
  flex-wrap: wrap;
  gap: 8px;
}

/* 搜索框 */
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

/* 筛选下拉 */
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

/* 学号代码样式 */
.user-code {
  font-family: "Cascadia Code", "Fira Code", "Consolas", monospace;
  font-size: 12px;
  background: var(--panel-soft);
  padding: 2px 8px;
  border-radius: 4px;
  color: var(--ink-secondary);
  border: 1px solid var(--line-soft);
}

/* 重置密码按钮 */
.reset-pwd-btn {
  color: #d4a844;
  border-color: rgba(212, 168, 68, 0.2);
  background: var(--warn-soft);
}

.reset-pwd-btn:hover {
  background: #fdf5e0;
  border-color: rgba(212, 168, 68, 0.4);
}

/* 空状态 */
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

/* 加载动画 */
.loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid var(--border, var(--line-soft));
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  vertical-align: middle;
  margin-right: 6px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 响应式 */
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
