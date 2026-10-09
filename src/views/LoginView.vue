<template>
  <AuthLogin
    :default-username="registeredUsername"
    :registration-active="activeTab === 'register'"
    :loading="loginLoading"
    :disabled="loginLoading"
    :message="store.loginMessage"
    @submit="handleLogin"
    @clear-message="store.loginMessage = ''"
  >
    <template #heading>
      <h1 class="auth-title">{{ activeTab === 'login' ? '用户登录' : '注册学生账号' }}</h1>
      <p class="auth-subtitle">{{ activeTab === 'login' ? '欢迎回来，请输入您的账号信息' : '填写账号和用户名即可，学院与班级自动安排' }}</p>
    </template>
    <template #tabs>
      <div class="auth-tabs" role="tablist" aria-label="登录或注册">
        <button v-for="tab in tabs" :key="tab.key" :id="tab.key + '-tab'" type="button" role="tab"
          :aria-selected="activeTab === tab.key" :aria-controls="tab.key + '-panel'"
          :tabindex="activeTab === tab.key ? 0 : -1" @click="activeTab = tab.key"
          @keydown="switchTab($event)">{{ tab.label }}</button>
      </div>
    </template>
    <template #registration>
      <div v-if="activeTab === 'register'" id="register-panel" role="tabpanel" aria-labelledby="register-tab">
        <RegisterView @login="returnToLogin" />
      </div>
    </template>
  </AuthLogin>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AuthLogin from '@/components/auth/AuthLogin.vue'
import RegisterView from './RegisterView.vue'

const registeredUsername = ref(typeof window.history.state?.registeredUsername === 'string' ? window.history.state.registeredUsername : '')
const tabs = [{ key: 'login', label: '登录' }, { key: 'register', label: '注册' }] as const
const activeTab = ref<'login' | 'register'>(useRoute().query.tab === 'register' ? 'register' : 'login')
function returnToLogin(username: string) {
  registeredUsername.value = username
  activeTab.value = 'login'
}
async function switchTab(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  activeTab.value = event.key === 'Home' ? 'login' : event.key === 'End' ? 'register' : activeTab.value === 'login' ? 'register' : 'login'
  await nextTick()
  document.getElementById(activeTab.value + '-tab')?.focus()
}
const store = useAppStore()
const router = useRouter()
const loginLoading = ref(false)

async function handleLogin(payload: { username: string; password: string }) {
  if (loginLoading.value) return;
  loginLoading.value = true;
  try {
    const success = await store.login(payload)
    if (success) {
      const firstMenuItem = store.menuItems[0]
      if (firstMenuItem) {
        router.push('/' + firstMenuItem.key)
      } else {
        router.push('/overview')
      }
    }
  } finally {
    loginLoading.value = false;
  }
}
</script>

<style scoped>
.auth-title { color:#203d32; margin:0 0 10px; font-size:30px; }
.auth-subtitle { color:#526e60; font-size:14px; line-height:1.7; }
.auth-tabs { display:flex; padding:4px; gap:4px; background:#eaf3ee; border:1px solid #c3dbcf; border-radius:12px; margin:0 0 24px; }
.auth-tabs button { flex:1; padding:11px 16px; border:0; border-radius:8px; color:#405d50; background:transparent; font:inherit; font-weight:600; cursor:pointer; }
.auth-tabs button[aria-selected="true"] { background:#287d63; color:#fff; }
.auth-tabs button:focus-visible { outline:3px solid #6bb69a; outline-offset:2px; }
</style>
