<template>
  <div class="teacher-ai-layout">
    <!-- Chat-style AI question generation -->
    <div class="chat-main">
      <header class="top-bar">
        <div class="tb-left">
          <span class="tb-title">AI 智能出题</span>
          <span class="tb-info">已用 {{ store.aiQuotaUsed }}/{{ store.aiQuotaUsed + store.aiQuotaRemaining }}</span>
        </div>
        <button class="ghost-btn" @click="$router.push('/questions')">返回题库</button>
      </header>

      <!-- Messages area -->
      <div ref="msgList" class="msg-area">
        <div v-if="messages.length === 0 && !loading" class="welcome">
          <div class="welcome-icon">🤖</div>
          <h3>AI 智能出题助手</h3>
          <p class="welcome-sub">描述你想要的题目，AI 将为你生成并支持批量导入题库</p>
          <div class="welcome-chips">
            <button v-for="c in quickChips" :key="c.label" class="wc-chip" @click="doSend(c.prompt)">{{ c.icon }} {{ c.label }}</button>
          </div>
        </div>

        <MessageBubbles
          :messages="messages"
          :streaming-active="streamingActive"
          :streaming-reasoning="streamingReasoning"
          :loading="false"
        />

        <!-- Import bar — shown after AI generates questions -->
        <div v-if="parsedQuestions.length > 0 && !streamingActive" class="import-bar">
          <div class="import-header">
            <span>📋 已生成 {{ parsedQuestions.length }} 道题目（已选 {{ selectedCount }}）</span>
            <div class="import-actions">
              <button class="primary-btn" :disabled="selectedCount === 0" @click="importSelected">导入选中到题库</button>
              <button class="ghost-btn" @click="discardAll">放弃</button>
            </div>
          </div>
          <div v-for="(q, qi) in parsedQuestions" :key="qi" class="import-card">
            <label class="import-check">
              <input type="checkbox" :checked="selected[qi] !== false" @change="selected[qi] = !selected[qi]" />
              <span class="import-num">{{ qi + 1 }}.</span>
              <span class="import-type">{{ typeLabel(q.type as any) }}</span>
              <span class="import-diff">{{ diffLabel(q.difficulty) }}</span>
            </label>
            <p class="import-title">{{ q.title }}</p>
          </div>
        </div>
      </div>

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
              :disabled="loading"
              @keydown="onKeydown"
              @input="autoResize"
            ></textarea>
            <button v-if="!streamingActive" class="action-btn send-action" :disabled="!inputText.trim() || loading" @click="doSend">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            </button>
            <button v-else class="action-btn stop-action" @click="stopStreaming">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import { aiChatStream } from '@/api/client'
import MessageBubbles from '@/views/student/AiPractice/MessageBubbles.vue'
import type { AiQuestion } from '@/api/client'

const store = useAppStore()

const inputText = ref('')
const msgList = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const loading = ref(false)
const streamingActive = ref(false)
const streamingReasoning = ref('')
const streamingContent = ref('')
const messages = ref<Array<{ role: string; content: string; reasoning?: string }>>([])
const selected = reactive<Record<number, boolean>>({})
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

const selectedCount = computed(() => Object.values(selected).filter(Boolean).length)

const parsedQuestions = computed((): AiQuestion[] => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const content = messages.value[i].content
    if (!content) continue
    try {
      const jsonMatch = content.match(/\[[\s\S]*\]/)
      if (!jsonMatch) continue
      const parsed = JSON.parse(jsonMatch[0])
      if (Array.isArray(parsed) && parsed.length > 0 && parsed[0].title) {
        return parsed as AiQuestion[]
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

function diffLabel(d: string) {
  return { easy: '简单', medium: '中等', hard: '困难' }[d] || d
}

function doSend(text?: string) {
  const t = (text || inputText.value).trim()
  if (!t || loading.value) return
  inputText.value = ''
  sendMessage(t)
}

function sendMessage(prompt: string) {
  messages.value.push({ role: 'user', content: prompt })
  const aiIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })

  streamingActive.value = true
  streamingReasoning.value = ''
  streamingContent.value = ''
  loading.value = true

  abortCtrl = aiChatStream(
    { message: prompt, deepThinking: store.deepThinkingEnabled },
    // onChunk
    (chunk) => {
      if (chunk.type === 'reasoning') {
        streamingReasoning.value += chunk.text
      } else {
        streamingContent.value += chunk.text
        messages.value[aiIdx].content = streamingContent.value
      }
      smoothScrollToBottom(200)
    },
    // onComplete
    (data) => {
      streamingActive.value = false
      loading.value = false
      if (data.content) {
        messages.value[aiIdx].content = data.content
      }
      if (data.reasoning || streamingReasoning.value) {
        messages.value[aiIdx].reasoning = data.reasoning || streamingReasoning.value
      }
      smoothScrollToBottom(400)
    },
    // onError
    (err) => {
      streamingActive.value = false
      loading.value = false
      messages.value[aiIdx].content = '❌ ' + (err || '生成失败，请重试')
    },
  )
}

function stopStreaming() {
  if (abortCtrl) {
    abortCtrl.abort()
    abortCtrl = null
  }
  streamingActive.value = false
  loading.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    doSend()
  }
}

function autoResize() {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
    inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 150) + 'px'
  }
}

// Smooth scroll
let scrollRafId: number | null = null
let scrollTarget = 0

function smoothScrollToBottom(duration: number) {
  const el = msgList.value
  if (!el) return
  scrollTarget = el.scrollHeight
  if (scrollRafId !== null) {
    cancelAnimationFrame(scrollRafId)
    scrollRafId = null
  }
  const start = el.scrollTop
  if (scrollTarget - start < 20) { el.scrollTop = scrollTarget; return }
  const cappedTarget = Math.min(scrollTarget, start + 200)
  const startTime = performance.now()
  function animate(now: number) {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    const ease = 1 - (1 - progress) * (1 - progress)
    el!.scrollTop = start + (cappedTarget - start) * ease
    if (progress < 1) scrollRafId = requestAnimationFrame(animate)
    else { el!.scrollTop = scrollTarget; scrollRafId = null }
  }
  scrollRafId = requestAnimationFrame(animate)
}

function discardAll() {
  messages.value = []
  Object.keys(selected).forEach(k => delete selected[Number(k)])
}

async function importSelected() {
  const qs = parsedQuestions.value.filter((_, i) => selected[i] !== false)
  if (qs.length === 0) return
  await store.handleAiImportQuestions(qs)
  // Remove imported questions from preview
  discardAll()
}
</script>

<style scoped>
.teacher-ai-layout {
  display: flex;
  flex: 1;
  height: calc(100vh - 140px);
  max-height: 750px;
  min-height: 420px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}

.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

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
.tb-info { font-size: 11px; color: #9ca3af; background: #f3f4f6; padding: 2px 8px; border-radius: 4px; }

.msg-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  scroll-behavior: smooth;
}
.msg-area::-webkit-scrollbar { width: 6px; }
.msg-area::-webkit-scrollbar-track { background: transparent; }
.msg-area::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }

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
.wc-chip:hover { border-color: #9ca3af; background: #f9fafb; }

/* Import bar */
.import-bar {
  margin-top: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fafbfc;
  overflow: hidden;
}
.import-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; background: #f3f4f6; border-bottom: 1px solid #e5e7eb;
  font-size: 13px; font-weight: 600; color: #374151;
}
.import-actions { display: flex; gap: 8px; }

.import-card {
  padding: 10px 14px;
  border-bottom: 1px solid #f3f4f6;
}
.import-card:last-child { border-bottom: none; }
.import-check {
  display: flex; align-items: center; gap: 8px; cursor: pointer;
  font-size: 12px; color: #6b7280; margin-bottom: 4px;
}
.import-num { font-weight: 600; color: #374151; }
.import-type {
  padding: 1px 6px; border-radius: 4px; background: #eef2ff;
  color: #6366f1; font-size: 11px; font-weight: 500;
}
.import-diff {
  padding: 1px 6px; border-radius: 4px; background: #f3f4f6;
  color: #6b7280; font-size: 11px;
}
.import-title { font-size: 13px; color: #374151; margin: 0; line-height: 1.4; }

/* Input */
.bottom-area { flex-shrink: 0; border-top: 1px solid #f3f4f6; background: #fff; }
.input-row { padding: 10px 16px; }
.input-wrapper { position: relative; display: flex; align-items: flex-end; }
.msg-input {
  width: 100%;
  padding: 12px 52px 12px 16px;
  border: 2px solid #e5e7eb; border-radius: 14px;
  font-size: 14px; font-family: inherit; line-height: 1.5;
  resize: none; outline: none; background: #f9fafb; color: #111827;
  max-height: 150px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.msg-input:focus { border-color: #6366f1; background: #fff; box-shadow: 0 0 0 4px rgba(99,102,241,0.1); }
.msg-input:disabled { opacity: 0.5; }

.action-btn {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  width: 44px; height: 44px; border: none; border-radius: 12px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.15s; z-index: 2;
}
.send-action { background: #6366f1; color: #fff; }
.send-action:hover:not(:disabled) { background: #4f46e5; transform: scale(1.06); }
.send-action:disabled { opacity: 0.3; cursor: not-allowed; }
.stop-action { background: #ef4444; color: #fff; }
.stop-action:hover { background: #dc2626; }

/* Ghost btn */
.ghost-btn {
  padding: 6px 14px; border: 1px solid #d1d5db; border-radius: 6px;
  background: #fff; color: #6b7280; font-size: 12px; cursor: pointer;
}
.ghost-btn:hover { background: #f3f4f6; }
.primary-btn {
  padding: 6px 16px; border: none; border-radius: 6px;
  background: #6366f1; color: #fff; font-size: 12px; font-weight: 600; cursor: pointer;
}
.primary-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.primary-btn:hover:not(:disabled) { background: #4f46e5; }
</style>
