<template>
  <div class="chat-layout">
    <!-- Sidebar overlay backdrop (mobile only) -->
    <div v-if="sidebarExpanded" class="sidebar-overlay" @click="sidebarExpanded = false"></div>

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
                :class="'rec-' + rec.priority"
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
                    <button class="fb-btn fb-helpful" @click.stop="giveFeedback(rec, 'helpful')" title="有帮助">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                    </button>
                    <button class="fb-btn fb-nothelpful" @click.stop="giveFeedback(rec, 'not_helpful')" title="没帮助">
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
          :loading="currentLoading"
          :tab="activeTab"
        />
      </div>

      <!-- Bottom input area -->
      <div class="bottom-area">
        <!-- Tab switcher -->
        <div class="tab-switcher-row">
          <div class="tab-switcher">
            <button :class="{ active: activeTab === 'chat' }" @click="switchTab('chat')">💬 对话</button>
            <button :class="{ active: activeTab === 'practice' }" @click="switchTab('practice')">📝 练题</button>
          </div>
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
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
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
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useAppStore } from '@/stores/app'
import type { RecommendationItem } from '@/api/client'
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

// 个性化推荐列表
const personalizedRecommendations = computed(() => {
  return store.recommendations || []
})

function switchTab(tab: 'chat' | 'practice') {
  activeTab.value = tab
  nextTick(() => scrollToBottom(300))
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
    window.location.hash = '#/wrong-book'
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

function giveFeedback(rec: RecommendationItem, feedbackType: string) {
  store.submitFeedback(rec.type, feedbackType)
}

function refreshRecommendations() {
  store.loadRecommendations()
}

// Smart auto-scroll: pauses when user scrolls up, resumes on new message
const userScrolledUp = ref(false)
const nearBottomThreshold = 80 // px from bottom considered "at bottom"
let scrollRafId = 0

function onChatScroll() {
  const el = getScrollContainer()
  if (!el) return
  const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  // User scrolled up → pause auto-scroll. Scrolled back to bottom → resume.
  userScrolledUp.value = distFromBottom > nearBottomThreshold
}

watch(() => currentMessages.value.length, () => {
  userScrolledUp.value = false  // new message → resume auto-scroll
  nextTick(() => scrollToBottom(300))
})
watch(() => activeTab.value === 'chat' ? store.chatStreamingContent : store.practiceStreamingContent,
  () => { if (!userScrolledUp.value) scrollToBottom(0) })
watch(() => activeTab.value === 'chat' ? store.chatStreamingReasoning : store.practiceStreamingReasoning,
  () => { if (!userScrolledUp.value) scrollToBottom(0) })

/** Find the scrollable element (.chat-main which has overflow-y:auto) */
function getScrollContainer(): HTMLElement | null {
  return msgList.value?.closest('.chat-main') as HTMLElement | null
}

function scrollToBottom(duration: number) {
  const el = getScrollContainer()
  if (!el) return
  // Cancel any pending scroll animation
  if (scrollRafId) cancelAnimationFrame(scrollRafId)

  if (duration <= 0) {
    el.scrollTop = el.scrollHeight
    return
  }

  // Use requestAnimationFrame for smooth, jank-free scrolling
  const startTop = el.scrollTop
  const targetTop = el.scrollHeight - el.clientHeight
  const distance = targetTop - startTop
  if (distance <= 0) return

  const startTime = performance.now()
  function animate(now: number) {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    // Ease-out cubic for natural deceleration
    const eased = 1 - Math.pow(1 - progress, 3)
    if (el) el.scrollTop = startTop + distance * eased
    if (progress < 1) {
      scrollRafId = requestAnimationFrame(animate)
    } else {
      scrollRafId = 0
    }
  }
  scrollRafId = requestAnimationFrame(animate)
}

// 行为自动采集：页面停留时间
let pageEnterTime = 0

// Attach/detach scroll listener
onMounted(() => {
  pageEnterTime = Date.now()
  nextTick(() => {
    getScrollContainer()?.addEventListener('scroll', onChatScroll, { passive: true })
  })
  // 记录页面访问行为
  store.trackBehavior('page_view', 'page', undefined, { page: 'ai-practice' })
})
onUnmounted(() => {
  getScrollContainer()?.removeEventListener('scroll', onChatScroll)
  if (scrollRafId) cancelAnimationFrame(scrollRafId)
  // 记录页面停留时长
  if (pageEnterTime > 0) {
    const durationMs = Date.now() - pageEnterTime
    store.trackBehavior('page_leave', 'page', undefined, { page: 'ai-practice' }, durationMs)
  }
  // Stop any ongoing AI streaming requests to prevent AbortError
  store.handleChatStop()
  store.handlePracticeStop()
})
</script>

<style scoped>
/* ===== Layout ===== */
.chat-layout {
  display: flex;
  flex-direction: row;
  height: 100%;
  background: #fff;
  border: none;
  border-radius: 0;
  overflow: hidden;
  position: relative;
  gap: 0;
}

/* Hide custom scrollbar from inner elements — outer .content-panel handles scrolling */
.chat-layout::-webkit-scrollbar { display: none; }
.chat-main::-webkit-scrollbar { display: none; }
.msg-area::-webkit-scrollbar { display: none; }

/* ===== Messages ===== */
.msg-area {
  flex: 1;
  overflow: visible; /* no inner scrollbar — page scrolls naturally */
  padding: 18px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
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
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: #d1d5db transparent;
}

/* ===== Tab switcher (moved to bottom area) ===== */
.tab-switcher-row {
  display: flex;
  justify-content: center;
  padding: 6px 16px 0;
}

/* Tab switcher */
.tab-switcher {
  display: flex;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
}
.tab-switcher button {
  padding: 6px 16px;
  border: none;
  background: transparent;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}
.tab-switcher button.active {
  background: #1e1b4b;
  color: #fff;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0,0,0,0.2);
  border-radius: 8px;
}
.tab-switcher button:not(.active):hover {
  background: #f3f4f6;
  color: #374151;
}

/* Welcome */
.welcome {
  text-align: center;
  padding: 30px 16px;
  margin: auto;
}

.welcome-icon { font-size: 36px; margin-bottom: 10px; }
.welcome h3 { font-size: 17px; color: #111827; margin: 0 0 12px; font-weight: 600; }

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
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fff;
  color: #374151;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
}
.wc-chip:hover { border-color: #9ca3af; background: #f9fafb; }

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
  color: #374151;
}
.rec-refresh {
  background: none;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 4px 8px;
  cursor: pointer;
  color: #6b7280;
  display: flex;
  align-items: center;
  transition: all 0.15s;
}
.rec-refresh:hover { background: #f3f4f6; color: #374151; }
.rec-refresh:disabled { opacity: 0.5; cursor: not-allowed; }
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.rec-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
  gap: 8px;
}
.rec-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.15s;
  background: #fff;
  position: relative;
}
.rec-card:hover { border-color: #a5b4fc; box-shadow: 0 2px 8px rgba(99,102,241,0.1); transform: translateY(-1px); }
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
  color: #111827;
  margin-bottom: 4px;
  line-height: 1.4;
}
.rec-card-desc {
  font-size: 11px;
  color: #6b7280;
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
  border: 1px solid #6366f1;
  border-radius: 8px;
  background: #fff;
  color: #6366f1;
  cursor: pointer;
  transition: all 0.12s;
}
.rec-action-btn:hover { background: #6366f1; color: #fff; }

.rec-feedback {
  display: flex;
  gap: 4px;
}
.fb-btn {
  background: none;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 3px 6px;
  cursor: pointer;
  color: #9ca3af;
  display: flex;
  align-items: center;
  transition: all 0.12s;
}
.fb-btn:hover { border-color: #d1d5db; }
.fb-helpful:hover { color: #10b981; border-color: #10b981; background: #f0fdf4; }
.fb-nothelpful:hover { color: #ef4444; border-color: #ef4444; background: #fef2f2; }

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
  padding: 12px 48px 12px 14px;  /* right padding for button */
  border: 2px solid #e5e7eb;
  border-radius: 14px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
  resize: none;
  outline: none;
  background: #f9fafb;
  color: #111827;
  min-height: 44px;
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
  transform: translateY(-50%) scale(1.06);
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
  transform: translateY(-50%) scale(1.06);
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
    height: 100%;
  }

  .tab-switcher button {
    padding: 4px 10px;
    font-size: 11px;
  }

  .msg-area {
    padding: 12px 8px;
    gap: 8px;
  }

  .welcome { padding: 20px 10px; }
  .welcome h3 { font-size: 15px; }
  .wc-chip { font-size: 11px; padding: 4px 8px; }

  .quick-chips { padding: 4px 8px 0; gap: 4px; }
  .quick-chips button { font-size: 10px; padding: 3px 8px; }

  .input-row { padding: 6px 8px; }
  .msg-input {
    padding: 10px 40px 10px 12px;
    font-size: 13px;
    border-radius: 12px;
  }
  .action-btn {
    width: 36px; height: 36px;
    right: 6px;
    border-radius: 10px;
  }

  .rec-list {
    grid-template-columns: 1fr;
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
}
</style>
