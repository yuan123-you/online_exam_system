<template>
  <BaseModal @close="$emit('close')">
    <template #header>
      <div>
        <h3>错题重做</h3>
        <p class="muted">{{ entry.subject }} / {{ entry.knowledgePoint }} / {{ typeLabel(entry.type) }}</p>
      </div>
    </template>

    <div class="preview-item">
      <h4>{{ entry.title }}</h4>
      <div v-if="entry.question?.type === 'single' || entry.question?.type === 'judge'" class="option-list">
        <label v-for="option in entry.question.options" :key="option" class="option-item">
          <input v-model="singleAnswer" :value="option" name="singleAnswer" type="radio" />
          <span>{{ option }}</span>
        </label>
      </div>
      <div v-else-if="entry.question?.type === 'multiple'" class="option-list">
        <label v-for="option in entry.question.options" :key="option" class="option-item">
          <input v-model="multipleAnswer" :value="option" type="checkbox" />
          <span>{{ option }}</span>
        </label>
      </div>
      <textarea v-else v-model="textAnswer" placeholder="请输入你的答案"></textarea>
      <p class="muted">参考答案：{{ joinAnswer(entry.expectedAnswer) }}</p>
    </div>

    <div class="action-row">
      <button class="ghost-btn" type="button" @click="$emit('close')">取消</button>
      <button class="primary-btn" type="button" @click="submitRetry">提交重做</button>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref } from "vue";
import BaseModal from "./BaseModal.vue";
import type { WrongBookEntry } from "../types";
import { joinAnswer, typeLabel } from "../utils/format";

const props = defineProps<{
  entry: WrongBookEntry;
}>();

const emit = defineEmits<{
  close: [];
  submit: [payload: { entryId: string; answer: string[] }];
}>();

const singleAnswer = ref(props.entry.lastRetryAnswer?.[0] || props.entry.latestAnswer?.[0] || "");
const multipleAnswer = ref<string[]>(props.entry.lastRetryAnswer?.length ? [...props.entry.lastRetryAnswer] : [...(props.entry.latestAnswer || [])]);
const textAnswer = ref(props.entry.lastRetryAnswer?.[0] || props.entry.latestAnswer?.[0] || "");

function submitRetry() {
  const answer =
    props.entry.type === "single" || props.entry.type === "judge"
      ? singleAnswer.value
        ? [singleAnswer.value]
        : []
      : props.entry.type === "multiple"
        ? multipleAnswer.value
        : textAnswer.value
          ? [textAnswer.value.trim()]
          : [];

  emit("submit", { entryId: props.entry.id, answer });
}
</script>
