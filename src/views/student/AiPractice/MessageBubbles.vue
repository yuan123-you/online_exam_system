<!-- Shared message bubble rendering for both chat and practice sessions -->
<template>
  <div
    v-for="(msg, idx) in messages"
    :key="idx"
    v-memo="[
      msg.content,
      msg.reasoning,
      msg.duration,
      msg.role,
      idx === messages.length - 1 && streamingActive,
      idx === messages.length - 1 && !!streamingReasoning,
      idx === messages.length - 1 && !!streamingContent,
      idx === messages.length - 1 && !!streamingHint,
      idx === messages.length - 1 && !!typewriterContent,
      idx === messages.length - 1 && !!feedbackVisible,
      idx === messages.length - 1 && !!feedbackMessage,
      submitted[idx],
      copiedIdx === idx,
      JSON.stringify(answers[idx] || {}),
      JSON.stringify(textAnswers[idx] || {}),
      JSON.stringify(results[idx] || {}),
      JSON.stringify(explanationsOpen[idx] || {}),
    ]"
    :class="['chat-bubble', msg.role === 'user' ? 'bubble-user' : 'bubble-ai']"
  >
    <div v-if="msg.role === 'assistant'" class="bubble-avatar">🤖</div>
    <div class="bubble-col">
      <div class="bubble-body">
        <!-- Loading dots before first token -->
        <div
          v-if="msg.role === 'assistant' && isLast(idx) && streamingActive && !streamingContent && !streamingReasoning && !streamingHint"
          class="typing-dots"
        ><span></span><span></span><span></span></div>

        <!-- Pre-output / In-output Feedback message -->
        <div
          v-if="msg.role === 'assistant' && isLast(idx) && feedbackVisible && feedbackMessage"
          class="feedback-message"
          :class="{ 'feedback-pre': !streamingContent, 'feedback-in': !!streamingContent }"
        >
          <span class="feedback-text">{{ feedbackMessage }}</span>
        </div>

        <!-- Reasoning (deep thinking) block — during streaming: open -->
        <div
          v-if="msg.role === 'assistant' && isLast(idx) && streamingActive && streamingReasoning"
          class="reasoning-block"
        >
          <details open>
            <summary>💭 深度思考中... <span class="live-timer-wrapper"><LiveTimer :start-time="currentStreamStartTime" :active="true" /></span></summary>
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

        <!-- Streaming content: typewriter effect for chat, generating hint for practice -->
        <div
          v-if="msg.role === 'assistant' && isLast(idx) && streamingActive && (streamingContent || streamingHint)"
          :ref="(el: any) => setStreamingEl(idx, el as HTMLElement | null)"
          class="message-content streaming"
        ></div>
        <!-- Stable content: rendered once after streaming completes -->
        <div
          v-else-if="msg.content"
          class="message-content"
          :class="{
            'fade-in': msg.role === 'assistant' && isNewlyCompleted(idx)
          }"
          v-html="renderContent(msg.content, msg.role, idx)"
        ></div>
      </div>

      <!-- Duration + Copy row (AI only) — show during and after streaming -->
      <div v-if="msg.role === 'assistant' && msg.content" class="msg-meta">
        <!-- Live timer during streaming -->
        <span v-if="isLast(idx) && streamingActive" class="msg-duration live-duration">
          ⏱ <LiveTimer :start-time="currentStreamStartTime" :active="true" />
        </span>
        <!-- Final duration after streaming -->
        <span v-else-if="msg.duration != null" class="msg-duration">⏱ {{ formatDuration(msg.duration) }}</span>
        <div class="msg-actions" v-if="!(isLast(idx) && streamingActive)">
          <!-- Regenerate button — shows for all AI messages, with refresh icon -->
          <button
            class="action-icon-btn regenerate-btn"
            @click="regenerateMessage(idx)"
            title="重新生成"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 2v6h-6M3 12a9 9 0 0 1 15-6.7L21 8M3 22v-6h6M21 12a9 9 0 0 1-15 6.7L3 16"/>
            </svg>
          </button>
          <!-- Copy button -->
          <button class="action-icon-btn copy-icon-btn" @click="copyPlain(msg.content, idx)" title="复制">
            <svg v-if="copiedIdx !== idx" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- Copy row for user messages -->
      <div v-if="msg.role === 'user' && msg.content" class="msg-meta msg-meta-user">
        <button class="copy-btn" @click="copyPlain(msg.content, idx)">📋 复制</button>
      </div>

      <!-- Parsed practice question cards — exam paper style (practice tab only) -->
      <div
        v-if="tab === 'practice' && msg.role === 'assistant' && msg.content && parsedQuestions(idx).length > 0"
        class="exam-paper"
      >
        <div class="exam-header">
          <span class="exam-title">📋 练习题（共 {{ parsedQuestions(idx).length }} 题）</span>
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
              @input="autoSaveAnswers(idx)"
            ></textarea>
          </div>

          <!-- Result after submission -->
          <div
            v-if="submitted[idx] && results[idx]?.[qi] !== undefined"
            :class="['eq-result', results[idx][qi] ? 'correct' : 'wrong']"
          >
            <span v-if="results[idx][qi]">✅ 回答正确</span>
            <span v-else>❌ 回答错误，正确答案：{{ formatAnswer(q.answer || [], q.type || 'single') }}</span>
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

        <!-- Footer: submit button / score at the bottom -->
        <div class="exam-footer">
          <button v-if="!submitted[idx]" class="exam-submit" @click="submitAnswers(idx)">
            📝 提交答案
          </button>
          <div v-else class="exam-score-summary">
            <span class="ess-badge" :class="{ pass: scorePercent(idx) >= 60, fail: scorePercent(idx) < 60 }">
              {{ scorePercent(idx) >= 60 ? '✅' : '❌' }} {{ scoreSummary(idx) }}
            </span>
          </div>
        </div>
      </div>
    </div>
    <div v-if="msg.role === 'user'" class="bubble-avatar user-avatar">👤</div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch, nextTick, computed, onBeforeUnmount } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import type { AiQuestion } from '@/api/client'
import type { PracticeSession } from '@/api/client'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import { savePracticeRecords } from '@/api/client'
import LiveTimer from './LiveTimer.vue'

const props = defineProps<{
  messages: Array<{ role: string; content: string; reasoning?: string; duration?: number; _retryMessage?: string }>
  streamingActive: boolean
  streamingReasoning: string
  streamingContent?: string
  streamingHint?: string
  typewriterContent?: string
  feedbackMessage?: string
  feedbackVisible?: boolean
  streamingQuestions?: Array<Record<string, unknown>>
  loading: boolean
  tab?: 'chat' | 'practice'
}>()

const emit = defineEmits<{
  (e: 'regenerate', msgIndex: number): void
}>()

const store = useAppStore()

const copiedIdx = ref<number | null>(null)

// ===== Streaming content renderer — RAF-batched, flicker-free =====
// Use a Map keyed by message index to support v-for streaming elements
const streamingContentEls = new Map<number, HTMLElement>()
let streamingRafId = 0
let lastStreamingHtml = ''

/** Render content specifically for streaming display */
function renderStreamingContent(text: string): string {
  if (!text) return ''
  const isPractice = props.tab === 'practice'
  if (isPractice) {
    // Practice mode: show question-by-question cards during streaming
    const sq = props.streamingQuestions
    if (sq && sq.length > 0) {
      let html = '<div class="streaming-questions">'
      html += `<div class="sq-header">📝 已生成 ${sq.length} 道题，继续生成中...</div>`
      for (let i = 0; i < sq.length; i++) {
        const q = sq[i]
        const typeStr = (q.type as string) || 'single'
        const title = (q.title as string) || ''
        const options = (q.options as string[]) || []
        const isNew = (q as any)._isNew
        html += `<div class="sq-card${isNew ? ' sq-new' : ''}">`
        html += `<div class="sq-card-head"><span class="sq-num">${i + 1}.</span><span class="sq-type">${typeLabel(typeStr as any)}</span></div>`
        html += `<div class="sq-card-title">${escapeHtml(title)}</div>`
        if (options.length > 0) {
          html += '<div class="sq-options">'
          for (let oi = 0; oi < options.length; oi++) {
            const optText = String(options[oi]).replace(/^[A-D][.、\s]+/, '')
            const letter = ['A','B','C','D','E','F'][oi] || oi
            html += `<div class="sq-opt"><span class="sq-opt-letter">${letter}</span><span class="sq-opt-text">${escapeHtml(optText)}</span></div>`
          }
          html += '</div>'
        }
        html += '</div>'
      }
      html += '</div>'
      return html
    }
    return '<div class="generating-hint">📝 正在生成题目<span class="gen-dots">...</span></div>'
  }
  // Chat mode: use typewriter content for character-by-character display
  const tw = props.typewriterContent
  if (tw) {
    return renderMarkdown(tw)
  }
  return renderMarkdown(text)
}

/** Update the streaming container DOM — called via RAF batching */
function updateStreamingDOM() {
  // Find the last message index (the one being streamed)
  const lastIdx = props.messages.length - 1
  if (lastIdx < 0) return
  const el = streamingContentEls.get(lastIdx)
  if (!el) return
  const content = props.streamingContent || props.streamingHint || ''
  if (!content) {
    if (el.innerHTML) el.innerHTML = ''
    lastStreamingHtml = ''
    return
  }
  const html = renderStreamingContent(content)
  if (html !== lastStreamingHtml) {
    el.innerHTML = html
    lastStreamingHtml = html
  }
}

/** Schedule a streaming DOM update on the next animation frame */
function scheduleStreamingUpdate() {
  if (streamingRafId) return
  streamingRafId = requestAnimationFrame(() => {
    streamingRafId = 0
    updateStreamingDOM()
  })
}

/** Set the streaming element ref for a given message index */
function setStreamingEl(idx: number, el: HTMLElement | null) {
  if (el) {
    streamingContentEls.set(idx, el)
  } else {
    streamingContentEls.delete(idx)
  }
}

// Watch streaming content changes — batch via RAF
watch(() => props.streamingContent, () => {
  if (props.streamingActive) scheduleStreamingUpdate()
})
watch(() => props.streamingHint, () => {
  if (props.streamingActive) scheduleStreamingUpdate()
})
// Watch typewriter content for chat mode character-by-character display
watch(() => props.typewriterContent, () => {
  if (props.streamingActive) scheduleStreamingUpdate()
})
// Watch streaming questions for practice mode question-by-question display
watch(() => props.streamingQuestions, () => {
  if (props.streamingActive) scheduleStreamingUpdate()
}, { deep: true })

// Handle streaming state transitions
watch(() => props.streamingActive, (active, wasActive) => {
  if (active && !wasActive) {
    // New stream started — reset and schedule first update
    lastStreamingHtml = ''
    nextTick(() => updateStreamingDOM())
  }
  if (!active && wasActive) {
    // Streaming completed — cancel pending RAF, clear streaming state
    if (streamingRafId) { cancelAnimationFrame(streamingRafId); streamingRafId = 0 }
    lastStreamingHtml = ''
    streamingContentEls.clear()
  }
})

onBeforeUnmount(() => {
  if (streamingRafId) { cancelAnimationFrame(streamingRafId); streamingRafId = 0 }
  streamingContentEls.clear()
})

// ===== Stream start time from store (persists across component re-creations) =====
const currentStreamStartTime = computed(() =>
  props.tab === 'practice' ? store.practiceStreamStartTime : store.chatStreamStartTime
)

// 监听练习会话恢复，自动加载已保存的答案
watch(() => store.activePracticeSession, (session) => {
  if (session && session.status !== 'submitted') {
    nextTick(() => loadAnswersFromSession(session))
  }
}, { immediate: true })

// 当消息变化时，为新解析出的题目初始化答案状态（确保 v-model 绑定不报错）
watch(() => props.messages.length, () => {
  nextTick(() => {
    for (let i = 0; i < props.messages.length; i++) {
      const msg = props.messages[i]
      if (msg.role !== 'assistant' || !msg.content) continue
      const qs = parsedQuestions(i)
      if (qs.length === 0) continue
      for (let qi = 0; qi < qs.length; qi++) {
        ensure(i, qi)
      }
    }
  })
}, { immediate: true })

// ===== Newly completed message tracking (for fade-in effect) =====
const newlyCompletedSet = ref(new Set<number>())

watch(() => props.streamingActive, (active, wasActive) => {
  if (!active && wasActive && props.messages.length > 0) {
    const lastIdx = props.messages.length - 1
    newlyCompletedSet.value.add(lastIdx)
    // Remove fade-in class after animation completes
    setTimeout(() => {
      newlyCompletedSet.value.delete(lastIdx)
    }, 600)
  }
})

function isNewlyCompleted(idx: number): boolean {
  return newlyCompletedSet.value.has(idx)
}

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

/** Regenerate any AI message — emits event for parent to handle */
function regenerateMessage(idx: number) {
  emit('regenerate', idx)
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

// ---- Copy Plain Text (strip markdown + AI thinking) ----
async function copyPlain(text: string, idx: number) {
  // 安全网：剥离 AI 思考文本（正常情况下已在 store 中处理）
  const cleaned = stripThinkingForCopy(text)
  const plain = cleaned
    .replace(/```[\s\S]*?```/g, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/\n{2,}/g, '\n\n')
    .trim()
  await copyToClipboard(plain, idx)
}

/** 复制时剥离 AI 思考/推理文本 */
function stripThinkingForCopy(text: string): string {
  if (!text || text.length < 30) return text || ''

  // 策略1：JSON 数组前有思考文本
  const arrIdx = text.search(/\[\s*\{/)
  if (arrIdx > 0) {
    const before = text.substring(0, arrIdx).trim()
    if (isThinkingText(before)) return text.substring(arrIdx)
  }

  // 策略2：JSON 对象前有思考文本
  const objIdx = text.search(/\{\s*"/)
  if (objIdx > 0) {
    const before = text.substring(0, objIdx).trim()
    if (isThinkingText(before)) return text.substring(objIdx)
  }

  // 策略3：代码块前有思考文本
  const codeIdx = text.search(/```(?:json)?\s*\n/)
  if (codeIdx > 0) {
    const before = text.substring(0, codeIdx).trim()
    if (isThinkingText(before)) return text.substring(codeIdx)
  }

  return text
}

/** 检测文本是否为 AI 思考输出 */
function isThinkingText(text: string): boolean {
  if (!text || text.length < 20) return false
  const patterns = [
    /分析/, /让我/, /好的[，，]?\s*我/, /我将/, /我们需要/,
    /检查/, /起草/, /构建/, /组装/, /最终/, /自检/,
    /步骤/, /考虑/, /优化/, /重读/, /尝试/, /看看/,
    /约束/, /格式.*要求/, /示例.*输出/, /题\s*\d/,
    /角色/, /任务/, /内容.*涵盖/, /严格遵循/,
  ]
  let matches = 0
  for (const p of patterns) { if (p.test(text)) matches++ }
  return matches >= 2
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

// ---- Question parsing (with content-hash memoization) ----
const _parseCache = new Map<string, AiQuestion[]>()
let _lastCacheClear = 0

/** Extract questions from AI response — text format first, JSON as fallback */
function parsedQuestions(msgIdx: number): AiQuestion[] {
  const msg = props.messages[msgIdx]
  if (!msg || msg.role !== 'assistant') return []
  const content = msg.content
  if (!content) return []

  // Content hash cache
  const cacheKey = content.slice(0, 80) + '|' + content.length
  const cached = _parseCache.get(cacheKey)
  if (cached !== undefined) return cached

  const now = Date.now()
  if (now - _lastCacheClear > 30000) { if (_parseCache.size > 50) _parseCache.clear(); _lastCacheClear = now }

  let result: AiQuestion[] = []

  // Strategy 1: Parse text format (===题目开始=== ... ===题目结束===)
  result = parseTextFormat(content)
  if (result.length > 0) { result = normalizeParsedQuestions(result); _parseCache.set(cacheKey, result); return result }

  // Strategy 2: Parse JSON (fallback for legacy format or if text format not used)
  result = parseJsonFormat(content)
  if (result.length > 0) { result = normalizeParsedQuestions(result); _parseCache.set(cacheKey, result); return result }

  // Strategy 3: Parse Markdown format (### 第N题 / **[题型]** / A. 选项 / **答案：**)
  result = parseMarkdownFormat(content)
  if (result.length > 0) { result = normalizeParsedQuestions(result); _parseCache.set(cacheKey, result); return result }

  // Strategy 4: Regex extraction
  result = extractQuestionsByRegex(content)
  if (result.length > 0) { result = normalizeParsedQuestions(result); _parseCache.set(cacheKey, result); return result }

  _parseCache.set(cacheKey, [])
  return []
}

/** Normalize parsed questions: clean answer field to only contain valid letters for choice/judge */
function normalizeParsedQuestions(questions: AiQuestion[]): AiQuestion[] {
  return questions.map(q => ({
    ...q,
    answer: normalizeAnswers(q.answer || [], q.type || 'single'),
  }))
}

/** Enforce option count: single/multiple -> 4, judge -> 2 */
function capOptions(q: Partial<AiQuestion>) {
  if (!q.options) return
  if (q.type === 'judge' && q.options.length > 2) {
    q.options = q.options.slice(0, 2)
  } else if (q.options.length > 4 && (q.type === 'single' || q.type === 'multiple')) {
    q.options = q.options.slice(0, 4)
  }
}

/** Parse the text-based question format */
function parseTextFormat(content: string): AiQuestion[] {
  const questions: AiQuestion[] = []
  // Split by ===题目开始=== ... ===题目结束===
  const blocks = content.split(/===题目开始===/g).filter(b => b.trim())
  for (const block of blocks) {
    const endIdx = block.indexOf('===题目结束===')
    const body = (endIdx >= 0 ? block.substring(0, endIdx) : block).trim()
    if (!body) continue

    const q: Partial<AiQuestion> = { score: 5, subject: 'AI练习', difficulty: '中等' }
    const lines = body.split('\n')

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim()
      if (!line) continue

      if (line.startsWith('题型：') || line.startsWith('题型:')) {
        const t = line.replace(/^题型[：:]\s*/, '')
        if (t.includes('单选')) q.type = 'single'
        else if (t.includes('多选')) q.type = 'multiple'
        else if (t.includes('判断')) q.type = 'judge'
        else if (t.includes('填空')) q.type = 'fill'
        else if (t.includes('简答')) q.type = 'short'
        else if (t.includes('编程')) q.type = 'coding'
        else q.type = 'single'
      } else if (line.startsWith('题目：') || line.startsWith('题目:')) {
        q.title = line.replace(/^题目[：:]\s*/, '')
      } else if (/^[A-D][.、)\s]/.test(line)) {
        // Option line: "A. xxx" or "A、xxx"
        if (!q.options) q.options = []
        q.options.push(line)
      } else if (line.startsWith('答案：') || line.startsWith('答案:')) {
        const ans = line.replace(/^答案[：:]\s*/, '').trim()
        q.answer = ans.split(/[,，\s]+/).filter(Boolean)
      } else if (line.startsWith('分数：') || line.startsWith('分数:')) {
        q.score = parseInt(line.replace(/^分数[：:]\s*/, '')) || 5
      } else if (line.startsWith('解析：') || line.startsWith('解析:')) {
        // Collect all remaining lines as explanation
        const explLines = [line.replace(/^解析[：:]\s*/, '')]
        for (let j = i + 1; j < lines.length; j++) {
          const nl = lines[j].trim()
          if (nl.startsWith('题型：') || nl.startsWith('题目：') || /^[A-D][.、)\s]/.test(nl) || nl.startsWith('答案：')) break
          if (nl) explLines.push(nl)
        }
        q.explanation = explLines.join('\n')
        break
      }
    }

    if (q.title && q.type) {
      q.id = `q-${questions.length}-${Date.now()}`
      q.knowledgePoint = (q.title || '').substring(0, 30)
      if (!q.options) q.options = []
      if (!q.answer) q.answer = []
      capOptions(q)
      questions.push(q as AiQuestion)
    }
  }
  return questions
}

/** Parse Markdown format: ### 第N题 / **[题型]** / A. 选项 / **答案：** / **解析：** */
function parseMarkdownFormat(content: string): AiQuestion[] {
  const questions: AiQuestion[] = []
  // Split by question headers: "### 第X题", "## 第X题", "### 1.", "### 1、", "### 题目1"
  const blocks = content.split(/\n(?:#{2,3}\s*(?:第\s*\d+\s*题|[0-9]+[.、)\s]|[一二三四五六七八九十]+[、.]\s*|题目\s*\d+))/g)
  // The split removes the headers; re-find them via regex to attach to blocks
  const headerRegex = /(?:#{2,3}\s*(?:第\s*(\d+)\s*题|([0-9]+)[.、)\s]|([一二三四五六七八九十]+)[、.]\s*|题目\s*(\d+)))/g
  const headers: { index: number; num: number }[] = []
  let hm: RegExpExecArray | null
  while ((hm = headerRegex.exec(content)) !== null) {
    const numRaw = hm[1] || hm[2] || (hm[3] ? String('一二三四五六七八九十'.indexOf(hm[3]) + 1) : '1') || hm[4] || '1'
    const num = parseInt(numRaw)
    headers.push({ index: hm.index, num: isNaN(num) ? 1 : num })
  }
  if (headers.length === 0) return []

  for (let hi = 0; hi < headers.length; hi++) {
    const start = headers[hi].index
    const end = hi + 1 < headers.length ? headers[hi + 1].index : content.length
    const block = content.substring(start, end)
    if (!block.trim()) continue

    const q: Partial<AiQuestion> = { score: 5, subject: 'AI练习', difficulty: 'medium', options: [], answer: [] }
    const lines = block.split('\n')

    let inExplanation = false
    let explanationLines: string[] = []

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim()
      if (!line) continue

      // Skip the header line itself
      if (/^#{2,3}\s/.test(line)) continue

      // Type detection: **[单选题]** or **[题型]** or [单选题]
      const typeMatch = line.match(/\*?\*?\[?\s*(单选题|多选题|判断题|填空题|简答题|编程题|单选|多选|判断|填空|简答|编程)\s*\]?\*?\*?/)
      if (typeMatch && !q.type) {
        const t = typeMatch[1]
        if (t.includes('单选')) q.type = 'single'
        else if (t.includes('多选')) q.type = 'multiple'
        else if (t.includes('判断')) q.type = 'judge'
        else if (t.includes('填空')) q.type = 'fill'
        else if (t.includes('简答')) q.type = 'short'
        else if (t.includes('编程')) q.type = 'coding'
        else q.type = 'single'
      }

      // Answer line: **答案：** X or **答案:** X or 答案：X
      if (/^\*{0,2}\s*答案\s*[：:]\s*\*{0,2}/.test(line)) {
        const ans = line.replace(/^\*{0,2}\s*答案\s*[：:]\s*\*{0,2}/, '').replace(/\*{0,2}$/, '').trim()
        q.answer = ans.split(/[,，\s]+/).filter(Boolean)
        continue
      }

      // Explanation line: **解析：** ... or **解析:** ...
      if (/^\*{0,2}\s*解析\s*[：:]/.test(line)) {
        inExplanation = true
        const expl = line.replace(/^\*{0,2}\s*解析\s*[：:]\s*\*{0,2}/, '').trim()
        if (expl) explanationLines.push(expl)
        continue
      }

      // Score line: **分数：** 5
      if (/^\*{0,2}\s*分数\s*[：:]/.test(line)) {
        const sc = line.replace(/^\*{0,2}\s*分数\s*[：:]\s*/, '').replace(/\D/g, '')
        q.score = parseInt(sc) || 5
        continue
      }

      // Option line: A. xxx / A、xxx / A) xxx / A xxx
      if (/^[A-D][.、)\s]/.test(line)) {
        if (!q.options) q.options = []
        q.options.push(line)
        continue
      }

      // If we're in explanation mode, collect all lines
      if (inExplanation) {
        // Stop if we hit a separator or new question marker
        if (/^---+$/.test(line) || /^#{2,3}/.test(line)) {
          inExplanation = false
          continue
        }
        explanationLines.push(line)
        continue
      }

      // Title: first non-empty, non-header, non-option, non-meta line
      if (!q.title && !/^[A-D][.、)\s]/.test(line) && !/^\*{0,2}\s*(答案|解析|分数|题型|难度)/.test(line) && !/^---+$/.test(line)) {
        // Strip leading type marker like **[单选题]** from title
        let title = line.replace(/\*?\*?\[?\s*(?:单选题|多选题|判断题|填空题|简答题|编程题|单选|多选|判断|填空|简答|编程)\s*\]?\*?\*?/, '').trim()
        // Strip leading ** markers
        title = title.replace(/^\*+/, '').replace(/\*+$/, '').trim()
        if (title) q.title = title
      }
    }

    if (q.title && q.type) {
      q.id = `q-${questions.length}-${Date.now()}`
      q.knowledgePoint = (q.title || '').substring(0, 30)
      if (q.explanation === undefined) q.explanation = ''
      if (explanationLines.length > 0) q.explanation = explanationLines.join('\n')
      if (!q.options) q.options = []
      if (!q.answer) q.answer = []
      capOptions(q)
      questions.push(q as AiQuestion)
    }
  }
  return questions
}

/**
 * Find the start index of a JSON array that looks like a question list.
 * Scans all '[' positions and returns the first one whose following ~300 chars
 * contain question-like fields ("title", "type", "options", "answer", "explanation").
 * Returns -1 if no suitable array start is found.
 */
function findQuestionArrayStart(content: string): number {
  let searchFrom = 0
  while (true) {
    const idx = content.indexOf('[', searchFrom)
    if (idx === -1) break
    // Peek ahead ~300 chars to check for question-like fields
    const peek = content.substring(idx, Math.min(idx + 300, content.length))
    if (/"(?:title|type|options|answer|explanation|subject|score)"\s*:/.test(peek)) {
      return idx
    }
    searchFrom = idx + 1
  }
  return -1
}

/** Parse JSON format (fallback) */
function parseJsonFormat(content: string): AiQuestion[] {
  let jsonStr = ''
  const fenceMatch = content.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/)
  if (fenceMatch) { jsonStr = fenceMatch[1].trim() }

  if (!jsonStr) {
    // Smart search: find the '[' that starts a question-like JSON array.
    // Instead of using the first '[' (which may belong to thinking text like
    // "让我分析一下[要求]"), scan all '[' positions and pick the one whose
    // following content contains question fields ("title", "type", "options"...).
    const startIdx = findQuestionArrayStart(content)
    if (startIdx === -1) return []
    let depth = 0, inString = false, escaped = false
    for (let i = startIdx; i < content.length; i++) {
      const ch = content[i]
      if (escaped) { escaped = false; continue }
      if (ch === '\\') { escaped = true; continue }
      if (ch === '"') { inString = !inString; continue }
      if (inString) continue
      if (ch === '[') depth++
      else if (ch === ']') { depth--; if (depth === 0) { jsonStr = content.substring(startIdx, i + 1); break } }
    }
  }
  if (!jsonStr) return []
  const parsed = tryParseJson(jsonStr)
  // Support both array and single object
  const parsedArr = Array.isArray(parsed) ? parsed : (parsed && typeof parsed === 'object' && (parsed as any).title ? [parsed] : [])
  if (parsedArr.length > 0) {
    // Filter out entries with blank / placeholder titles
    const filtered = parsedArr.filter((q: any) => q.title && String(q.title).trim())
    if (filtered.length > 0) {
      const capped = filtered.map((q: any) => {
        // Cap options
        let opts = q.options || []
        if (q.type === 'judge' && opts.length > 2) {
          opts = opts.slice(0, 2)
        } else if (opts.length > 4 && (q.type === 'single' || q.type === 'multiple')) {
          opts = opts.slice(0, 4)
        }
        // Normalize answer for choice/judge types
        let answer = q.answer || []
        if (q.type === 'single' || q.type === 'multiple' || q.type === 'judge') {
          const cleanAnswer: string[] = []
          for (const a of answer) {
            const m = String(a).match(/([A-D])/)
            if (m) cleanAnswer.push(m[1])
          }
          if (cleanAnswer.length > 0) answer = cleanAnswer
        }
        return { ...q, options: opts, answer }
      })
      return capped as AiQuestion[]
    }
  }
  return []
}

/** Last-resort: regex-based extraction of individual question objects from malformed text */
function extractQuestionsByRegex(text: string): AiQuestion[] {
  const questions: AiQuestion[] = []
  // Extract individual JSON objects `{...}` that contain a "title" field.
  // This is field-order agnostic, so it handles JSON where "subject" precedes
  // "title" or fields are in any order.
  const objRegex = /\{[^{}]*?"title"\s*:\s*"[^"]*(?:\\.[^"]*)*"[^{}]*?\}/gs
  let match
  while ((match = objRegex.exec(text)) !== null) {
    try {
      const objStr = match[0]
      // Try to parse the individual object
      const parsed = tryParseJson('[' + objStr + ']')
      if (Array.isArray(parsed) && parsed.length > 0) {
        const q = parsed[0] as any
        if (q.title && String(q.title).trim()) {
          const title = String(q.title)
          const type = (q.type || 'single') as string
          let options: string[] = q.options || []
          let answer: string[] = q.answer || []
          const score = q.score || 5
          const explanation = q.explanation || ''

          if (type === 'judge' && options.length > 2) {
            options = options.slice(0, 2)
          } else if (options.length > 4 && (type === 'single' || type === 'multiple')) {
            options = options.slice(0, 4)
          }
          // Normalize answer for choice/judge
          if (type === 'single' || type === 'multiple' || type === 'judge') {
            const cleanAnswer: string[] = []
            for (const a of answer) {
              const m = String(a).match(/([A-D])/)
              if (m) cleanAnswer.push(m[1])
            }
            if (cleanAnswer.length > 0) answer = cleanAnswer
          }
          questions.push({
            id: `q-${questions.length}-${Date.now()}`,
            title, type: type as AiQuestion['type'],
            options, answer,
            score, explanation,
            subject: q.subject || 'AI练习',
            knowledgePoint: title.substring(0, 30),
            difficulty: q.difficulty || 'medium',
          })
        }
      }
    } catch { /* skip malformed block */ }
  }
  return questions
}

/** Parse JSON with recovery: trailing commas, unescaped newlines, smart quotes */
function tryParseJson(raw: string): unknown {
  // Attempt 1: direct parse
  try { return JSON.parse(raw) } catch { /* continue */ }

  // Attempt 2: remove trailing commas before ] or }
  try {
    const cleaned = raw.replace(/,\s*([}\]])/g, '$1')
    return JSON.parse(cleaned)
  } catch { /* continue */ }

  // Attempt 3: fix common AI output issues (smart quotes, Chinese commas)
  try {
    const fixed = raw
      .replace(/[\u201C\u201D]/g, '"')
      .replace(/[\u2018\u2019]/g, "'")
      .replace(/，/g, ',')
    return JSON.parse(fixed)
  } catch { /* continue */ }

  // Attempt 4: escape literal newlines inside JSON strings
  try {
    let escaped = ''
    let inString = false
    let isEscaped = false
    for (let i = 0; i < raw.length; i++) {
      const ch = raw[i]
      if (isEscaped) { escaped += ch; isEscaped = false; continue }
      if (ch === '\\') { escaped += ch; isEscaped = true; continue }
      if (ch === '"') { inString = !inString; escaped += ch; continue }
      if (inString && ch === '\n') { escaped += '\\n'; continue }
      if (inString && ch === '\r') { continue }
      if (inString && ch === '\t') { escaped += '\\t'; continue }
      escaped += ch
    }
    const cleaned = escaped.replace(/,\s*([}\]])/g, '$1')
    return JSON.parse(cleaned)
  } catch { /* continue */ }

  // Attempt 5: fix missing opening braces before each question object
  try {
    let fixed = raw
    // Each question object must start with {"title":
    // Fix patterns like: "title": → {"title":
    fixed = fixed.replace(/(?<!\\)"title"\s*:/g, '{"title":')
    // Fix missing [ before first option value (e.g., "options":A. → "options":["A.)
    fixed = fixed.replace(/"options"\s*:\s*([A-D]\.[^",\]\}]+)/g, '"options":["$1"]')
    // Fix unquoted option strings in arrays: ,B. xxx → ,"B. xxx"
    fixed = fixed.replace(/\[\s*([A-D]\.[^",\]]+?)(\s*,|\s*\])/g, '["$1"$2')
    fixed = fixed.replace(/,\s*([A-D]\.[^",\]]+?)(\s*,|\s*\])/g, ',"$1"$2')
    // Fix missing commas between } and { (squashed objects)
    fixed = fixed.replace(/\}\s*\{/g, '},{')
    // Wrap in array if not already
    if (!fixed.trim().startsWith('[')) fixed = '[' + fixed + ']'
    if (!fixed.trim().endsWith(']')) fixed = fixed.trim() + ']'
    // Normalize trailing commas
    fixed = fixed.replace(/,\s*([}\]])/g, '$1')
    return JSON.parse(fixed)
  } catch { /* continue */ }

  return null
}

function extractKey(opt: string): string {
  const m = opt.match(/^([A-D])(?:\.\s*|$)/)
  return m ? m[1] : opt
}

/** Normalize answer array: extract only valid answer letters for choice/judge, keep text for fill/short */
function normalizeAnswers(answer: string[], type: string): string[] {
  if (type === 'fill' || type === 'short' || type === 'coding') {
    return answer.filter(a => a && a.trim())
  }
  // For choice/judge: extract letter from each answer item
  const letters: string[] = []
  for (const a of answer) {
    const m = String(a).match(/([A-D])/)
    if (m) letters.push(m[1])
  }
  return letters
}

/** Format answer for display: clean letter(s) for choice, raw text for fill/short */
function formatAnswer(answer: string[], type: string): string {
  const norm = normalizeAnswers(answer, type)
  return norm.join('，')
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
  autoSaveAnswers(msgIdx)
}

function toggleMulti(msgIdx: number, qi: number, key: string) {
  ensure(msgIdx, qi)
  const arr = answers[msgIdx][qi]
  const pos = arr.indexOf(key)
  if (pos >= 0) arr.splice(pos, 1)
  else arr.push(key)
  autoSaveAnswers(msgIdx)
}

function optionClass(msgIdx: number, qi: number, opt: string): string {
  const key = extractKey(opt)
  const sel = answers[msgIdx]?.[qi]?.includes(key)
  if (!submitted[msgIdx]) return sel ? 'selected' : ''
  const q = parsedQuestions(msgIdx)[qi]
  if (!q) return ''
  const normAnswer = normalizeAnswers(q.answer || [], q.type || 'single')
  const correct = normAnswer.includes(key)
  if (correct) return 'correct'
  if (sel && !correct) return 'wrong'
  return ''
}

/** 自动保存当前消息的所有答案到练习会话（防抖） */
function autoSaveAnswers(msgIdx: number) {
  const session = store.activePracticeSession
  if (!session || session.status !== 'active') return

  const qs = parsedQuestions(msgIdx)
  const answerEntries: Array<{ questionIndex: number; answer: string[] | string }> = []

  for (let qi = 0; qi < qs.length; qi++) {
    const q = qs[qi]
    if (q.type === 'fill' || q.type === 'short' || q.type === 'coding') {
      const textVal = textAnswers[msgIdx]?.[qi]
      if (textVal) {
        answerEntries.push({ questionIndex: qi, answer: textVal })
      }
    } else {
      const sel = answers[msgIdx]?.[qi]
      if (sel && sel.length > 0) {
        answerEntries.push({ questionIndex: qi, answer: sel })
      }
    }
  }

  if (answerEntries.length > 0) {
    store.scheduleAnswerSave(session.id, answerEntries)
  }
}

/** 从恢复的练习会话中加载已保存的答案和提交状态 */
function loadAnswersFromSession(session: PracticeSession | null) {
  if (!session || !session.questions) return

  // 找到包含题目的 AI 消息索引
  for (let msgIdx = 0; msgIdx < props.messages.length; msgIdx++) {
    const msg = props.messages[msgIdx]
    if (msg.role !== 'assistant') continue
    const qs = parsedQuestions(msgIdx)
    if (qs.length === 0) continue

    // 检查题目数量是否匹配
    if (qs.length !== session.questions.length) continue

    // 加载每道题的答案和状态
    for (let qi = 0; qi < session.questions.length; qi++) {
      const savedQ = session.questions[qi]
      ensure(msgIdx, qi)

      // 恢复用户答案
      if (savedQ.userAnswer && savedQ.userAnswer.length > 0) {
        const q = qs[qi]
        if (q.type === 'fill' || q.type === 'short' || q.type === 'coding') {
          textAnswers[msgIdx][qi] = savedQ.userAnswer.join(', ')
        } else {
          answers[msgIdx][qi] = savedQ.userAnswer
        }
      }

      // 恢复提交状态
      if (savedQ.isSubmitted) {
        submitted[msgIdx] = true
        if (!results[msgIdx]) results[msgIdx] = {}
        results[msgIdx][qi] = savedQ.isCorrect ?? false
      }
    }
    break // 只处理第一个匹配的消息
  }
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

function scorePercent(msgIdx: number): number {
  if (!results[msgIdx]) return 0
  const qs = parsedQuestions(msgIdx)
  let totalScore = 0; let earned = 0
  for (let qi = 0; qi < qs.length; qi++) {
    totalScore += (qs[qi].score || 5)
    if (results[msgIdx][qi]) earned += (qs[qi].score || 5)
  }
  return totalScore > 0 ? Math.round((earned / totalScore) * 100) : 0
}

async function submitAnswers(msgIdx: number) {
  const qs = parsedQuestions(msgIdx)
  submitted[msgIdx] = true
  if (!results[msgIdx]) results[msgIdx] = {}

  const records: Array<Record<string, unknown>> = []
  const now = new Date().toISOString()

  for (let qi = 0; qi < qs.length; qi++) {
    const q = qs[qi]
    let student: string[]
    if (q.type === 'fill' || q.type === 'short' || q.type === 'coding') {
      student = (textAnswers[msgIdx]?.[qi] || '').split(',').map(s => s.trim()).filter(Boolean)
    } else {
      student = (answers[msgIdx]?.[qi] || []).sort()
    }
    const correct = normalizeAnswers(q.answer || [], q.type || 'single').sort()
    const isCorrect = JSON.stringify(student.sort()) === JSON.stringify(correct.sort())
    results[msgIdx][qi] = isCorrect

    // Build practice record with unique ID, timestamp, and question annotation
    records.push({
      questionId: q.id || `practice-${msgIdx}-${qi}`,
      subject: q.subject || 'AI练习',
      knowledgePoint: q.knowledgePoint || (q.title || '').substring(0, 30),
      type: q.type || 'single',
      title: q.title || '',
      answer: student,
      expectedAnswer: q.answer || [],
      fullScore: q.score || 5,
      lastScore: isCorrect ? (q.score || 5) : 0,
      lastRetryCorrect: isCorrect,
      wrongCount: isCorrect ? 0 : 1,
      lastWrongAt: now,
    })
  }

  // Auto-expand explanations for all questions after submission
  if (!explanationsOpen[msgIdx]) explanationsOpen[msgIdx] = {}
  for (let qi = 0; qi < qs.length; qi++) {
    explanationsOpen[msgIdx][qi] = true
  }

  const cc = Object.values(results[msgIdx]).filter(Boolean).length

  // Auto-save to practice records (async, non-blocking)
  savePracticeRecordsSilent(records)

  // 同时通过练习会话 API 持久化提交
  const session = store.activePracticeSession
  if (session && session.status === 'active') {
    const answerEntries: Array<{ questionIndex: number; answer: string[] | string }> = []
    for (let qi = 0; qi < qs.length; qi++) {
      const q = qs[qi]
      if (q.type === 'fill' || q.type === 'short' || q.type === 'coding') {
        const textVal = textAnswers[msgIdx]?.[qi] || ''
        answerEntries.push({ questionIndex: qi, answer: textVal })
      } else {
        answerEntries.push({ questionIndex: qi, answer: (answers[msgIdx]?.[qi] || []).sort() })
      }
    }
    store.handleSubmitPracticeSession(session.id, answerEntries, records).catch(() => {})
  }

  store.showToast(`练习完成！正确 ${cc}/${qs.length}`, cc === qs.length ? 'success' : 'info')
}

/** Auto-save practice records without showing errors to user */
function savePracticeRecordsSilent(records: Array<Record<string, unknown>>) {
  savePracticeRecords(records).catch(() => {})
}

// ---- Markdown rendering ----
function renderContent(text: string, role: string, idx: number): string {
  if (role === 'user') return escapeHtml(text)

  const isPractice = props.tab === 'practice'
  const isStreaming = isLast(idx) && props.streamingActive

  // --- Chat mode: hide raw JSON question data, show friendly hint ---
  if (!isPractice) {
    // If the content looks like structured question JSON, hide it and suggest switching to practice tab
    if (/[\{\[]/.test(text) && (text.includes('"title"') || text.includes('"type"')) && (text.includes('"options"') || text.includes('"answer"'))) {
      return '<p style="color:var(--muted);">💡 如需练习题目，请切换到「📝 练题」模式</p>'
    }
    return renderMarkdown(text)
  }

  // --- Practice mode ---
  // First, check if cards were already parsed successfully (non-streaming, content present)
  if (!isStreaming) {
    const parsed = parsedQuestions(idx)
    if (parsed.length > 0) {
      return `<p style="color:var(--muted);font-style:italic;">📋 已生成 ${parsed.length} 道练习题，请在下方作答 👇</p>`
    }
    // Parsing failed — render content as markdown so user can still see what AI generated,
    // with a friendly hint that interactive cards couldn't be created.
    if (/[\{\[]/.test(text) && (text.includes('"title"') || text.includes('"type"') || text.includes('"options"'))) {
      return '<p style="color:var(--muted);">📋 题目卡片解析失败，可点击右上角「🔄」按钮重新生成：</p>' + renderMarkdown(text)
    }
    // Not JSON-like — render normally
    return renderMarkdown(text)
  }

  // During streaming: hide ALL potential JSON from view
  return '<div class="generating-hint">📝 正在生成题目<span class="gen-dots">...</span></div>'
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

/** Render LaTeX math to HTML using KaTeX */
function renderLatex(latex: string, displayMode: boolean): string {
  try {
    return katex.renderToString(latex, { displayMode, throwOnError: false, strict: false })
  } catch {
    return escapeHtml(latex)
  }
}

function renderMarkdown(text: string): string {
  // Step 1: Extract and protect LaTeX before escaping HTML
  const mathBlocks: string[] = []

  // Protect display math ($$...$$) first
  let html = text.replace(/\$\$([\s\S]*?)\$\$/g, (_, math) => {
    const idx = mathBlocks.length
    mathBlocks.push(renderLatex(math.trim(), true))
    return `%%MATH${idx}%%`
  })

  // Protect inline math ($...$) — but not $$ (already handled)
  html = html.replace(/\$([^\$\n]+?)\$/g, (_, math) => {
    const idx = mathBlocks.length
    mathBlocks.push(renderLatex(math.trim(), false))
    return `%%MATH${idx}%%`
  })

  // Step 2: Escape HTML (math blocks are already safe HTML)
  html = escapeHtml(html)

  // Step 3: Restore math blocks
  html = html.replace(/%%MATH(\d+)%%/g, (_, idx) => mathBlocks[parseInt(idx)])

  // Step 4: Markdown formatting
  // Code blocks
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre class="md-code"><code>$2</code></pre>')
  html = html.replace(/`([^`]+)`/g, '<code class="md-inline">$1</code>')
  // Headers
  html = html.replace(/^#### (.+)$/gm, '<h6 class="md-h6">$1</h6>')
  html = html.replace(/^### (.+)$/gm, '<h5 class="md-h5">$1</h5>')
  html = html.replace(/^## (.+)$/gm, '<h4 class="md-h4">$1</h4>')
  html = html.replace(/^# (.+)$/gm, '<h3 class="md-h3">$1</h3>')
  // Bold & italic
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>')
  // Horizontal rule
  html = html.replace(/^---+$/gm, '<hr class="md-hr">')
  // Ordered & unordered lists
  html = html.replace(/^(\d+)\. (.+)$/gm, '<li class="md-li">$1. $2</li>')
  html = html.replace(/^[-*] (.+)$/gm, '<li class="md-li">• $1</li>')
  // Paragraphs & line breaks
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
  min-width: 0;
}
.bubble-user { align-self: flex-end; flex-direction: row; }
.bubble-ai { align-self: flex-start; }

/* ---- DeepSeek-style bubbles ---- */
.bubble-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; flex-shrink: 0;
  background: var(--ai-surface-hover);
  border: 1px solid var(--ai-border);
}
.user-avatar { background: var(--ai-user-bubble); border-color: var(--ai-user-bubble); }

.bubble-body {
  padding: 12px 18px; border-radius: 16px;
  font-size: 14px; line-height: 1.75; min-width: 0;
}
.bubble-user .bubble-body {
  background: var(--ai-user-bubble);
  color: var(--ai-user-bubble-text);
  border-bottom-right-radius: 6px;
}
.bubble-ai .bubble-body {
  background: var(--ai-surface);
  color: var(--ai-text);
  border: 1px solid var(--ai-border);
  border-bottom-left-radius: 6px;
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

/* Feedback message — pre-output and in-output */
.feedback-message {
  padding: 8px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 8px;
  animation: feedback-fade-in 0.4s ease-out both;
  display: flex;
  align-items: center;
  gap: 6px;
}
.feedback-pre {
  background: linear-gradient(135deg, var(--ai-accent-soft) 0%, #f0fdf4 100%);
  color: var(--ai-accent-hover);
  border: 1px solid var(--ai-accent-border);
}
.feedback-in {
  background: #fefce8;
  color: #a16207;
  border: 1px solid #fef08a;
  font-size: 12px;
  padding: 6px 12px;
}
.feedback-text {
  display: inline-block;
}
@keyframes feedback-fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Streaming progress — question-by-question indicator */
.message-content :deep(.streaming-progress) {
  color: var(--ai-accent);
  font-size: 13px;
  font-weight: 500;
  padding: 6px 0;
  display: flex;
  align-items: center;
  gap: 4px;
  animation: progress-pulse 2s ease-in-out infinite;
}
@keyframes progress-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* Streaming question cards — appear one by one during practice mode */
.message-content :deep(.streaming-questions) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.message-content :deep(.sq-header) {
  color: var(--ai-accent);
  font-size: 13px;
  font-weight: 500;
  padding: 4px 0 6px;
  animation: progress-pulse 2s ease-in-out infinite;
}
.message-content :deep(.sq-card) {
  background: var(--ai-surface-soft);
  border: 1px solid var(--ai-border);
  border-radius: 8px;
  padding: 10px 12px;
  transition: all 0.3s ease-out;
}
.message-content :deep(.sq-card.sq-new) {
  animation: sq-card-appear 0.4s ease-out both;
}
@keyframes sq-card-appear {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.message-content :deep(.sq-card-head) {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.message-content :deep(.sq-num) {
  font-size: 13px;
  font-weight: 700;
  color: var(--ai-text);
}
.message-content :deep(.sq-type) {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--ai-accent-soft);
  color: var(--ai-accent);
  font-weight: 500;
}
.message-content :deep(.sq-card-title) {
  font-size: 13px;
  line-height: 1.5;
  color: var(--ai-text-secondary);
  margin-bottom: 6px;
}
.message-content :deep(.sq-options) {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.message-content :deep(.sq-opt) {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ai-text-secondary);
  line-height: 1.4;
}
.message-content :deep(.sq-opt-letter) {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--ai-surface-hover);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
  color: var(--ai-text-muted);
  flex-shrink: 0;
}
.message-content :deep(.sq-opt-text) {
  overflow-wrap: anywhere;
}

/* Reasoning */
/* ---- DeepSeek-style reasoning block (no inner scrollbar) ---- */
.reasoning-block { margin-bottom: 8px; }
.reasoning-block summary {
  font-size: 13px; color: var(--ai-text-muted); cursor: pointer; font-weight: 500;
  padding: 8px 12px; border-radius: 8px;
  background: var(--ai-surface-soft); border: 1px solid var(--ai-border);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  display: flex;
  align-items: center;
  gap: 6px;
  list-style: none;
}
.reasoning-block summary::-webkit-details-marker { display: none; }
.reasoning-block summary::before {
  content: '▶';
  font-size: 10px;
  transition: transform 0.2s;
  display: inline-block;
}
.reasoning-block details[open] > summary::before,
.reasoning-block summary:has(+ *)::before {
  transform: rotate(90deg);
}
.reasoning-block summary:hover {
  color: var(--ai-text-secondary);
  border-color: var(--ai-text-faint);
  background: var(--ai-surface-hover);
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.reasoning-block.reasoning-saved summary { color: var(--ai-text-muted); font-weight: 500; }

.reasoning-text {
  font-size: 13px; line-height: 1.65; color: var(--ai-text-muted);
  padding: 10px 14px; margin-top: 6px;
  border-left: 2px solid var(--ai-border);
  border-radius: 0 6px 6px 0;
  /* No max-height, no overflow — scrolls with page */
}
.reasoning-saved .reasoning-text {
  color: var(--ai-text-muted);
  background: var(--ai-surface-soft);
  border-left-color: var(--ai-border);
}

/* Reasoning formatted content */
.reasoning-text :deep(.reasoning-p) { margin: 2px 0; }
.reasoning-text :deep(.reasoning-h4) {
  font-size: 13px; font-weight: 600; color: var(--ai-text-secondary);
  margin: 8px 0 4px; padding-bottom: 2px;
  border-bottom: 1px solid var(--ai-border);
}
.reasoning-text :deep(.reasoning-h5) {
  font-size: 12px; font-weight: 600; color: var(--ai-text-muted);
  margin: 6px 0 2px;
}
.reasoning-text :deep(.reasoning-li) {
  display: block; font-size: 12px;
  padding: 1px 0 1px 8px; color: var(--ai-text-muted);
}
.reasoning-text :deep(.reasoning-code) {
  background: rgba(0,0,0,0.06); padding: 1px 4px;
  border-radius: 3px; font-size: 11px; font-family: monospace;
}
.reasoning-text :deep(strong) { color: var(--ai-text-secondary); font-weight: 600; }
.reasoning-text :deep(em) { color: var(--ai-text-muted); }

/* Message content */
.message-content {
  word-break: break-word;
  overflow-wrap: anywhere;
  max-width: 100%;
  overflow: hidden;
}
/* Streaming content — GPU-accelerated, contained layout */
.message-content.streaming {
  contain: content;
  will-change: contents;
}
.message-content.streaming::after {
  content: '▋';
  animation: cursor-pulse 1.4s ease-in-out infinite;
  color: var(--primary);
  margin-left: 1px;
  font-weight: 300;
}

/* Fade-in effect when streaming completes */
.message-content.fade-in {
  animation: fade-in-complete 0.5s ease-out both;
}
@keyframes fade-in-complete {
  from { opacity: 0.7; }
  to { opacity: 1; }
}

@keyframes cursor-pulse {
  0%, 100% { opacity: 0.25; }
  50% { opacity: 1; }
}

/* Generating hint — shown while AI streams question JSON */
.message-content :deep(.generating-hint) {
  color: var(--ai-accent);
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
  background: var(--ai-code-bg); color: var(--ai-code-text);
  padding: 12px 14px; border-radius: 8px; overflow-x: auto;
  font-size: 13px; line-height: 1.55; margin: 8px 0;
  border: 1px solid var(--ai-code-border);
  max-width: 100%;
  box-sizing: border-box;
}
/* Inline code in AI bubbles */
.bubble-ai .message-content :deep(.md-inline) {
  background: var(--ai-inline-code-bg); padding: 1px 6px;
  border-radius: 4px; font-size: 12px; font-family: 'SF Mono', 'Cascadia Code', monospace;
  color: var(--ai-inline-code-text);
}
/* Inline code in user bubbles */
.bubble-user .message-content :deep(.md-inline) {
  background: rgba(255,255,255,0.12); padding: 1px 6px;
  border-radius: 4px; font-size: 12px; font-family: 'SF Mono', 'Cascadia Code', monospace;
  color: rgba(255,255,255,0.85);
}
.message-content :deep(.md-h4),
.message-content :deep(.md-h5) { margin: 8px 0 3px; font-weight: 600; }
.message-content :deep(.md-li) { margin: 1px 0; padding-left: 2px; overflow-wrap: anywhere; word-break: break-word; }
/* Links and long URLs inside message content */
.message-content :deep(a) {
  word-break: break-all;
  overflow-wrap: anywhere;
  max-width: 100%;
  display: inline-block;
  vertical-align: bottom;
}

/* ===== Exam Paper Style ===== */
.exam-paper {
  margin-top: 10px;
  border: 1px solid var(--ai-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--ai-surface);
}

.exam-header {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  background: var(--ai-surface-soft);
  border-bottom: 1px solid var(--ai-border);
}

.exam-title { font-size: 13px; font-weight: 600; color: var(--ai-text-secondary); }

.exam-submit {
  padding: 10px 28px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}
.exam-submit:hover {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}
.exam-submit:active {
  transform: translateY(0) scale(0.98);
}

.exam-score { font-size: 13px; font-weight: 600; color: var(--ai-accent); }

/* Footer: submit button / score at bottom */
.exam-footer {
  padding: 16px;
  display: flex;
  justify-content: center;
  border-top: 1px solid var(--ai-border);
  background: var(--ai-surface-soft);
}

.exam-score-summary {
  display: flex;
  align-items: center;
  justify-content: center;
}

.ess-badge {
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}
.ess-badge.pass { background: #dcfce7; color: #16a34a; }
.ess-badge.fail { background: #fef2f2; color: #dc2626; }

/* Each question */
.exam-question {
  padding: 14px 16px;
  border-bottom: 1px solid var(--ai-border-soft);
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
  color: var(--ai-text);
}

.eq-type {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--ai-accent-soft);
  color: var(--ai-accent);
  font-weight: 500;
}

.eq-score { font-size: 11px; color: var(--ai-text-faint); margin-left: auto; }

.eq-body {
  font-size: 14px;
  line-height: 1.6;
  color: var(--ai-text);
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
  border: 1.5px solid var(--ai-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.12s;
}
.eq-opt:hover { background: var(--ai-surface-soft); }
.eq-opt.selected { border-color: var(--ai-accent); background: var(--ai-accent-soft); }
.eq-opt.correct { border-color: #22c55e; background: #f0fdf4; }
.eq-opt.wrong { border-color: #ef4444; background: #fef2f2; }

.eo-letter {
  width: 24px; height: 24px;
  border-radius: 50%;
  background: var(--ai-surface-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--ai-text-muted);
  flex-shrink: 0;
}
.eq-opt.selected .eo-letter { background: var(--ai-accent); color: #fff; }
.eq-opt.correct .eo-letter { background: #22c55e; color: #fff; }
.eq-opt.wrong .eo-letter { background: #ef4444; color: #fff; }

.eq-opt input { display: none; }

.eo-text { font-size: 13px; color: var(--ai-text-secondary); line-height: 1.4; }

/* Textarea for subjective */
.eq-textarea textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1.5px solid var(--ai-border);
  border-radius: 8px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.5;
  resize: vertical;
  background: var(--ai-surface-soft);
  color: var(--ai-text);
  outline: none;
  transition: border-color 0.15s;
}
.eq-textarea textarea:focus { border-color: var(--ai-accent); background: var(--ai-surface); }
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
  color: var(--ai-accent);
  padding: 6px 8px;
  border-radius: 6px;
  user-select: none;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.eq-expl-header:hover {
  color: var(--ai-accent-hover);
  background: var(--ai-accent-soft);
}

.eq-expl-arrow {
  font-size: 10px;
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  display: inline-block;
}

.eq-expl-body {
  margin-top: 6px;
  padding: 8px 10px;
  background: var(--ai-surface-soft);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ai-text-secondary);
  border: 1px solid var(--ai-border);
}

.eq-expl-body :deep(.expl-section) {
  color: var(--ai-accent);
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
  color: var(--ai-text-faint);
}

/* Live timer during streaming — styling delegated to LiveTimer component */
.live-duration {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

/* Live timer inside reasoning summary */
.live-timer-wrapper {
  margin-left: 8px;
}
.live-timer-wrapper :deep(.live-timer-display) {
  font-size: 11px;
}

/* Simple copy button — compact size (50%+ smaller than original) */
.copy-btn {
  padding: 2px 5px;
  border: 1px solid var(--ai-border);
  border-radius: 4px;
  background: var(--ai-surface);
  color: var(--ai-text-muted);
  font-size: 10px;
  line-height: 1.4;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.copy-btn:hover {
  border-color: var(--ai-accent);
  color: var(--ai-accent);
  background: var(--ai-accent-soft);
  box-shadow: 0 1px 4px rgba(99,102,241,0.15);
}

/* ===== AI message action buttons (regenerate + copy) ===== */
.msg-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-icon-btn {
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid var(--ai-border);
  border-radius: 6px;
  background: var(--ai-surface);
  color: var(--ai-text-faint);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}
.action-icon-btn svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.action-icon-btn:hover {
  border-color: var(--ai-accent);
  color: var(--ai-accent);
  background: var(--ai-accent-soft);
  box-shadow: 0 1px 4px rgba(99,102,241,0.15);
}
.action-icon-btn:active {
  transform: scale(0.92);
}

/* Regenerate button — refresh icon */
.regenerate-btn:hover {
  border-color: var(--ai-accent);
  color: var(--ai-accent);
  background: var(--ai-accent-soft);
}
.regenerate-btn:hover svg {
  animation: regen-spin 0.6s ease-in-out;
}
@keyframes regen-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(-360deg); }
}

/* Copy button — checkmark icon when copied */
.copy-icon-btn.copied {
  border-color: #22c55e;
  color: #22c55e;
  background: #f0fdf4;
}

/* ===== Responsive — Mobile (≤768px) ===== */
@media (max-width: 768px) {
  .chat-bubble { max-width: 90%; }

  .bubble-body {
    padding: 10px 14px;
    font-size: 13px;
    border-radius: 14px;
  }
  .bubble-avatar {
    width: 26px; height: 26px;
    font-size: 12px;
  }

  /* Feedback messages on mobile */
  .feedback-message {
    padding: 6px 10px;
    font-size: 12px;
    border-radius: 8px;
  }
  .feedback-in {
    font-size: 11px;
    padding: 5px 10px;
  }

  /* Streaming question cards on mobile */
  .message-content :deep(.sq-card) {
    padding: 8px 10px;
  }
  .message-content :deep(.sq-card-title) {
    font-size: 12px;
  }
  .message-content :deep(.sq-opt) {
    font-size: 11px;
  }
  .message-content :deep(.sq-opt-letter) {
    width: 16px; height: 16px; font-size: 9px;
  }

  /* Force message-content to stay within screen bounds */
  .message-content {
    max-width: 100%;
    overflow-wrap: anywhere;
    word-break: break-word;
  }

  /* Code blocks: allow horizontal scroll within container, never overflow parent */
  .message-content :deep(.md-code) {
    padding: 10px 10px;
    font-size: 12px;
    max-width: 100%;
    box-sizing: border-box;
  }

  /* Inline code: prevent long inline code from overflowing */
  .message-content :deep(.md-inline) {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    vertical-align: bottom;
  }

  /* Paragraphs and lists: ensure wrapping */
  .message-content :deep(.md-p) { overflow-wrap: anywhere; }
  .message-content :deep(.md-li) { overflow-wrap: anywhere; }

  /* Reasoning block compact on mobile */
  .reasoning-block summary { font-size: 12px; padding: 6px 10px; }
  .reasoning-text { font-size: 12px; padding: 8px 10px; }

  .msg-meta { margin-top: 4px; padding: 0 2px; }
  .copy-btn { font-size: 9px; padding: 1px 4px; }
  .copy-trigger { font-size: 9px; padding: 1px 4px; }
  .action-icon-btn { width: 24px; height: 24px; }
  .action-icon-btn svg { width: 12px; height: 12px; }

  .exam-paper { margin-top: 8px; border-radius: 8px; }
  .exam-header { padding: 8px 12px; }
  .exam-title { font-size: 12px; }
  .exam-submit { font-size: 11px; padding: 4px 12px; }
  .exam-question { padding: 10px 12px; }
  .eq-body { font-size: 13px; overflow-wrap: anywhere; }
  .eq-opt { padding: 6px 10px; gap: 6px; }
  .eo-letter { width: 22px; height: 22px; font-size: 11px; }
  .eo-text { font-size: 12px; overflow-wrap: anywhere; }
  .eq-textarea textarea { font-size: 12px; padding: 8px 10px; }
}

/* ===== Responsive — Small mobile (≤480px) ===== */
@media (max-width: 480px) {
  .chat-bubble { max-width: 88%; gap: 6px; }

  .bubble-body {
    padding: 8px 12px;
    font-size: 13px;
    border-radius: 12px;
  }
  .bubble-avatar {
    width: 24px; height: 24px;
    font-size: 11px;
  }

  .message-content :deep(.md-code) {
    padding: 8px 8px;
    font-size: 11px;
    border-radius: 6px;
  }

  .message-content :deep(.md-h4) { font-size: 13px; }
  .message-content :deep(.md-h5) { font-size: 12px; }

  .exam-paper { border-radius: 6px; }
  .exam-header { padding: 6px 10px; }
  .exam-question { padding: 8px 10px; }
  .eq-opt { padding: 5px 8px; gap: 5px; }
  .eo-text { font-size: 11px; }
}

/* ===== Responsive — Tablet (769px–1024px) ===== */
@media (min-width: 769px) and (max-width: 1024px) {
  .chat-bubble { max-width: 80%; }

  .bubble-body {
    padding: 11px 16px;
    font-size: 14px;
  }

  .message-content :deep(.md-code) {
    max-width: 100%;
    box-sizing: border-box;
  }
}

/* ===== Responsive — Landscape on mobile ===== */
@media (max-width: 1023px) and (orientation: landscape) {
  .chat-bubble { max-width: 85%; }
  .bubble-body { padding: 8px 12px; font-size: 13px; }
  .bubble-avatar { width: 24px; height: 24px; font-size: 11px; }

  .message-content :deep(.md-code) {
    padding: 8px 10px;
    font-size: 12px;
  }
}

/* ===== Dark mode overrides ===== */
[data-theme="dark"] .feedback-pre {
  background: linear-gradient(135deg, var(--ai-accent-soft) 0%, rgba(129, 140, 248, 0.08) 100%);
}
[data-theme="dark"] .feedback-in {
  background: rgba(234, 179, 8, 0.12);
  color: #fbbf24;
  border-color: rgba(234, 179, 8, 0.3);
}
[data-theme="dark"] .ess-badge.pass { background: rgba(34, 197, 94, 0.18); color: #86efac; }
[data-theme="dark"] .ess-badge.fail { background: rgba(239, 68, 68, 0.18); color: #fca5a5; }
[data-theme="dark"] .eq-opt.correct { background: rgba(34, 197, 94, 0.12); }
[data-theme="dark"] .eq-opt.wrong { background: rgba(239, 68, 68, 0.12); }
[data-theme="dark"] .eq-result.correct { background: rgba(34, 197, 94, 0.12); color: #4ade80; }
[data-theme="dark"] .eq-result.wrong { background: rgba(239, 68, 68, 0.12); color: #fca5a5; }
[data-theme="dark"] .copy-icon-btn.copied { background: rgba(34, 197, 94, 0.12); }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .feedback-pre {
    background: linear-gradient(135deg, var(--ai-accent-soft) 0%, rgba(129, 140, 248, 0.08) 100%);
  }
  :root:not([data-theme="light"]) .feedback-in {
    background: rgba(234, 179, 8, 0.12);
    color: #fbbf24;
    border-color: rgba(234, 179, 8, 0.3);
  }
  :root:not([data-theme="light"]) .ess-badge.pass { background: rgba(34, 197, 94, 0.18); color: #86efac; }
  :root:not([data-theme="light"]) .ess-badge.fail { background: rgba(239, 68, 68, 0.18); color: #fca5a5; }
  :root:not([data-theme="light"]) .eq-opt.correct { background: rgba(34, 197, 94, 0.12); }
  :root:not([data-theme="light"]) .eq-opt.wrong { background: rgba(239, 68, 68, 0.12); }
  :root:not([data-theme="light"]) .eq-result.correct { background: rgba(34, 197, 94, 0.12); color: #4ade80; }
  :root:not([data-theme="light"]) .eq-result.wrong { background: rgba(239, 68, 68, 0.12); color: #fca5a5; }
  :root:not([data-theme="light"]) .copy-icon-btn.copied { background: rgba(34, 197, 94, 0.12); }
}
</style>
