<template>
  <ProfilePanel
    v-if="store.bootstrap"
    :bootstrap="store.bootstrap"
    @submit="store.handlePasswordChange"
  />
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import ProfilePanel from '@/components/common/ProfilePanel.vue'

const store = useAppStore()
</script>
