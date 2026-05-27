<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal-card">
      <div class="modal-header">
        <slot name="header" />
        <button class="icon-btn" type="button" @click="$emit('close')">关闭</button>
      </div>
      <div class="modal-body">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineEmits<{
  close: [];
}>();
</script>
