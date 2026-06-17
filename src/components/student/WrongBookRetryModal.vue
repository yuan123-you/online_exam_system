<template>
  <BaseModal @close="$emit('close')">
    <template #header>
      <div>
        <h3>错题重做</h3>
        <p class="muted" style="font-size:13px;margin-top:4px;">
          {{ entry.subject }} / {{ entry.knowledgePoint }} / <span class="tag">{{ typeLabel(entry.type) }}</span>
        </p>
      </div>
    </template>

    <div class="preview-item">
      <h4>{{ entry.title }}</h4>

      <!-- 选择题：渲染交互式选项卡片 -->
      <template v-if="isChoice && options.length > 0">
        <div class="option-list" style="margin-top:12px;">
          <label
            v-for="opt in options"
            :key="opt"
            class="option-item"
            :class="optionClass(opt)"
          >
            <input
              v-if="entry.type === 'single' || entry.type === 'judge'"
              v-model="selectedKey"
              :value="extractKey(opt)"
              name="wrongRetry"
              type="radio"
              :disabled="submitDisabled"
            />
            <input
              v-else
              v-model="selectedKeys"
              :value="extractKey(opt)"
              type="checkbox"
              :disabled="submitDisabled"
            />
            <span class="option-letter">{{ extractKey(opt) }}</span>
            <span class="option-text">{{ stripKey(opt) }}</span>
          </label>
        </div>
      </template>

      <!-- 填空/简答/编程题：文本输入 -->
      <textarea
        v-else
        v-model="textAnswer"
        placeholder="请输入你的答案"
        :disabled="submitDisabled"
        style="margin-top:12px;width:100%;min-height:120px;"
      ></textarea>

      <!-- 提交结果反馈 -->
      <div v-if="retryResult.status === 'success'" class="retry-result" :class="retryResult.correct ? 'correct' : 'incorrect'">
        <span class="result-icon">{{ retryResult.correct ? '✓' : '✗' }}</span>
        <span>{{ retryResult.message }}</span>
      </div>
      <div v-else-if="retryResult.status === 'error'" class="retry-result incorrect">
        <span class="result-icon">✗</span>
        <span>{{ retryResult.message || '提交失败，请重试' }}</span>
      </div>

      <!-- 参考答案：仅在提交完成后展示，避免提前泄露 -->
      <p
        v-if="retryResult.status === 'success'"
        class="muted reference-answer"
      >
        参考答案：{{ joinAnswer(entry.expectedAnswer) }}
      </p>
    </div>

    <div class="action-row" style="justify-content:flex-end;margin-top:12px;">
      <button class="ghost-btn" type="button" @click="$emit('close')">关闭</button>
      <template v-if="retryResult.status === 'success'">
        <button v-if="!retryResult.correct" class="primary-btn" type="button" @click="retryAgain">再试一次</button>
      </template>
      <button
        v-else
        class="primary-btn"
        type="button"
        :disabled="submitDisabled || !canSubmit"
        @click="submitRetry"
      >
        {{ retryResult.status === 'submitting' ? '提交中...' : '提交重做' }}
      </button>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import BaseModal from "../common/BaseModal.vue";
import type { WrongBookEntry } from "../../types";
import { joinAnswer, typeLabel } from "../../utils/format";

interface RetryResult {
  status: 'idle' | 'submitting' | 'success' | 'error';
  correct?: boolean;
  message?: string;
}

const props = defineProps<{
  entry: WrongBookEntry;
  retryResult: RetryResult;
}>();

const emit = defineEmits<{
  close: [];
  submit: [payload: { entryId: string; answer: string[] }];
  reset: [];
}>();

// 选项：优先使用题目完整选项，否则空（回退到文本输入）
const options = computed(() => props.entry.question?.options || [])
const isChoice = computed(() =>
  props.entry.type === 'single' || props.entry.type === 'multiple' || props.entry.type === 'judge'
)

// 提取选项字母键： "A. xxx" → "A"，"A、xxx" → "A"
function extractKey(opt: string): string {
  const m = opt.match(/^([A-Z])[.、\s]/)
  return m ? m[1] : opt.charAt(0)
}
// 去除选项字母前缀： "A. xxx" → "xxx"
function stripKey(opt: string): string {
  return opt.replace(/^[A-Z][.、\s]+/, '')
}

// 初始作答：回显上次重做或最近一次答案
const prevAnswer = props.entry.lastRetryAnswer?.length ? props.entry.lastRetryAnswer : (props.entry.latestAnswer || [])

const selectedKey = ref(prevAnswer[0] || '')
const selectedKeys = ref<string[]>([...prevAnswer])
const textAnswer = ref(props.entry.type === 'fill' || props.entry.type === 'short' || props.entry.type === 'coding'
  ? (prevAnswer[0] || '')
  : '')

const submitDisabled = computed(() => props.retryResult.status === 'submitting' || props.retryResult.status === 'success')

const canSubmit = computed(() => {
  if (props.entry.type === 'single' || props.entry.type === 'judge') {
    return !!selectedKey.value
  }
  if (props.entry.type === 'multiple') {
    return selectedKeys.value.length > 0
  }
  return textAnswer.value.trim().length > 0
})

function isSelected(opt: string): boolean {
  const key = extractKey(opt)
  if (props.entry.type === 'single' || props.entry.type === 'judge') {
    return selectedKey.value === key
  }
  return selectedKeys.value.includes(key)
}

// 提交成功后，对选项卡片标记对错样式
function optionClass(opt: string): Record<string, boolean> {
  const key = extractKey(opt)
  const selected = isSelected(opt)
  const result = props.retryResult
  // 仅在提交完成后高亮
  if (result.status !== 'success') {
    return { selected }
  }
  const expected = (props.entry.expectedAnswer || []).map(normalizeKey)
  const isCorrect = expected.includes(key)
  return {
    selected,
    'option-correct': isCorrect,
    'option-wrong': selected && !isCorrect,
  }
}

function normalizeKey(k: string): string {
  return String(k || '').trim().toUpperCase().charAt(0)
}

function submitRetry() {
  if (submitDisabled.value || !canSubmit.value) return
  let answer: string[]
  if (props.entry.type === 'single' || props.entry.type === 'judge') {
    answer = selectedKey.value ? [selectedKey.value] : []
  } else if (props.entry.type === 'multiple') {
    answer = [...selectedKeys.value]
  } else {
    answer = textAnswer.value.trim() ? [textAnswer.value.trim()] : []
  }
  emit("submit", { entryId: props.entry.id, answer });
}

function retryAgain() {
  selectedKey.value = ''
  selectedKeys.value = []
  textAnswer.value = ''
  emit("reset")
}
</script>

<style scoped>
.option-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  border: 1.5px solid var(--border, #e0e0e0);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s ease;
  background: var(--bg, #fff);
}

.option-item:hover {
  border-color: var(--primary, #3d9980);
  background: var(--primary-soft, #e0f2ec);
}

.option-item.selected {
  border-color: var(--primary, #3d9980);
  background: var(--primary-soft, #e0f2ec);
}

.option-item.option-correct {
  border-color: var(--ok, #3d9980);
  background: var(--ok-soft, #e0f2ec);
}

.option-item.option-wrong {
  border-color: var(--danger, #d9605f);
  background: rgba(217, 96, 95, 0.08);
}

.option-item input {
  margin-top: 3px;
  accent-color: var(--primary, #3d9980);
  cursor: pointer;
}

.option-letter {
  font-weight: 600;
  color: var(--primary, #3d9980);
  min-width: 18px;
}

.option-text {
  flex: 1;
  word-break: break-word;
}

.retry-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 14px;
  border-radius: 8px;
  font-weight: 600;
  font-size: 14px;
}

.retry-result.correct {
  background: var(--ok-soft, #e0f2ec);
  color: var(--ok, #3d9980);
  border-left: 3px solid var(--ok, #3d9980);
}

.retry-result.incorrect {
  background: rgba(217, 96, 95, 0.08);
  color: var(--danger, #d9605f);
  border-left: 3px solid var(--danger, #d9605f);
}

.result-icon {
  font-size: 18px;
  font-weight: 700;
}

.reference-answer {
  margin-top: 12px;
  padding: 10px 14px;
  background: var(--ok-soft, #e0f2ec);
  border-radius: var(--radius, 8px);
  border-left: 3px solid var(--ok, #3d9980);
}

.action-row button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
