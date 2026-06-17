<template>
  <aside v-if="props.expanded" class="chat-sidebar">
    <!-- Brand header with close button -->
    <div class="sidebar-brand">
      <span class="brand-icon">🧠</span>
      <span class="brand-text">AI 助手</span>
      <button
        class="brand-collapse-btn"
        @click="emit('update:expanded', false)"
        title="收起侧栏"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 6 9 12 15 18"/>
        </svg>
      </button>
    </div>

    <!-- New chat -->
    <button class="new-chat-btn" @click="store.handleNewConversation(store.activeTab); emit('update:expanded', false)">
      <span class="nc-icon">➕</span> 新{{ store.activeTab === 'practice' ? '练题' : '对话' }}
    </button>

    <!-- Search bar -->
    <div class="search-bar">
      <input
        v-model="searchInput"
        type="text"
        class="search-input"
        placeholder="搜索历史对话..."
        @focus="searchFocused = true"
        @blur="searchFocused = false"
      />
      <button v-if="searchInput" class="search-clear" @click="clearSearch">×</button>
      <span v-else class="search-icon">🔍</span>
    </div>

    <!-- History list with time grouping -->
    <div class="history-section">
      <div v-if="store.conversationsLoading" class="conv-loading">加载中...</div>

      <template v-if="!searchKeyword">
        <!-- Grouped display -->
        <div v-if="filteredGroups.length === 0 && !store.conversationsLoading" class="conv-empty">
          暂无对话记录
        </div>
        <template v-for="group in filteredGroups" :key="group.label">
          <div class="history-group-label">{{ group.label }}</div>
          <div class="conv-list">
            <button
              v-for="conv in group.items"
              :key="conv.id"
              :class="['conv-item', { active: conv.id === (store.activeTab === 'practice' ? store.activePracticeConversationId : store.activeChatConversationId) }]"
              @click="store.handleSwitchConversation(conv.id); emit('update:expanded', false)"
            >
              <span class="ci-icon">{{ conv.sessionType === 'practice' ? '📝' : '💬' }}</span>
              <span class="ci-text">
                <span class="ci-title" :title="conv.title">{{ conv.title }}</span>
                <span class="ci-meta-row">
                  <span class="ci-badge" :class="conv.sessionType === 'practice' ? 'badge-practice' : 'badge-chat'">
                    {{ conv.sessionType === 'practice' ? '练题' : '对话' }}
                  </span>
                  <span class="ci-time">{{ formatTime(conv.updatedAt) }}</span>
                </span>
              </span>
              <button class="ci-delete" title="删除" @click.stop="onDelete(conv.id)">×</button>
            </button>
          </div>
        </template>
      </template>

      <!-- Search results -->
      <template v-else>
        <div v-if="searchResults.length === 0 && !searching" class="conv-empty">
          未找到相关对话
        </div>
        <div v-if="searching" class="conv-loading">搜索中...</div>
        <div class="conv-list">
          <button
            v-for="conv in searchResults"
            :key="conv.id"
            :class="['conv-item', { active: conv.id === store.activeConversationId }]"
            @click="store.handleSwitchConversation(conv.id); emit('update:expanded', false)"
          >
            <span class="ci-icon">{{ conv.sessionType === 'practice' ? '📝' : '💬' }}</span>
            <span class="ci-text">
              <span class="ci-title" :title="conv.title">{{ conv.title }}</span>
              <span v-if="conv.snippets && conv.snippets.length > 0" class="ci-snippet">
                {{ conv.snippets[0] }}
              </span>
              <span class="ci-meta-row">
                <span class="ci-badge" :class="conv.sessionType === 'practice' ? 'badge-practice' : 'badge-chat'">
                  {{ conv.sessionType === 'practice' ? '练题' : '对话' }}
                </span>
                <span class="ci-time">{{ formatTime(conv.updatedAt) }}</span>
              </span>
            </span>
            <button class="ci-delete" title="删除" @click.stop="onDelete(conv.id)">×</button>
          </button>
        </div>
      </template>
    </div>

  </aside>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { useDebouncedRef } from '@/composables/useDebounce'
import type { SearchResultConversation } from '@/api/client'

const props = defineProps<{ expanded: boolean }>()
const emit = defineEmits<{ 'update:expanded': [value: boolean] }>()

const store = useAppStore()

// Search state
const { debouncedValue: searchKeyword, inputValue: searchInput, setValue: setSearch } = useDebouncedRef('')
const searchFocused = ref(false)
const searchResults = ref<SearchResultConversation[]>([])
const searching = ref(false)

onMounted(() => {
  store.handleLoadConversations()
})

// Debounced search via useDebouncedRef
watch(searchKeyword, async (val) => {
  if (!val.trim()) {
    searchResults.value = []
    searching.value = false
    return
  }
  searching.value = true
  searchResults.value = await store.handleSearchConversations(val)
  searching.value = false
})

function clearSearch() {
  setSearch('')
  searchResults.value = []
  searching.value = false
}

// Time-based grouping
interface ConversationGroup {
  label: string
  items: Array<{ id: string; title: string; role: string; sessionType?: string; createdAt: string; updatedAt: string }>
}

const filteredGroups = computed<ConversationGroup[]>(() => {
  const convs = store.conversations
  if (convs.length === 0) return []

  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400_000)
  const weekStart = new Date(today.getTime() - 7 * 86400_000)
  const monthStart = new Date(today.getTime() - 30 * 86400_000)

  const groups: Record<string, ConversationGroup> = {}

  for (const conv of convs) {
    const d = new Date(conv.updatedAt)
    let label: string
    if (d >= today) {
      label = '今天'
    } else if (d >= yesterday) {
      label = '昨天'
    } else if (d >= weekStart) {
      label = '本周'
    } else if (d >= monthStart) {
      label = '本月'
    } else {
      label = '更早'
    }

    if (!groups[label]) {
      groups[label] = { label, items: [] }
    }
    groups[label].items.push(conv)
  }

  // Return in chronological group order
  const order = ['今天', '昨天', '本周', '本月', '更早']
  return order.filter(l => groups[l]).map(l => groups[l])
})

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return Math.floor(diff / 60_000) + '分钟前'
  if (diff < 86400_000) return Math.floor(diff / 3600_000) + '小时前'
  const days = Math.floor(diff / 86400_000)
  if (days < 7) return days + '天前'
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

async function onDelete(id: string) {
  const ok = await store.confirmDialog('确定删除此对话？', { title: '删除对话确认', confirmText: '删除', danger: true })
  if (ok) {
    store.handleDeleteConversation(id)
  }
}
</script>

<style scoped>
.chat-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: var(--ai-surface-soft);
  border-right: 1px solid var(--ai-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
  position: relative;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 16px 12px;
}

.brand-icon { font-size: 22px; }
.brand-text { font-size: 15px; font-weight: 700; color: var(--ai-text); flex: 1; }

.brand-collapse-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--ai-border);
  border-radius: 6px;
  background: var(--ai-surface);
  color: var(--ai-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.brand-collapse-btn svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.brand-collapse-btn:hover {
  background: var(--ai-surface-hover);
  color: var(--ai-text);
  border-color: var(--ai-text-faint);
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transform: scale(1.05);
}
.brand-collapse-btn:active {
  transform: scale(0.97);
}

.new-chat-btn {
  margin: 0 12px 8px;
  padding: 10px 14px;
  border: 1px solid var(--ai-border);
  border-radius: 10px;
  background: var(--ai-surface);
  color: var(--ai-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.12s;
}
.new-chat-btn:hover {
  border-color: var(--ai-text-faint);
  background: var(--ai-surface-soft);
}
.nc-icon { font-size: 14px; }

/* Search bar */
.search-bar {
  margin: 0 12px 8px;
  position: relative;
  display: flex;
  align-items: center;
}

.search-input {
  width: 100%;
  padding: 7px 28px 7px 10px;
  border: 1.5px solid var(--ai-border);
  border-radius: 8px;
  font-size: 12px;
  font-family: inherit;
  outline: none;
  background: var(--ai-surface);
  color: var(--ai-text-secondary);
  transition: border-color 0.15s;
}
.search-input:focus {
  border-color: var(--ai-accent);
  box-shadow: 0 0 0 3px rgba(99,102,241,0.08);
}
.search-input::placeholder { color: var(--ai-text-faint); }

.search-icon {
  position: absolute;
  right: 8px;
  font-size: 12px;
  pointer-events: none;
}

.search-clear {
  position: absolute;
  right: 4px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: var(--ai-border);
  color: var(--ai-text-muted);
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
.search-clear:hover { background: var(--ai-text-faint); }

/* History section */
.history-section {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
  scroll-behavior: smooth;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: var(--ai-border) transparent;
}

.history-section::-webkit-scrollbar { width: 4px; }
.history-section::-webkit-scrollbar-track { background: transparent; }
.history-section::-webkit-scrollbar-thumb {
  background: var(--ai-border);
  border-radius: 2px;
}

.history-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--ai-text-faint);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 8px 8px 4px;
}

.history-group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--ai-text-muted);
  padding: 10px 8px 3px;
  border-bottom: 1px solid var(--ai-border-soft);
  margin-bottom: 2px;
}

.conv-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.conv-empty, .conv-loading {
  padding: 20px 8px;
  text-align: center;
  color: var(--ai-text-faint);
  font-size: 12px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--ai-text-secondary);
  text-align: left;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.1s;
  position: relative;
}
.conv-item:hover { background: var(--ai-surface-hover); }
.conv-item.active { background: var(--ai-border); }

.ci-icon { font-size: 14px; flex-shrink: 0; }

.ci-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.ci-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--ai-text-secondary);
}

.ci-snippet {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--ai-text-faint);
  margin-top: 1px;
  font-style: italic;
}

/* Session type badge */
.ci-meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.ci-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
  flex-shrink: 0;
}
.badge-chat { background: var(--ai-accent-soft); color: var(--ai-accent); }
.badge-practice { background: #dbeafe; color: #2563eb; }

.ci-time {
  font-size: 11px;
  color: var(--ai-text-faint);
  margin-top: 1px;
}

.ci-delete {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ai-text-faint);
  font-size: 14px;
  cursor: pointer;
  display: none;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
.conv-item:hover .ci-delete { display: flex; }
.ci-delete:hover { background: #fee2e2; color: #ef4444; }

/* ===== Responsive — Mobile overlay sidebar ===== */
@media (max-width: 768px) {
  .chat-sidebar {
    position: absolute;
    left: 0; top: 0; bottom: 0;
    width: 240px;
    z-index: 40;
    box-shadow: 4px 0 16px rgba(0,0,0,0.12);
  }

  .brand-collapse-btn {
    width: 28px;
    height: 28px;
    border-radius: 6px;
  }
}

/* ===== Dark mode overrides ===== */
[data-theme="dark"] .badge-practice { background: rgba(37, 99, 235, 0.18); color: #93c5fd; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .badge-practice { background: rgba(37, 99, 235, 0.18); color: #93c5fd; }
}
</style>
