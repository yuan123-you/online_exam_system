<template>
  <BaseModal @close="handleClose">
    <template #header>
      <div>
        <h3>{{ exam.name }}</h3>
        <p class="muted" style="font-size:13px;margin-top:4px;">{{ exam.paper.name }} · 剩余时间 {{ countdown }}</p>
      </div>
    </template>

    <div class="exam-shell">
      <aside class="exam-sidebar">
        <div class="exam-info-block">
          <div class="exam-info-row">
            <span>切屏次数</span>
            <strong :class="{ 'exam-switch-danger': switchCount > exam.antiCheatLimit }">{{ switchCount }} / {{ exam.antiCheatLimit }}</strong>
          </div>
          <div class="exam-info-row">
            <span>题量</span>
            <strong>{{ exam.questions.length }}</strong>
          </div>
          <div class="exam-info-row">
            <span>倒计时</span>
            <strong class="exam-countdown" :class="{ urgent: isUrgent }">{{ countdown }}</strong>
          </div>
        </div>

        <div class="exam-progress">
          <div class="exam-progress-label">
            <span>答题进度</span>
            <span>{{ answeredCount }}/{{ exam.questions.length }}</span>
          </div>
          <div class="exam-progress-bar">
            <div class="exam-progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
        </div>

        <div class="question-nav">
          <button
            v-for="(item, index) in exam.questions"
            :key="item.id"
            class="question-index"
            :class="{ current: index === currentIndex, answered: Boolean(answerMap[item.id]?.length) }"
            type="button"
            @click="jumpTo(index)"
          >
            {{ index + 1 }}
          </button>
        </div>

        <div class="exam-sidebar-actions">
          <button class="ghost-btn" type="button" @click="saveDraft(false)">保存答卷</button>
          <button class="primary-btn" type="button" @click="submitPaper">提交试卷</button>
        </div>
        <p class="message" style="color:#60a5fa;">{{ message }}</p>
      </aside>

      <section class="exam-question-area">
        <div class="exam-question-header">
          <span class="exam-question-number">第 {{ currentIndex + 1 }} 题</span>
          <span class="exam-question-type">{{ typeLabel(currentQuestion.type) }}</span>
          <span style="flex:1;"></span>
          <span style="font-size:13px;color:var(--muted);">{{ currentQuestion.score }} 分</span>
        </div>

        <div class="exam-question-body">
          <h3 style="display:none;"></h3>
          <p style="font-size:15px;line-height:1.7;margin:0 0 20px;color:var(--ink);">{{ currentQuestion.title }}</p>

          <div v-if="currentQuestion.type === 'single' || currentQuestion.type === 'judge'" class="option-list">
            <label v-for="(option, oi) in currentQuestion.options" :key="option" class="option-item">
              <input v-model="singleAnswer" :value="option" name="singleAnswer" type="radio" />
              <span class="option-letter">{{ String.fromCharCode(65 + oi) }}</span>
              <span class="option-text">{{ option }}</span>
            </label>
          </div>

          <div v-else-if="currentQuestion.type === 'multiple'" class="option-list">
            <label v-for="(option, oi) in currentQuestion.options" :key="option" class="option-item">
              <input v-model="multipleAnswer" :value="option" type="checkbox" />
              <span class="option-letter">{{ String.fromCharCode(65 + oi) }}</span>
              <span class="option-text">{{ option }}</span>
            </label>
          </div>

          <textarea v-else v-model="textAnswer" class="exam-textarea" placeholder="请输入你的答案"></textarea>
        </div>

        <div class="exam-nav-footer">
          <button class="exam-nav-btn" type="button" :disabled="currentIndex === 0" @click="jumpTo(currentIndex - 1)">上一题</button>
          <span class="exam-nav-indicator">{{ currentIndex + 1 }} / {{ exam.questions.length }}</span>
          <button
            class="exam-nav-btn next"
            type="button"
            :disabled="currentIndex === exam.questions.length - 1"
            @click="jumpTo(currentIndex + 1)"
          >下一题</button>
        </div>
      </section>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import BaseModal from "./BaseModal.vue";
import type { AnswerPayload, ExamDetail } from "../types";
import { formatDuration, typeLabel } from "../utils/format";
import { saveSubmission, submitSubmission } from "../api/client";

const props = defineProps<{
  exam: ExamDetail;
}>();

const emit = defineEmits<{
  close: [];
  submitted: [];
  refreshed: [];
}>();

const currentIndex = ref(0);
const answerMap = ref<Record<string, string[]>>(
  Object.fromEntries((props.exam.session?.answers || []).map((item) => [item.questionId, [...item.answer]]))
);
const deadlineAt = ref(props.exam.session?.deadlineAt || new Date().toISOString());
const switchCount = ref(props.exam.session?.switchCount || 0);
const message = ref("");

const singleAnswer = ref("");
const multipleAnswer = ref<string[]>([]);
const textAnswer = ref("");

const currentQuestion = computed(() => props.exam.questions[currentIndex.value]);
const countdown = computed(() => formatDuration(Math.max(0, new Date(deadlineAt.value).getTime() - Date.now())));
const answeredCount = computed(() => Object.values(answerMap.value).filter((a) => a.length > 0).length);
const progressPercent = computed(() => Math.round((answeredCount.value / props.exam.questions.length) * 100));
const isUrgent = computed(() => {
  const remaining = new Date(deadlineAt.value).getTime() - Date.now();
  return remaining > 0 && remaining < 5 * 60 * 1000;
});

let timer: number | undefined;
let autoSaveTimer: number | undefined;

function hydrateCurrentInput() {
  const existing = answerMap.value[currentQuestion.value.id] || [];
  singleAnswer.value = existing[0] || "";
  multipleAnswer.value = [...existing];
  textAnswer.value = existing[0] || "";
}

function persistCurrentAnswer() {
  const question = currentQuestion.value;
  const answer =
    question.type === "single" || question.type === "judge"
      ? singleAnswer.value
        ? [singleAnswer.value]
        : []
      : question.type === "multiple"
        ? [...multipleAnswer.value]
        : textAnswer.value.trim()
          ? [textAnswer.value.trim()]
          : [];
  answerMap.value[question.id] = answer;
}

function buildAnswers(): AnswerPayload[] {
  return props.exam.questions.map((question) => ({ questionId: question.id, answer: answerMap.value[question.id] || [] }));
}

async function saveDraft(silent = true) {
  persistCurrentAnswer();
  const result = await saveSubmission(props.exam.id, buildAnswers(), switchCount.value);
  if (result.submission?.deadlineAt) {
    deadlineAt.value = result.submission.deadlineAt;
  }
  if (!silent) {
    message.value = "答卷已保存。";
    setTimeout(() => {
      if (message.value === "答卷已保存。") message.value = "";
    }, 2000);
  }
  emit("refreshed");
}

async function submitPaper() {
  persistCurrentAnswer();
  await submitSubmission(props.exam.id, buildAnswers(), switchCount.value);
  emit("submitted");
}

function jumpTo(index: number) {
  persistCurrentAnswer();
  currentIndex.value = index;
  hydrateCurrentInput();
}

async function handleVisibilityChange() {
  if (document.visibilityState !== "hidden") return;
  switchCount.value += 1;
  await saveDraft(true);
  if (switchCount.value > props.exam.antiCheatLimit) {
    await submitPaper();
  }
}

async function handleClose() {
  try {
    await saveDraft(true);
  } catch {
    // noop
  }
  emit("close");
}

watch(currentQuestion, hydrateCurrentInput, { immediate: true });

onMounted(() => {
  timer = window.setInterval(() => {
    if (new Date(deadlineAt.value).getTime() <= Date.now()) {
      submitPaper().catch(() => undefined);
    }
  }, 1000);
  autoSaveTimer = window.setInterval(() => {
    saveDraft(true).catch(() => undefined);
  }, 30000);
  document.addEventListener("visibilitychange", handleVisibilityChange);
});

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer);
  if (autoSaveTimer) window.clearInterval(autoSaveTimer);
  document.removeEventListener("visibilitychange", handleVisibilityChange);
});
</script>
