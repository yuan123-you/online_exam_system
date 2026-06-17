<template>
  <div
    :class="['app-shell', { 'sidebar-open': sidebarOpen, 'sidebar-collapsed': sidebarCollapsed }]"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend.passive="onTouchEnd"
  >
    <!-- Sidebar backdrop overlay (mobile / tablet) -->
    <div class="sidebar-overlay" @click="sidebarOpen = false"></div>

    <aside ref="sidebarRef" class="sidebar">
      <div class="sidebar-top">
        <div class="sidebar-brand">
          <div class="sidebar-brand-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10v6M2 10l10-5 10 5-10 5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/></svg>
          </div>
          <div class="sidebar-brand-text">
            <h2>在线考试</h2>
            <p class="eyebrow">EXAM SYSTEM</p>
          </div>
        </div>
        <button class="sidebar-collapse-btn" type="button" @click="toggleSidebar" :title="sidebarCollapsed ? '展开' : '收起'">
          <svg :class="['collapse-icon', { rotated: sidebarCollapsed }]" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </button>
      </div>

      <div class="sidebar-body">
        <div class="sidebar-user">
          <div class="sidebar-user-avatar">{{ avatarLetter }}</div>
          <div class="sidebar-user-info">
            <h3>{{ store.currentUser?.name }}</h3>
            <span>{{ roleLabel(store.currentUser?.role || 'student') }} · {{ store.currentUser?.username }}</span>
          </div>
        </div>

        <nav class="menu-list">
          <button
            v-for="item in store.menuItems"
            :key="item.key"
            class="menu-btn"
            :class="{ active: activeMenuKey === item.key }"
            type="button"
            @click="handleMenuClick(item.key)"
          >
            <span class="menu-icon" v-html="getMenuIcon(item.key)"></span>
            <span class="menu-label">
              <strong>{{ item.label }}</strong>
            </span>
          </button>
        </nav>

        <div class="menu-footer">
          <button type="button" @click="handleReload" :disabled="refreshing">
            <span class="menu-icon" :class="{ 'spin-icon': refreshing }">
              <svg viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
            </span>
            <span class="menu-footer-label">{{ refreshing ? '刷新中...' : '刷新数据' }}</span>
          </button>
          <button type="button" @click="toggleTheme" :title="'主题: ' + themeLabel()">
            <span class="menu-icon">
              <svg v-if="currentTheme === 'light'" viewBox="0 0 24 24"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
              <svg v-else-if="currentTheme === 'dark'" viewBox="0 0 24 24"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
              <svg v-else viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            </span>
            <span class="menu-footer-label">{{ themeLabel() }}</span>
          </button>
          <button class="danger-btn" type="button" @click="handleLogout">
            <span class="menu-icon">
              <svg viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
            </span>
            <span class="menu-footer-label">退出登录</span>
          </button>
        </div>
      </div>

    </aside>

    <main :class="['main-panel', { 'main-panel--full': isAiPractice }]" @click="handleClickOutside">
      <!-- Desktop topbar (hidden on AI assistant page for full-screen layout) -->
      <header v-if="!isAiPractice" class="topbar">
        <div style="display:flex;align-items:center;gap:12px;">
          <button class="mobile-menu-btn" type="button" @click="sidebarOpen = !sidebarOpen">
            <svg viewBox="0 0 24 24"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
          </button>
          <div>
            <h1>{{ activeItem?.label || '工作台' }}</h1>
            <p class="muted" style="margin:2px 0 0;font-size:13px;">{{ activeItem?.description || '统一查看考试、题库与作答数据。' }}</p>
          </div>
        </div>
        <div style="position:relative;">
          <button class="notif-bell-btn" type="button" @click="showNotifications = !showNotifications" title="通知">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
            </svg>
            <span v-if="store.unreadNotificationCount > 0" class="notif-badge">{{ store.unreadNotificationCount > 99 ? '99+' : store.unreadNotificationCount }}</span>
          </button>
          <NotificationPanel :visible="showNotifications" @close="showNotifications = false" />
        </div>
      </header>

      <section :class="['content-panel', { 'content-panel--full': isAiPractice }]">
        <router-view v-slot="{ Component }">
          <keep-alive :include="['AiQuestionGen']">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </section>
    </main>

    <!-- iOS Bottom Tab Bar — always visible on mobile/tablet, including AI assistant page -->
    <nav class="ios-tab-bar">
      <div class="ios-tab-bar-inner">
        <template v-for="(item, index) in tabItems" :key="item.key">
          <!-- Center AI button with prominent design -->
          <button
            v-if="isCenterAiBtn(index)"
            :class="['ios-tab-btn', 'ios-tab-btn--ai', { active: activeMenuKey === item.key }]"
            type="button"
            @click="navigateTo(item.key)"
          >
            <span class="ai-btn-ring">
              <span class="tab-icon" v-html="getMenuIcon(item.key)"></span>
            </span>
            <span class="ai-btn-label">{{ item.label }}</span>
          </button>
          <!-- Regular tab button -->
          <button
            v-else
            :class="['ios-tab-btn', { active: activeMenuKey === item.key }]"
            type="button"
            @click="navigateTo(item.key)"
          >
            <span class="tab-icon" v-html="getMenuIcon(item.key)"></span>
            <span>{{ item.label }}</span>
          </button>
        </template>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { roleLabel } from '@/utils/format'
import { useDeviceType } from '@/composables/useDeviceType'
import { useSmoothScroll } from '@/composables/useSmoothScroll'
import { useTheme } from '@/composables/useTheme'
import NotificationPanel from '@/components/common/NotificationPanel.vue'

const router = useRouter()
const route = useRoute()
const store = useAppStore()
const { isMobile } = useDeviceType()
const { currentTheme, toggleTheme, themeLabel } = useTheme()
useSmoothScroll()

const sidebarOpen = ref(false)
const sidebarCollapsed = ref(false)
const showNotifications = ref(false)
const sidebarRef = ref<HTMLElement | null>(null)

// ---- Click outside to collapse sidebar (desktop) ----
function handleClickOutside(e: MouseEvent) {
  // Only on desktop (>= 1024px) where sidebar is inline, not overlay
  if (window.innerWidth < 1024) return
  if (sidebarCollapsed.value) return
  const target = e.target as HTMLElement
  if (sidebarRef.value?.contains(target)) return
  sidebarCollapsed.value = true
}

// ---- Touch gesture support for sidebar drawer ----
const EDGE_SWIPE_ZONE = 40 // px from left edge to trigger open
const SWIPE_THRESHOLD = 60 // minimum horizontal distance to count as a swipe
const SWIPE_MAX_VERTICAL = 80 // max vertical drift allowed

let touchStartX = 0
let touchStartY = 0
let touchTracking = false

function onTouchStart(e: TouchEvent) {
  if (!isMobile.value) return
  const touch = e.touches[0]
  touchStartX = touch.clientX
  touchStartY = touch.clientY
  touchTracking = true
}

function onTouchMove(e: TouchEvent) {
  // Passive listener; we only record data, never call preventDefault.
  if (!touchTracking) return
  // Intentionally empty -- we evaluate on touchend.
}

function onTouchEnd(e: TouchEvent) {
  if (!touchTracking || !isMobile.value) return
  touchTracking = false
  const touch = e.changedTouches[0]
  const dx = touch.clientX - touchStartX
  const dy = Math.abs(touch.clientY - touchStartY)

  // Reject mostly-vertical gestures
  if (dy > SWIPE_MAX_VERTICAL) return

  if (!sidebarOpen.value) {
    // Swipe right from left edge -> open drawer
    if (touchStartX < EDGE_SWIPE_ZONE && dx > SWIPE_THRESHOLD) {
      sidebarOpen.value = true
    }
  } else {
    // Swipe left -> close drawer
    if (dx < -SWIPE_THRESHOLD) {
      sidebarOpen.value = false
    }
  }
}

// Auto-close drawer sidebar on route change (mobile)
watch(route, () => {
  if (isMobile.value) {
    sidebarOpen.value = false
  }
})

// Derive active menu key from current route path
const activeMenuKey = computed(() => {
  const path = route.path
  // Remove leading slash and get first segment
  const segments = path.split('/').filter(Boolean)
  if (segments[0] === 'home') {
    // /home redirects to the first menu item — treat as active home
    return store.menuItems[0]?.key || 'overview'
  }
  return segments[0] || 'overview'
})

const activeItem = computed(() => store.menuItems.find((item) => item.key === activeMenuKey.value))

const isAiPractice = computed(() => activeMenuKey.value === 'ai-practice')

// Show up to 5 items in bottom tab bar (iOS convention)
const tabItems = computed(() => store.menuItems.slice(0, 5))

// Determine if the tab at given index is the center AI button
const isCenterAiBtn = (index: number) => {
  const items = tabItems.value
  const centerIndex = Math.floor(items.length / 2)
  return index === centerIndex && items[centerIndex]?.key === 'ai-practice'
}

const avatarLetter = computed(() => {
  const name = store.currentUser?.name
  return name ? name.charAt(0) : '?'
})

function navigateTo(key: string) {
  // First menu item navigates through /home for proper home route semantics
  const firstKey = store.menuItems[0]?.key
  if (key === firstKey) {
    router.push('/home')
  } else {
    router.push('/' + key)
  }
  sidebarOpen.value = false
}

function handleMenuClick(key: string) {
  navigateTo(key)
}

const refreshing = ref(false)

async function handleReload() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    // 1. Reload bootstrap (core) data first
    await store.loadData()
    // 2. Reload route-specific store-level data
    const extraTasks: Promise<void>[] = []
    const key = activeMenuKey.value
    if (key === 'grades') {
      extraTasks.push(store.loadStudentGrades())
    }
    if (key === 'monitor') {
      extraTasks.push(store.loadMonitorData(store.monitorExamId))
    }
    if (key === 'analysis') {
      extraTasks.push(store.loadQuestionAnalysisData(store.questionAnalysisExamId))
    }
    if (key === 'wrong-book' || key === 'practice-records') {
      extraTasks.push(store.loadQuotaData())
    }
    extraTasks.push(store.loadNotificationData())
    // Wait for ALL data loading to complete before notifying views
    await Promise.all(extraTasks)
    // 3. Notify paginated views to re-fetch with their current filters/search state
    store.triggerRefresh()
    store.showToast('数据已刷新', 'success')
  } catch {
    store.showToast('刷新失败，请重试', 'error')
  } finally {
    refreshing.value = false
  }
}

function handleLogout() {
  sidebarOpen.value = false
  store.logout()
  router.push('/login')
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const _s = 'fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"'

const iconMap: Record<string, string> = {
  overview: `<svg viewBox="0 0 24 24" ${_s}><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/></svg>`,
  students: `<svg viewBox="0 0 24 24" ${_s}><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
  teachers: `<svg viewBox="0 0 24 24" ${_s}><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  org: `<svg viewBox="0 0 24 24" ${_s}><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>`,
  logs: `<svg viewBox="0 0 24 24" ${_s}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>`,
  'admin-exams': `<svg viewBox="0 0 24 24" ${_s}><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`,
  profile: `<svg viewBox="0 0 24 24" ${_s}><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  questions: `<svg viewBox="0 0 24 24" ${_s}><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`,
  'ai-questions': `<svg viewBox="0 0 24 24" ${_s}><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>`,
  papers: `<svg viewBox="0 0 24 24" ${_s}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`,
  exams: `<svg viewBox="0 0 24 24" ${_s}><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`,
  grading: `<svg viewBox="0 0 24 24" ${_s}><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
  analysis: `<svg viewBox="0 0 24 24" ${_s}><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>`,
  'available-exams': `<svg viewBox="0 0 24 24" ${_s}><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`,
  'my-exams': `<svg viewBox="0 0 24 24" ${_s}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>`,
  'wrong-book': `<svg viewBox="0 0 24 24" ${_s}><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>`,
  'ai-practice': `<svg viewBox="0 0 24 24" ${_s}><path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1.17A7 7 0 0 1 14 23h-4a7 7 0 0 1-6.83-4H2a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1h1a7 7 0 0 1 7-7h1V5.73A2 2 0 0 1 12 2z"/><circle cx="9" cy="14" r="1.5"/><circle cx="15" cy="14" r="1.5"/><path d="M9 18h6"/></svg>`,
  grades: `<svg viewBox="0 0 24 24" ${_s}><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>`,
  monitor: `<svg viewBox="0 0 24 24" ${_s}><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>`,
  'practice-records': `<svg viewBox="0 0 24 24" ${_s}><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>`,
}

function getMenuIcon(key: string): string {
  return iconMap[key] || `<svg viewBox="0 0 24 24" ${_s}><circle cx="12" cy="12" r="3"/></svg>`
}
</script>

<style scoped>
/* Notification bell button */
.collapse-icon {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: block;
}
.collapse-icon.rotated {
  transform: rotate(180deg);
}

/* Refresh button spin animation */
.spin-icon {
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Notification bell button */
.notif-bell-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid rgba(0,0,0,0.08);
  background: transparent;
  color: #374151;
  cursor: pointer;
  transition: background 0.15s ease;
}
.notif-bell-btn:hover {
  background: rgba(0,0,0,0.04);
}
.notif-bell-btn svg {
  stroke: currentColor;
}
.notif-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  color: #fff;
  background: #ef4444;
  border-radius: 9px;
  pointer-events: none;
}

/* ---- Dark mode overrides ---- */
[data-theme="dark"] .notif-bell-btn {
  color: #e5e7eb;
  border-color: rgba(255, 255, 255, 0.12);
}
[data-theme="dark"] .notif-bell-btn:hover {
  background: rgba(255, 255, 255, 0.08);
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .notif-bell-btn {
    color: #e5e7eb;
    border-color: rgba(255, 255, 255, 0.12);
  }
  :root:not([data-theme="light"]) .notif-bell-btn:hover {
    background: rgba(255, 255, 255, 0.08);
  }
}
</style>
