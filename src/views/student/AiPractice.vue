<template>
  <div class="chat-layout">
    <!-- Sidebar overlay backdrop (mobile only) -->
    <div v-if="sidebarExpanded" class="sidebar-overlay" @click="sidebarExpanded = false"></div>

    <!-- Sidebar -->
    <ChatSidebar v-model:expanded="sidebarExpanded" />

    <!-- Main chat area -->
    <div class="chat-main">
      <!-- Top bar -->
      <header class="top-bar">
        <div class="tb-left">
          <span class="tb-title">{{ activeTab === 'chat' ? 'AI 对话' : 'AI 练题' }}</span>
        </div>
        <div class="tb-right">
          <!-- Tab switcher -->
          <div class="tab-switcher">
            <button :class="{ active: activeTab === 'chat' }" @click="switchTab('chat')">💬 对话</button>
            <button :class="{ active: activeTab === 'practice' }" @click="switchTab('practice')">📝 练题</button>
          </div>
        </div>
      </header>

      <!-- Messages area -->
      <div ref="msgList" class="msg-area">
        <!-- Welcome -->
        <div v-if="currentMessages.length === 0 && !currentLoading" class="welcome">
          <div class="welcome-icon">{{ activeTab === 'chat' ? '👋' : '📝' }}</div>
          <h3>{{ activeTab === 'chat' ? '有什么可以帮助你的？' : 'AI 智能练题' }}</h3>
          <div class="welcome-chips" v-if="activeTab === 'chat'">
            <button v-for="s in suggestions" :key="s" class="wc-chip" @click="send(s)">{{ s }}</button>
          </div>
          <div class="welcome-chips" v-if="activeTab === 'practice'">
            <button v-for="c in practiceQuickChips" :key="c.label" class="wc-chip" @click="send(c.prompt)">{{ c.icon }} {{ c.label }}</button>
          </div>
        </div>

        <!-- Messages -->
        <MessageBubbles
          :messages="currentMessages"
          :streaming-active="currentStreamingActive"
          :streaming-reasoning="currentStreamingReasoning"
          :loading="currentLoading"
          :tab="activeTab"
        />
      </div>

      <!-- Bottom input area -->
      <div class="bottom-area">
        <!-- Follow-up suggestion chips based on user preferences (shown during active conversation) -->
        <div v-if="currentMessages.length > 0 && recentThemes.length > 0 && !currentLoading" class="quick-chips">
          <button
            v-for="theme in recentThemes"
            :key="theme"
            @click="send(activeTab === 'practice' ? `帮我出关于「${theme}」的练习题，附详细解析` : `请深入讲解「${theme}」`)"
          >
            {{ theme }}
          </button>
        </div>

        <!-- Input with inline send/stop button -->
        <div class="input-row">
          <div class="input-wrapper">
            <textarea
              ref="inputRef"
              v-model="inputText"
              class="msg-input"
              rows="1"
              :placeholder="activeTab === 'chat' ? '输入消息，Enter 发送，Shift+Enter 换行' : '输入出题需求，Enter 发送'"
              :disabled="currentLoading"
              @keydown="onKeydown"
              @input="autoResize"
            ></textarea>
            <!-- Unified send/stop button inside the input -->
            <button
              v-if="!currentStreamingActive"
              class="action-btn send-action"
              :disabled="!inputText.trim() || currentLoading"
              @click="doSend"
              title="发送 (Enter)"
            >
              <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
              </svg>
            </button>
            <button
              v-else
              class="action-btn stop-action"
              @click="stopStreaming"
              title="停止生成"
            >
              <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <rect x="4" y="4" width="16" height="16" rx="2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useAppStore } from '@/stores/app'
import MessageBubbles from './AiPractice/MessageBubbles.vue'
import ChatSidebar from './AiPractice/ChatSidebar.vue'

const store = useAppStore()

const activeTab = ref<'chat' | 'practice'>(store.activeTab || 'chat')
// Keep store in sync with local tab
watch(activeTab, (val) => { store.activeTab = val })
const sidebarExpanded = ref(window.innerWidth > 768)
const inputText = ref('')
const msgList = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)

// Current session state
const currentMessages = computed(() => activeTab.value === 'chat' ? store.chatMessages : store.practiceMessages)
const currentLoading = computed(() => activeTab.value === 'chat' ? store.chatLoading : store.practSessionLoading)
const currentStreamingActive = computed(() => activeTab.value === 'chat' ? store.chatStreamingActive : store.practiceStreamingActive)
const currentStreamingReasoning = computed(() => activeTab.value === 'chat' ? store.chatStreamingReasoning : store.practiceStreamingReasoning)

// Personalized suggestions from user preferences (with defaults as fallback)
const defaultPracticeQuickChips = [
  { icon: '📐', label: '高等数学', prompt: '帮我出5道高等数学单选题，涵盖微积分、线性代数，难度较难，附详细解析' },
  { icon: '📖', label: '大学语文', prompt: '帮我出5道大学语文单选题，涵盖诗词鉴赏、文言文阅读、文学常识，附详细解析' },
  { icon: '🏛️', label: '马克思主义', prompt: '帮我出5道马克思主义基本原理单选题，涵盖唯物辩证法、剩余价值理论、科学社会主义，附详细解析' },
  { icon: '🗳️', label: '政治', prompt: '帮我出5道政治理论单选题，涵盖中国特色社会主义、时政热点、思想道德修养，附详细解析' },
  { icon: '📜', label: '中国近现代史', prompt: '帮我出5道中国近现代史判断题，涵盖鸦片战争到改革开放重要事件，附详细解析' },
  { icon: '🇬🇧', label: '大学英语', prompt: '帮我出5道大学英语单选题，涵盖词汇辨析、语法结构、阅读理解，附详细解析' },
  { icon: '⚛️', label: '大学物理', prompt: '帮我出5道大学物理填空题，涵盖力学、电磁学、热学，附详细解析' },
  { icon: '💻', label: '计算机基础', prompt: '帮我出5道计算机基础单选题，涵盖数据结构、操作系统、计算机网络，附详细解析' },
]

const defaultSuggestions = [
  '光合作用的基本原理是什么？',
  '如何提高学习效率？',
  '什么是勾股定理？',
  '太阳系有哪些行星？',
  '中国有多少个省份？',
  '怎样写好一篇作文？',
]

// Use personalized data from store, falling back to defaults
const practiceQuickChips = computed(() => {
  const prefs = store.userPreferences
  if (prefs && prefs.practiceTopics && prefs.practiceTopics.length > 0) {
    return prefs.practiceTopics.map(t => ({
      icon: t.icon || '📚',
      label: t.label,
      prompt: t.prompt,
    }))
  }
  return defaultPracticeQuickChips
})

const suggestions = computed(() => {
  const prefs = store.userPreferences
  if (prefs && prefs.suggestions && prefs.suggestions.length > 0) {
    return prefs.suggestions.map(s => s.text)
  }
  return defaultSuggestions
})

// Recent themes for follow-up suggestions (shown during conversation)
const recentThemes = computed(() => {
  const prefs = store.userPreferences
  if (prefs && prefs.recentThemes && prefs.recentThemes.length > 0) {
    return prefs.recentThemes.slice(0, 4)
  }
  return []
})

function switchTab(tab: 'chat' | 'practice') {
  activeTab.value = tab
  nextTick(() => scrollToBottom(300))
}

function send(text: string) {
  if (!text.trim()) return
  if (activeTab.value === 'chat') {
    store.handleChatSend(text.trim())
  } else {
    store.handlePracticeSend(text.trim())
  }
}

function doSend() {
  const text = inputText.value.trim()
  if (!text) return
  inputText.value = ''
  send(text)
}

function stopStreaming() {
  if (activeTab.value === 'chat') store.handleChatStop()
  else store.handlePracticeStop()
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

// Scroll — instant during streaming (no jitter), smooth for new messages
watch(() => currentMessages.value.length, () => nextTick(() => scrollToBottom(300)))
watch(() => activeTab.value === 'chat' ? store.chatStreamingContent : store.practiceStreamingContent,
  () => scrollToBottom(0))  // instant during streaming — no animation jitter
watch(() => activeTab.value === 'chat' ? store.chatStreamingReasoning : store.practiceStreamingReasoning,
  () => scrollToBottom(0))

/** Find the scrollable element (.chat-main which has overflow-y:auto) */
function getScrollContainer(): HTMLElement | null {
  return msgList.value?.closest('.chat-main') as HTMLElement | null
}

function scrollToBottom(duration: number) {
  const el = getScrollContainer()
  if (!el) return
  if (duration <= 0) {
    // Instant snap — no animation jitter during rapid streaming updates
    el.scrollTop = el.scrollHeight
    return
  }
  // Smooth scroll for new message arrival
  el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
}
</script>

<style scoped>
/* ===== Layout ===== */
.chat-layout {
  display: flex;
  flex-direction: row;
  height: calc(100vh - 180px);
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
}

/* Hide custom scrollbar from inner elements — outer .content-panel handles scrolling */
.chat-layout::-webkit-scrollbar { display: none; }
.chat-main::-webkit-scrollbar { display: none; }
.msg-area::-webkit-scrollbar { display: none; }

/* ===== Messages ===== */
.msg-area {
  flex: 1;
  overflow: visible; /* no inner scrollbar — page scrolls naturally */
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ===== Main ===== */
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  overflow-y: auto;
  overflow-x: hidden;
}

/* ===== Top bar ===== */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-bottom: 1px solid #f3f4f6;
  background: #fafbfc;
  flex-shrink: 0;
}

.tb-left { display: flex; align-items: center; gap: 8px; }
.tb-title { font-size: 14px; font-weight: 600; color: #111827; }
.tb-right { display: flex; align-items: center; gap: 12px; }

/* Tab switcher */
.tab-switcher {
  display: flex;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}
.tab-switcher button {
  padding: 5px 14px;
  border: none;
  background: #fff;
  color: #6b7280;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
}
.tab-switcher button.active {
  background: #1e1b4b;
  color: #fff;
  font-weight: 600;
  box-shadow: inset 0 1px 3px rgba(0,0,0,0.2);
}
.tab-switcher button:not(.active):hover { background: #f3f4f6; }

/* Welcome */
.welcome {
  text-align: center;
  padding: 40px 20px;
  margin: auto;
}

.welcome-icon { font-size: 40px; margin-bottom: 12px; }
.welcome h3 { font-size: 18px; color: #111827; margin: 0 0 16px; font-weight: 600; }

.welcome-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
  max-width: 560px;
  margin: 0 auto;
}

.wc-chip {
  padding: 6px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fff;
  color: #374151;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
}
.wc-chip:hover { border-color: #9ca3af; background: #f9fafb; }

/* ===== Bottom area ===== */
.bottom-area {
  flex-shrink: 0;
  border-top: 1px solid #f3f4f6;
  background: #fff;
}

.quick-chips {
  display: flex;
  gap: 6px;
  padding: 8px 16px 0;
  overflow-x: auto;
}
.quick-chips button {
  padding: 4px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #f9fafb;
  color: #6b7280;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.1s;
}
.quick-chips button:hover { background: #f3f4f6; border-color: #d1d5db; }

/* Input row */
.input-row {
  padding: 10px 16px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: flex-end;
}

.msg-input {
  width: 100%;
  padding: 14px 52px 14px 18px;  /* right padding for button */
  border: 2px solid #e5e7eb;
  border-radius: 14px;
  font-size: 15px;
  font-family: inherit;
  line-height: 1.6;
  resize: none;
  outline: none;
  background: #f9fafb;
  color: #111827;
  min-height: 48px;
  max-height: 200px;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.msg-input:focus {
  border-color: #6366f1;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(99,102,241,0.1);
}
.msg-input:disabled { opacity: 0.5; }

/* Unified action button inside input */
.action-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  z-index: 2;
}
.send-action {
  background: #6366f1;
  color: #fff;
}
.send-action:hover:not(:disabled) {
  background: #4f46e5;
  transform: scale(1.06);
  box-shadow: 0 2px 8px rgba(99,102,241,0.35);
}
.send-action:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.stop-action {
  background: #ef4444;
  color: #fff;
  animation: stop-pulse 2s ease-in-out infinite;
}
.stop-action:hover {
  background: #dc2626;
  transform: scale(1.06);
  box-shadow: 0 2px 8px rgba(239,68,68,0.35);
}
@keyframes stop-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239,68,68,0.4); }
  50% { box-shadow: 0 0 0 8px rgba(239,68,68,0); }
}

/* Sidebar overlay backdrop — mobile only */
.sidebar-overlay {
  display: none;
}

/* ===== Responsive — Tablet & Mobile ===== */
@media (max-width: 768px) {
  .sidebar-overlay {
    display: block;
    position: absolute;
    inset: 0;
    background: rgba(0,0,0,0.3);
    z-index: 25;
  }
  .chat-layout {
    min-height: calc(100vh - 140px);
    border-radius: 0;
    border: none;
  }

  .top-bar {
    padding: 8px 12px;
    flex-wrap: wrap;
    gap: 8px;
  }
  .tb-right { gap: 6px; }
  .tb-title { font-size: 13px; }

  .tab-switcher button {
    padding: 4px 10px;
    font-size: 11px;
  }

  .msg-area {
    padding: 16px 10px;
    gap: 10px;
  }

  .welcome { padding: 24px 12px; }
  .welcome h3 { font-size: 15px; }
  .wc-chip { font-size: 11px; padding: 5px 10px; }

  .quick-chips { padding: 6px 10px 0; gap: 4px; }
  .quick-chips button { font-size: 10px; padding: 3px 8px; }

  .input-row { padding: 8px 10px; }
  .msg-input {
    padding: 10px 44px 10px 12px;
    font-size: 13px;
    border-radius: 12px;
  }
  .action-btn {
    width: 38px; height: 38px;
    right: 6px;
    border-radius: 10px;
  }
}
</style>
