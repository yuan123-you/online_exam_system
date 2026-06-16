<template>
  <AuthLogin
    :loading="loginLoading"
    :disabled="loginLoading"
    :message="store.loginMessage"
    @submit="handleLogin"
    @clear-message="store.loginMessage = ''"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AuthLogin from '@/components/auth/AuthLogin.vue'

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
