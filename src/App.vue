<template>
  <router-view />

  <!-- Global Modals -->
  <EntityEditorModal
    v-if="store.editorState.visible"
    :kind="store.editorState.kind"
    :bootstrap="store.bootstrap!"
    :model="store.editorState.model"
    @close="store.editorState.visible = false"
    @submit="store.handleEntitySubmit"
  />

  <PaperFormModal
    v-if="store.paperFormVisible"
    :bootstrap="store.bootstrap!"
    :model="store.paperModel"
    @close="store.paperFormVisible = false"
    @submit="store.handlePaperSubmit"
  />

  <PaperPreviewModal
    v-if="store.previewPaperModel"
    :bootstrap="store.bootstrap!"
    :paper="store.previewPaperModel"
    @close="store.previewPaperModel = null"
  />

  <SubmissionReviewModal
    v-if="store.reviewingSubmission"
    :submission="store.reviewingSubmission"
    :can-grade="store.isTeacher"
    @close="store.reviewingSubmission = null"
    @grade="store.handleGrade"
  />

  <WrongBookRetryModal
    v-if="store.retryingEntry"
    :entry="store.retryingEntry"
    @close="store.retryingEntry = null"
    @submit="store.handleWrongRetry"
  />

  <BatchUserImportModal
    v-if="store.batchImportRole"
    :role="store.batchImportRole"
    :bootstrap="store.bootstrap!"
    @close="store.batchImportRole = null"
    @success="store.loadData"
  />

  <ExamSessionModal
    v-if="store.activeExam"
    :exam="store.activeExam"
    @close="handleExamClose"
    @submitted="store.handleExamSubmitted"
    @refreshed="store.loadData"
  />

  <AutoGenPaperModal
    v-if="store.autoGenVisible"
    :bootstrap="store.bootstrap!"
    @close="store.autoGenVisible = false"
    @submit="store.handleAutoGenerate"
  />

  <!-- Confirm Dialog -->
  <ConfirmDialog
    v-if="store.confirmState.visible"
    :title="store.confirmState.title"
    :message="store.confirmState.message"
    :confirm-text="store.confirmState.confirmText"
    :danger="store.confirmState.danger"
    @confirm="store.handleConfirmOk"
    @cancel="store.handleConfirmCancel"
  />

  <!-- Toast Notifications -->
  <div class="toast-container" role="region" aria-label="通知" aria-live="polite">
    <transition-group name="toast">
      <div
        v-for="t in toasts"
        :key="t.id"
        :class="['toast', 'toast-' + t.type]"
        role="alert"
      >
        <span class="toast-icon" aria-hidden="true">
          <svg v-if="t.type === 'success'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
          <svg v-else-if="t.type === 'error'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
          <svg v-else-if="t.type === 'warning'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
        </span>
        <span class="toast-msg">{{ t.message }}</span>
        <button class="toast-close" type="button" aria-label="关闭" @click="dismissToast(t.id)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
        </button>
        <span class="toast-progress" :style="{ animationDuration: toastDuration(t.id) + 'ms' }" />
      </div>
    </transition-group>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import { useToast } from '@/composables/useToast'
import EntityEditorModal from '@/components/common/EntityEditorModal.vue'
import BatchUserImportModal from '@/components/common/BatchUserImportModal.vue'
import ExamSessionModal from '@/components/student/ExamSessionModal.vue'
import WrongBookRetryModal from '@/components/student/WrongBookRetryModal.vue'
import PaperFormModal from '@/components/teacher/PaperFormModal.vue'
import PaperPreviewModal from '@/components/teacher/PaperPreviewModal.vue'
import SubmissionReviewModal from '@/components/teacher/SubmissionReviewModal.vue'
import AutoGenPaperModal from '@/components/teacher/AutoGenPaperModal.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'

const store = useAppStore()
const { toasts, dismiss, getDuration } = useToast()

function dismissToast(id: number) {
  dismiss(id)
}

function toastDuration(id: number) {
  return getDuration(id)
}

function handleExamClose() {
  store.activeExam = null
  sessionStorage.removeItem('active_exam_id')
}
</script>
