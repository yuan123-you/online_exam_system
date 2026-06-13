<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal-card">
      <button class="close-btn" type="button" @click="$emit('close')">&times;</button>
      <div class="modal-header">
        <slot name="header" />
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
