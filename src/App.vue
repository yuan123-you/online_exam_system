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
    @close="store.activeExam = null"
    @submitted="store.handleExamSubmitted"
    @refreshed="store.loadData"
  />

  <AutoGenPaperModal
    v-if="store.autoGenVisible"
    :bootstrap="store.bootstrap!"
    @close="store.autoGenVisible = false"
    @submit="store.handleAutoGenerate"
  />

  <!-- Toast Notifications -->
  <div class="toast-container">
    <transition-group name="toast">
      <div v-for="t in store.toasts" :key="t.id" :class="['toast', 'toast-' + t.type]">
        <span class="toast-icon">{{ t.type === 'success' ? '&#10003;' : t.type === 'error' ? '&#10005;' : '&#8505;' }}</span>
        <span class="toast-msg">{{ t.message }}</span>
      </div>
    </transition-group>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import EntityEditorModal from '@/components/common/EntityEditorModal.vue'
import BatchUserImportModal from '@/components/common/BatchUserImportModal.vue'
import ExamSessionModal from '@/components/student/ExamSessionModal.vue'
import WrongBookRetryModal from '@/components/student/WrongBookRetryModal.vue'
import PaperFormModal from '@/components/teacher/PaperFormModal.vue'
import PaperPreviewModal from '@/components/teacher/PaperPreviewModal.vue'
import SubmissionReviewModal from '@/components/teacher/SubmissionReviewModal.vue'
import AutoGenPaperModal from '@/components/teacher/AutoGenPaperModal.vue'

const store = useAppStore()
</script>
