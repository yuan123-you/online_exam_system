<!-- Shared message bubble rendering for both chat and practice sessions -->
<template>
  <div
    v-for="(msg, idx) in messages"
    :key="idx"
    :class="['chat-bubble', msg.role === 'user' ? 'bubble-user' : 'bubble-ai']"
  >
    <div v-if="msg.role === 'assistant'" class="bubble-avatar">🤖</div>
    <div class="bubble-body">
      <!-- Loading dots before first token -->
      <div
        v-if="msg.role === 'assistant' && isLast(idx) && streamingActive && !msg.content && !streamingReasoning"
        class="typing-dots"
      ><span></span><span></span><span></span></div>

      <!-- Reasoning (deep thinking) block — during streaming: open -->
      <div
        v-if="msg.role === 'assistant' && isLast(idx) && streamingActive && streamingReasoning"
        class="reasoning-block"
      >
        <details open>
          <summary>🤔 深度思考中...</summary>
          <div class="reasoning-text">{{ streamingReasoning }}</div>
        </details>
      </div>

      <!-- Reasoning block — after streaming: collapsed, preserved for review -->
      <div
        v-if="msg.role === 'assistant' && msg.reasoning && !(isLast(idx) && streamingActive)"
        class="reasoning-block reasoning-saved"
      >
        <details>
          <summary>🤔 深度思考</summary>
          <div class="reasoning-text">{{ msg.reasoning }}</div>
        </details>
      </div>

      <!-- Rendered Markdown content -->
      <div
        v-if="msg.content"
        class="message-content"
        :class="{ streaming: msg.role === 'assistant' && isLast(idx) && streamingActive }"
        v-html="renderContent(msg.content, msg.role, idx)"
      ></div>

      <!-- Duration + Copy row (AI only, after streaming) -->
      <div v-if="msg.role === 'assistant' && msg.content && !(isLast(idx) && streamingActive)" class="msg-meta">
        <span v-if="msg.duration != null" class="msg-duration">{{ formatDuration(msg.duration) }}</span>
        <div class="copy-dropdown">
          <button class="copy-trigger" @click="toggleCopyMenu(idx)" :class="{ open: copyMenuIdx === idx }">
            📋 复制
          </button>
          <div v-if="copyMenuIdx === idx" class="copy-menu">
            <button @click="copyMarkdown(msg.content, idx); copyMenuIdx = null">📝 复制 Markdown</button>
            <button @click="copyPlain(msg.content, idx); copyMenuIdx = null">📄 复制文本</button>
          </div>
        </div>
      </div>

      <!-- Copy row for user messages -->
      <div v-if="msg.role === 'user' && msg.content" class="msg-meta msg-meta-user">
        <div class="copy-dropdown">
          <button class="copy-trigger" @click="toggleCopyMenu(idx)" :class="{ open: copyMenuIdx === idx }">
            复制
          </button>
          <div v-if="copyMenuIdx === idx" class="copy-menu copy-menu-right">
            <button @click="copyMarkdown(msg.content, idx); copyMenuIdx = null">📝 复制 Markdown</button>
            <button @click="copyPlain(msg.content, idx); copyMenuIdx = null">📄 复制文本</button>
          </div>
        </div>
      </div>

      <!-- Parsed practice question cards — exam paper style -->
      <div
        v-if="msg.role === 'assistant' && msg.content && parsedQuestions(idx).length > 0"
        class="exam-paper"
      >
        <div class="exam-header">
          <span class="exam-title">📋 练习题（共 {{ parsedQuestions(idx).length }} 题）</span>
          <button v-if="!submitted[idx]" class="exam-submit" @click="submitAnswers(idx)">交卷</button>
          <span v-else class="exam-score">
            得分：{{ scoreSummary(idx) }}
          </span>
        </div>

        <div v-for="(q, qi) in parsedQuestions(idx)" :key="qi" class="exam-question">
          <!-- Question number + type badge -->
          <div class="eq-head">
            <span class="eq-num">{{ qi + 1 }}.</span>
            <span class="eq-type">{{ typeLabel((q.type as any) || 'single') }}</span>
            <span class="eq-score">{{ q.score || 5 }}分</span>
          </div>

          <!-- Question text -->
          <div class="eq-body">{{ q.title }}</div>

          <!-- Single choice / Judge: radio buttons with A/B/C/D labels -->
          <div v-if="(q.type === 'single' || q.type === 'judge') && q.options?.length" class="eq-options">
            <label
              v-for="(opt, oi) in q.options"
              :key="oi"
              :class="['eq-opt', optionClass(idx, qi, opt)]"
            >
              <span class="eo-letter">{{ ['A','B','C','D','E','F'][oi] || oi }}</span>
              <input
                type="radio"
                :name="'pq-' + idx + '-' + qi"
                :value="extractKey(opt)"
                :checked="answers[idx]?.[qi]?.includes(extractKey(opt))"
                :disabled="submitted[idx]"
                @change="setSingle(idx, qi, extractKey(opt))"
              />
              <span class="eo-text">{{ opt.replace(/^[A-D][.、\s]+/, '') }}</span>
            </label>
          </div>

          <!-- Multiple choice: checkboxes -->
          <div v-if="q.type === 'multiple' && q.options?.length" class="eq-options">
            <label
              v-for="(opt, oi) in q.options"
              :key="oi"
              :class="['eq-opt', optionClass(idx, qi, opt)]"
            >
              <span class="eo-letter">{{ ['A','B','C','D','E','F'][oi] || oi }}</span>
              <input
                type="checkbox"
                :value="extractKey(opt)"
                :checked="answers[idx]?.[qi]?.includes(extractKey(opt))"
                :disabled="submitted[idx]"
                @change="toggleMulti(idx, qi, extractKey(opt))"
              />
              <span class="eo-text">{{ opt.replace(/^[A-D][.、\s]+/, '') }}</span>
            </label>
          </div>

          <!-- Fill / Short answer / Coding: textarea -->
          <div v-if="q.type === 'fill' || q.type === 'short' || q.type === 'coding'" class="eq-textarea">
            <textarea
              v-model="textAnswers[idx][qi]"
              :placeholder="q.type === 'coding' ? '请输入代码...' : '请输入答案...'"
              :disabled="submitted[idx]"
              rows="3"
            ></textarea>
          </div>

          <!-- Result after submission -->
          <div
            v-if="submitted[idx] && results[idx]?.[qi] !== undefined"
            :class="['eq-result', results[idx][qi] ? 'correct' : 'wrong']"
          >
            <span v-if="results[idx][qi]">✅ 回答正确</span>
            <span v-else>❌ 回答错误，正确答案：{{ (q.answer || []).join('，') }}</span>
          </div>
        </div>
      </div>
    </div>
    <div v-if="msg.role === 'user'" class="bubble-avatar user-avatar">👤</div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import type { AiQuestion } from '@/api/client'

const props = defineProps<{
  messages: Array<{ role: string; content: string }>
  streamingActive: boolean
  streamingReasoning: string
  loading: boolean
}>()

const store = useAppStore()

// Copy state
const copyMenuIdx = ref<number | null>(null)
const copiedIdx = ref<number | null>(null)

function toggleCopyMenu(idx: number) {
  copyMenuIdx.value = copyMenuIdx.value === idx ? null : idx
}

function formatDuration(seconds: number): string {
  if (seconds < 60) return seconds.toFixed(1) + 's'
  const m = Math.floor(seconds / 60)
  const s = (seconds % 60).toFixed(1)
  return m + 'm ' + s + 's'
}

// Per-message answer state
const answers = reactive<Record<number, Record<number, string[]>>>({})
const textAnswers = reactive<Record<number, Record<number, string>>>({})
const results = reactive<Record<number, Record<number, boolean>>>({})
const submitted = reactive<Record<number, boolean>>({})

function isLast(idx: number) {
  return idx === props.messages.length - 1
}

// ---- Copy Plain Text (strip markdown) ----
async function copyPlain(text: string, idx: number) {
  const plain = text
    .replace(/```[\s\S]*?```/g, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/\n{2,}/g, '\n\n')
    .trim()
  await copyToClipboard(plain, idx)
}

// ---- Copy Markdown ----
async function copyMarkdown(text: string, idx: number) {
  await copyToClipboard(text, idx)
}

async function copyToClipboard(text: string, idx: number) {
  try {
    await navigator.clipboard.writeText(text)
    copiedIdx.value = idx
    store.showToast('已复制', 'success')
    setTimeout(() => {
      if (copiedIdx.value === idx) copiedIdx.value = null
    }, 1500)
  } catch {
    // Fallback for insecure contexts
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'; ta.style.left = '-9999px'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    copiedIdx.value = idx
    store.showToast('已复制', 'success')
    setTimeout(() => {
      if (copiedIdx.value === idx) copiedIdx.value = null
    }, 1500)
  }
}

// ---- Question parsing ----
function parsedQuestions(msgIdx: number): AiQuestion[] {
  const msg = props.messages[msgIdx]
  if (!msg || msg.role !== 'assistant') return []
  try {
    const content = msg.content
    // Try extracting JSON array from content
    const jsonMatch = content.match(/\[[\s\S]*\]/)
    if (!jsonMatch) return []
    const parsed = JSON.parse(jsonMatch[0])
    if (Array.isArray(parsed) && parsed.length > 0 && parsed[0].title) {
      return parsed as AiQuestion[]
    }
    return []
  } catch {
    return []
  }
}

function extractKey(opt: string): string {
  const m = opt.match(/^([A-D])(?:\.\s*|$)/)
  return m ? m[1] : opt
}

// ---- Answer helpers ----
function ensure(msgIdx: number, qi: number) {
  if (!answers[msgIdx]) answers[msgIdx] = {}
  if (!answers[msgIdx][qi]) answers[msgIdx][qi] = []
  if (!textAnswers[msgIdx]) textAnswers[msgIdx] = {}
  if (!textAnswers[msgIdx][qi]) textAnswers[msgIdx][qi] = ''
}

function setSingle(msgIdx: number, qi: number, key: string) {
  ensure(msgIdx, qi)
  answers[msgIdx][qi] = [key]
}

function toggleMulti(msgIdx: number, qi: number, key: string) {
  ensure(msgIdx, qi)
  const arr = answers[msgIdx][qi]
  const pos = arr.indexOf(key)
  if (pos >= 0) arr.splice(pos, 1)
  else arr.push(key)
}

function optionClass(msgIdx: number, qi: number, opt: string): string {
  const key = extractKey(opt)
  const sel = answers[msgIdx]?.[qi]?.includes(key)
  if (!submitted[msgIdx]) return sel ? 'selected' : ''
  const q = parsedQuestions(msgIdx)[qi]
  if (!q) return ''
  const correct = (q.answer || []).includes(key)
  if (correct) return 'correct'
  if (sel && !correct) return 'wrong'
  return ''
}

function scoreSummary(msgIdx: number): string {
  if (!results[msgIdx]) return '未提交'
  const qs = parsedQuestions(msgIdx)
  let correct = 0
  for (let qi = 0; qi < qs.length; qi++) {
    if (results[msgIdx][qi]) correct++
  }
  let totalScore = 0
  for (const q of qs) totalScore += (q.score || 5)
  const earned = qs.reduce((sum, q, qi) => sum + (results[msgIdx][qi] ? (q.score || 5) : 0), 0)
  return `${correct}/${qs.length} 题正确，${earned}/${totalScore} 分`
}

function submitAnswers(msgIdx: number) {
  const qs = parsedQuestions(msgIdx)
  submitted[msgIdx] = true
  if (!results[msgIdx]) results[msgIdx] = {}

  for (let qi = 0; qi < qs.length; qi++) {
    const q = qs[qi]
    let student: string[]
    if (q.type === 'fill' || q.type === 'short' || q.type === 'coding') {
      student = (textAnswers[msgIdx]?.[qi] || '').split(',').map(s => s.trim()).filter(Boolean)
    } else {
      student = (answers[msgIdx]?.[qi] || []).sort()
    }
    const correct = (q.answer || []).sort()
    results[msgIdx][qi] = JSON.stringify(student.sort()) === JSON.stringify(correct.sort())
  }

  const cc = Object.values(results[msgIdx]).filter(Boolean).length
  store.showToast(`练习完成！正确 ${cc}/${qs.length}`, cc === qs.length ? 'success' : 'info')
}

// ---- Markdown rendering ----
function renderContent(text: string, role: string, idx: number): string {
  if (role === 'user') return escapeHtml(text)

  const parsed = parsedQuestions(idx)
  let display = text
  if (parsed.length > 0) {
    const m = text.match(/```(?:json)?\s*[\s\S]*?```|\[[\s\S]*\]/)
    if (m) {
      display = text.substring(0, m.index!) + text.substring(m.index! + m[0].length)
      if (!display.trim()) return '<p style="color:var(--muted);font-style:italic;">已为你生成下方练习题 👇</p>'
    }
  }

  let html = escapeHtml(display)
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre class="md-code"><code>$2</code></pre>')
  html = html.replace(/`([^`]+)`/g, '<code class="md-inline">$1</code>')
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>')
  html = html.replace(/^### (.+)$/gm, '<h5 class="md-h5">$1</h5>')
  html = html.replace(/^## (.+)$/gm, '<h4 class="md-h4">$1</h4>')
  html = html.replace(/^(\d+)\. (.+)$/gm, '<li class="md-li">$1. $2</li>')
  html = html.replace(/^- (.+)$/gm, '<li class="md-li">• $1</li>')
  html = html.replace(/\n\n+/g, '</p><p class="md-p">')
  html = html.replace(/\n/g, '<br>')
  return '<p class="md-p">' + html + '</p>'
}

function escapeHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
</script>

<style scoped>
/* ===== Bubbles ===== */
.chat-bubble {
  display: flex; gap: 8px; max-width: 85%;
}
.bubble-user { align-self: flex-end; flex-direction: row-reverse; }
.bubble-ai { align-self: flex-start; }

.bubble-avatar {
  width: 30px; height: 30px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; flex-shrink: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.user-avatar { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }

.bubble-body {
  padding: 16px 20px; border-radius: 18px;
  font-size: 14px; line-height: 1.7; min-width: 0;
}
.bubble-user .bubble-body {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff; border-bottom-right-radius: 4px;
}
.bubble-ai .bubble-body {
  background: var(--bg-alt, #f3f4f6);
  color: var(--text); border-bottom-left-radius: 4px;
}

/* Typing dots */
.typing-dots { display: flex; gap: 3px; padding: 2px 0; }
.typing-dots span {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--muted);
  animation: dot 1.4s infinite ease-in-out both;
}
.typing-dots span:nth-child(1) { animation-delay: -0.32s; }
.typing-dots span:nth-child(2) { animation-delay: -0.16s; }
@keyframes dot {
  0%,80%,100% { transform: scale(0.4); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* Reasoning */
.reasoning-block { margin-bottom: 6px; }
.reasoning-block summary { font-size: 12px; color: #8b5cf6; cursor: pointer; font-weight: 500; }
.reasoning-block.reasoning-saved summary { color: var(--muted); font-weight: 400; }
.reasoning-text {
  font-size: 11px; line-height: 1.4; color: #7c3aed;
  background: #f5f3ff; padding: 6px 8px; border-radius: 5px;
  white-space: pre-wrap; max-height: 160px; overflow-y: auto; margin-top: 3px;
}
.reasoning-saved .reasoning-text {
  color: var(--muted);
  background: var(--bg-alt, #f5f5f5);
}

/* Message content */
.message-content { word-break: break-word; }
.message-content.streaming::after {
  content: '▋';
  animation: cursor-pulse 1.4s ease-in-out infinite;
  color: var(--primary);
  margin-left: 1px;
  font-weight: 300;
}
@keyframes cursor-pulse {
  0%, 100% { opacity: 0.25; }
  50% { opacity: 1; }
}

.message-content :deep(.md-p) { margin: 3px 0; }
.message-content :deep(.md-code) {
  background: #1e1e2e; color: #cdd6f4;
  padding: 10px; border-radius: 7px; overflow-x: auto;
  font-size: 12px; line-height: 1.4; margin: 6px 0;
}
.message-content :deep(.md-inline) {
  background: rgba(0,0,0,0.07); padding: 1px 5px;
  border-radius: 3px; font-size: 12px; font-family: monospace;
}
.message-content :deep(.md-h4),
.message-content :deep(.md-h5) { margin: 8px 0 3px; font-weight: 600; }
.message-content :deep(.md-li) { margin: 1px 0; padding-left: 2px; }

/* ===== Exam Paper Style ===== */
.exam-paper {
  margin-top: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.exam-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.exam-title { font-size: 13px; font-weight: 600; color: #374151; }

.exam-submit {
  padding: 5px 16px;
  border: none;
  border-radius: 6px;
  background: #111827;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.exam-submit:hover { background: #374151; }

.exam-score { font-size: 13px; font-weight: 600; color: #6366f1; }

/* Each question */
.exam-question {
  padding: 14px 16px;
  border-bottom: 1px solid #f3f4f6;
}
.exam-question:last-child { border-bottom: none; }

.eq-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.eq-num {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.eq-type {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #eef2ff;
  color: #6366f1;
  font-weight: 500;
}

.eq-score { font-size: 11px; color: #9ca3af; margin-left: auto; }

.eq-body {
  font-size: 14px;
  line-height: 1.6;
  color: #1f2937;
  margin-bottom: 10px;
}

/* Options */
.eq-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.eq-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.12s;
}
.eq-opt:hover { background: #f9fafb; }
.eq-opt.selected { border-color: #6366f1; background: #eef2ff; }
.eq-opt.correct { border-color: #22c55e; background: #f0fdf4; }
.eq-opt.wrong { border-color: #ef4444; background: #fef2f2; }

.eo-letter {
  width: 24px; height: 24px;
  border-radius: 50%;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  flex-shrink: 0;
}
.eq-opt.selected .eo-letter { background: #6366f1; color: #fff; }
.eq-opt.correct .eo-letter { background: #22c55e; color: #fff; }
.eq-opt.wrong .eo-letter { background: #ef4444; color: #fff; }

.eq-opt input { display: none; }

.eo-text { font-size: 13px; color: #374151; line-height: 1.4; }

/* Textarea for subjective */
.eq-textarea textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.5;
  resize: vertical;
  background: #f9fafb;
  color: #111827;
  outline: none;
  transition: border-color 0.15s;
}
.eq-textarea textarea:focus { border-color: #6366f1; background: #fff; }
.eq-textarea textarea:disabled { opacity: 0.6; }

/* Result */
.eq-result {
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
}
.eq-result.correct { background: #f0fdf4; color: #166534; }
.eq-result.wrong { background: #fef2f2; color: #991b1b; }

/* ===== Meta row (duration + copy) ===== */
.msg-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(0,0,0,0.06);
}
.msg-meta-user {
  justify-content: flex-end;
}

.msg-duration {
  font-size: 11px;
  color: #9ca3af;
}

/* ===== Copy Dropdown ===== */
.copy-dropdown {
  position: relative;
}

.copy-trigger {
  padding: 5px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
}
.copy-trigger:hover, .copy-trigger.open {
  border-color: #6366f1;
  color: #6366f1;
  background: #eef2ff;
}

.copy-menu {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 4px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  overflow: hidden;
  z-index: 20;
  min-width: 160px;
}
.copy-menu-right {
  right: auto;
  left: 0;
}

.copy-menu button {
  display: block;
  width: 100%;
  padding: 8px 14px;
  border: none;
  background: transparent;
  color: #374151;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
}
.copy-menu button:hover {
  background: #f3f4f6;
}
</style>
