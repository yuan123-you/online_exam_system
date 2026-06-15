<template>
  <nav
    class="pagination-bar"
    v-if="show"
    role="navigation"
    :aria-label="ariaLabel"
  >
    <!-- 左侧：总记录数 + 每页条数选择器 -->
    <div class="pagination-left">
      <span class="pagination-info" aria-live="polite">
        共 <strong>{{ total }}</strong> 条
        <template v-if="totalPages > 1">，第 {{ currentPage }} / {{ totalPages }} 页</template>
      </span>
      <div class="page-size-selector" v-if="showPageSize">
        <label :for="pageSizeId" class="sr-only">每页显示条数</label>
        <select
          :id="pageSizeId"
          :value="pageSize"
          @change="onPageSizeChange"
          class="page-size-select"
          aria-label="每页显示条数"
        >
          <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条/页</option>
        </select>
      </div>
    </div>

    <!-- 右侧：页码导航 + 跳转输入 -->
    <div class="pagination-right">
      <!-- 页码导航 -->
      <div class="pagination-btns" v-if="totalPages > 1" role="group" aria-label="页码导航">
        <button
          class="pagination-btn"
          :disabled="currentPage <= 1"
          @click="goPage(currentPage - 1)"
          :aria-label="'上一页，当前第' + currentPage + '页'"
          aria-keyshortcuts="ArrowLeft"
          title="上一页"
        >‹ 上一页</button>

        <button
          v-for="p in visiblePages"
          :key="'page-' + p"
          :class="['pagination-btn', { active: p === currentPage, ellipsis: p === -1 }]"
          :disabled="p === -1"
          @click="p !== -1 && goPage(p)"
          :aria-label="p === -1 ? '省略的页码' : '第' + p + '页'"
          :aria-current="p === currentPage ? 'page' : undefined"
        >{{ p === -1 ? '...' : p }}</button>

        <button
          class="pagination-btn"
          :disabled="currentPage >= totalPages"
          @click="goPage(currentPage + 1)"
          :aria-label="'下一页，当前第' + currentPage + '页'"
          aria-keyshortcuts="ArrowRight"
          title="下一页"
        >下一页 ›</button>
      </div>

      <!-- 跳转输入 -->
      <div class="page-jump" v-if="totalPages > 1 && showPageJump">
        <label :for="jumpInputId" class="sr-only">跳转到页码</label>
        <input
          :id="jumpInputId"
          ref="jumpInputRef"
          v-model="jumpInput"
          type="text"
          class="page-jump-input"
          :class="{ 'input-error': jumpError }"
          placeholder="页码"
          :aria-label="'跳转页码，共' + totalPages + '页'"
          :aria-invalid="!!jumpError"
          :aria-errormessage="jumpError ? jumpErrorId : undefined"
          @keyup.enter="handleJump"
          inputmode="numeric"
          pattern="[0-9]*"
        />
        <button
          class="page-jump-btn"
          type="button"
          @click="handleJump"
          :aria-label="'跳转到第' + jumpInput + '页'"
        >前往</button>
        <span
          v-if="jumpError"
          :id="jumpErrorId"
          class="jump-error"
          role="alert"
          aria-live="assertive"
        >{{ jumpError }}</span>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  total: number
  currentPage: number
  pageSize: number
  pageSizeOptions?: number[]
  showPageSize?: boolean
  showPageJump?: boolean
  maxVisiblePages?: number
  ariaLabel?: string
}>(), {
  pageSizeOptions: () => [10, 20, 50, 100],
  showPageSize: true,
  showPageJump: true,
  maxVisiblePages: 5,
  ariaLabel: '分页导航',
})

const emit = defineEmits<{
  (e: 'page-change', page: number): void
  (e: 'page-size-change', size: number): void
}>()

// 唯一 ID 用于 ARIA 关联
const uid = Math.random().toString(36).slice(2, 8)
const pageSizeId = `pg-size-${uid}`
const jumpInputId = `pg-jump-${uid}`
const jumpErrorId = `pg-err-${uid}`

const jumpInput = ref('')
const jumpError = ref('')
const jumpInputRef = ref<HTMLInputElement | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const show = computed(() => props.total > 0 || props.currentPage > 1)

const visiblePages = computed(() => {
  const total = totalPages.value
  const current = props.currentPage
  const max = props.maxVisiblePages
  const pages: number[] = []

  if (total <= max + 2) {
    for (let i = 1; i <= total; i++) pages.push(i)
  } else {
    pages.push(1)

    let start = Math.max(2, current - Math.floor(max / 2))
    let end = Math.min(total - 1, start + max - 1)
    if (end - start < max - 1) {
      start = Math.max(2, end - max + 1)
    }

    if (start > 2) pages.push(-1) // 省略号

    for (let i = start; i <= end; i++) pages.push(i)

    if (end < total - 1) pages.push(-1) // 省略号

    pages.push(total)
  }

  return pages
})

// 当前页变化时清空跳转输入和错误
watch(() => props.currentPage, () => {
  jumpInput.value = ''
  jumpError.value = ''
})

function goPage(page: number) {
  if (page < 1 || page > totalPages.value || page === props.currentPage) return
  emit('page-change', page)
}

function onPageSizeChange(event: Event) {
  const target = event.target as HTMLSelectElement
  const size = Number(target.value)
  if (size !== props.pageSize) {
    emit('page-size-change', size)
  }
}

function validateJumpInput(value: string): { valid: boolean; error: string; page: number } {
  if (!value || value.trim() === '') {
    return { valid: false, error: '请输入页码', page: 0 }
  }
  if (!/^\d+$/.test(value.trim())) {
    return { valid: false, error: '请输入有效的数字', page: 0 }
  }
  const page = parseInt(value.trim(), 10)
  if (page < 1) {
    return { valid: false, error: '页码不能小于1', page: 0 }
  }
  if (page > totalPages.value) {
    return { valid: false, error: `页码不能超过总页数(${totalPages.value})`, page: 0 }
  }
  return { valid: true, error: '', page }
}

function handleJump() {
  const result = validateJumpInput(jumpInput.value)
  if (!result.valid) {
    jumpError.value = result.error
    return
  }
  jumpError.value = ''
  jumpInput.value = ''
  goPage(result.page)
}
</script>

<style scoped>
/* 屏幕阅读器专用 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 0;
}

.pagination-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination-info {
  font-size: 13px;
  color: var(--muted);
  white-space: nowrap;
}

.pagination-info strong {
  color: var(--text, var(--ink));
}

/* 每页条数选择器 */
.page-size-selector {
  flex-shrink: 0;
}

.page-size-select {
  height: 32px;
  padding: 0 24px 0 8px;
  border: 1px solid var(--border, var(--line-soft));
  border-radius: 6px;
  background: var(--card-bg, var(--panel, #fff));
  color: var(--text, var(--ink));
  font-size: 13px;
  outline: none;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 6px center;
  transition: border-color 0.15s;
}

.page-size-select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(61, 153, 128, 0.12);
}

/* 页码按钮 */
.pagination-btns {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.pagination-btn {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--border, var(--line-soft));
  border-radius: 6px;
  background: var(--card-bg, var(--panel, #fff));
  color: var(--text, var(--ink));
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  white-space: nowrap;
}

.pagination-btn:hover:not(:disabled):not(.active):not(.ellipsis) {
  border-color: var(--primary);
  color: var(--primary);
}

.pagination-btn:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.pagination-btn.active {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
  font-weight: 600;
}

.pagination-btn.ellipsis {
  border: none;
  background: transparent;
  cursor: default;
  color: var(--muted);
  min-width: 24px;
  pointer-events: none;
}

.pagination-btn:disabled:not(.ellipsis) {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 跳转输入 */
.page-jump {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
}

.page-jump-input {
  width: 56px;
  height: 32px;
  padding: 0 6px;
  border: 1px solid var(--border, var(--line-soft));
  border-radius: 6px;
  background: var(--card-bg, var(--panel, #fff));
  color: var(--text, var(--ink));
  font-size: 13px;
  text-align: center;
  outline: none;
  transition: border-color 0.15s;
  -moz-appearance: textfield;
}

.page-jump-input::-webkit-inner-spin-button,
.page-jump-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.page-jump-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(61, 153, 128, 0.12);
}

.page-jump-input.input-error {
  border-color: var(--danger, #cf5c5c);
  box-shadow: 0 0 0 2px rgba(207, 92, 92, 0.12);
}

.page-jump-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--border, var(--line-soft));
  border-radius: 6px;
  background: var(--card-bg, var(--panel, #fff));
  color: var(--text, var(--ink));
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.page-jump-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.page-jump-btn:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.jump-error {
  font-size: 12px;
  color: var(--danger, #cf5c5c);
  white-space: nowrap;
  animation: shake 0.3s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  75% { transform: translateX(3px); }
}

/* 响应式：平板 */
@media (max-width: 1023px) and (min-width: 768px) {
  .pagination-bar {
    gap: 8px;
  }
}

/* 响应式：移动端 */
@media (max-width: 767px) {
  .pagination-bar {
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 12px 0;
  }

  .pagination-left,
  .pagination-right {
    width: 100%;
    justify-content: center;
  }

  .pagination-btns {
    justify-content: center;
  }

  .pagination-btn {
    min-width: 30px;
    height: 30px;
    font-size: 12px;
    padding: 0 6px;
  }

  .page-size-select {
    font-size: 12px;
    height: 30px;
  }

  .page-jump {
    margin-top: 4px;
  }

  .page-jump-input {
    width: 48px;
    height: 30px;
    font-size: 12px;
  }

  .page-jump-btn {
    height: 30px;
    font-size: 12px;
    padding: 0 8px;
  }

  .jump-error {
    font-size: 11px;
  }
}
</style>
