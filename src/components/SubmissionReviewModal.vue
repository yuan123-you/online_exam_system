<template>
  <BaseModal @close="$emit('close')">
    <template #header>
      <div>
        <h3>{{ submission.examName || "答卷详情" }}</h3>
        <p class="muted" style="font-size:13px;margin-top:4px;">{{ submission.studentName }} · <span class="tag">{{ submission.status }}</span> · {{ formatDate(submission.submittedAt) }}</p>
      </div>
    </template>

    <div class="stats-grid compact-grid">
      <article class="panel compact-card">
        <span class="muted">成绩</span>
        <strong>{{ submission.finalScore ?? submission.autoScore ?? 0 }} / {{ submission.totalScore ?? "-" }}</strong>
      </article>
      <article class="panel compact-card">
        <span class="muted">耗时</span>
        <strong>{{ submission.usedTimeText || "-" }}</strong>
      </article>
      <article class="panel compact-card">
        <span class="muted">排名</span>
        <strong>{{ submission.rank ? `${submission.rank} / ${submission.finishedCount}` : "-" }}</strong>
      </article>
    </div>

    <form class="preview-list" @submit.prevent="submitGrade">
      <div v-for="detail in submission.answerDetail || []" :key="detail.questionId" class="preview-item">
        <div class="between">
          <h4 style="flex:1;">{{ detail.title }}</h4>
          <span class="tag">{{ typeLabel(detail.type) }}</span>
        </div>
        <p class="muted" style="margin-top:8px;">学生作答：{{ joinAnswer(detail.answer) || "未作答" }}</p>
        <p class="muted">参考答案：{{ joinAnswer(detail.expectedAnswer) }}</p>
        <p class="muted">当前得分：<strong style="color:var(--primary);">{{ detail.score }}</strong> / {{ detail.fullScore }}</p>
        <label v-if="canGrade" class="grade-input">
          <span>人工评分</span>
          <input v-model.number="scores[detail.questionId]" :max="detail.fullScore" min="0" type="number" />
        </label>
      </div>
      <div class="action-row" style="justify-content:flex-end;margin-top:8px;">
        <button class="ghost-btn" type="button" @click="$emit('close')">关闭</button>
        <button v-if="canGrade" class="primary-btn" type="submit">提交阅卷</button>
      </div>
    </form>
  </BaseModal>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import BaseModal from "./BaseModal.vue";
import type { SubmissionReview } from "../types";
import { formatDate, joinAnswer, typeLabel } from "../utils/format";

const props = defineProps<{
  submission: SubmissionReview;
  canGrade?: boolean;
}>();

const emit = defineEmits<{
  close: [];
  grade: [payload: { submissionId: string; scores: Record<string, number> }];
}>();

const scores = reactive<Record<string, number>>(
  Object.fromEntries((props.submission.answerDetail || []).map((item) => [item.questionId, Number(item.score || 0)]))
);

function submitGrade() {
  emit("grade", { submissionId: props.submission.id, scores });
}
</script>
