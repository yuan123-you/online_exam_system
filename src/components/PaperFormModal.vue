<template>
  <BaseModal @close="$emit('close')">
    <template #header>
      <div>
        <h3>{{ model ? "编辑试卷" : "新增试卷" }}</h3>
        <p class="muted">题目池会自动排除已被其他试卷占用的题目。</p>
      </div>
    </template>

    <form class="form-grid" @submit.prevent="submitForm">
      <label>
        <span>试卷名称</span>
        <input v-model.trim="name" required />
      </label>
      <label>
        <span>考试时长（分钟）</span>
        <input v-model.number="durationMinutes" type="number" min="1" required />
      </label>
      <label>
        <span>及格线</span>
        <input v-model.number="passScore" type="number" min="0" required />
      </label>
      <label>
        <span>科目筛选</span>
        <select v-model="subjectFilter">
          <option value="">全部科目</option>
          <option v-for="item in subjects" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>
      <label class="span-2">
        <span>关键字筛选</span>
        <input v-model.trim="keyword" placeholder="输入题目或知识点关键字" />
      </label>
      <label class="span-2">
        <span>可选题目（按住 Ctrl 或 Shift 多选）</span>
        <select v-model="selectedIds" class="multi-select" multiple size="14">
          <option v-for="item in filteredQuestions" :key="item.id" :value="item.id">
            {{ typeLabel(item.type) }} · {{ item.subject }} / {{ item.knowledgePoint }} · {{ item.title }}
          </option>
        </select>
      </label>

      <div class="selection-summary span-2">
        <span>已选题量：{{ selectedIds.length }}</span>
        <span>总分：{{ totalScore }}</span>
      </div>

      <div class="action-row">
        <button class="ghost-btn" type="button" @click="$emit('close')">取消</button>
        <button class="primary-btn" type="submit">保存试卷</button>
      </div>
    </form>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import BaseModal from "./BaseModal.vue";
import type { BootstrapData, Paper } from "../types";
import { typeLabel } from "../utils/format";

const props = defineProps<{
  bootstrap: BootstrapData;
  model?: Paper | null;
}>();

const emit = defineEmits<{
  close: [];
  submit: [payload: { entity: string; record: Record<string, unknown> }];
}>();

const name = ref(props.model?.name || "");
const durationMinutes = ref(props.model?.durationMinutes || 60);
const passScore = ref(props.model?.passScore || 60);
const keyword = ref("");
const subjectFilter = ref("");
const selectedIds = ref<string[]>(props.model?.questionIds ? [...props.model.questionIds] : []);

const subjects = computed(() => [...new Set(props.bootstrap.questions.map((item) => item.subject))]);
const occupiedIds = computed(() => {
  const currentId = props.model?.id;
  return new Set(
    props.bootstrap.papers
      .filter((paper) => paper.id !== currentId)
      .flatMap((paper) => paper.questionIds)
  );
});

const availableQuestions = computed(() =>
  props.bootstrap.questions.filter((question) => !occupiedIds.value.has(question.id) || selectedIds.value.includes(question.id))
);

const filteredQuestions = computed(() =>
  availableQuestions.value.filter((question) => {
    const subjectMatched = !subjectFilter.value || question.subject === subjectFilter.value;
    const keywordMatched =
      !keyword.value ||
      question.title.includes(keyword.value) ||
      question.knowledgePoint.includes(keyword.value) ||
      question.subject.includes(keyword.value);
    return subjectMatched && keywordMatched;
  })
);

const totalScore = computed(() =>
  selectedIds.value
    .map((id) => props.bootstrap.questions.find((question) => question.id === id))
    .filter(Boolean)
    .reduce((sum, question) => sum + Number(question?.score || 0), 0)
);

function submitForm() {
  emit("submit", {
    entity: "papers",
    record: {
      id: props.model?.id || undefined,
      name: name.value,
      durationMinutes: Number(durationMinutes.value),
      passScore: Number(passScore.value),
      totalScore: totalScore.value,
      questionIds: selectedIds.value,
    },
  });
}
</script>
