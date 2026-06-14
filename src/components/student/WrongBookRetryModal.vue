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

      <!-- Extract letter key from option string: "A. xxx" → "A" -->
      <template v-if="isChoice && options.length > 0">
        <div class="option-list" style="margin-top:12px;">
          <label v-for="opt in options" :key="opt" class="option-item" :class="{ selected: isSelected(opt) }">
            <input
              v-if="entry.type === 'single' || entry.type === 'judge'"
              v-model="selectedKey"
              :value="extractKey(opt)"
              name="wrongRetry"
              type="radio"
            />
            <input
              v-else
              v-model="selectedKeys"
              :value="extractKey(opt)"
              type="checkbox"
            />
            <span class="option-letter">{{ extractKey(opt) }}</span>
            <span class="option-text">{{ stripKey(opt) }}</span>
          </label>
        </div>
      </template>

      <textarea v-else v-model="textAnswer" placeholder="请输入你的答案" style="margin-top:12px;width:100%;min-height:120px;"></textarea>

      <p class="muted" style="margin-top:12px;padding:10px 14px;background:var(--ok-soft);border-radius:var(--radius);border-left:3px solid var(--ok);">
        参考答案：{{ joinAnswer(entry.expectedAnswer) }}
      </p>
    </div>

    <div class="action-row" style="justify-content:flex-end;margin-top:12px;">
      <button class="ghost-btn" type="button" @click="$emit('close')">取消</button>
      <button class="primary-btn" type="button" @click="submitRetry">提交重做</button>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import BaseModal from "../common/BaseModal.vue";
import type { WrongBookEntry } from "../../types";
import { joinAnswer, typeLabel } from "../../utils/format";

const props = defineProps<{
  entry: WrongBookEntry;
}>();

const emit = defineEmits<{
  close: [];
  submit: [payload: { entryId: string; answer: string[] }];
}>();

// Options: use entry.question.options if available, otherwise empty
const options = computed(() => props.entry.question?.options || [])
const isChoice = computed(() =>
  props.entry.type === 'single' || props.entry.type === 'multiple' || props.entry.type === 'judge'
)

// Extract letter key: "A. xxx" → "A", "A、xxx" → "A"
function extractKey(opt: string): string {
  const m = opt.match(/^([A-D])[.、\s]/)
  return m ? m[1] : opt.charAt(0)
}
// Strip key prefix: "A. xxx" → "xxx"
function stripKey(opt: string): string {
  return opt.replace(/^[A-D][.、\s]+/, '')
}

// Use stored answers (letter keys) for initial selection
const prevAnswer = props.entry.lastRetryAnswer?.length ? props.entry.lastRetryAnswer : (props.entry.latestAnswer || [])

const selectedKey = ref(prevAnswer[0] || '')
const selectedKeys = ref<string[]>([...prevAnswer])
const textAnswer = ref(props.entry.type === 'fill' || props.entry.type === 'short' || props.entry.type === 'coding'
  ? (prevAnswer[0] || '')
  : '')

function isSelected(opt: string): boolean {
  const key = extractKey(opt)
  if (props.entry.type === 'single' || props.entry.type === 'judge') {
    return selectedKey.value === key
  }
  return selectedKeys.value.includes(key)
}

function submitRetry() {
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
</script>
