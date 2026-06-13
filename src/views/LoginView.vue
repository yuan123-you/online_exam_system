<template>
  <AuthLogin
    :loading="store.loginLoading"
    :message="store.loginMessage"
    @submit="handleLogin"
    @clear-message="store.loginMessage = ''"
  />
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AuthLogin from '@/components/auth/AuthLogin.vue'

const store = useAppStore()
const router = useRouter()

async function handleLogin(payload: { username: string; password: string }) {
  const success = await store.login(payload)
  if (success) {
    const firstMenuItem = store.menuItems[0]
    if (firstMenuItem) {
      router.push('/' + firstMenuItem.key)
    } else {
      router.push('/overview')
    }
  }
}
</script>
