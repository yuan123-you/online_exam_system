<template>
  <div class="chat-input-area">
    <div class="input-tools">
      <button class="tool-btn" title="上传图片（即将支持）" disabled>🖼</button>
      <button class="tool-btn" title="上传文件（即将支持）" disabled>📎</button>
    </div>
    <textarea
      ref="inputRef"
      :value="modelValue"
      class="chat-textarea"
      rows="1"
      :placeholder="placeholder"
      :disabled="disabled"
      @input="onInput"
      @keydown="onKeydown"
    ></textarea>
    <button
      class="send-btn"
      :disabled="!modelValue.trim() || disabled"
      @click="$emit('send')"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="22" y1="2" x2="11" y2="13"></line>
        <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

const props = defineProps<{
  modelValue: string
  disabled: boolean
  placeholder: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'send': []
}>()

const inputRef = ref<HTMLTextAreaElement | null>(null)

function onInput(e: Event) {
  const target = e.target as HTMLTextAreaElement
  emit('update:modelValue', target.value)
  nextTick(() => autoResize())
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (props.modelValue.trim() && !props.disabled) {
      emit('send')
    }
  }
}

function autoResize() {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
    inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 160) + 'px'
  }
}
</script>

<style scoped>
.chat-input-area {
  display: flex; align-items: flex-end; gap: 6px;
  padding: 10px 14px; border-top: 1px solid var(--border);
  background: var(--bg-alt, #fafbfc);
  flex-shrink: 0;
}

.input-tools { display: flex; gap: 2px; flex-shrink: 0; }

.tool-btn {
  width: 34px; height: 34px; border: none; border-radius: 8px;
  background: transparent; font-size: 16px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background 0.12s;
}
.tool-btn:hover:not(:disabled) { background: var(--bg); }
.tool-btn:disabled { opacity: 0.35; cursor: not-allowed; }

.chat-textarea {
  flex: 1;
  padding: 8px 12px;
  border: 1.5px solid var(--border);
  border-radius: 10px;
  font-size: 14px; font-family: inherit; line-height: 1.5;
  resize: none; outline: none;
  background: var(--card-bg, #fff);
  color: var(--text);
  max-height: 160px;
  transition: border-color 0.15s;
}
.chat-textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 12%, transparent);
}

.send-btn {
  width: 38px; height: 38px; border: none; border-radius: 10px;
  background: var(--primary); color: #fff;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: all 0.12s;
}
.send-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--primary) 85%, black);
  transform: scale(1.04);
}
.send-btn:disabled { opacity: 0.35; cursor: not-allowed; }
</style>
