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

    <!-- AI Grading Controls -->
    <div v-if="canGrade" class="ai-grading-toolbar">
      <button class="primary-btn" type="button" :disabled="store.aiGradeLoading" @click="runAiGrading">
        {{ store.aiGradeLoading ? 'AI 评分中...' : 'AI 预评分' }}
      </button>
      <button v-if="aiDetails.length > 0" class="ghost-btn" type="button" @click="applyAllAiScores">
        一键采纳 AI 评分
      </button>
      <span v-if="aiGradeInfo" class="ai-score-summary">
        AI 总分: {{ aiGradeInfo.aiScore }} / {{ submission.totalScore ?? '-' }}
        <span class="muted">（仅供参考）</span>
      </span>
    </div>

    <form class="preview-list" @submit.prevent="submitGrade">
      <div v-for="detail in enrichedDetails" :key="detail.questionId" class="preview-item">
        <div class="between">
          <h4 style="flex:1;">{{ detail.title }}</h4>
          <span class="tag">{{ typeLabel(detail.type) }}</span>
        </div>
        <p class="muted" style="margin-top:8px;">学生作答：{{ joinAnswer(detail.answer) || "未作答" }}</p>
        <p class="muted">参考答案：{{ joinAnswer(detail.expectedAnswer) }}</p>
        <p class="muted">当前得分：<strong style="color:var(--primary);">{{ scores[detail.questionId] ?? 0 }}</strong> / {{ detail.fullScore }}</p>

        <!-- AI Score Badge -->
        <div v-if="detail.aiScore !== undefined" class="ai-score-badge" :class="getAiBadgeClass(detail)">
          <span class="ai-badge-label">AI 评分</span>
          <span class="ai-badge-value">{{ detail.aiScore }}/{{ detail.fullScore }}</span>
          <span v-if="detail.aiComment" class="ai-badge-comment">{{ detail.aiComment }}</span>
          <button v-if="canGrade" class="ghost-btn ai-adopt-btn" type="button" @click="adoptAiScore(detail)">
            采纳
          </button>
        </div>

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
import { reactive, ref, computed } from "vue";
import BaseModal from "../common/BaseModal.vue";
import { useAppStore } from "../../stores/app";
import type { QuestionType, SubmissionReview } from "../../types";
import { formatDate, joinAnswer, typeLabel } from "../../utils/format";

interface DetailWithAi {
  questionId: string;
  title: string;
  type: QuestionType;
  score: number;
  fullScore: number;
  answer: string[];
  expectedAnswer: string[];
  aiScore?: number;
  aiComment?: string;
}

const store = useAppStore();

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

const aiDetails = ref<DetailWithAi[]>([]);
const aiGradeInfo = ref<{ aiScore: number; manualScore: number } | null>(null);

const enrichedDetails = computed<DetailWithAi[]>(() => {
  return (props.submission.answerDetail || []).map(detail => {
    const aiDetail = aiDetails.value.find(a => a.questionId === detail.questionId);
    return {
      questionId: detail.questionId,
      title: detail.title,
      type: detail.type,
      score: detail.score,
      fullScore: detail.fullScore,
      answer: detail.answer,
      expectedAnswer: detail.expectedAnswer,
      aiScore: aiDetail?.aiScore,
      aiComment: aiDetail?.aiComment,
    };
  });
});
async function runAiGrading() {
  try {
    const result = await store.handleAiGradeSubmission(props.submission.id);
    if (result) {
      aiGradeInfo.value = { aiScore: result.aiScore, manualScore: result.manualScore };
      aiDetails.value = (result.details || []).map((d: any) => ({
        questionId: d.questionId,
        title: d.title,
        type: d.type as QuestionType,
        score: d.score,
        fullScore: d.fullScore,
        answer: d.answer,
        expectedAnswer: d.expectedAnswer,
        aiScore: d.aiScore,
        aiComment: d.aiComment,
      }));
    }
  } catch {
    // Error handled in store
  }
}

function adoptAiScore(detail: DetailWithAi) {
  if (detail.aiScore !== undefined) {
    scores[detail.questionId] = detail.aiScore;
  }
}

function applyAllAiScores() {
  aiDetails.value.forEach(d => {
    if (d.aiScore !== undefined) {
      scores[d.questionId] = d.aiScore;
    }
  });
  store.showToast('已采纳所有 AI 评分，可手动微调后提交', 'success');
}

function getAiBadgeClass(detail: DetailWithAi) {
  if (detail.aiScore === undefined || detail.fullScore === 0) return '';
  const ratio = detail.aiScore / detail.fullScore;
  return ratio >= 0.6 ? 'ai-badge-pass' : 'ai-badge-fail';
}

function submitGrade() {
  emit("grade", { submissionId: props.submission.id, scores });
}
</script>

<style scoped>
.ai-grading-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.ai-score-summary {
  font-weight: 600;
  color: var(--primary);
  font-size: 14px;
}

.ai-score-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  margin: 8px 0;
  font-size: 13px;
  flex-wrap: wrap;
}

.ai-badge-pass {
  background: #ecfdf5;
  border: 1px solid #86efac;
  color: #166534;
}

.ai-badge-fail {
  background: #fef2f2;
  border: 1px solid #fca5a5;
  color: #991b1b;
}

.ai-badge-label {
  font-weight: 600;
  font-size: 12px;
}

.ai-badge-value {
  font-weight: 700;
  font-size: 14px;
}

.ai-badge-comment {
  color: inherit;
  opacity: 0.8;
  font-size: 12px;
}

.ai-adopt-btn {
  margin-left: auto;
  font-size: 12px;
  padding: 2px 8px;
}
</style>
