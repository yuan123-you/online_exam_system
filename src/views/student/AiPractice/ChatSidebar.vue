<template>
  <aside class="chat-sidebar" :class="{ collapsed: !props.expanded }">
    <template v-if="props.expanded">
      <!-- Logo area -->
      <div class="sidebar-brand">
        <span class="brand-icon">🧠</span>
        <span class="brand-text">AI 助手</span>
      </div>

      <!-- New chat -->
      <button class="new-chat-btn" @click="store.handleNewConversation(); emit('update:expanded', false)">
        <span class="nc-icon">➕</span> 新对话
      </button>

      <!-- Search bar -->
      <div class="search-bar">
        <input
          v-model="searchKeyword"
          type="text"
          class="search-input"
          placeholder="搜索历史对话..."
          @input="onSearchInput"
          @focus="searchFocused = true"
          @blur="searchFocused = false"
        />
        <button v-if="searchKeyword" class="search-clear" @click="clearSearch">×</button>
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
                :class="['conv-item', { active: conv.id === store.activeConversationId }]"
                @click="store.handleSwitchConversation(conv.id); emit('update:expanded', false)"
              >
                <span class="ci-icon">💬</span>
                <span class="ci-text">
                  <span class="ci-title" :title="conv.title">{{ conv.title }}</span>
                  <span class="ci-time">{{ formatTime(conv.updatedAt) }}</span>
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
              <span class="ci-icon">💬</span>
              <span class="ci-text">
                <span class="ci-title" :title="conv.title">{{ conv.title }}</span>
                <span v-if="conv.snippets && conv.snippets.length > 0" class="ci-snippet">
                  {{ conv.snippets[0] }}
                </span>
                <span class="ci-time">{{ formatTime(conv.updatedAt) }}</span>
              </span>
              <button class="ci-delete" title="删除" @click.stop="onDelete(conv.id)">×</button>
            </button>
          </div>
        </template>
      </div>
    </template>

    <!-- Collapse toggle -->
    <button class="sidebar-collapse" @click="emit('update:expanded', !props.expanded)" :title="props.expanded ? '收起侧栏' : '展开侧栏'">
      {{ props.expanded ? '◀' : '▶' }}
    </button>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import type { SearchResultConversation } from '@/api/client'

const props = defineProps<{ expanded: boolean }>()
const emit = defineEmits<{ 'update:expanded': [value: boolean] }>()

const store = useAppStore()

// Search state
const searchKeyword = ref('')
const searchFocused = ref(false)
const searchResults = ref<SearchResultConversation[]>([])
const searching = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  store.handleLoadConversations()
})

// Debounced search
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  if (!searchKeyword.value.trim()) {
    searchResults.value = []
    searching.value = false
    return
  }
  searching.value = true
  searchTimer = setTimeout(async () => {
    searchResults.value = await store.handleSearchConversations(searchKeyword.value)
    searching.value = false
  }, 300)
}

function clearSearch() {
  searchKeyword.value = ''
  searchResults.value = []
  searching.value = false
}

// Time-based grouping
interface ConversationGroup {
  label: string
  items: Array<{ id: string; title: string; role: string; createdAt: string; updatedAt: string }>
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

function onDelete(id: string) {
  if (confirm('确定删除此对话？')) {
    store.handleDeleteConversation(id)
  }
}
</script>

<style scoped>
.chat-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: #f9fafb;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.2s;
  position: relative;
}

.chat-sidebar.collapsed {
  width: 0;
  min-width: 0;
  border-right: none;
  overflow: visible;
}

.sidebar-collapse {
  position: absolute;
  top: 50%;
  right: -18px;
  transform: translateY(-50%);
  width: 36px;
  height: 56px;
  border: 1px solid #e5e7eb;
  border-radius: 0 8px 8px 0;
  background: #fff;
  color: #6b7280;
  font-size: 15px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  box-shadow: 2px 0 6px rgba(0,0,0,0.06);
  transition: all 0.15s;
}
.sidebar-collapse:hover { background: #f3f4f6; color: #111827; box-shadow: 2px 0 10px rgba(0,0,0,0.1); }

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 16px 12px;
}

.brand-icon { font-size: 22px; }
.brand-text { font-size: 15px; font-weight: 700; color: #111827; }

.new-chat-btn {
  margin: 0 12px 8px;
  padding: 10px 14px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #fff;
  color: #374151;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.12s;
}
.new-chat-btn:hover {
  border-color: #9ca3af;
  background: #f9fafb;
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
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  font-size: 12px;
  font-family: inherit;
  outline: none;
  background: #fff;
  color: #374151;
  transition: border-color 0.15s;
}
.search-input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99,102,241,0.08);
}
.search-input::placeholder { color: #9ca3af; }

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
  background: #e5e7eb;
  color: #6b7280;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
.search-clear:hover { background: #d1d5db; }

/* History section */
.history-section {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
  scroll-behavior: smooth;
}

.history-section::-webkit-scrollbar { width: 4px; }
.history-section::-webkit-scrollbar-track { background: transparent; }
.history-section::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 2px;
}

.history-label {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 8px 8px 4px;
}

.history-group-label {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  padding: 10px 8px 3px;
  border-bottom: 1px solid #f3f4f6;
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
  color: #9ca3af;
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
  color: #374151;
  text-align: left;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.1s;
  position: relative;
}
.conv-item:hover { background: #f3f4f6; }
.conv-item.active { background: #e5e7eb; }

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
  color: #374151;
}

.ci-snippet {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: #9ca3af;
  margin-top: 1px;
  font-style: italic;
}

.ci-time {
  font-size: 11px;
  color: #9ca3af;
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
  color: #9ca3af;
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
    z-index: 30;
    box-shadow: 4px 0 16px rgba(0,0,0,0.12);
    transform: translateX(-100%);
    transition: transform 0.25s ease;
    overflow: visible;
  }
  .chat-sidebar:not(.collapsed) {
    transform: translateX(0);
  }
  .chat-sidebar.collapsed {
    width: 240px;
    overflow: visible;
    transform: translateX(-100%);
  }

  /* Button peeks from left viewport edge when collapsed */
  .sidebar-collapse {
    right: -30px;
    left: auto;
    border-radius: 0 8px 8px 0;
    height: 52px;
    width: 30px;
    font-size: 14px;
  }
}
</style>
