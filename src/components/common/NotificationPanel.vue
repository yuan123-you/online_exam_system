<template>
  <div class="notif-panel" v-if="visible">
    <div class="notif-header">
      <h4>通知</h4>
      <div class="notif-actions">
        <button v-if="store.unreadNotificationCount > 0" class="ghost-btn-sm" @click="store.handleMarkAllNotificationsRead()">
          全部已读
        </button>
        <button class="close-btn" @click="$emit('close')">&times;</button>
      </div>
    </div>
    <div class="notif-list">
      <div v-if="store.notifications.length === 0" class="notif-empty">暂无通知</div>
      <div
        v-for="n in store.notifications"
        :key="n.id"
        :class="['notif-item', { unread: !n.isRead }]"
        @click="handleClick(n)"
      >
        <div class="notif-type-badge" :class="'type-' + n.type">{{ typeLabel(n.type) }}</div>
        <div class="notif-body">
          <div class="notif-title">{{ n.title }}</div>
          <div class="notif-content">{{ n.content }}</div>
          <div class="notif-meta">
            <span>{{ n.senderName }}</span>
            <span>{{ formatTime(n.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import type { Notification } from '@/api/client'

defineProps<{ visible: boolean }>()
defineEmits<{ close: [] }>()

const store = useAppStore()

function typeLabel(type: string): string {
  switch (type) {
    case 'exam': return '考试'
    case 'grade': return '成绩'
    case 'system': return '系统'
    default: return '通知'
  }
}

function formatTime(iso: string): string {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return `${diffMin}分钟前`
    const diffHour = Math.floor(diffMin / 60)
    if (diffHour < 24) return `${diffHour}小时前`
    const diffDay = Math.floor(diffHour / 24)
    if (diffDay < 7) return `${diffDay}天前`
    return d.toLocaleDateString('zh-CN')
  } catch { return '' }
}

function handleClick(n: Notification) {
  if (!n.isRead) {
    store.handleMarkNotificationRead(n.id)
  }
}
</script>

<style scoped>
.notif-panel {
  position: absolute;
  top: 100%;
  right: 0;
  width: 360px;
  max-height: 480px;
  background: var(--panel);
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--line);
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
}

.notif-header h4 {
  margin: 0;
  font-size: 0.95rem;
  color: var(--ink);
}

.notif-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ghost-btn-sm {
  font-size: 0.75rem;
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  color: var(--muted);
  padding: 0 4px;
}

.notif-list {
  overflow-y: auto;
  flex: 1;
}

.notif-empty {
  padding: 2rem;
  text-align: center;
  color: var(--muted);
  font-size: 0.875rem;
}

.notif-item {
  display: flex;
  gap: 10px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer;
  transition: background 0.15s;
}

.notif-item:hover {
  background: var(--panel-hover);
}

.notif-item.unread {
  background: var(--primary-soft);
}

.notif-type-badge {
  font-size: 0.65rem;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
  white-space: nowrap;
  height: fit-content;
  margin-top: 2px;
}

.type-exam { background: var(--primary-soft); color: var(--primary); }
.type-grade { background: var(--ok-soft); color: var(--ok); }
.type-system { background: var(--warn-soft); color: var(--warn); }
.type-general { background: var(--panel-soft); color: var(--muted); }

.notif-body {
  flex: 1;
  min-width: 0;
}

.notif-title {
  font-weight: 600;
  font-size: 0.85rem;
  color: var(--ink);
  margin-bottom: 2px;
}

.notif-content {
  font-size: 0.8rem;
  color: var(--muted);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notif-meta {
  display: flex;
  justify-content: space-between;
  font-size: 0.7rem;
  color: var(--muted-light);
  margin-top: 4px;
}

@media (max-width: 640px) {
  .notif-panel {
    width: calc(100vw - 32px);
    right: -60px;
  }
}
</style>
