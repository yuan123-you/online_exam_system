<template>
  <div class="modal-backdrop" @click.self="handleCancel">
    <div class="modal-card confirm-dialog" style="max-width: 420px;">
      <button class="close-btn" type="button" @click="handleCancel">&times;</button>
      <div class="modal-header">
        <h3>{{ title }}</h3>
      </div>
      <div class="modal-body">
        <p class="confirm-message">{{ message }}</p>
        <div class="confirm-actions">
          <button class="btn" @click="handleCancel">取消</button>
          <button :class="['btn', danger ? 'danger-btn' : 'primary-btn']" @click="handleConfirm">
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  message: string
  confirmText?: string
  danger?: boolean
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}
</script>
