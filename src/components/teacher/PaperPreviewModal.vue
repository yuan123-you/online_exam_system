<template>
  <BaseModal @close="$emit('close')">
    <template #header>
      <div>
        <h3>{{ paper.name }}</h3>
        <p class="muted" style="font-size:13px;margin-top:4px;">题量 {{ questions.length }} · 总分 {{ paper.totalScore }} · 时长 {{ paper.durationMinutes }} 分钟</p>
      </div>
    </template>
    <div class="preview-list">
      <div v-for="(item, index) in questions" :key="item.id" class="preview-item">
        <h4>{{ index + 1 }}. {{ item.title }}</h4>
        <p class="muted"><span class="tag" style="margin-right:8px;">{{ typeLabel(item.type) }}</span>{{ item.subject }} / {{ item.knowledgePoint }} · {{ item.score }} 分</p>
      </div>
      <div v-if="questions.length === 0" class="empty-state">暂无题目</div>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed } from "vue";
import BaseModal from "../common/BaseModal.vue";
import type { BootstrapData, Paper } from "../../types";
import { typeLabel } from "../../utils/format";

const props = defineProps<{
  bootstrap: BootstrapData;
  paper: Paper;
}>();

defineEmits<{
  close: [];
}>();

const questions = computed(() =>
  props.paper.questionIds
    .map((id) => props.bootstrap.questions.find((item) => item.id === id))
    .filter(Boolean)
);
</script>
