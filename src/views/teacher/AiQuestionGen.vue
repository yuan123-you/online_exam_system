<template>
  <div class="aiqg-layout">
    <!-- Mobile settings toggle -->
    <button class="mobile-toggle" @click="mobileSettingsOpen = !mobileSettingsOpen">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/>
        <line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/>
        <line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/>
        <line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/>
        <line x1="17" y1="16" x2="23" y2="16"/>
      </svg>
      <span>{{ mobileSettingsOpen ? '收起参数' : '展开参数' }}</span>
    </button>

    <!-- LEFT: Settings Panel -->
    <aside class="settings-panel" :class="{ 'mobile-open': mobileSettingsOpen }">
      <div class="sp-scroll">
        <h3 class="sp-title">参数设置</h3>

        <!-- Question Type -->
        <div class="sp-section">
          <label class="sp-label">题目类型</label>
          <div class="type-group">
            <label
              v-for="t in questionTypes"
              :key="t.value"
              :class="['type-chip', { active: params.type === t.value }]"
            >
              <input type="radio" :value="t.value" v-model="params.type" class="sr-only" />
              {{ t.label }}
            </label>
          </div>
        </div>

        <!-- Difficulty -->
        <div class="sp-section">
          <label class="sp-label">难度级别</label>
          <div class="diff-group">
            <button
              v-for="d in difficulties"
              :key="d.value"
              :class="['diff-card', `diff-${d.value}`, { active: params.difficulty === d.value }]"
              @click="params.difficulty = d.value"
            >
              <span class="diff-icon">{{ d.icon }}</span>
              <span class="diff-text">{{ d.label }}</span>
            </button>
          </div>
        </div>

        <!-- Subject -->
        <div class="sp-section">
          <label class="sp-label">科目</label>
          <div class="subject-wrapper">
            <select v-if="!customSubjectMode" v-model="params.subject" class="sp-select">
              <option v-for="s in subjectOptions" :key="s" :value="s">{{ s }}</option>
            </select>
            <input
              v-else
              v-model="params.subject"
              class="sp-input"
              placeholder="输入自定义科目"
              @keydown.enter="customSubjectMode = false"
            />
            <button class="custom-toggle" @click="toggleCustomSubject">
              {{ customSubjectMode ? '列表' : '自定义' }}
            </button>
          </div>
        </div>

        <!-- Knowledge Point -->
        <div class="sp-section">
          <label class="sp-label">知识点</label>
          <input v-model="params.knowledgePoint" class="sp-input" placeholder="如：微积分、TCP协议" />
        </div>

        <!-- Count -->
        <div class="sp-section">
          <label class="sp-label">题目数量</label>
          <div class="count-control">
            <button class="count-btn" :disabled="params.count <= 1" @click="params.count--">−</button>
            <input type="number" v-model.number="params.count" class="count-input" min="1" max="20" />
            <button class="count-btn" :disabled="params.count >= 20" @click="params.count++">+</button>
          </div>
        </div>

        <!-- Deep Thinking -->
        <div class="sp-section">
          <label class="sp-label deep-toggle-label">
            深度思考
            <span class="deep-desc">更深入的分析，耗时更长</span>
          </label>
          <button
            :class="['toggle-switch', { on: deepThinking }]"
            @click="deepThinking = !deepThinking"
            role="switch"
            :aria-checked="deepThinking"
          >
            <span class="toggle-knob"></span>
          </button>
        </div>

        <!-- Generate Button -->
        <button
          class="generate-btn"
          :class="{ loading: genLoading }"
          :disabled="genLoading"
          @click="handleGenerate"
        >
          <span v-if="genLoading" class="gen-spinner"></span>
          <span v-else class="gen-icon">✨</span>
          {{ genLoading ? '生成中...' : 'AI 一键生成' }}
        </button>
      </div>
    </aside>

    <!-- RIGHT: Chat & Results -->
    <div class="chat-panel">
      <!-- Top bar -->
      <header class="top-bar">
        <div class="tb-left">
          <span class="tb-title">AI 智能出题</span>
          <span class="tb-quota">已用 {{ store.aiQuotaUsed }} / {{ store.aiQuotaUsed + store.aiQuotaRemaining }}</span>
        </div>
        <button class="ghost-btn" @click="$router.push(store.isAdmin ? '/overview' : '/questions')">返回</button>
      </header>

      <!-- Messages area -->
      <div ref="msgList" class="msg-area">
        <!-- Welcome -->
        <div v-if="messages.length === 0 && !streamingActive" class="welcome">
          <div class="welcome-icon">🤖</div>
          <h3>AI 智能出题助手</h3>
          <p class="welcome-sub">设置参数一键生成，或直接描述出题需求</p>
          <div class="welcome-chips">
            <button v-for="c in quickChips" :key="c.label" class="wc-chip" @click="doSend(c.prompt)">
              {{ c.icon }} {{ c.label }}
            </button>
          </div>
        </div>

        <MessageBubbles
          :messages="messages"
          :streaming-active="streamingActive"
          :streaming-reasoning="streamingReasoning"
          :streaming-content="streamingContent"
          :loading="false"
          tab="chat"
          @regenerate="handleRegenerate"
        />

        <!-- Generated questions preview -->
        <div v-if="parsedQuestions.length > 0 && !streamingActive" class="preview-panel">
          <div class="preview-header">
            <div class="preview-title">
              📋 已生成 {{ parsedQuestions.length }} 道题目
              <span class="preview-selected">（已选 {{ selectedCount }} / {{ parsedQuestions.length }}）</span>
            </div>
            <div class="preview-actions">
              <button class="select-all-btn" @click="toggleSelectAll">
                {{ allSelected ? '取消全选' : '☑ 全选' }}
              </button>
              <button class="primary-btn" :disabled="selectedCount === 0 || importing" @click="importSelected">
                {{ importing ? '导入中...' : `📥 导入选中 (${selectedCount})` }}
              </button>
              <button class="primary-btn add-all-btn" :disabled="parsedQuestions.length === 0 || importing" @click="importAll">
                ⚡ 一键全部导入
              </button>
              <button class="ghost-btn" @click="discardAll">✕ 放弃</button>
            </div>
          </div>
          <div class="preview-list">
            <div
              v-for="(q, qi) in parsedQuestions"
              :key="qi"
              :class="['preview-card', { checked: selected[qi] !== false }]"
            >
              <div class="pc-card-head" @click="selected[qi] = selected[qi] === false">
                <label class="pc-check" @click.stop>
                  <input type="checkbox" :checked="selected[qi] !== false" @change="selected[qi] = !selected[qi]" />
                  <span class="pc-check-box"></span>
                </label>
                <span class="pc-num">{{ qi + 1 }}.</span>
                <span :class="['pc-type', `type-${q.type}`]">{{ typeLabel(q.type as any) }}</span>
                <span :class="['pc-diff', `diff-${q.difficulty}`]">{{ diffLabel(q.difficulty) }}</span>
                <span v-if="q.score" class="pc-score">{{ q.score }}分</span>
                <button
                  class="pc-toggle"
                  @click.stop="expandedCards[qi] = !expandedCards[qi]"
                  :title="expandedCards[qi] ? '收起' : '展开'"
                >
                  {{ expandedCards[qi] ? '▾' : '▸' }}
                </button>
              </div>
              <div class="pc-card-body">
                <p class="pc-title">{{ q.title }}</p>
                <!-- Options preview for single/multiple/judge -->
                <div
                  v-if="(q.type === 'single' || q.type === 'multiple' || q.type === 'judge') && q.options?.length"
                  class="pc-options"
                >
                  <div
                    v-for="(opt, oi) in q.options"
                    :key="oi"
                    :class="['pc-opt', isCorrectOption(q, opt) ? 'pc-opt-correct' : '']"
                  >
                    <span class="pc-opt-letter">{{ ['A','B','C','D','E','F'][oi] || oi }}</span>
                    <span class="pc-opt-text">{{ stripOptPrefix(opt) }}</span>
                    <span v-if="isCorrectOption(q, opt)" class="pc-opt-mark">✓</span>
                  </div>
                </div>
                <!-- Answer line -->
                <div v-if="q.answer && q.answer.length > 0" class="pc-answer">
                  <span class="pc-answer-label">正确答案：</span>
                  <span class="pc-answer-value">{{ formatAnswer(q) }}</span>
                </div>
                <!-- Explanation (collapsible) -->
                <div v-if="q.explanation" class="pc-explanation">
                  <div class="pc-expl-header" @click="toggleExplanation(qi)">
                    <span>💡 解析</span>
                    <span class="pc-expl-arrow">{{ expandedCards[qi] ? '▾' : '▸' }}</span>
                  </div>
                  <div v-if="expandedCards[qi]" class="pc-expl-body">{{ q.explanation }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Scroll to bottom -->
      <transition name="scroll-btn-fade">
        <button v-if="showScrollToBottom" class="scroll-to-bottom-btn" @click="handleScrollToBottom" title="返回底部">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>
      </transition>

      <!-- Input area -->
      <div class="bottom-area">
        <div class="input-row">
          <div class="input-wrapper">
            <textarea
              ref="inputRef"
              v-model="inputText"
              class="msg-input"
              rows="1"
              placeholder="描述出题需求，Enter 发送，Shift+Enter 换行"
              :disabled="streamingActive"
              @keydown="onKeydown"
              @input="autoResize"
            ></textarea>
            <button v-if="!streamingActive" class="action-btn send-action" :disabled="!inputText.trim()" @click="doSend()">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            </button>
            <button v-else class="action-btn stop-action" @click="stopStreaming">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import { aiChatStream, aiGenerateQuestionsStream, aiImportQuestions } from '@/api/client'
import { useAutoScroll } from '@/composables/useAutoScroll'
import { useToast } from '@/composables/useToast'
import MessageBubbles from '@/views/student/AiPractice/MessageBubbles.vue'
import type { AiQuestion } from '@/api/client'

defineOptions({ name: 'AiQuestionGen' })

const store = useAppStore()
const toast = useToast()

// ---- Settings State ----
const mobileSettingsOpen = ref(false)
const customSubjectMode = ref(false)
const deepThinking = ref(false)
const genLoading = ref(false)
const importing = ref(false)

const params = reactive({
  type: 'single',
  difficulty: 'medium',
  subject: '高等数学',
  knowledgePoint: '',
  count: 5,
})

const questionTypes = [
  { value: 'single', label: '单选题' },
  { value: 'multiple', label: '多选题' },
  { value: 'judge', label: '判断题' },
  { value: 'fill', label: '填空题' },
  { value: 'short', label: '简答题' },
  { value: 'coding', label: '编程题' },
]

const difficulties = [
  { value: 'easy', label: '简单', icon: '🟢' },
  { value: 'medium', label: '中等', icon: '🟡' },
  { value: 'hard', label: '困难', icon: '🔴' },
]

const subjectOptions = [
  '高等数学', '大学语文', '马克思主义', '政治', 'Java',
  '计算机网络', '数据库', '中国近现代史', '大学英语', '大学物理',
  '数据结构', '操作系统', '算法', '计算机基础',
]

function toggleCustomSubject() {
  customSubjectMode.value = !customSubjectMode.value
  if (customSubjectMode.value) params.subject = ''
  else params.subject = subjectOptions[0]
}

// ---- Chat State ----
const inputText = ref('')
const msgList = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const streamingActive = ref(false)
const streamingReasoning = ref('')
const streamingContent = ref('')
const messages = ref<Array<{ role: string; content: string; reasoning?: string }>>([])
const selected = reactive<Record<number, boolean>>({})
const expandedCards = reactive<Record<number, boolean>>({})
let abortCtrl: AbortController | null = null

const quickChips = [
  { icon: '📐', label: '高等数学', prompt: '帮我出5道高等数学单选题，涵盖微积分、线性代数，难度较难，附详细解析' },
  { icon: '📖', label: '大学语文', prompt: '帮我出5道大学语文单选题，涵盖诗词鉴赏、文言文阅读、文学常识，附详细解析' },
  { icon: '🏛️', label: '马克思主义', prompt: '帮我出5道马克思主义基本原理单选题，涵盖唯物辩证法、科学社会主义，附详细解析' },
  { icon: '🗳️', label: '政治', prompt: '帮我出5道政治理论单选题，涵盖中国特色社会主义、时政热点，附详细解析' },
  { icon: '💻', label: 'Java', prompt: '帮我出5道Java基础单选题，涵盖面向对象、集合、异常处理，难度中等，附详细解析' },
  { icon: '🌐', label: '计算机网络', prompt: '帮我出5道计算机网络单选题，涵盖TCP/IP、HTTP、DNS，附详细解析' },
  { icon: '🗄️', label: '数据库', prompt: '帮我出5道数据库单选题，涵盖SQL查询、索引、事务，难度中等，附详细解析' },
  { icon: '📜', label: '中国近现代史', prompt: '帮我出5道中国近现代史判断题，涵盖鸦片战争到改革开放，附详细解析' },
]

// ---- Computed ----
const selectedCount = computed(() => Object.values(selected).filter(Boolean).length)
const allSelected = computed(() => {
  const qs = parsedQuestions.value
  if (qs.length === 0) return false
  return qs.every((_, i) => selected[i] !== false)
})

const parsedQuestions = computed((): AiQuestion[] => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const content = messages.value[i].content
    if (!content) continue
    try {
      const jsonMatch = content.match(/\[[\s\S]*\]/)
      if (!jsonMatch) continue
      const parsed = JSON.parse(jsonMatch[0])
      if (Array.isArray(parsed) && parsed.length > 0) {
        const filtered = parsed.filter((q: any) => q.title && q.title.trim() && q.title !== '...')
        if (filtered.length > 0) {
          const capped = filtered.map((q: any) => {
            if (q.options && q.options.length > 4 && (q.type === 'single' || q.type === 'judge' || q.type === 'multiple')) {
              return { ...q, options: q.options.slice(0, 4) }
            }
            return q
          })
          return capped as AiQuestion[]
        }
      }
    } catch { continue }
  }
  return []
})

watch(parsedQuestions, (qs) => {
  for (let i = 0; i < qs.length; i++) {
    if (selected[i] === undefined) selected[i] = true
  }
})

// ---- Helpers ----
function diffLabel(d: string) {
  return { easy: '简单', medium: '中等', hard: '困难' }[d] || d
}

/** Strip leading "A. " / "A、 " prefix from option text */
function stripOptPrefix(opt: string): string {
  return String(opt).replace(/^[A-D][.、)\s]+/, '').trim()
}

/** Extract the option letter (A/B/C/D) from an option string like "A. xxx" */
function extractOptLetter(opt: string): string {
  const m = String(opt).match(/^([A-D])/)
  return m ? m[1] : ''
}

/** Check if the given option is one of the correct answers */
function isCorrectOption(q: AiQuestion, opt: string): boolean {
  if (!q.answer || q.answer.length === 0) return false
  const letter = extractOptLetter(opt)
  if (!letter) return false
  return q.answer.some((a) => String(a).includes(letter))
}

/** Format the answer array for display */
function formatAnswer(q: AiQuestion): string {
  if (!q.answer || q.answer.length === 0) return ''
  if (q.type === 'fill' || q.type === 'short' || q.type === 'coding') {
    return q.answer.join('，')
  }
  // For choice/judge: extract letters
  const letters: string[] = []
  for (const a of q.answer) {
    const m = String(a).match(/([A-D])/)
    if (m) letters.push(m[1])
  }
  return letters.length > 0 ? letters.join('，') : q.answer.join('，')
}

/** Toggle explanation expansion for a card */
function toggleExplanation(qi: number) {
  expandedCards[qi] = !expandedCards[qi]
}

function toggleSelectAll() {
  const qs = parsedQuestions.value
  if (allSelected.value) {
    qs.forEach((_, i) => { selected[i] = false })
  } else {
    qs.forEach((_, i) => { selected[i] = true })
  }
}

// ---- Auto Scroll ----
function getScrollContainer(): HTMLElement | null {
  return msgList.value
}

const {
  showScrollToBottom,
  onStreamingUpdate,
  onNewMessage,
  handleScrollToBottom,
} = useAutoScroll(getScrollContainer, {
  nearBottomRatio: 0.2,
  scrollDuration: 300,
  streamingScrollDuration: 0,
})

// Auto-scroll on streaming content changes
watch(streamingContent, () => { if (streamingActive.value) onStreamingUpdate() })
watch(streamingReasoning, () => { if (streamingActive.value) onStreamingUpdate() })
watch(() => messages.value.length, () => { onNewMessage() })

// ---- Generate (structured) ----
function handleGenerate() {
  if (genLoading.value) return
  genLoading.value = true
  streamingActive.value = true
  streamingReasoning.value = ''
  streamingContent.value = ''

  // Add a user message summarizing the params
  const summary = `生成 ${params.count} 道${diffLabel(params.difficulty)}${typeLabel(params.type as any)}，科目：${params.subject}${params.knowledgePoint ? '，知识点：' + params.knowledgePoint : ''}`
  messages.value.push({ role: 'user', content: summary })
  const aiIdx = messages.value.length
  const startTime = Date.now()
  // Sync stream start time to store so LiveTimer in MessageBubbles can tick
  store.chatStreamStartTime = startTime
  messages.value.push({ role: 'assistant', content: '', _startedAt: startTime } as any)

  abortCtrl = aiGenerateQuestionsStream(
    {
      subject: params.subject,
      knowledgePoint: params.knowledgePoint || undefined,
      type: params.type,
      difficulty: params.difficulty,
      count: params.count,
      deepThinking: deepThinking.value,
    },
    (chunk) => {
      if (chunk.type === 'reasoning') {
        streamingReasoning.value += chunk.text
      } else {
        streamingContent.value += chunk.text
        messages.value[aiIdx].content = streamingContent.value
      }
      onStreamingUpdate()
    },
    (data) => {
      streamingActive.value = false
      genLoading.value = false
      if (data.content) {
        messages.value[aiIdx].content = data.content
      }
      if (data.reasoning || streamingReasoning.value) {
        messages.value[aiIdx].reasoning = data.reasoning || streamingReasoning.value
      }
      const msg = messages.value[aiIdx] as any
      if (msg._startedAt) {
        msg.duration = (Date.now() - msg._startedAt) / 1000
        delete msg._startedAt
      }
      onNewMessage()
    },
    (err) => {
      streamingActive.value = false
      genLoading.value = false
      messages.value[aiIdx].content = '❌ ' + (err || '生成失败，请重试')
      toast.error(err || '生成失败，请调整参数后重试')
    },
    () => {
      streamingActive.value = false
      genLoading.value = false
    }
  )
}

// ---- Chat (free-form) ----
function doSend(text?: string) {
  const t = (text || inputText.value).trim()
  if (!t || streamingActive.value) return
  inputText.value = ''
  sendMessage(t)
}

function sendMessage(prompt: string) {
  messages.value.push({ role: 'user', content: prompt })
  const aiIdx = messages.value.length
  const startTime = Date.now()
  // Sync stream start time to store so LiveTimer in MessageBubbles can tick
  store.chatStreamStartTime = startTime
  messages.value.push({ role: 'assistant', content: '', _startedAt: startTime } as any)

  streamingActive.value = true
  streamingReasoning.value = ''
  streamingContent.value = ''

  abortCtrl = aiChatStream(
    { message: prompt, deepThinking: deepThinking.value },
    (chunk) => {
      if (chunk.type === 'reasoning') {
        streamingReasoning.value += chunk.text
      } else {
        streamingContent.value += chunk.text
        messages.value[aiIdx].content = streamingContent.value
      }
      onStreamingUpdate()
    },
    (data) => {
      streamingActive.value = false
      if (data.content) {
        messages.value[aiIdx].content = data.content
      }
      if (data.reasoning || streamingReasoning.value) {
        messages.value[aiIdx].reasoning = data.reasoning || streamingReasoning.value
      }
      const msg = messages.value[aiIdx] as any
      if (msg._startedAt) {
        msg.duration = (Date.now() - msg._startedAt) / 1000
        delete msg._startedAt
      }
      onNewMessage()
    },
    (err) => {
      streamingActive.value = false
      messages.value[aiIdx].content = '❌ ' + (err || '生成失败，请重试')
      toast.error(err || '对话请求失败，请重试')
    },
  )
}

function stopStreaming() {
  if (abortCtrl) {
    abortCtrl.abort()
    abortCtrl = null
  }
  streamingActive.value = false
  genLoading.value = false
}

/** Handle regenerate event from MessageBubbles — re-sends the preceding user message */
function handleRegenerate(msgIndex: number) {
  // Don't regenerate while streaming
  if (streamingActive.value) return

  // Find the preceding user message
  if (msgIndex <= 0 || messages.value[msgIndex - 1]?.role !== 'user') return
  const userText = messages.value[msgIndex - 1].content

  // Remove the AI message and the user message before it
  messages.value.splice(msgIndex - 1, 2)

  // Clear selected/expanded cards since we're regenerating
  Object.keys(selected).forEach(k => delete selected[Number(k)])
  Object.keys(expandedCards).forEach(k => delete expandedCards[Number(k)])

  // Re-send the user message
  sendMessage(userText)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    doSend()
  }
}

function autoResize() {
  if (inputRef.value) {
    const el = inputRef.value
    el.style.height = 'auto'
    const maxH = 150
    const threshold = maxH * 0.5
    if (el.scrollHeight <= threshold) {
      el.style.height = el.scrollHeight + 'px'
      el.style.overflowY = 'hidden'
    } else {
      el.style.height = Math.min(el.scrollHeight, maxH) + 'px'
      el.style.overflowY = 'auto'
    }
  }
}

function discardAll() {
  messages.value = []
  Object.keys(selected).forEach(k => delete selected[Number(k)])
  Object.keys(expandedCards).forEach(k => delete expandedCards[Number(k)])
}

async function importSelected() {
  const qs = parsedQuestions.value.filter((_, i) => selected[i] !== false)
  if (qs.length === 0) {
    toast.warning('请至少选择一道题目再导入')
    return
  }
  importing.value = true
  try {
    await store.handleAiImportQuestions(qs)
    toast.success(`成功导入 ${qs.length} 道题目到题库`)
    discardAll()
  } catch (err: any) {
    toast.error(err?.message || '导入失败，请重试')
  } finally {
    importing.value = false
  }
}

async function importAll() {
  const qs = parsedQuestions.value
  if (qs.length === 0) {
    toast.warning('没有可导入的题目')
    return
  }
  importing.value = true
  try {
    await store.handleAiImportQuestions(qs)
    toast.success(`成功导入全部 ${qs.length} 道题目到题库`)
    discardAll()
  } catch (err: any) {
    toast.error(err?.message || '导入失败，请重试')
  } finally {
    importing.value = false
  }
}

// ---- Auto-save Draft ----
const DRAFT_KEY = 'ai-question-gen-draft'

function saveDraft() {
  const draft = {
    type: params.type,
    difficulty: params.difficulty,
    subject: params.subject,
    knowledgePoint: params.knowledgePoint,
    count: params.count,
    deepThinking: deepThinking.value,
  }
  try {
    localStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
  } catch { /* ignore storage errors */ }
}

function restoreDraft() {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return
    const draft = JSON.parse(raw)
    if (draft.type) params.type = draft.type
    if (draft.difficulty) params.difficulty = draft.difficulty
    if (draft.subject) params.subject = draft.subject
    if (draft.knowledgePoint !== undefined) params.knowledgePoint = draft.knowledgePoint
    if (draft.count) params.count = draft.count
    if (draft.deepThinking !== undefined) deepThinking.value = draft.deepThinking
  } catch { /* ignore parse errors */ }
}

// Auto-save when params change
watch(() => ({ ...params }), saveDraft, { deep: true })
watch(deepThinking, saveDraft)

onMounted(() => {
  restoreDraft()
})
</script>

<style scoped>
.aiqg-layout {
  --primary: #6366f1;
  --primary-hover: #4f46e5;
  display: flex;
  height: calc(100vh - 140px);
  max-height: 750px;
  max-width: 100%;
  min-height: 420px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
}

/* ===== Mobile Toggle ===== */
.mobile-toggle {
  display: none;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 20;
  transition: all 0.15s;
}
.mobile-toggle:hover { background: #f9fafb; }

/* ===== LEFT: Settings Panel ===== */
.settings-panel {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid #f3f4f6;
  background: #fafbfc;
  overflow-y: auto;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.sp-scroll { padding: 20px 16px; }
.sp-title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 16px;
}

.sp-section {
  margin-bottom: 18px;
}
.sp-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* Type chips */
.type-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.type-chip {
  padding: 5px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
  color: #374151;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}
.type-chip:hover { border-color: var(--primary); color: var(--primary); }
.type-chip.active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
  font-weight: 600;
}

/* Difficulty cards */
.diff-group {
  display: flex;
  gap: 8px;
}
.diff-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 6px;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s;
  font-size: 12px;
  color: #374151;
}
.diff-card:hover { transform: translateY(-1px); box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.diff-card.active.diff-easy { border-color: #22c55e; background: #f0fdf4; color: #166534; }
.diff-card.active.diff-medium { border-color: #f59e0b; background: #fffbeb; color: #92400e; }
.diff-card.active.diff-hard { border-color: #ef4444; background: #fef2f2; color: #991b1b; }
.diff-icon { font-size: 16px; }
.diff-text { font-weight: 600; }

/* Subject */
.subject-wrapper {
  display: flex;
  gap: 6px;
}
.sp-select {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
  color: #374151;
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
}
.sp-select:focus { border-color: var(--primary); }
.sp-input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
  color: #374151;
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
}
.sp-input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.custom-toggle {
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.custom-toggle:hover { color: var(--primary); border-color: var(--primary); }

/* Count */
.count-control {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.count-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: #f9fafb;
  color: #374151;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.12s;
}
.count-btn:hover:not(:disabled) { background: #e5e7eb; }
.count-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.count-input {
  width: 48px;
  height: 36px;
  border: none;
  border-left: 1px solid #e5e7eb;
  border-right: 1px solid #e5e7eb;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  outline: none;
  -moz-appearance: textfield;
}
.count-input::-webkit-inner-spin-button,
.count-input::-webkit-outer-spin-button { -webkit-appearance: none; }

/* Deep thinking toggle */
.deep-toggle-label { display: flex; align-items: center; gap: 8px; }
.deep-desc { font-weight: 400; font-size: 11px; color: #9ca3af; text-transform: none; letter-spacing: 0; }
.toggle-switch {
  position: relative;
  width: 40px;
  height: 22px;
  border: none;
  border-radius: 11px;
  background: #d1d5db;
  cursor: pointer;
  transition: background 0.2s;
  flex-shrink: 0;
}
.toggle-switch.on { background: var(--primary); }
.toggle-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.toggle-switch.on .toggle-knob { transform: translateX(18px); }

/* Generate button */
.generate-btn {
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;
  margin-top: 4px;
}
.generate-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(99,102,241,0.4);
}
.generate-btn:active:not(:disabled) { transform: translateY(0); }
.generate-btn:disabled { opacity: 0.7; cursor: not-allowed; }
.generate-btn.loading {
  animation: gen-pulse 1.5s ease-in-out infinite;
}
.gen-icon { font-size: 16px; }
.gen-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes gen-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(99,102,241,0.4); }
  50% { box-shadow: 0 0 0 8px rgba(99,102,241,0); }
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== RIGHT: Chat Panel ===== */
.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* Top bar */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-bottom: 1px solid #f3f4f6;
  background: #fafbfc;
  flex-shrink: 0;
}
.tb-left { display: flex; align-items: center; gap: 10px; }
.tb-title { font-size: 14px; font-weight: 600; color: #111827; }
.tb-quota {
  font-size: 11px;
  color: #9ca3af;
  background: #f3f4f6;
  padding: 2px 8px;
  border-radius: 4px;
}

/* Message area */
.msg-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  scroll-behavior: smooth;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: #d1d5db transparent;
}
.msg-area::-webkit-scrollbar { width: 6px; }
.msg-area::-webkit-scrollbar-track { background: transparent; }
.msg-area::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }

/* Welcome */
.welcome { text-align: center; padding: 40px 20px; margin: auto; }
.welcome-icon { font-size: 40px; margin-bottom: 12px; }
.welcome h3 { font-size: 18px; color: #111827; margin: 0 0 4px; }
.welcome-sub { color: #9ca3af; font-size: 13px; margin: 0 0 16px; }
.welcome-chips {
  display: flex; flex-wrap: wrap; gap: 6px; justify-content: center;
  max-width: 560px; margin: 0 auto;
}
.wc-chip {
  padding: 6px 14px; border: 1px solid #e5e7eb; border-radius: 16px;
  background: #fff; color: #374151; font-size: 12px; cursor: pointer;
  transition: all 0.12s;
}
.wc-chip:hover { border-color: var(--primary); color: var(--primary); background: #f5f3ff; }

/* Preview panel */
.preview-panel {
  margin-top: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fafbfc;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border-bottom: 1px solid #e5e7eb;
  flex-wrap: wrap;
  gap: 8px;
}
.preview-title { font-size: 14px; font-weight: 700; color: #4338ca; }
.preview-selected { font-weight: 500; color: #6b7280; font-size: 12px; }
.preview-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.select-all-btn {
  padding: 6px 12px; border: 1px solid #d1d5db; border-radius: 6px;
  background: #fff; color: #374151; font-size: 12px; cursor: pointer;
  transition: all 0.12s; font-weight: 500;
}
.select-all-btn:hover { border-color: var(--primary); color: var(--primary); background: #f5f3ff; }

.preview-list { padding: 8px; display: flex; flex-direction: column; gap: 8px; }
.preview-card {
  background: #fff;
  border: 1.5px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.18s cubic-bezier(0.4, 0, 0.2, 1);
}
.preview-card:hover { border-color: #c7d2fe; box-shadow: 0 2px 8px rgba(99,102,241,0.08); }
.preview-card.checked {
  border-color: var(--primary);
  background: linear-gradient(135deg, #faf5ff 0%, #fff 30%);
  box-shadow: 0 2px 10px rgba(99,102,241,0.12);
}

/* Card head: checkbox + meta + toggle */
.pc-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f9fafb;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  transition: background 0.12s;
}
.preview-card.checked .pc-card-head { background: #f5f3ff; }
.pc-card-head:hover { background: #f3f4f6; }
.preview-card.checked .pc-card-head:hover { background: #ede9fe; }

/* Custom checkbox */
.pc-check {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  flex-shrink: 0;
  position: relative;
}
.pc-check input[type="checkbox"] {
  position: absolute;
  opacity: 0;
  width: 18px;
  height: 18px;
  cursor: pointer;
  margin: 0;
}
.pc-check-box {
  width: 18px;
  height: 18px;
  border: 2px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.pc-check input:checked + .pc-check-box {
  background: var(--primary);
  border-color: var(--primary);
}
.pc-check input:checked + .pc-check-box::after {
  content: '✓';
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.pc-num { font-weight: 700; color: #111827; font-size: 14px; flex-shrink: 0; }
.pc-type {
  padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600;
  flex-shrink: 0;
}
.pc-type.type-single { background: #eef2ff; color: #6366f1; }
.pc-type.type-multiple { background: #f0fdf4; color: #16a34a; }
.pc-type.type-judge { background: #fef3c7; color: #d97706; }
.pc-type.type-fill { background: #fce7f3; color: #db2777; }
.pc-type.type-short { background: #e0e7ff; color: #4338ca; }
.pc-type.type-coding { background: #cffafe; color: #0891b2; }
.pc-diff {
  padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 500;
  flex-shrink: 0;
}
.pc-diff.diff-easy { background: #f0fdf4; color: #16a34a; }
.pc-diff.diff-medium { background: #fffbeb; color: #d97706; }
.pc-diff.diff-hard { background: #fef2f2; color: #dc2626; }
.pc-score {
  font-size: 11px; color: #9ca3af; font-weight: 500;
  margin-left: auto;
  flex-shrink: 0;
}
.pc-toggle {
  width: 24px; height: 24px;
  border: none; background: transparent;
  color: #6b7280; font-size: 14px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  border-radius: 4px; transition: background 0.12s;
  flex-shrink: 0;
}
.pc-toggle:hover { background: rgba(0,0,0,0.06); color: #374151; }

/* Card body */
.pc-card-body {
  padding: 12px 14px;
}
.pc-title {
  font-size: 14px; color: #1f2937; margin: 0 0 10px; line-height: 1.6;
  font-weight: 500;
  overflow-wrap: break-word;
}

/* Options */
.pc-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}
.pc-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
  transition: all 0.15s;
}
.pc-opt:hover { background: #f3f4f6; }
.pc-opt-correct {
  border-color: #22c55e;
  background: #f0fdf4;
}
.pc-opt-correct:hover { background: #dcfce7; }
.pc-opt-letter {
  width: 24px; height: 24px;
  border-radius: 50%;
  background: #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #4b5563;
  flex-shrink: 0;
}
.pc-opt-correct .pc-opt-letter {
  background: #22c55e;
  color: #fff;
}
.pc-opt-text {
  font-size: 13px; color: #374151; line-height: 1.5;
  flex: 1; min-width: 0;
  overflow-wrap: break-word;
}
.pc-opt-correct .pc-opt-text { color: #166534; font-weight: 500; }
.pc-opt-mark {
  color: #22c55e;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

/* Answer line */
.pc-answer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #fefce8;
  border: 1px solid #fde68a;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 8px;
}
.pc-answer-label { color: #92400e; font-weight: 600; }
.pc-answer-value { color: #b45309; font-weight: 700; }

/* Explanation */
.pc-explanation {
  margin-top: 6px;
  border-top: 1px dashed #e5e7eb;
  padding-top: 8px;
}
.pc-expl-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;
  padding: 4px 8px;
  border-radius: 4px;
  user-select: none;
  transition: background 0.12s;
}
.pc-expl-header:hover { background: #eef2ff; }
.pc-expl-arrow {
  font-size: 10px;
  transition: transform 0.2s;
}
.pc-expl-body {
  margin-top: 6px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: #374151;
  border: 1px solid #e5e7eb;
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

/* Scroll to bottom */
.scroll-to-bottom-btn {
  position: absolute;
  bottom: 70px;
  right: 20px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 12px rgba(99, 102, 241, 0.4);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 10;
}
.scroll-to-bottom-btn:hover {
  background: var(--primary-hover);
  transform: scale(1.1);
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.5);
}
.scroll-to-bottom-btn:active { transform: scale(0.95); }

.scroll-btn-fade-enter-active { transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1); }
.scroll-btn-fade-leave-active { transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1); }
.scroll-btn-fade-enter-from { opacity: 0; transform: translateY(10px) scale(0.8); }
.scroll-btn-fade-leave-to { opacity: 0; transform: translateY(10px) scale(0.8); }

/* Input area */
.bottom-area { flex-shrink: 0; border-top: 1px solid #f3f4f6; background: #fff; }
.input-row { padding: 10px 16px; }
.input-wrapper { position: relative; display: flex; align-items: flex-end; }
.msg-input {
  width: 100%;
  padding: 12px 44px 12px 16px;
  border: 2px solid #e5e7eb; border-radius: 14px;
  font-size: 14px; font-family: inherit; line-height: 1.5;
  resize: none; outline: none; background: #f9fafb; color: #111827;
  max-height: 150px;
  overflow-y: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
  scrollbar-width: thin;
  scrollbar-color: #d4d4d8 transparent;
}
.msg-input::-webkit-scrollbar { width: 4px; }
.msg-input::-webkit-scrollbar-track { background: transparent; }
.msg-input::-webkit-scrollbar-thumb { background: #d4d4d8; border-radius: 2px; }
.msg-input::-webkit-scrollbar-button { display: none; }
.msg-input:focus { border-color: var(--primary); background: #fff; box-shadow: 0 0 0 4px rgba(99,102,241,0.1); }
.msg-input:disabled { opacity: 0.5; }

.action-btn {
  position: absolute; right: 6px; bottom: 50%;
  transform: translateY(50%);
  width: 36px; height: 36px; border: none; border-radius: 10px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.15s; z-index: 2;
}
.send-action { background: var(--primary); color: #fff; }
.send-action:hover:not(:disabled) { background: var(--primary-hover); transform: scale(1.06); }
.send-action:disabled { opacity: 0.3; cursor: not-allowed; }
.stop-action { background: #ef4444; color: #fff; }
.stop-action:hover { background: #dc2626; transform: scale(1.06); }

/* Shared buttons */
.ghost-btn {
  padding: 6px 14px; border: 1px solid #d1d5db; border-radius: 6px;
  background: #fff; color: #6b7280; font-size: 12px; cursor: pointer;
  transition: all 0.12s;
}
.ghost-btn:hover { background: #f3f4f6; border-color: #9ca3af; }
.primary-btn {
  padding: 6px 16px; border: none; border-radius: 6px;
  background: var(--primary); color: #fff; font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.12s;
}
.primary-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.primary-btn:hover:not(:disabled) { background: var(--primary-hover); }
.add-all-btn {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}
.add-all-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
}

/* ===== Responsive ===== */
@media (max-width: 767px) {
  .aiqg-layout {
    flex-direction: column;
    max-height: none;
    height: calc(100vh - 120px);
    min-height: 360px;
  }
  .mobile-toggle { display: flex; }
  .settings-panel {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #f3f4f6;
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }
  .settings-panel.mobile-open {
    max-height: 600px;
    overflow-y: auto;
  }
  .chat-panel { flex: 1; min-height: 0; }
  .top-bar { padding-left: 100px; }
  .preview-header { flex-direction: column; align-items: flex-start; padding: 10px 12px; }
  .preview-actions { width: 100%; justify-content: flex-start; }
  .preview-list { padding: 6px; gap: 6px; }
  .pc-card-head { padding: 8px 10px; gap: 6px; flex-wrap: wrap; }
  .pc-card-body { padding: 10px; }
  .pc-title { font-size: 13px; }
  .pc-opt { padding: 6px 10px; gap: 8px; }
  .pc-opt-letter { width: 20px; height: 20px; font-size: 11px; }
  .pc-opt-text { font-size: 12px; }
  .pc-answer { padding: 6px 10px; font-size: 12px; }
  .pc-expl-body { font-size: 11px; padding: 8px 10px; }
}

/* ===== Dark mode overrides ===== */
[data-theme="dark"] .aiqg-layout {
  background: var(--ai-surface);
  border-color: var(--ai-border);
}
[data-theme="dark"] .mobile-toggle {
  background: var(--ai-surface);
  border-color: var(--ai-border);
  color: var(--ai-text-secondary);
}
[data-theme="dark"] .mobile-toggle:hover { background: var(--ai-surface-hover); }
[data-theme="dark"] .settings-panel {
  background: var(--ai-surface-soft);
  border-right-color: var(--ai-border-soft);
}
[data-theme="dark"] .sp-title { color: var(--ai-text); }
[data-theme="dark"] .sp-label { color: var(--ai-text-muted); }
[data-theme="dark"] .type-chip {
  background: var(--ai-surface);
  border-color: var(--ai-border);
  color: var(--ai-text-secondary);
}
[data-theme="dark"] .diff-card {
  background: var(--ai-surface);
  border-color: var(--ai-border);
  color: var(--ai-text-secondary);
}
[data-theme="dark"] .diff-card.active.diff-easy { background: rgba(34, 197, 94, 0.15); color: #4ade80; }
[data-theme="dark"] .diff-card.active.diff-medium { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
[data-theme="dark"] .diff-card.active.diff-hard { background: rgba(239, 68, 68, 0.15); color: #f87171; }
[data-theme="dark"] .sp-select,
[data-theme="dark"] .sp-input {
  background: var(--ai-surface);
  border-color: var(--ai-border);
  color: var(--ai-text);
}
[data-theme="dark"] .custom-toggle {
  background: var(--ai-surface);
  border-color: var(--ai-border);
  color: var(--ai-text-muted);
}
[data-theme="dark"] .count-control {
  background: var(--ai-surface);
  border-color: var(--ai-border);
}
[data-theme="dark"] .count-btn {
  background: var(--ai-surface-hover);
  color: var(--ai-text-secondary);
}
[data-theme="dark"] .count-btn:hover:not(:disabled) { background: var(--ai-border); }
[data-theme="dark"] .count-input { color: var(--ai-text); }
[data-theme="dark"] .deep-desc { color: var(--ai-text-faint); }
[data-theme="dark"] .toggle-switch { background: #3f3f5a; }
[data-theme="dark"] .top-bar {
  background: var(--ai-surface-soft);
  border-bottom-color: var(--ai-border-soft);
}
[data-theme="dark"] .tb-title { color: var(--ai-text); }
[data-theme="dark"] .tb-quota {
  color: var(--ai-text-muted);
  background: var(--ai-surface-hover);
}
[data-theme="dark"] .msg-area { scrollbar-color: var(--ai-border) transparent; }
[data-theme="dark"] .msg-area::-webkit-scrollbar-thumb { background: var(--ai-border); }
[data-theme="dark"] .welcome h3 { color: var(--ai-text); }
[data-theme="dark"] .welcome-sub { color: var(--ai-text-muted); }
[data-theme="dark"] .wc-chip {
  background: var(--ai-surface);
  border-color: var(--ai-border);
  color: var(--ai-text-secondary);
}
[data-theme="dark"] .wc-chip:hover { background: var(--ai-accent-soft); }
[data-theme="dark"] .preview-panel {
  background: var(--ai-surface-soft);
  border-color: var(--ai-border);
}
[data-theme="dark"] .preview-header {
  background: linear-gradient(135deg, rgba(129, 140, 248, 0.12) 0%, rgba(129, 140, 248, 0.06) 100%);
  border-bottom-color: var(--ai-border);
}
[data-theme="dark"] .preview-title { color: var(--ai-accent); }
[data-theme="dark"] .preview-selected { color: var(--ai-text-muted); }
[data-theme="dark"] .select-all-btn {
  background: var(--ai-surface);
  border-color: var(--ai-border);
  color: var(--ai-text-secondary);
}
[data-theme="dark"] .select-all-btn:hover { background: var(--ai-accent-soft); }
[data-theme="dark"] .preview-card {
  background: var(--ai-surface);
  border-color: var(--ai-border);
}
[data-theme="dark"] .preview-card:hover { border-color: var(--ai-accent-border); }
[data-theme="dark"] .preview-card.checked {
  background: linear-gradient(135deg, var(--ai-accent-soft) 0%, var(--ai-surface) 30%);
}
[data-theme="dark"] .pc-card-head {
  background: var(--ai-surface-hover);
  border-bottom-color: var(--ai-border-soft);
}
[data-theme="dark"] .preview-card.checked .pc-card-head { background: var(--ai-accent-soft); }
[data-theme="dark"] .pc-card-head:hover { background: var(--ai-border-soft); }
[data-theme="dark"] .preview-card.checked .pc-card-head:hover { background: rgba(129, 140, 248, 0.2); }
[data-theme="dark"] .pc-check-box {
  border-color: var(--ai-border);
  background: var(--ai-surface);
}
[data-theme="dark"] .pc-num { color: var(--ai-text); }
[data-theme="dark"] .pc-type.type-single { background: rgba(99, 102, 241, 0.15); color: #a5b4fc; }
[data-theme="dark"] .pc-type.type-multiple { background: rgba(22, 163, 74, 0.15); color: #4ade80; }
[data-theme="dark"] .pc-type.type-judge { background: rgba(217, 119, 6, 0.15); color: #fbbf24; }
[data-theme="dark"] .pc-type.type-fill { background: rgba(219, 39, 119, 0.15); color: #f472b6; }
[data-theme="dark"] .pc-type.type-short { background: rgba(67, 56, 202, 0.15); color: #a5b4fc; }
[data-theme="dark"] .pc-type.type-coding { background: rgba(8, 145, 178, 0.15); color: #22d3ee; }
[data-theme="dark"] .pc-diff.diff-easy { background: rgba(22, 163, 74, 0.15); color: #4ade80; }
[data-theme="dark"] .pc-diff.diff-medium { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
[data-theme="dark"] .pc-diff.diff-hard { background: rgba(239, 68, 68, 0.15); color: #f87171; }
[data-theme="dark"] .pc-score { color: var(--ai-text-faint); }
[data-theme="dark"] .pc-toggle { color: var(--ai-text-muted); }
[data-theme="dark"] .pc-toggle:hover { background: rgba(255, 255, 255, 0.08); color: var(--ai-text-secondary); }
[data-theme="dark"] .pc-title { color: var(--ai-text); }
[data-theme="dark"] .pc-opt {
  border-color: var(--ai-border);
  background: var(--ai-surface-hover);
}
[data-theme="dark"] .pc-opt:hover { background: var(--ai-border-soft); }
[data-theme="dark"] .pc-opt-correct {
  border-color: #22c55e;
  background: rgba(34, 197, 94, 0.12);
}
[data-theme="dark"] .pc-opt-correct:hover { background: rgba(34, 197, 94, 0.2); }
[data-theme="dark"] .pc-opt-letter {
  background: var(--ai-border);
  color: var(--ai-text-secondary);
}
[data-theme="dark"] .pc-opt-correct .pc-opt-letter { background: #22c55e; color: #fff; }
[data-theme="dark"] .pc-opt-text { color: var(--ai-text-secondary); }
[data-theme="dark"] .pc-opt-correct .pc-opt-text { color: #4ade80; }
[data-theme="dark"] .pc-answer {
  background: rgba(254, 240, 138, 0.1);
  border-color: rgba(253, 224, 71, 0.3);
}
[data-theme="dark"] .pc-answer-label { color: #fbbf24; }
[data-theme="dark"] .pc-answer-value { color: #f59e0b; }
[data-theme="dark"] .pc-explanation { border-top-color: var(--ai-border); }
[data-theme="dark"] .pc-expl-header { color: var(--ai-accent); }
[data-theme="dark"] .pc-expl-header:hover { background: var(--ai-accent-soft); }
[data-theme="dark"] .pc-expl-body {
  background: var(--ai-surface-hover);
  color: var(--ai-text-secondary);
  border-color: var(--ai-border);
}
[data-theme="dark"] .bottom-area {
  border-top-color: var(--ai-border-soft);
  background: var(--ai-surface);
}
[data-theme="dark"] .msg-input {
  background: var(--ai-surface-hover);
  border-color: var(--ai-border);
  color: var(--ai-text);
}
[data-theme="dark"] .msg-input:focus { background: var(--ai-surface); }
[data-theme="dark"] .msg-input::-webkit-scrollbar-thumb { background: var(--ai-border); }
[data-theme="dark"] .ghost-btn {
  background: var(--ai-surface);
  border-color: var(--ai-border);
  color: var(--ai-text-muted);
}
[data-theme="dark"] .ghost-btn:hover { background: var(--ai-surface-hover); border-color: var(--ai-text-faint); }

/* ===== Auto dark mode (system preference) ===== */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .aiqg-layout {
    background: var(--ai-surface);
    border-color: var(--ai-border);
  }
  :root:not([data-theme="light"]) .mobile-toggle {
    background: var(--ai-surface);
    border-color: var(--ai-border);
    color: var(--ai-text-secondary);
  }
  :root:not([data-theme="light"]) .mobile-toggle:hover { background: var(--ai-surface-hover); }
  :root:not([data-theme="light"]) .settings-panel {
    background: var(--ai-surface-soft);
    border-right-color: var(--ai-border-soft);
  }
  :root:not([data-theme="light"]) .sp-title { color: var(--ai-text); }
  :root:not([data-theme="light"]) .sp-label { color: var(--ai-text-muted); }
  :root:not([data-theme="light"]) .type-chip {
    background: var(--ai-surface);
    border-color: var(--ai-border);
    color: var(--ai-text-secondary);
  }
  :root:not([data-theme="light"]) .diff-card {
    background: var(--ai-surface);
    border-color: var(--ai-border);
    color: var(--ai-text-secondary);
  }
  :root:not([data-theme="light"]) .diff-card.active.diff-easy { background: rgba(34, 197, 94, 0.15); color: #4ade80; }
  :root:not([data-theme="light"]) .diff-card.active.diff-medium { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
  :root:not([data-theme="light"]) .diff-card.active.diff-hard { background: rgba(239, 68, 68, 0.15); color: #f87171; }
  :root:not([data-theme="light"]) .sp-select,
  :root:not([data-theme="light"]) .sp-input {
    background: var(--ai-surface);
    border-color: var(--ai-border);
    color: var(--ai-text);
  }
  :root:not([data-theme="light"]) .custom-toggle {
    background: var(--ai-surface);
    border-color: var(--ai-border);
    color: var(--ai-text-muted);
  }
  :root:not([data-theme="light"]) .count-control {
    background: var(--ai-surface);
    border-color: var(--ai-border);
  }
  :root:not([data-theme="light"]) .count-btn {
    background: var(--ai-surface-hover);
    color: var(--ai-text-secondary);
  }
  :root:not([data-theme="light"]) .count-btn:hover:not(:disabled) { background: var(--ai-border); }
  :root:not([data-theme="light"]) .count-input { color: var(--ai-text); }
  :root:not([data-theme="light"]) .deep-desc { color: var(--ai-text-faint); }
  :root:not([data-theme="light"]) .toggle-switch { background: #3f3f5a; }
  :root:not([data-theme="light"]) .top-bar {
    background: var(--ai-surface-soft);
    border-bottom-color: var(--ai-border-soft);
  }
  :root:not([data-theme="light"]) .tb-title { color: var(--ai-text); }
  :root:not([data-theme="light"]) .tb-quota {
    color: var(--ai-text-muted);
    background: var(--ai-surface-hover);
  }
  :root:not([data-theme="light"]) .msg-area { scrollbar-color: var(--ai-border) transparent; }
  :root:not([data-theme="light"]) .msg-area::-webkit-scrollbar-thumb { background: var(--ai-border); }
  :root:not([data-theme="light"]) .welcome h3 { color: var(--ai-text); }
  :root:not([data-theme="light"]) .welcome-sub { color: var(--ai-text-muted); }
  :root:not([data-theme="light"]) .wc-chip {
    background: var(--ai-surface);
    border-color: var(--ai-border);
    color: var(--ai-text-secondary);
  }
  :root:not([data-theme="light"]) .wc-chip:hover { background: var(--ai-accent-soft); }
  :root:not([data-theme="light"]) .preview-panel {
    background: var(--ai-surface-soft);
    border-color: var(--ai-border);
  }
  :root:not([data-theme="light"]) .preview-header {
    background: linear-gradient(135deg, rgba(129, 140, 248, 0.12) 0%, rgba(129, 140, 248, 0.06) 100%);
    border-bottom-color: var(--ai-border);
  }
  :root:not([data-theme="light"]) .preview-title { color: var(--ai-accent); }
  :root:not([data-theme="light"]) .preview-selected { color: var(--ai-text-muted); }
  :root:not([data-theme="light"]) .select-all-btn {
    background: var(--ai-surface);
    border-color: var(--ai-border);
    color: var(--ai-text-secondary);
  }
  :root:not([data-theme="light"]) .select-all-btn:hover { background: var(--ai-accent-soft); }
  :root:not([data-theme="light"]) .preview-card {
    background: var(--ai-surface);
    border-color: var(--ai-border);
  }
  :root:not([data-theme="light"]) .preview-card:hover { border-color: var(--ai-accent-border); }
  :root:not([data-theme="light"]) .preview-card.checked {
    background: linear-gradient(135deg, var(--ai-accent-soft) 0%, var(--ai-surface) 30%);
  }
  :root:not([data-theme="light"]) .pc-card-head {
    background: var(--ai-surface-hover);
    border-bottom-color: var(--ai-border-soft);
  }
  :root:not([data-theme="light"]) .preview-card.checked .pc-card-head { background: var(--ai-accent-soft); }
  :root:not([data-theme="light"]) .pc-card-head:hover { background: var(--ai-border-soft); }
  :root:not([data-theme="light"]) .preview-card.checked .pc-card-head:hover { background: rgba(129, 140, 248, 0.2); }
  :root:not([data-theme="light"]) .pc-check-box {
    border-color: var(--ai-border);
    background: var(--ai-surface);
  }
  :root:not([data-theme="light"]) .pc-num { color: var(--ai-text); }
  :root:not([data-theme="light"]) .pc-type.type-single { background: rgba(99, 102, 241, 0.15); color: #a5b4fc; }
  :root:not([data-theme="light"]) .pc-type.type-multiple { background: rgba(22, 163, 74, 0.15); color: #4ade80; }
  :root:not([data-theme="light"]) .pc-type.type-judge { background: rgba(217, 119, 6, 0.15); color: #fbbf24; }
  :root:not([data-theme="light"]) .pc-type.type-fill { background: rgba(219, 39, 119, 0.15); color: #f472b6; }
  :root:not([data-theme="light"]) .pc-type.type-short { background: rgba(67, 56, 202, 0.15); color: #a5b4fc; }
  :root:not([data-theme="light"]) .pc-type.type-coding { background: rgba(8, 145, 178, 0.15); color: #22d3ee; }
  :root:not([data-theme="light"]) .pc-diff.diff-easy { background: rgba(22, 163, 74, 0.15); color: #4ade80; }
  :root:not([data-theme="light"]) .pc-diff.diff-medium { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
  :root:not([data-theme="light"]) .pc-diff.diff-hard { background: rgba(239, 68, 68, 0.15); color: #f87171; }
  :root:not([data-theme="light"]) .pc-score { color: var(--ai-text-faint); }
  :root:not([data-theme="light"]) .pc-toggle { color: var(--ai-text-muted); }
  :root:not([data-theme="light"]) .pc-toggle:hover { background: rgba(255, 255, 255, 0.08); color: var(--ai-text-secondary); }
  :root:not([data-theme="light"]) .pc-title { color: var(--ai-text); }
  :root:not([data-theme="light"]) .pc-opt {
    border-color: var(--ai-border);
    background: var(--ai-surface-hover);
  }
  :root:not([data-theme="light"]) .pc-opt:hover { background: var(--ai-border-soft); }
  :root:not([data-theme="light"]) .pc-opt-correct {
    border-color: #22c55e;
    background: rgba(34, 197, 94, 0.12);
  }
  :root:not([data-theme="light"]) .pc-opt-correct:hover { background: rgba(34, 197, 94, 0.2); }
  :root:not([data-theme="light"]) .pc-opt-letter {
    background: var(--ai-border);
    color: var(--ai-text-secondary);
  }
  :root:not([data-theme="light"]) .pc-opt-correct .pc-opt-letter { background: #22c55e; color: #fff; }
  :root:not([data-theme="light"]) .pc-opt-text { color: var(--ai-text-secondary); }
  :root:not([data-theme="light"]) .pc-opt-correct .pc-opt-text { color: #4ade80; }
  :root:not([data-theme="light"]) .pc-answer {
    background: rgba(254, 240, 138, 0.1);
    border-color: rgba(253, 224, 71, 0.3);
  }
  :root:not([data-theme="light"]) .pc-answer-label { color: #fbbf24; }
  :root:not([data-theme="light"]) .pc-answer-value { color: #f59e0b; }
  :root:not([data-theme="light"]) .pc-explanation { border-top-color: var(--ai-border); }
  :root:not([data-theme="light"]) .pc-expl-header { color: var(--ai-accent); }
  :root:not([data-theme="light"]) .pc-expl-header:hover { background: var(--ai-accent-soft); }
  :root:not([data-theme="light"]) .pc-expl-body {
    background: var(--ai-surface-hover);
    color: var(--ai-text-secondary);
    border-color: var(--ai-border);
  }
  :root:not([data-theme="light"]) .bottom-area {
    border-top-color: var(--ai-border-soft);
    background: var(--ai-surface);
  }
  :root:not([data-theme="light"]) .msg-input {
    background: var(--ai-surface-hover);
    border-color: var(--ai-border);
    color: var(--ai-text);
  }
  :root:not([data-theme="light"]) .msg-input:focus { background: var(--ai-surface); }
  :root:not([data-theme="light"]) .msg-input::-webkit-scrollbar-thumb { background: var(--ai-border); }
  :root:not([data-theme="light"]) .ghost-btn {
    background: var(--ai-surface);
    border-color: var(--ai-border);
    color: var(--ai-text-muted);
  }
  :root:not([data-theme="light"]) .ghost-btn:hover { background: var(--ai-surface-hover); border-color: var(--ai-text-faint); }
}
</style>
