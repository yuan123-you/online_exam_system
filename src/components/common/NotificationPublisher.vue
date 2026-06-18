<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="publish-card">
      <button class="close-btn" type="button" @click="$emit('close')">&times;</button>
      <div class="publish-header">
        <h3>发布通知</h3>
        <p class="muted">{{ roleLabel }}向指定对象发送站内通知</p>
      </div>

      <div class="publish-body">
        <!-- 通知类型 -->
        <div class="form-group">
          <label>通知类型</label>
          <div class="type-chips">
            <button
              v-for="t in typeOptions"
              :key="t.value"
              :class="['chip', { active: form.type === t.value }]"
              type="button"
              @click="form.type = t.value"
            >{{ t.label }}</button>
          </div>
        </div>

        <!-- 标题 -->
        <div class="form-group">
          <label>标题 <span class="required">*</span></label>
          <input v-model="form.title" type="text" placeholder="输入通知标题" maxlength="100" />
        </div>

        <!-- 内容 -->
        <div class="form-group">
          <label>内容 <span class="required">*</span></label>
          <textarea v-model="form.content" placeholder="输入通知内容" rows="4" maxlength="1000"></textarea>
          <span class="char-count">{{ form.content.length }}/1000</span>
        </div>

        <!-- 目标角色（管理员） -->
        <div v-if="isAdmin" class="form-group">
          <label>目标角色</label>
          <div class="type-chips">
            <button
              v-for="r in targetRoleOptions"
              :key="r.value"
              :class="['chip', { active: form.targetRole === r.value }]"
              type="button"
              @click="handleRoleChange(r.value)"
            >{{ r.label }}</button>
          </div>
        </div>

        <!-- 教师端：选择班级 -->
        <div v-if="isTeacher" class="form-group">
          <label>目标班级 <span class="required">*</span></label>
          <div class="select-toolbar">
            <button class="ghost-btn-sm" type="button" @click="selectAllClasses">全选</button>
            <button class="ghost-btn-sm" type="button" @click="deselectAllClasses">清空</button>
          </div>
          <div class="check-grid">
            <label
              v-for="cls in availableClasses"
              :key="cls.id"
              :class="['check-item', { checked: form.selectedClassIds.includes(cls.id) }]"
            >
              <input
                type="checkbox"
                :value="cls.id"
                :checked="form.selectedClassIds.includes(cls.id)"
                @change="toggleClass(cls.id)"
              />
              <span class="check-label">{{ cls.name }}</span>
              <span class="check-sub">{{ getDeptName(cls.departmentId) }}</span>
            </label>
          </div>
          <p v-if="availableClasses.length === 0" class="empty-hint">暂无可选班级</p>
          <p v-else class="select-count">已选 {{ form.selectedClassIds.length }} / {{ availableClasses.length }} 个班级</p>
        </div>

        <!-- 管理员端：选择学院/教师 -->
        <div v-if="isAdmin && form.targetRole === 'teacher'" class="form-group">
          <label>指定学院（可选，不选则发给所有教师）</label>
          <div class="select-toolbar">
            <button class="ghost-btn-sm" type="button" @click="selectAllDepts">全选</button>
            <button class="ghost-btn-sm" type="button" @click="deselectAllDepts">清空</button>
          </div>
          <div class="check-grid">
            <label
              v-for="dept in departments"
              :key="dept.id"
              :class="['check-item', { checked: form.selectedDeptIds.includes(dept.id) }]"
            >
              <input
                type="checkbox"
                :value="dept.id"
                :checked="form.selectedDeptIds.includes(dept.id)"
                @change="toggleDept(dept.id)"
              />
              <span class="check-label">{{ dept.name }}</span>
              <span class="check-sub">{{ getDeptTeacherCount(dept.id) }} 名教师</span>
            </label>
          </div>
          <p v-if="departments.length === 0" class="empty-hint">暂无学院数据</p>
          <p v-else class="select-count">已选 {{ form.selectedDeptIds.length }} / {{ departments.length }} 个学院</p>
        </div>

        <!-- 管理员端：选择学院（发给学生时） -->
        <div v-if="isAdmin && form.targetRole === 'student'" class="form-group">
          <label>指定学院筛选班级（可选，不选则发给所有班级学生）</label>
          <div class="select-toolbar">
            <button class="ghost-btn-sm" type="button" @click="selectAllDepts">全选</button>
            <button class="ghost-btn-sm" type="button" @click="deselectAllDepts">清空</button>
          </div>
          <div class="check-grid">
            <label
              v-for="dept in departments"
              :key="dept.id"
              :class="['check-item', { checked: form.selectedDeptIds.includes(dept.id) }]"
            >
              <input
                type="checkbox"
                :value="dept.id"
                :checked="form.selectedDeptIds.includes(dept.id)"
                @change="toggleDept(dept.id)"
              />
              <span class="check-label">{{ dept.name }}</span>
              <span class="check-sub">{{ getDeptStudentCount(dept.id) }} 名学生</span>
            </label>
          </div>
          <p v-if="departments.length === 0" class="empty-hint">暂无学院数据</p>
        </div>
      </div>

      <div class="publish-footer">
        <button class="btn-cancel" type="button" @click="$emit('close')">取消</button>
        <button
          class="btn-submit"
          type="button"
          :disabled="!canSubmit || submitting"
          @click="handleSubmit"
        >
          {{ submitting ? '发送中...' : '发送通知' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { useAppStore } from '@/stores/app'

const emit = defineEmits<{ close: []; submitted: [] }>()
const store = useAppStore()

const isAdmin = computed(() => store.isAdmin)
const isTeacher = computed(() => store.isTeacher)
const roleLabel = computed(() => isAdmin.value ? '管理员' : '教师')

const typeOptions = [
  { value: 'general', label: '普通' },
  { value: 'exam', label: '考试' },
  { value: 'grade', label: '成绩' },
  { value: 'system', label: '系统' },
]

const targetRoleOptions = [
  { value: 'student', label: '学生' },
  { value: 'teacher', label: '教师' },
  { value: 'all', label: '所有人' },
]

const form = reactive({
  type: 'general',
  title: '',
  content: '',
  targetRole: 'student',
  selectedClassIds: [] as string[],
  selectedDeptIds: [] as string[],
})

const submitting = ref(false)

// 教师端：所有班级（教师可向任何班级发通知）
const availableClasses = computed(() => store.bootstrap?.classes || [])

// 管理员端：所有学院
const departments = computed(() => store.bootstrap?.departments || [])

function getDeptName(deptId: string): string {
  return store.bootstrap?.departments.find(d => d.id === deptId)?.name || ''
}

function getDeptTeacherCount(deptId: string): number {
  return store.teachers.filter(t => t.departmentId === deptId).length
}

function getDeptStudentCount(deptId: string): number {
  const classIds = new Set(
    store.bootstrap?.classes.filter(c => c.departmentId === deptId).map(c => c.id) || []
  )
  return store.students.filter(s => classIds.has(s.classId || '')).length
}

// 班级选择
function toggleClass(id: string) {
  const idx = form.selectedClassIds.indexOf(id)
  if (idx >= 0) form.selectedClassIds.splice(idx, 1)
  else form.selectedClassIds.push(id)
}

function selectAllClasses() {
  form.selectedClassIds = availableClasses.value.map(c => c.id)
}

function deselectAllClasses() {
  form.selectedClassIds = []
}

// 学院选择
function toggleDept(id: string) {
  const idx = form.selectedDeptIds.indexOf(id)
  if (idx >= 0) form.selectedDeptIds.splice(idx, 1)
  else form.selectedDeptIds.push(id)
}

function selectAllDepts() {
  form.selectedDeptIds = departments.value.map(d => d.id)
}

function deselectAllDepts() {
  form.selectedDeptIds = []
}

function handleRoleChange(role: string) {
  form.targetRole = role
  form.selectedDeptIds = []
}

// 提交验证
const canSubmit = computed(() => {
  if (!form.title.trim() || !form.content.trim()) return false
  if (isTeacher.value && form.selectedClassIds.length === 0) return false
  return true
})

// 提交逻辑
async function handleSubmit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true

  try {
    if (isTeacher.value) {
      // 教师：为每个选中的班级发送一条通知
      const classIds = form.selectedClassIds
      for (let i = 0; i < classIds.length; i++) {
        await store.handleCreateNotification({
          title: form.title.trim(),
          content: form.content.trim(),
          type: form.type,
          targetRole: 'student',
          targetClassId: classIds[i],
        }, i < classIds.length - 1) // 仅最后一条显示 toast
      }
    } else if (isAdmin.value) {
      if (form.targetRole === 'all') {
        await store.handleCreateNotification({
          title: form.title.trim(),
          content: form.content.trim(),
          type: form.type,
          targetRole: 'all',
        })
      } else if (form.targetRole === 'teacher') {
        if (form.selectedDeptIds.length === 0) {
          await store.handleCreateNotification({
            title: form.title.trim(),
            content: form.content.trim(),
            type: form.type,
            targetRole: 'teacher',
          })
        } else {
          const deptIds = form.selectedDeptIds
          for (let i = 0; i < deptIds.length; i++) {
            await store.handleCreateNotification({
              title: form.title.trim(),
              content: form.content.trim(),
              type: form.type,
              targetRole: 'teacher',
              targetDepartmentId: deptIds[i],
            }, i < deptIds.length - 1)
          }
        }
      } else {
        if (form.selectedDeptIds.length === 0) {
          await store.handleCreateNotification({
            title: form.title.trim(),
            content: form.content.trim(),
            type: form.type,
            targetRole: 'student',
          })
        } else {
          const targetClasses = store.bootstrap?.classes.filter(
            c => form.selectedDeptIds.includes(c.departmentId)
          ) || []
          for (let i = 0; i < targetClasses.length; i++) {
            await store.handleCreateNotification({
              title: form.title.trim(),
              content: form.content.trim(),
              type: form.type,
              targetRole: 'student',
              targetClassId: targetClasses[i].id,
            }, i < targetClasses.length - 1)
          }
        }
      }
    }
    // 刷新通知数据
    await store.loadNotificationData()
    emit('submitted')
    emit('close')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 16px;
}

.publish-card {
  position: relative;
  background: var(--panel);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--line);
  width: 100%;
  max-width: 560px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  background: none;
  border: none;
  font-size: 1.3rem;
  cursor: pointer;
  color: var(--muted);
  z-index: 1;
  line-height: 1;
  padding: 4px;
}
.close-btn:hover { color: var(--ink); }

.publish-header {
  padding: 20px 20px 0;
}
.publish-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--ink);
}
.publish-header .muted {
  font-size: 13px;
  margin-top: 4px;
}

.publish-body {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-group label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}
.required { color: #ef4444; }

.form-group input[type="text"],
.form-group textarea {
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 14px;
  background: var(--panel-soft);
  color: var(--ink);
  outline: none;
  transition: border-color 0.15s;
  font-family: inherit;
}
.form-group input[type="text"]:focus,
.form-group textarea:focus {
  border-color: var(--primary);
}

.char-count {
  font-size: 11px;
  color: var(--muted-light);
  text-align: right;
}

/* 类型选择芯片 */
.type-chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.chip {
  padding: 5px 14px;
  border-radius: 100px;
  border: 1.5px solid var(--line-soft);
  background: var(--panel-soft);
  color: var(--ink);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.chip:hover {
  border-color: var(--primary);
}
.chip.active {
  background: var(--primary-soft);
  border-color: var(--primary);
  color: var(--primary-dark);
  font-weight: 600;
}

/* 选择工具栏 */
.select-toolbar {
  display: flex;
  gap: 8px;
}
.ghost-btn-sm {
  font-size: 12px;
  padding: 2px 10px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
}
.ghost-btn-sm:hover {
  background: var(--primary-soft);
}

/* 复选框网格 */
.check-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 6px;
  max-height: 200px;
  overflow-y: auto;
  padding: 2px;
}
.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius);
  border: 1.5px solid var(--line-soft);
  background: var(--panel-soft);
  cursor: pointer;
  transition: all 0.15s;
  font-size: 13px;
}
.check-item:hover {
  border-color: var(--primary);
}
.check-item.checked {
  background: var(--primary-soft);
  border-color: var(--primary);
}
.check-item input[type="checkbox"] {
  accent-color: var(--primary);
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}
.check-label {
  font-weight: 500;
  color: var(--ink);
}
.check-sub {
  font-size: 11px;
  color: var(--muted);
  margin-left: auto;
  white-space: nowrap;
}

.empty-hint {
  font-size: 13px;
  color: var(--muted);
  text-align: center;
  padding: 12px;
}
.select-count {
  font-size: 12px;
  color: var(--muted);
  margin: 0;
}

/* 底部按钮 */
.publish-footer {
  padding: 14px 20px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.btn-cancel {
  padding: 8px 20px;
  border-radius: var(--radius);
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-size: 14px;
  cursor: pointer;
}
.btn-cancel:hover { background: var(--panel-soft); }

.btn-submit {
  padding: 8px 24px;
  border-radius: var(--radius);
  border: none;
  background: var(--primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-submit:hover:not(:disabled) { opacity: 0.9; }
.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 响应式 */
@media (max-width: 640px) {
  .publish-card {
    max-width: 100%;
    max-height: 100vh;
    border-radius: 0;
  }
  .check-grid {
    grid-template-columns: 1fr;
  }
}
</style>
