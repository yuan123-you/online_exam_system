<template>
  <div :class="['app-shell', { 'sidebar-open': sidebarOpen, 'sidebar-collapsed': sidebarCollapsed }]">
    <!-- Sidebar overlay (mobile/tablet) -->
    <div class="sidebar-overlay" @click="sidebarOpen = false"></div>

    <aside class="sidebar">
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
          {{ sidebarCollapsed ? '»' : '«' }}
        </button>
      </div>

      <div class="sidebar-body">
        <div class="sidebar-user">
          <div class="sidebar-user-avatar">{{ avatarLetter }}</div>
          <div class="sidebar-user-info">
            <h3>{{ currentUser.name }}</h3>
            <span>{{ roleLabel(currentUser.role) }} · {{ currentUser.username }}</span>
          </div>
        </div>

        <nav class="menu-list">
          <button
            v-for="item in menuItems"
            :key="item.key"
            class="menu-btn"
            :class="{ active: activeMenu === item.key }"
            type="button"
            @click="handleMenuClick(item.key)"
          >
            <span class="menu-icon" v-html="getMenuIcon(item.key)"></span>
            <span class="menu-label">
              <strong>{{ item.label }}</strong>
              <span>{{ item.description }}</span>
            </span>
          </button>
        </nav>

        <div class="menu-footer">
          <button type="button" @click="$emit('reload')">
            <span class="menu-icon">
              <svg viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
            </span>
            刷新数据
          </button>
          <button class="danger-btn" type="button" @click="handleLogout">
            <span class="menu-icon">
              <svg viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
            </span>
            退出登录
          </button>
        </div>
      </div>

      <button class="sidebar-expand-btn" type="button" @click="sidebarCollapsed = false" title="展开侧栏">»</button>
    </aside>

    <main class="main-panel">
      <!-- Desktop topbar -->
      <header class="topbar">
        <div style="display:flex;align-items:center;gap:12px;">
          <button class="mobile-menu-btn" type="button" @click="sidebarOpen = !sidebarOpen">
            <svg viewBox="0 0 24 24"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
          </button>
          <div>
            <h1>{{ activeItem?.label || "工作台" }}</h1>
            <p class="muted" style="margin:2px 0 0;font-size:13px;">{{ activeItem?.description || "统一查看考试、题库与作答数据。" }}</p>
          </div>
        </div>
      </header>

      <section class="content-panel">
        <slot />
      </section>
    </main>

    <!-- iOS Bottom Tab Bar (shown on mobile/tablet via CSS) -->
    <nav class="ios-tab-bar">
      <div class="ios-tab-bar-inner">
        <button
          v-for="item in tabItems"
          :key="item.key"
          :class="['ios-tab-btn', { active: activeMenu === item.key }]"
          type="button"
          @click="$emit('change-menu', item.key)"
        >
          <span class="tab-icon" v-html="getMenuIcon(item.key)"></span>
          <span>{{ item.label }}</span>
        </button>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { MenuItem, User } from "../types";
import { roleLabel } from "../utils/format";

const props = defineProps<{
  currentUser: User;
  menuItems: MenuItem[];
  activeMenu: string;
}>();

const emit = defineEmits<{
  "change-menu": [key: string];
  reload: [];
  logout: [];
}>();

const sidebarOpen = ref(false);
const sidebarCollapsed = ref(false);

const activeItem = computed(() => props.menuItems.find((item) => item.key === props.activeMenu));

// Show up to 5 items in bottom tab bar (iOS convention)
const tabItems = computed(() => props.menuItems.slice(0, 5));

const avatarLetter = computed(() => {
  const name = props.currentUser.name;
  return name ? name.charAt(0) : "?";
});

function handleMenuClick(key: string) {
  emit("change-menu", key);
  sidebarOpen.value = false;
}

function handleLogout() {
  sidebarOpen.value = false;
  emit("logout");
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
}

const iconMap: Record<string, string> = {
  overview: '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/></svg>',
  students: '<svg viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  teachers: '<svg viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
  org: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>',
  logs: '<svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>',
  profile: '<svg viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
  questions: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
  papers: '<svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>',
  exams: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>',
  grading: '<svg viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
  analysis: '<svg viewBox="0 0 24 24"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>',
  "available-exams": '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>',
  "my-exams": '<svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>',
  "wrong-book": '<svg viewBox="0 0 24 24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
  grades: '<svg viewBox="0 0 24 24"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>',
  monitor: '<svg viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>',
};

function getMenuIcon(key: string): string {
  return iconMap[key] || '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/></svg>';
}
</script>
