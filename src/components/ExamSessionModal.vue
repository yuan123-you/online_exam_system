<template>
  <BaseModal @close="handleClose">
    <template #header>
      <div>
        <h3>{{ exam.name }}</h3>
        <p class="muted">{{ exam.paper.name }} · 剩余时间 {{ countdown }}</p>
      </div>
    </template>

    <div class="exam-shell">
      <aside class="exam-sidebar">
        <div class="detail-list">
          <div class="detail-item"><span>切屏次数</span><strong :class="{ danger: switchCount > exam.antiCheatLimit }">{{ switchCount }} / {{ exam.antiCheatLimit }}</strong></div>
          <div class="detail-item"><span>题量</span><strong>{{ exam.questions.length }}</strong></div>
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
        <div class="action-row">
          <button class="ghost-btn" type="button" @click="saveDraft(false)">保存答卷</button>
          <button class="primary-btn" type="button" @click="submitPaper">提交试卷</button>
        </div>
        <p class="message">{{ message }}</p>
      </aside>

      <section class="panel exam-question">
        <span class="tag">{{ typeLabel(currentQuestion.type) }}</span>
        <h3>{{ currentQuestion.order }}. {{ currentQuestion.title }}</h3>

        <div v-if="currentQuestion.type === 'single' || currentQuestion.type === 'judge'" class="option-list">
          <label v-for="option in currentQuestion.options" :key="option" class="option-item">
            <input v-model="singleAnswer" :value="option" name="singleAnswer" type="radio" />
            <span>{{ option }}</span>
          </label>
        </div>

        <div v-else-if="currentQuestion.type === 'multiple'" class="option-list">
          <label v-for="option in currentQuestion.options" :key="option" class="option-item">
            <input v-model="multipleAnswer" :value="option" type="checkbox" />
            <span>{{ option }}</span>
          </label>
        </div>

        <textarea v-else v-model="textAnswer" class="exam-textarea" placeholder="请输入你的答案"></textarea>
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
