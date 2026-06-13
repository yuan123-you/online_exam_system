<template>
  <aside class="chat-sidebar" :class="{ collapsed: !expanded }">
    <template v-if="expanded">
      <!-- Logo area -->
      <div class="sidebar-brand">
        <span class="brand-icon">🧠</span>
        <span class="brand-text">AI 助手</span>
      </div>

      <!-- New chat -->
      <button class="new-chat-btn" @click="store.handleNewConversation()">
        <span class="nc-icon">➕</span> 新对话
      </button>

      <!-- History list -->
      <div class="history-section">
        <div class="history-label">历史对话</div>
        <div class="conv-list">
          <div v-if="store.conversations.length === 0 && !store.conversationsLoading" class="conv-empty">
            暂无对话记录
          </div>
          <div v-if="store.conversationsLoading" class="conv-loading">加载中...</div>
          <button
            v-for="conv in store.conversations"
            :key="conv.id"
            :class="['conv-item', { active: conv.id === store.activeConversationId }]"
            @click="store.handleSwitchConversation(conv.id)"
          >
            <span class="ci-icon">💬</span>
            <span class="ci-text">
              <span class="ci-title">{{ conv.title }}</span>
              <span class="ci-time">{{ formatTime(conv.updatedAt) }}</span>
            </span>
            <button class="ci-delete" title="删除" @click.stop="onDelete(conv.id)">×</button>
          </button>
        </div>
      </div>
    </template>

    <!-- Collapse toggle -->
    <button class="sidebar-collapse" @click="expanded = !expanded" :title="expanded ? '收起侧栏' : '展开侧栏'">
      {{ expanded ? '◀' : '▶' }}
    </button>
  </aside>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const expanded = ref(true)

onMounted(() => {
  store.handleLoadConversations()
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
  right: -16px;
  transform: translateY(-50%);
  width: 32px;
  height: 48px;
  border: 1px solid #e5e7eb;
  border-radius: 0 8px 8px 0;
  background: #fff;
  color: #6b7280;
  font-size: 12px;
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
  margin: 0 12px 12px;
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
    width: 260px;
    z-index: 30;
    box-shadow: 4px 0 16px rgba(0,0,0,0.12);
    transform: translateX(-100%);
    transition: transform 0.25s ease;
  }
  .chat-sidebar:not(.collapsed) {
    transform: translateX(0);
  }
  .chat-sidebar.collapsed {
    width: 260px;
    overflow: hidden;
    transform: translateX(-100%);
  }

  /* Mobile toggle button — fixed to left edge */
  .sidebar-collapse {
    right: auto;
    left: 100%;
    border-radius: 0 8px 8px 0;
    height: 56px;
    width: 28px;
  }
}
</style>
