<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand-card">
        <p class="eyebrow">ONLINE EXAM</p>
        <h2>在线考试系统</h2>
        <p class="muted">{{ roleLabel(currentUser.role) }}工作台</p>
      </div>
      <div class="user-card">
        <h3>{{ currentUser.name }}</h3>
        <p class="muted">{{ currentUser.username }}</p>
      </div>
      <nav class="menu-list">
        <button
          v-for="item in menuItems"
          :key="item.key"
          class="menu-btn"
          :class="{ active: activeMenu === item.key }"
          type="button"
          @click="$emit('change-menu', item.key)"
        >
          <strong>{{ item.label }}</strong>
          <span>{{ item.description }}</span>
        </button>
      </nav>
      <div class="sidebar-actions">
        <button class="ghost-btn wide" type="button" @click="$emit('reload')">刷新数据</button>
        <button class="danger-btn wide" type="button" @click="$emit('logout')">退出登录</button>
      </div>
    </aside>

    <main class="main-panel">
      <header class="topbar">
        <div>
          <h1>{{ activeItem?.label || "工作台" }}</h1>
          <p class="muted">{{ activeItem?.description || "统一查看考试、题库与作答数据。" }}</p>
        </div>
      </header>
      <section class="content-panel">
        <slot />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { MenuItem, User } from "../types";
import { roleLabel } from "../utils/format";

const props = defineProps<{
  currentUser: User;
  menuItems: MenuItem[];
  activeMenu: string;
}>();

defineEmits<{
  "change-menu": [key: string];
  reload: [];
  logout: [];
}>();

const activeItem = computed(() => props.menuItems.find((item) => item.key === props.activeMenu));
</script>
