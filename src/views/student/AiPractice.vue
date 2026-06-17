<template>
  <div class="chat-layout">
    <!-- Sidebar overlay backdrop (mobile only) -->
    <div v-if="sidebarExpanded" class="sidebar-overlay" @click="sidebarExpanded = false"></div>

    <!-- Floating sidebar control buttons (visible when sidebar is collapsed) -->
    <div v-if="!sidebarExpanded" class="sidebar-float-controls">
      <!-- History toggle button -->
      <button class="float-btn float-btn--history" @click="sidebarExpanded = true" title="展开历史记录">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="12 6 12 12 16 14"/>
        </svg>
      </button>
    </div>

    <!-- Sidebar -->
    <ChatSidebar v-model:expanded="sidebarExpanded" />

    <!-- Main chat area -->
    <div class="chat-main">
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

          <!-- 个性化推荐面板 -->
          <div v-if="personalizedRecommendations.length > 0" class="recommendations-panel">
            <div class="rec-header">
              <span class="rec-title">🎯 为你推荐</span>
              <button class="rec-refresh" @click="refreshRecommendations" :disabled="store.recommendationsLoading" title="刷新推荐">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spinning: store.recommendationsLoading }">
                  <path d="M21 2v6h-6M3 12a9 9 0 0 1 15-6.7L21 8M3 22v-6h6M21 12a9 9 0 0 1-15 6.7L3 16"/>
                </svg>
              </button>
            </div>
            <div class="rec-list">
              <div
                v-for="(rec, idx) in personalizedRecommendations"
                :key="idx"
                class="rec-card"
                :class="['rec-' + rec.priority, { 'rec-feedback-given': feedbackGivenMap[idx] }]"
                @click="handleRecommendationClick(rec)"
              >
                <div class="rec-card-header">
                  <span class="rec-type-badge" :class="'badge-' + rec.type">{{ recTypeLabel(rec.type) }}</span>
                  <span class="rec-priority-dot" :class="'dot-' + rec.priority"></span>
                </div>
                <div class="rec-card-title">{{ rec.title }}</div>
                <div class="rec-card-desc">{{ rec.description }}</div>
                <div class="rec-card-actions">
                  <button class="rec-action-btn" @click.stop="handleRecommendationClick(rec)">
                    {{ rec.action === 'wrongbook' ? '去重练' : rec.action === 'chat' ? '开始对话' : '开始练习' }}
                  </button>
                  <div class="rec-feedback">
                    <button class="fb-btn fb-helpful" :class="{ 'fb-active-helpful': feedbackGivenMap[idx] === 'helpful' }" @click.stop="giveFeedback(rec, 'helpful', idx)" :disabled="!!feedbackGivenMap[idx]" title="有帮助">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                    </button>
                    <button class="fb-btn fb-nothelpful" :class="{ 'fb-active-nothelpful': feedbackGivenMap[idx] === 'not_helpful' }" @click.stop="giveFeedback(rec, 'not_helpful', idx)" :disabled="!!feedbackGivenMap[idx]" title="没帮助">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3H10z"/><path d="M17 2h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17"/></svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Messages -->
        <MessageBubbles
          :messages="currentMessages"
          :streaming-active="currentStreamingActive"
          :streaming-reasoning="currentStreamingReasoning"
          :streaming-content="currentStreamingContent"
          :streaming-hint="currentStreamingHint"
          :typewriter-content="currentTypewriterContent"
          :feedback-message="currentFeedbackMessage"
          :feedback-visible="currentFeedbackVisible"
          :streaming-questions="currentStreamingQuestions"
          :loading="currentLoading"
          :tab="activeTab"
          @regenerate="handleRegenerate"
        />
      </div>

      <!-- 返回底部按钮 -->
      <transition name="scroll-btn-fade">
        <button v-if="showScrollToBottom" class="scroll-to-bottom-btn" @click="handleScrollToBottom" title="返回底部">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>
      </transition>

      <!-- Floating input trigger button (visible when input panel is collapsed) -->
      <transition name="float-input-fade">
        <button
          v-if="!inputPanelExpanded && showInputTrigger"
          class="float-input-trigger"
          @click="handleTriggerClick"
          title="打开输入框"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
        </button>
      </transition>

      <!-- Floating input panel (dropdown from side) -->
      <transition name="input-panel-slide">
        <div v-if="inputPanelExpanded" class="float-input-panel">
          <!-- Panel header with tab switcher and collapse button -->
          <div class="panel-header">
            <div class="tab-switcher">
              <button :class="{ active: activeTab === 'chat' }" @click="switchTab('chat')">💬 对话</button>
              <button :class="{ active: activeTab === 'practice' }" @click="switchTab('practice')">📝 练题</button>
            </div>
            <button class="panel-collapse-btn" @click="handleCollapseClick" title="收起输入框">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 15 12 9 18 15"/>
              </svg>
            </button>
          </div>

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
                :placeholder="activeTab === 'chat' ? '输入消息发送对话' : '输入出题需求发送'"
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
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                </svg>
              </button>
              <button
                v-else
                class="action-btn stop-action"
                @click="stopStreaming"
                title="停止生成"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <rect x="4" y="4" width="16" height="16" rx="2"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import type { RecommendationItem } from '@/api/client'
import { useAutoScroll } from '@/composables/useAutoScroll'
import MessageBubbles from './AiPractice/MessageBubbles.vue'
import ChatSidebar from './AiPractice/ChatSidebar.vue'

const store = useAppStore()
const router = useRouter()

const activeTab = ref<'chat' | 'practice'>(store.activeTab || 'chat')
// Keep store in sync with local tab (bidirectional)
watch(activeTab, (val) => { store.activeTab = val })
watch(() => store.activeTab, (val) => { if (val && val !== activeTab.value) activeTab.value = val })
const sidebarExpanded = ref(window.innerWidth > 768)
const inputText = ref('')
const msgList = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const inputPanelExpanded = ref(false)
const showInputTrigger = ref(true)
// Prevents auto-hide during programmatic scroll-to-bottom
let programmaticScrolling = false
// Pauses auto-show/hide after manual collapse; re-enabled on next user scroll
let autoShowPaused = false

// Near-bottom detection: panel shows/hides based on distance from bottom (percentage)
const nearBottomRatio = 0.15 // 15% of viewport height from bottom
// Hysteresis: the hide threshold is larger than the show threshold by an amount
// greater than the panel height. This breaks the feedback loop where toggling
// the panel changes msg-area height → changes scroll metrics → toggles panel again.
const panelHeightEstimate = 160
let panelTransitioning = false

function checkNearBottom() {
  const el = msgList.value
  if (!el) return
  // Always show panel on welcome screen (no messages)
  if (currentMessages.value.length === 0) {
    showInputTrigger.value = false
    if (!inputPanelExpanded.value) inputPanelExpanded.value = true
    return
  }
  const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  // Hysteresis: use a larger threshold to hide the panel than to show it.
  // When panel is expanded, require scrolling further away to hide it.
  // When panel is collapsed, require scrolling closer to show it.
  const showThreshold = el.clientHeight * nearBottomRatio
  const hideThreshold = showThreshold + panelHeightEstimate
  const threshold = inputPanelExpanded.value ? hideThreshold : showThreshold
  const isNearBottom = distanceFromBottom <= threshold
  // Show floating trigger when panel is hidden (far from bottom)
  showInputTrigger.value = !isNearBottom
  // Don't auto-hide during programmatic scroll-to-bottom
  if (programmaticScrolling) return
  // Pause auto-show/hide after manual collapse until user scrolls
  if (autoShowPaused) return
  // Prevent toggle during CSS transition to avoid layout thrashing
  if (panelTransitioning) return
  // Don't auto-hide if user is typing or has unsent text
  if (document.activeElement === inputRef.value || inputText.value.trim()) {
    return
  }
  // Auto-show/hide panel based on distance from bottom (with hysteresis)
  if (isNearBottom !== inputPanelExpanded.value) {
    panelTransitioning = true
    inputPanelExpanded.value = isNearBottom
    // Re-enable after CSS transition completes (panel slide: 0.25s enter / 0.2s leave)
    setTimeout(() => {
      panelTransitioning = false
    }, 350)
  }
}

// Manual collapse: hide panel and pause auto-show/hide until next genuine user scroll.
// Sets panelTransitioning to guard against layout-induced scroll events that fire
// when the panel collapse changes msg-area height (which would otherwise immediately
// re-trigger auto-show).
function handleCollapseClick() {
  inputPanelExpanded.value = false
  autoShowPaused = true
  panelTransitioning = true
  setTimeout(() => {
    panelTransitioning = false
  }, 400)
}

// Trigger button: expand panel WITHOUT forcing scroll to bottom.
// Scrolling to bottom + auto-follow is deferred to the send action.
function handleTriggerClick() {
  inputPanelExpanded.value = true
  autoShowPaused = false
  // Focus the input so the user can start typing immediately
  nextTick(() => {
    inputRef.value?.focus()
  })
}

// Current session state
const currentMessages = computed(() => activeTab.value === 'chat' ? store.chatMessages : store.practiceMessages)
const currentLoading = computed(() => activeTab.value === 'chat' ? store.chatLoading : store.practSessionLoading)
const currentStreamingActive = computed(() => activeTab.value === 'chat' ? store.chatStreamingActive : store.practiceStreamingActive)
const currentStreamingReasoning = computed(() => activeTab.value === 'chat' ? store.chatStreamingReasoning : store.practiceStreamingReasoning)
const currentStreamingContent = computed(() => activeTab.value === 'chat' ? store.chatDisplayContent : store.practiceDisplayContent)
const currentStreamingHint = computed(() => activeTab.value === 'chat' ? store.chatStreamingHint : store.practiceStreamingHint)
const currentTypewriterContent = computed(() => activeTab.value === 'chat' ? store.chatTypewriterContent : '')
const currentFeedbackMessage = computed(() => activeTab.value === 'chat' ? store.chatFeedbackMessage : store.practiceFeedbackMessage)
const currentFeedbackVisible = computed(() => activeTab.value === 'chat' ? store.chatFeedbackVisible : store.practiceFeedbackVisible)
const currentStreamingQuestions = computed(() => activeTab.value === 'practice' ? store.practiceStreamingQuestions : [])

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

// 个性化推荐列表
const personalizedRecommendations = computed(() => {
  return store.recommendations || []
})

// 已反馈的推荐项索引（用于即时视觉反馈）
const feedbackGivenMap = ref<Record<number, 'helpful' | 'not_helpful'>>({})

function switchTab(tab: 'chat' | 'practice') {
  activeTab.value = tab
  nextTick(() => {
    programmaticScrolling = true
    scrollToBottom()
    setTimeout(() => {
      programmaticScrolling = false
      checkNearBottom()
    }, 350)
  })
}

function send(text: string) {
  if (!text.trim()) return
  // 记录行为日志
  store.trackBehavior(activeTab.value === 'chat' ? 'chat' : 'practice', 'conversation', undefined, { message: text.trim() })
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
  // Force scroll to bottom and re-enable auto-follow on send confirm
  nextTick(() => {
    programmaticScrolling = true
    scrollToBottom()
    setTimeout(() => {
      programmaticScrolling = false
      checkNearBottom()
    }, 350)
  })
}

function stopStreaming() {
  if (activeTab.value === 'chat') store.handleChatStop()
  else store.handlePracticeStop()
}

/** Handle regenerate event from MessageBubbles — re-sends the user message */
function handleRegenerate(msgIndex: number) {
  // Don't regenerate while streaming
  if (currentStreamingActive.value) return
  store.handleRetryMessage(msgIndex, activeTab.value)
  // Scroll to bottom after regenerate
  nextTick(() => {
    programmaticScrolling = true
    scrollToBottom()
    setTimeout(() => {
      programmaticScrolling = false
      checkNearBottom()
    }, 350)
  })
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
    const maxH = 200
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

// 推荐相关方法
function recTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    knowledge_gap: '薄弱强化',
    subject_review: '巩固提升',
    study_plan: '学习建议',
    chat: '对话推荐',
    wrongbook_retry: '错题重练',
    explore: '探索新知',
    practice: '练习推荐',
  }
  return labels[type] || '推荐'
}

function handleRecommendationClick(rec: RecommendationItem) {
  // 记录推荐点击行为
  store.trackBehavior('recommendation_click', 'recommendation', undefined, { type: rec.type, title: rec.title })

  if (rec.action === 'wrongbook') {
    // 跳转到错题本页面
    router.push('/wrong-book')
    return
  }

  if (rec.prompt) {
    // 根据推荐类型切换到对应tab
    if (rec.action === 'chat') {
      if (activeTab.value !== 'chat') switchTab('chat')
      store.handleChatSend(rec.prompt)
    } else if (rec.action === 'practice') {
      if (activeTab.value !== 'practice') switchTab('practice')
      store.handlePracticeSend(rec.prompt)
    }
  }
}

function giveFeedback(rec: RecommendationItem, feedbackType: string, idx: number) {
  // 即时视觉反馈
  feedbackGivenMap.value[idx] = feedbackType as 'helpful' | 'not_helpful'
  store.submitFeedback(rec.type, feedbackType, undefined, {
    type: rec.type,
    title: rec.title,
    description: rec.description,
    action: rec.action,
    prompt: rec.prompt,
    knowledgePoint: rec.knowledgePoint,
    subject: rec.subject,
  })
}

function refreshRecommendations() {
  feedbackGivenMap.value = {}
  store.loadRecommendations(true)
}

// Smart auto-scroll using composable
function getScrollContainer(): HTMLElement | null {
  return msgList.value as HTMLElement | null
}

const {
  userScrolledUp,
  showScrollToBottom,
  scrollToBottom,
  onStreamingUpdate,
  onNewMessage,
  handleScrollToBottom,
} = useAutoScroll(getScrollContainer, {
  nearBottomRatio: 0.2,   // 20% 视窗高度
  scrollDuration: 300,
  streamingScrollDuration: 0,
})

watch(() => currentMessages.value.length, () => {
  nextTick(() => {
    programmaticScrolling = true
    onNewMessage()
    setTimeout(() => {
      programmaticScrolling = false
      checkNearBottom()
    }, 350)
  })
})
watch(() => activeTab.value === 'chat' ? store.chatStreamingContent : store.practiceStreamingContent,
  () => onStreamingUpdate())
watch(() => activeTab.value === 'chat' ? store.chatStreamingReasoning : store.practiceStreamingReasoning,
  () => onStreamingUpdate())
// Also trigger scroll on typewriter content updates (character-by-character display)
watch(() => store.chatTypewriterContent,
  () => onStreamingUpdate())

// 行为自动采集：页面停留时间
let pageEnterTime = 0

// Attach/detach scroll listener (composable handles scroll event, we only handle lifecycle)
let scrollListenerAttached = false

// Scroll handler: clears manual-collapse pause on genuine user scroll, then runs near-bottom check.
// Layout-induced scrolls (from panel collapse/expand changing msg-area height) are ignored
// while panelTransitioning is true.
function onUserScroll() {
  // Only clear the manual-collapse pause on genuine user scrolls,
  // not on layout-induced scrolls during panel transitions
  if (autoShowPaused && !programmaticScrolling && !panelTransitioning) {
    autoShowPaused = false
  }
  checkNearBottom()
}

function attachScrollListener() {
  if (scrollListenerAttached || !msgList.value) return
  msgList.value.addEventListener('scroll', onUserScroll, { passive: true })
  scrollListenerAttached = true
  checkNearBottom()
}

function detachScrollListener() {
  if (!scrollListenerAttached || !msgList.value) return
  msgList.value.removeEventListener('scroll', onUserScroll)
  scrollListenerAttached = false
}

// Watch msgList ref to attach listener when element becomes available
watch(msgList, (el) => {
  if (el) {
    attachScrollListener()
  }
})

onMounted(() => {
  pageEnterTime = Date.now()
  // 记录页面访问行为
  store.trackBehavior('page_view', 'page', undefined, { page: 'ai-practice' })
  // 恢复练习会话（页面刷新时恢复用户进度）
  store.restorePracticeSession()
  // Attach scroll listener for near-bottom detection
  attachScrollListener()
})
onUnmounted(() => {
  if (pageEnterTime > 0) {
    const durationMs = Date.now() - pageEnterTime
    store.trackBehavior('page_leave', 'page', undefined, { page: 'ai-practice' }, durationMs)
  }
  // Do NOT stop AI streaming on unmount — chat and practice should continue
  // running in the background when the user navigates to other pages.
  // The streaming state lives in the Pinia store and persists across page switches.
  // Detach scroll listener
  detachScrollListener()
})
</script>

<style scoped>
/* ===== Layout ===== */
.chat-layout {
  display: flex;
  flex-direction: row;
  height: 100%;
  background: var(--ai-bg);
  border: none;
  border-radius: 0;
  overflow: hidden;
  position: relative;
  gap: 0;
}

/* Hide custom scrollbar from inner elements — .msg-area handles scrolling */
.chat-layout::-webkit-scrollbar { display: none; }
.msg-area::-webkit-scrollbar { width: 6px; }
.msg-area::-webkit-scrollbar-track { background: transparent; }
.msg-area::-webkit-scrollbar-thumb { background: var(--ai-border); border-radius: 3px; }

/* ===== Messages ===== */
.msg-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: var(--ai-border) transparent;
  padding: 18px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: relative;
  /* Enable touch scrolling on mobile/tablet */
  touch-action: pan-y;
  min-height: 0;
}

/* ===== Main ===== */
.chat-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--ai-bg);
  position: relative;
  /* Enable touch scrolling on mobile/tablet */
  touch-action: pan-y;
}

/* ===== 返回底部按钮 ===== */
.scroll-to-bottom-btn {
  position: absolute;
  bottom: 80px;
  right: 20px;
  width: 44px;
  height: 44px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(99, 102, 241, 0.9);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35), 0 1px 3px rgba(0, 0, 0, 0.1);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 10;
}
.scroll-to-bottom-btn svg {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
}
.scroll-to-bottom-btn:hover {
  background: rgba(79, 70, 229, 0.95);
  transform: scale(1.08);
  box-shadow: 0 6px 24px rgba(99, 102, 241, 0.45), 0 2px 6px rgba(0, 0, 0, 0.12);
}
.scroll-to-bottom-btn:active {
  transform: scale(0.96);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

/* 返回底部按钮过渡动画 */
.scroll-btn-fade-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.scroll-btn-fade-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.scroll-btn-fade-enter-from {
  opacity: 0;
  transform: translateY(12px) scale(0.85);
}
.scroll-btn-fade-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.85);
}

/* Tab switcher (inside panel header) */
.tab-switcher {
  display: flex;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--ai-border);
  background: var(--ai-surface-soft);
}
.tab-switcher button {
  padding: 6px 16px;
  border: none;
  background: transparent;
  color: var(--ai-text-muted);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}
.tab-switcher button.active {
  background: var(--ai-accent-dark);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0,0,0,0.2);
  border-radius: 8px;
}
.tab-switcher button:not(.active):hover {
  background: var(--ai-surface-hover);
  color: var(--ai-text-secondary);
}

/* Welcome */
.welcome {
  text-align: center;
  padding: 30px 16px;
  margin: auto;
}

.welcome-icon { font-size: 36px; margin-bottom: 10px; }
.welcome h3 { font-size: 17px; color: var(--ai-text); margin: 0 0 12px; font-weight: 600; }

.welcome-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  justify-content: center;
  max-width: 560px;
  margin: 0 auto;
}

.wc-chip {
  padding: 5px 12px;
  border: 1px solid var(--ai-border);
  border-radius: 16px;
  background: var(--ai-surface);
  color: var(--ai-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
}
.wc-chip:hover { border-color: var(--ai-text-faint); background: var(--ai-surface-soft); }

/* ===== Recommendations Panel ===== */
.recommendations-panel {
  max-width: 640px;
  margin: 14px auto 0;
  text-align: left;
}
.rec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 0 4px;
}
.rec-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ai-text-secondary);
}
.rec-refresh {
  background: none;
  border: 1px solid var(--ai-border);
  border-radius: 8px;
  padding: 4px 8px;
  cursor: pointer;
  color: var(--ai-text-muted);
  display: flex;
  align-items: center;
  transition: all 0.15s;
}
.rec-refresh svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.rec-refresh:hover { background: var(--ai-surface-hover); color: var(--ai-text-secondary); }
.rec-refresh:disabled { opacity: 0.5; cursor: not-allowed; }
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.rec-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
  gap: 8px;
}
.rec-card {
  border: 1px solid var(--ai-border);
  border-radius: 12px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--ai-surface);
  position: relative;
}
.rec-card:hover { border-color: var(--ai-accent-border); box-shadow: 0 2px 8px rgba(99,102,241,0.1); transform: translateY(-1px); }
.rec-card.rec-high { border-left: 3px solid #ef4444; }
.rec-card.rec-medium { border-left: 3px solid #f59e0b; }
.rec-card.rec-low { border-left: 3px solid #10b981; }

.rec-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.rec-type-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.badge-knowledge_gap { background: #fef2f2; color: #dc2626; }
.badge-subject_review { background: #fffbeb; color: #d97706; }
.badge-study_plan { background: #eff6ff; color: #2563eb; }
.badge-chat { background: #f0fdf4; color: #16a34a; }
.badge-wrongbook_retry { background: #fef2f2; color: #dc2626; }
.badge-explore { background: #faf5ff; color: #9333ea; }
.badge-practice { background: #f0fdf4; color: #16a34a; }

.rec-priority-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot-high { background: #ef4444; }
.dot-medium { background: #f59e0b; }
.dot-low { background: #10b981; }

.rec-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ai-text);
  margin-bottom: 4px;
  line-height: 1.4;
}
.rec-card-desc {
  font-size: 11px;
  color: var(--ai-text-muted);
  line-height: 1.5;
  margin-bottom: 8px;
}
.rec-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.rec-action-btn {
  font-size: 11px;
  font-weight: 500;
  padding: 4px 12px;
  border: 1px solid var(--ai-accent);
  border-radius: 8px;
  background: var(--ai-surface);
  color: var(--ai-accent);
  cursor: pointer;
  transition: all 0.12s;
}
.rec-action-btn:hover { background: var(--ai-accent); color: #fff; }

.rec-feedback {
  display: flex;
  gap: 4px;
}
.fb-btn {
  background: none;
  border: 1px solid var(--ai-border);
  border-radius: 6px;
  padding: 3px 6px;
  cursor: pointer;
  color: var(--ai-text-faint);
  display: flex;
  align-items: center;
  transition: all 0.12s;
}
.fb-btn svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.fb-btn:hover { border-color: #d1d5db; }
.fb-helpful:hover { color: #10b981; border-color: #10b981; background: #f0fdf4; }
.fb-nothelpful:hover { color: #ef4444; border-color: #ef4444; background: #fef2f2; }
.fb-btn:disabled { cursor: default; opacity: 0.9; }
.fb-active-helpful { color: #10b981 !important; border-color: #10b981 !important; background: #f0fdf4 !important; }
.fb-active-nothelpful { color: #ef4444 !important; border-color: #ef4444 !important; background: #fef2f2 !important; }
.rec-feedback-given { opacity: 0.6; }
.rec-feedback-given:hover { transform: none; box-shadow: none; }

/* ===== Floating input trigger button ===== */
.float-input-trigger {
  position: absolute;
  bottom: 20px;
  right: 20px;
  width: 44px;
  height: 44px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--ai-accent) 0%, var(--ai-accent-hover) 100%);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.4), 0 1px 3px rgba(0, 0, 0, 0.1);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 15;
}
.float-input-trigger svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}
.float-input-trigger:hover {
  background: linear-gradient(135deg, var(--ai-accent-hover) 0%, var(--ai-accent-dark) 100%);
  transform: scale(1.08);
  box-shadow: 0 6px 28px rgba(99, 102, 241, 0.5), 0 2px 6px rgba(0, 0, 0, 0.12);
}
.float-input-trigger:active {
  transform: scale(0.96);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

/* Float input trigger transition */
.float-input-fade-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.float-input-fade-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.float-input-fade-enter-from {
  opacity: 0;
  transform: translateY(16px) scale(0.8);
}
.float-input-fade-leave-to {
  opacity: 0;
  transform: translateY(16px) scale(0.8);
}

/* ===== Floating input panel ===== */
.float-input-panel {
  background: var(--ai-surface);
  border-top: 1px solid var(--ai-border);
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.1), 0 -1px 4px rgba(0, 0, 0, 0.04);
  padding-bottom: env(safe-area-inset-bottom, 0);
  flex-shrink: 0;
}

/* Panel header */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 0;
}

.panel-collapse-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--ai-border);
  border-radius: 6px;
  background: var(--ai-surface-soft);
  color: var(--ai-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.panel-collapse-btn svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.panel-collapse-btn:hover {
  background: var(--ai-surface-hover);
  color: var(--ai-text-secondary);
  border-color: var(--ai-text-faint);
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  transform: scale(1.05);
}
.panel-collapse-btn:active {
  transform: scale(0.97);
}

/* Input panel slide transition — smooth slide down/up */
.input-panel-slide-enter-active {
  transition: opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1), transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.input-panel-slide-leave-active {
  transition: opacity 0.28s cubic-bezier(0.4, 0, 0.2, 1), transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}
.input-panel-slide-enter-from {
  opacity: 0;
  transform: translateY(100%);
}
.input-panel-slide-leave-to {
  opacity: 0;
  transform: translateY(100%);
}

.quick-chips {
  display: flex;
  gap: 6px;
  padding: 8px 16px 0;
  overflow-x: auto;
}
.quick-chips button {
  padding: 4px 12px;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  background: var(--ai-surface-soft);
  color: var(--ai-text-muted);
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.1s;
}
.quick-chips button:hover { background: var(--ai-surface-hover); border-color: var(--ai-text-faint); }

/* Input row */
.input-row {
  padding: 8px 12px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}

.msg-input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 40px 10px 12px;  /* right padding reserved for send button */
  border: 2px solid var(--ai-border);
  border-radius: 14px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
  resize: none;
  outline: none;
  background: var(--ai-surface-soft);
  color: var(--ai-text);
  min-height: 44px;
  max-height: 200px;
  overflow-y: hidden;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
  scrollbar-width: thin;
  scrollbar-color: var(--ai-border) transparent;
}
.msg-input::-webkit-scrollbar {
  width: 4px;
}
.msg-input::-webkit-scrollbar-track {
  background: transparent;
}
.msg-input::-webkit-scrollbar-thumb {
  background: var(--ai-border);
  border-radius: 2px;
}
.msg-input::-webkit-scrollbar-button {
  display: none;
}
.msg-input:focus {
  border-color: var(--ai-accent);
  background: var(--ai-surface);
  box-shadow: 0 0 0 4px rgba(99,102,241,0.1);
}
.msg-input:disabled { opacity: 0.5; }

/* Unified action button inside input */
.action-btn {
  position: absolute;
  right: 6px;
  bottom: 50%;
  transform: translateY(50%);
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  z-index: 2;
}
.action-btn svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.send-action {
  background: var(--ai-accent);
  color: #fff;
}
.send-action:hover:not(:disabled) {
  background: var(--ai-accent-hover);
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

/* ===== Floating sidebar control buttons ===== */
.sidebar-float-controls {
  position: absolute;
  left: 4px;
  top: 4px;
  z-index: 30;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.float-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--ai-accent-border);
  border-radius: 4px;
  background: var(--ai-accent-soft);
  color: var(--ai-accent);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 3px rgba(99,102,241,0.1);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  -webkit-tap-highlight-color: transparent;
}
.float-btn svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.float-btn:hover {
  background: var(--ai-accent-soft);
  color: var(--ai-accent-hover);
  border-color: var(--ai-accent);
  box-shadow: 0 2px 6px rgba(99,102,241,0.18);
  transform: scale(1.05);
}
.float-btn:active {
  transform: scale(0.93);
  background: var(--ai-accent-soft);
}

/* History toggle button — always visible */
.float-btn--history {
  color: var(--ai-accent);
  border-color: var(--ai-accent-border);
  background: var(--ai-accent-soft);
}
.float-btn--history:hover {
  background: var(--ai-accent-soft);
  color: var(--ai-accent-hover);
  border-color: var(--ai-accent);
}

/* ===== Responsive — Mobile & Tablet (tab bar offset) ===== */
@media (max-width: 1023px) {
  .float-input-panel {
    /* Content panel padding already accounts for tab bar + safe area */
    padding-bottom: 0 !important;
  }
}

/* ===== Responsive — Tablet & Mobile ===== */
@media (max-width: 768px) {
  .sidebar-overlay {
    display: block;
    position: absolute;
    inset: 0;
    background: rgba(0,0,0,0.3);
    z-index: 35;
  }
  .chat-layout {
    height: 100%;
  }

  /* Floating controls on mobile */
  .sidebar-float-controls {
    left: 4px;
    top: 4px;
  }
  .float-btn {
    width: 28px;
    height: 28px;
    border-radius: 4px;
  }

  /* Space for floating buttons */
  .msg-area {
    padding: 12px 8px;
    padding-top: 46px;
    gap: 8px;
  }

  .tab-switcher button {
    padding: 4px 10px;
    font-size: 11px;
  }

  .welcome { padding: 20px 10px; padding-top: 40px; }
  .welcome h3 { font-size: 15px; }
  .wc-chip { font-size: 11px; padding: 4px 8px; }

  .quick-chips { padding: 4px 8px 0; gap: 4px; }
  .quick-chips button { font-size: 10px; padding: 3px 8px; }

  .input-row { padding: 6px 8px; }
  .msg-input {
    padding: 10px 34px 10px 12px;
    font-size: 13px;
    border-radius: 12px;
  }
  .action-btn {
    width: 28px; height: 28px;
    right: 5px;
    border-radius: 6px;
  }

  .rec-list {
    grid-template-columns: 1fr;
  }

  .scroll-to-bottom-btn {
    bottom: 80px;
    right: 12px;
    width: 40px;
    height: 40px;
    border-radius: 50%;
  }

  .float-input-trigger {
    bottom: 16px;
    right: 12px;
    width: 40px;
    height: 40px;
  }

  .float-input-panel {
    border-radius: 14px 14px 0 0;
  }

  .panel-header {
    padding: 8px 12px 0;
  }

  .panel-collapse-btn {
    width: 26px;
    height: 26px;
  }
}

/* ===== Responsive — Small mobile (≤480px) ===== */
@media (max-width: 480px) {
  .msg-area {
    padding: 8px 4px;
    padding-top: 44px;
    gap: 6px;
  }

  .welcome { padding: 16px 8px; padding-top: 36px; }
  .welcome h3 { font-size: 14px; }
  .welcome-chips { gap: 4px; }
  .wc-chip { font-size: 10px; padding: 3px 6px; }

  .quick-chips { padding: 2px 4px 0; }
  .quick-chips button { font-size: 10px; padding: 2px 6px; }

  .input-row { padding: 4px 6px; }
  .msg-input {
    padding: 8px 30px 8px 10px;
    font-size: 13px;
    border-radius: 10px;
  }
  .action-btn {
    width: 26px; height: 26px;
    right: 4px;
    border-radius: 6px;
  }

  .recommendations-panel { margin: 10px 4px 0; }

  .scroll-to-bottom-btn {
    bottom: 72px;
    right: 8px;
    width: 36px;
    height: 36px;
  }

  .float-input-trigger {
    bottom: 12px;
    right: 8px;
    width: 36px;
    height: 36px;
  }

  .float-input-panel {
    border-radius: 12px 12px 0 0;
  }
}

/* ===== Responsive — Tablet (768-1023) ===== */
@media (min-width: 769px) and (max-width: 1023px) {
  .msg-area {
    padding-top: 46px;
  }
  .sidebar-float-controls {
    left: 4px;
    top: 4px;
  }
  .float-btn {
    width: 28px;
    height: 28px;
    border-radius: 4px;
  }

  .recommendations-panel { max-width: 100%; }
  .rec-list {
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  }
}

/* ===== Responsive — Landscape on mobile ===== */
@media (max-width: 1023px) and (orientation: landscape) {
  .welcome {
    padding: 16px 10px;
  }
  .welcome-icon {
    font-size: 28px;
    margin-bottom: 6px;
  }
  .welcome h3 {
    font-size: 14px;
    margin-bottom: 8px;
  }
  .msg-area {
    padding: 8px 8px;
  }
  .input-row {
    padding: 4px 8px;
  }

  .float-input-panel {
    border-radius: 14px 14px 0 0;
  }
}

/* ===== Dark mode overrides ===== */
[data-theme="dark"] .badge-knowledge_gap,
[data-theme="dark"] .badge-wrongbook_retry { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
[data-theme="dark"] .badge-subject_review { background: rgba(217, 119, 6, 0.15); color: #fbbf24; }
[data-theme="dark"] .badge-study_plan { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
[data-theme="dark"] .badge-chat,
[data-theme="dark"] .badge-practice { background: rgba(34, 197, 94, 0.15); color: #86efac; }
[data-theme="dark"] .fb-helpful:hover,
[data-theme="dark"] .fb-active-helpful { background: rgba(16, 185, 129, 0.15) !important; }
[data-theme="dark"] .fb-nothelpful:hover,
[data-theme="dark"] .fb-active-nothelpful { background: rgba(239, 68, 68, 0.15) !important; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .badge-knowledge_gap,
  :root:not([data-theme="light"]) .badge-wrongbook_retry { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
  :root:not([data-theme="light"]) .badge-subject_review { background: rgba(217, 119, 6, 0.15); color: #fbbf24; }
  :root:not([data-theme="light"]) .badge-study_plan { background: rgba(37, 99, 235, 0.15); color: #93c5fd; }
  :root:not([data-theme="light"]) .badge-chat,
  :root:not([data-theme="light"]) .badge-practice { background: rgba(34, 197, 94, 0.15); color: #86efac; }
  :root:not([data-theme="light"]) .fb-helpful:hover,
  :root:not([data-theme="light"]) .fb-active-helpful { background: rgba(16, 185, 129, 0.15) !important; }
  :root:not([data-theme="light"]) .fb-nothelpful:hover,
  :root:not([data-theme="light"]) .fb-active-nothelpful { background: rgba(239, 68, 68, 0.15) !important; }
}
</style>
