<!-- Shared message bubble rendering for both chat and practice sessions -->
<template>
  <div
    v-for="(msg, idx) in messages"
    :key="idx"
    :class="['chat-bubble', msg.role === 'user' ? 'bubble-user' : 'bubble-ai']"
  >
    <div v-if="msg.role === 'assistant'" class="bubble-avatar">🤖</div>
    <div class="bubble-col">
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
            <summary>💭 深度思考中...</summary>
            <div class="reasoning-text" v-html="formatReasoning(streamingReasoning)"></div>
          </details>
        </div>

        <!-- Reasoning block — after streaming: collapsed by default, user can expand -->
        <div
          v-if="msg.role === 'assistant' && msg.reasoning && !(isLast(idx) && streamingActive)"
          class="reasoning-block reasoning-saved"
        >
          <details>
            <summary>💭 深度思考</summary>
            <div class="reasoning-text" v-html="formatReasoning(msg.reasoning)"></div>
          </details>
        </div>

        <!-- Rendered Markdown content -->
        <div
          v-if="msg.content"
          class="message-content"
          :class="{ streaming: msg.role === 'assistant' && isLast(idx) && streamingActive }"
          v-html="renderContent(msg.content, msg.role, idx)"
        ></div>
      </div>

      <!-- Duration + Copy row (AI only, after streaming) -->
      <div v-if="msg.role === 'assistant' && msg.content && !(isLast(idx) && streamingActive)" class="msg-meta">
        <span v-if="msg.duration != null" class="msg-duration">{{ formatDuration(msg.duration) }}</span>
        <button v-if="(msg as any)._retryMessage" class="retry-btn" @click="retryMessage(idx)">🔄 重新生成</button>
        <button class="copy-btn" @click="copyPlain(msg.content, idx)">📋 复制</button>
      </div>

      <!-- Copy row for user messages -->
      <div v-if="msg.role === 'user' && msg.content" class="msg-meta msg-meta-user">
        <button class="copy-btn" @click="copyPlain(msg.content, idx)">📋 复制</button>
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
            <!-- Enhanced explanation display -->
            <div v-if="q.explanation" class="eq-explanation">
              <div class="eq-expl-header" @click="toggleExplanation(idx, qi)">
                <span>💡 查看解析</span>
                <span class="eq-expl-arrow">{{ explanationsOpen[idx]?.[qi] ? '▼' : '▶' }}</span>
              </div>
              <div v-if="explanationsOpen[idx]?.[qi]" class="eq-expl-body" v-html="renderExplanation(q.explanation)"></div>
            </div>
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
  messages: Array<{ role: string; content: string; reasoning?: string; duration?: number; _retryMessage?: string }>
  streamingActive: boolean
  streamingReasoning: string
  loading: boolean
  tab?: 'chat' | 'practice'
}>()

const store = useAppStore()

const copiedIdx = ref<number | null>(null)

function formatDuration(seconds: number): string {
  if (seconds < 60) return seconds.toFixed(1) + 's'
  const m = Math.floor(seconds / 60)
  const s = (seconds % 60).toFixed(1)
  return m + 'm ' + s + 's'
}

/** Format raw AI reasoning text into clean, readable HTML */
function formatReasoning(text: string): string {
  if (!text) return ''
  let html = escapeHtml(text)

  // Collapse multiple blank lines into single paragraph breaks
  html = html.replace(/\n{3,}/g, '\n\n')

  // Convert **bold** and *italic* markdown
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>')

  // Convert markdown headings (### Title)
  html = html.replace(/^### (.+)$/gm, '<h5 class="reasoning-h5">$1</h5>')
  html = html.replace(/^## (.+)$/gm, '<h4 class="reasoning-h4">$1</h4>')

  // Convert numbered lists (1. Item)
  html = html.replace(/^(\d+)\. (.+)$/gm, '<span class="reasoning-li">$1. $2</span>')
  html = html.replace(/^- (.+)$/gm, '<span class="reasoning-li">• $2</span>')

  // Convert inline code
  html = html.replace(/`([^`]+)`/g, '<code class="reasoning-code">$1</code>')

  // Paragraph breaks: double newlines → paragraphs, single newlines → line breaks
  html = html.replace(/\n\n/g, '</p><p class="reasoning-p">')
  html = html.replace(/\n/g, '<br>')

  // Wrap in paragraph
  html = '<p class="reasoning-p">' + html + '</p>'

  // Clean up empty paragraphs
  html = html.replace(/<p class="reasoning-p"><\/p>/g, '')

  return html
}

// Per-message answer state
const answers = reactive<Record<number, Record<number, string[]>>>({})
const textAnswers = reactive<Record<number, Record<number, string>>>({})
const results = reactive<Record<number, Record<number, boolean>>>({})
const submitted = reactive<Record<number, boolean>>({})
const explanationsOpen = reactive<Record<number, Record<number, boolean>>>({})

function isLast(idx: number) {
  return idx === props.messages.length - 1
}

function retryMessage(idx: number) {
  store.handleRetryMessage(idx, props.tab || 'chat')
}

// Toggle explanation visibility
function toggleExplanation(msgIdx: number, qi: number) {
  if (!explanationsOpen[msgIdx]) explanationsOpen[msgIdx] = {}
  explanationsOpen[msgIdx][qi] = !explanationsOpen[msgIdx][qi]
}

// Render explanation text with basic markdown
function renderExplanation(text: string): string {
  if (!text) return ''
  let html = escapeHtml(text)
  // Bold
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  // Section headers like 【答案】【解析】
  html = html.replace(/【([^】]+)】/g, '<strong class="expl-section">【$1】</strong>')
  // Inline code
  html = html.replace(/`([^`]+)`/g, '<code class="md-inline">$1</code>')
  // Line breaks
  html = html.replace(/\n/g, '<br>')
  return html
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
/** Extract JSON question array from AI response, handling markdown fences and bare arrays */
function parsedQuestions(msgIdx: number): AiQuestion[] {
  const msg = props.messages[msgIdx]
  if (!msg || msg.role !== 'assistant') return []
  const content = msg.content
  if (!content) return []

  // Multiple extraction strategies, tried in order
  let jsonStr = ''

  // Strategy 1: extract from ```json ... ``` code fence
  const fenceMatch = content.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/)
  if (fenceMatch) {
    jsonStr = fenceMatch[1].trim()
  }

  // Strategy 2: balanced bracket extraction for bare JSON arrays
  if (!jsonStr) {
    const startIdx = content.indexOf('[')
    if (startIdx === -1) return []
    let depth = 0
    let inString = false
    let escaped = false
    for (let i = startIdx; i < content.length; i++) {
      const ch = content[i]
      if (escaped) { escaped = false; continue }
      if (ch === '\\') { escaped = true; continue }
      if (ch === '"') { inString = !inString; continue }
      if (inString) continue
      if (ch === '[') depth++
      else if (ch === ']') {
        depth--
        if (depth === 0) {
          jsonStr = content.substring(startIdx, i + 1)
          break
        }
      }
    }
  }

  if (!jsonStr) return []

  // Try to parse, with recovery for common AI output issues
  const parsed = tryParseJson(jsonStr)
  if (Array.isArray(parsed) && parsed.length > 0 && parsed[0].title) {
    return parsed as AiQuestion[]
  }
  return []
}

/** Parse JSON with recovery: trailing commas, unescaped newlines in strings */
function tryParseJson(raw: string): unknown {
  // Attempt 1: direct parse
  try { return JSON.parse(raw) } catch { /* continue */ }

  // Attempt 2: remove trailing commas before ] or }
  try {
    const cleaned = raw.replace(/,\s*([}\]])/g, '$1')
    return JSON.parse(cleaned)
  } catch { /* continue */ }

  // Attempt 3: try to fix unescaped quotes in strings (common AI mistake)
  try {
    // Replace smart quotes with straight quotes
    const fixed = raw
      .replace(/[\u201C\u201D]/g, '"')
      .replace(/[\u2018\u2019]/g, "'")
      .replace(/，/g, ',')  // Chinese commas in JSON
    return JSON.parse(fixed)
  } catch { /* continue */ }

  return null
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

  // Auto-expand explanations for all questions after submission
  if (!explanationsOpen[msgIdx]) explanationsOpen[msgIdx] = {}
  for (let qi = 0; qi < qs.length; qi++) {
    explanationsOpen[msgIdx][qi] = true
  }

  const cc = Object.values(results[msgIdx]).filter(Boolean).length
  store.showToast(`练习完成！正确 ${cc}/${qs.length}`, cc === qs.length ? 'success' : 'info')
}

// ---- Markdown rendering ----
function renderContent(text: string, role: string, idx: number): string {
  if (role === 'user') return escapeHtml(text)

  const isPractice = props.tab === 'practice'
  const isStreaming = isLast(idx) && props.streamingActive

  // --- Practice mode: detect and hide raw JSON question data ---
  if (isPractice) {
    const jsonRange = findJsonRange(text)

    if (jsonRange) {
      // JSON found — extract surrounding text (before/after the JSON block)
      const before = text.substring(0, jsonRange.start).trim()
      const after = text.substring(jsonRange.end).trim()
      const nonJson = (before + ' ' + after).trim()

      if (nonJson) {
        // There's explanatory text alongside the JSON — render that
        return renderMarkdown(nonJson)
      }

      // Entire content is just the JSON array
      if (isStreaming) {
        // Still generating — show animated placeholder
        return '<div class="generating-hint">📝 正在生成题目<span class="gen-dots">...</span></div>'
      }
      // Streaming complete, questions are parsed and displayed as cards below
      const parsed = parsedQuestions(idx)
      if (parsed.length > 0) {
        return `<p style="color:var(--muted);font-style:italic;">📋 已生成 ${parsed.length} 道练习题，请在下方作答 👇</p>`
      }
      // Parsing failed — show a friendly fallback (not the raw JSON)
      return '<p style="color:var(--muted);">📋 题目已生成，点击下方卡片作答</p>'
    }

    // JSON not fully formed yet but content starts like JSON — hide during streaming
    if (isStreaming && /^\s*[\[`]/.test(text)) {
      return '<div class="generating-hint">📝 正在生成题目<span class="gen-dots">...</span></div>'
    }
  }

  // --- Chat mode (or practice with non-JSON content): normal markdown ---
  return renderMarkdown(text)
}

/** Find the range [start, end) of a JSON question array in text. Returns null if not found. */
function findJsonRange(text: string): { start: number; end: number } | null {
  // Check for ```json fence first
  const fenceMatch = text.match(/```(?:json)?\s*\n?/)
  if (fenceMatch) {
    const fenceStart = fenceMatch.index!
    const contentStart = fenceStart + fenceMatch[0].length
    const endFence = text.indexOf('```', contentStart)
    if (endFence !== -1) {
      return { start: fenceStart, end: endFence + 3 }
    }
    // Unclosed fence during streaming — treat from fence start to end
    return { start: fenceStart, end: text.length }
  }

  // Check for bare JSON array
  const arrStart = text.indexOf('[')
  if (arrStart === -1) return null

  // Quick heuristic: does this look like a question array?
  // Check within first 200 chars for question-like fields
  const peek = text.substring(arrStart, Math.min(arrStart + 200, text.length))
  if (!/\b"(?:title|type|options|answer|explanation)"\s*:/.test(peek)) return null

  // Find matching closing bracket
  let depth = 0
  let inString = false
  let escaped = false
  for (let i = arrStart; i < text.length; i++) {
    const ch = text[i]
    if (escaped) { escaped = false; continue }
    if (ch === '\\') { escaped = true; continue }
    if (ch === '"') { inString = !inString; continue }
    if (inString) continue
    if (ch === '[') depth++
    else if (ch === ']') {
      depth--
      if (depth === 0) return { start: arrStart, end: i + 1 }
    }
  }
  // Unclosed bracket during streaming — hide from start to end
  if (depth > 0) return { start: arrStart, end: text.length }
  return null
}

/** Render plain text as markdown HTML */
function renderMarkdown(text: string): string {
  let html = escapeHtml(text)
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

/* Column wrapper for bubble body + meta (keeps layout) */
.bubble-col {
  display: flex; flex-direction: column;
  min-width: 0;
}
.bubble-user .bubble-col { align-items: flex-end; }
.bubble-ai .bubble-col { align-items: flex-start; }

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
.reasoning-block.reasoning-saved summary { color: #6b7280; font-weight: 500; }
.reasoning-block summary:hover { color: #7c3aed; }
.reasoning-text {
  font-size: 12px; line-height: 1.6; color: #5b21b6;
  background: #f5f3ff; padding: 10px 12px; border-radius: 6px;
  max-height: 200px; overflow-y: auto; margin-top: 4px;
}
.reasoning-saved .reasoning-text {
  color: #4b5563;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
}

/* Reasoning formatted content */
.reasoning-text :deep(.reasoning-p) { margin: 2px 0; }
.reasoning-text :deep(.reasoning-h4) {
  font-size: 13px; font-weight: 600; color: #374151;
  margin: 8px 0 4px; padding-bottom: 2px;
  border-bottom: 1px solid #e5e7eb;
}
.reasoning-text :deep(.reasoning-h5) {
  font-size: 12px; font-weight: 600; color: #4b5563;
  margin: 6px 0 2px;
}
.reasoning-text :deep(.reasoning-li) {
  display: block; font-size: 12px;
  padding: 1px 0 1px 8px; color: #4b5563;
}
.reasoning-text :deep(.reasoning-code) {
  background: rgba(0,0,0,0.06); padding: 1px 4px;
  border-radius: 3px; font-size: 11px; font-family: monospace;
}
.reasoning-text :deep(strong) { color: #374151; font-weight: 600; }
.reasoning-text :deep(em) { color: #6b7280; }

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

/* Generating hint — shown while AI streams question JSON */
.message-content :deep(.generating-hint) {
  color: #6366f1;
  font-size: 13px;
  font-weight: 500;
  padding: 4px 0;
  display: flex;
  align-items: center;
  gap: 2px;
}
.message-content :deep(.gen-dots) {
  display: inline-flex;
  gap: 1px;
}
.message-content :deep(.gen-dots)::before {
  content: '.';
  animation: gen-dot 1.4s infinite step-end both;
}
.message-content :deep(.gen-dots)::after {
  content: '..';
  animation: gen-dots 1.4s infinite step-end both;
}
@keyframes gen-dot {
  0%, 100% { opacity: 0; }
  50% { opacity: 1; }
}
@keyframes gen-dots {
  0% { opacity: 0; }
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

/* Explanation collapsible */
.eq-explanation {
  margin-top: 8px;
  border-top: 1px solid rgba(0,0,0,0.06);
  padding-top: 6px;
}

.eq-expl-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  color: #6366f1;
  padding: 4px 0;
  user-select: none;
}
.eq-expl-header:hover { color: #4f46e5; }

.eq-expl-arrow {
  font-size: 10px;
  transition: transform 0.15s;
}

.eq-expl-body {
  margin-top: 6px;
  padding: 8px 10px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: #374151;
  border: 1px solid #e5e7eb;
}

.eq-expl-body :deep(.expl-section) {
  color: #6366f1;
  display: inline-block;
  margin: 4px 0 2px;
}

/* ===== Meta row (duration + copy) — outside bubble body ===== */
.msg-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  padding: 0 4px;
}
.msg-meta-user {
  justify-content: flex-end;
}

.msg-duration {
  font-size: 11px;
  color: #9ca3af;
}

/* Simple copy button */
.copy-btn {
  padding: 4px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
}
.copy-btn:hover {
  border-color: #6366f1;
  color: #6366f1;
  background: #eef2ff;
}

/* Retry button for failed messages */
.retry-btn {
  padding: 4px 12px;
  border: 1px solid #fbbf24;
  border-radius: 6px;
  background: #fffbeb;
  color: #b45309;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  margin-right: 4px;
}
.retry-btn:hover {
  border-color: #f59e0b;
  background: #fef3c7;
  color: #92400e;
}

/* ===== Responsive — Mobile ===== */
@media (max-width: 768px) {
  .chat-bubble { max-width: 92%; }

  .bubble-body {
    padding: 10px 14px;
    font-size: 13px;
    border-radius: 14px;
  }
  .bubble-avatar {
    width: 26px; height: 26px;
    font-size: 12px;
  }

  .msg-meta { margin-top: 4px; padding: 0 2px; }
  .copy-trigger { font-size: 11px; padding: 4px 8px; }

  .exam-paper { margin-top: 8px; border-radius: 8px; }
  .exam-header { padding: 8px 12px; }
  .exam-title { font-size: 12px; }
  .exam-submit { font-size: 11px; padding: 4px 12px; }
  .exam-question { padding: 10px 12px; }
  .eq-body { font-size: 13px; }
  .eq-opt { padding: 6px 10px; gap: 6px; }
  .eo-letter { width: 22px; height: 22px; font-size: 11px; }
  .eo-text { font-size: 12px; }
  .eq-textarea textarea { font-size: 12px; padding: 8px 10px; }
}
</style>
