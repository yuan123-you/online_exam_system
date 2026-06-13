<template>
  <BaseModal @close="$emit('close')">
    <template #header>
      <div>
        <h3>自动组卷</h3>
        <p class="muted">设定规则，系统自动从题库中随机抽题组卷</p>
      </div>
    </template>

    <form class="form-grid" @submit.prevent="submitForm">
      <label>
        <span>试卷名称</span>
        <input v-model.trim="name" required placeholder="如：数学期中测试（自动组卷）" />
      </label>
      <label>
        <span>考试时长（分钟）</span>
        <input v-model.number="durationMinutes" type="number" min="1" required />
      </label>
      <label>
        <span>及格线</span>
        <input v-model.number="passScore" type="number" min="0" required />
      </label>

      <div class="span-2">
        <h4 style="margin:0 0 0.5rem">抽题规则</h4>
        <div v-for="(rule, i) in rules" :key="i" class="rule-row">
          <select v-model="rule.type" required>
            <option value="single">单选题</option>
            <option value="multiple">多选题</option>
            <option value="judge">判断题</option>
            <option value="fill">填空题</option>
            <option value="short">简答题</option>
            <option value="coding">编程题</option>
          </select>
          <input v-model.number="rule.count" type="number" min="1" placeholder="数量" style="width:80px" required />
          <select v-model="rule.subject">
            <option value="">不限科目</option>
            <option v-for="s in subjects" :key="s" :value="s">{{ s }}</option>
          </select>
          <select v-model="rule.difficulty">
            <option value="">不限难度</option>
            <option value="easy">易</option>
            <option value="medium">中</option>
            <option value="hard">难</option>
          </select>
          <input v-model.trim="rule.knowledgePoint" placeholder="知识点（可选）" style="width:120px" />
          <button class="danger-btn" type="button" @click="removeRule(i)">删除</button>
        </div>
        <button class="ghost-btn" type="button" @click="addRule" style="margin-top:0.5rem">+ 添加规则</button>
      </div>

      <div class="selection-summary span-2">
        <span>规则数：{{ rules.length }}</span>
        <span>预计题量：{{ totalExpectedCount }}</span>
      </div>

      <div class="action-row">
        <button class="ghost-btn" type="button" @click="$emit('close')">取消</button>
        <button class="primary-btn" type="submit">生成试卷</button>
      </div>
    </form>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import BaseModal from "../common/BaseModal.vue";
import type { BootstrapData } from "../../types";

const props = defineProps<{
  bootstrap: BootstrapData;
}>();

const emit = defineEmits<{
  close: [];
  submit: [payload: {
    name: string;
    durationMinutes: number;
    passScore: number;
    rules: Array<{ type: string; count: number; subject?: string; knowledgePoint?: string; difficulty?: string }>;
  }];
}>();

const name = ref("");
const durationMinutes = ref(60);
const passScore = ref(60);

interface Rule {
  type: string;
  count: number;
  subject: string;
  knowledgePoint: string;
  difficulty: string;
}

const rules = ref<Rule[]>([
  { type: "single", count: 10, subject: "", knowledgePoint: "", difficulty: "" },
  { type: "judge", count: 5, subject: "", knowledgePoint: "", difficulty: "" },
]);

const subjects = computed(() => [...new Set(props.bootstrap.questions.map((q) => q.subject))]);

const totalExpectedCount = computed(() => rules.value.reduce((sum, r) => sum + (r.count || 0), 0));

function addRule() {
  rules.value.push({ type: "single", count: 5, subject: "", knowledgePoint: "", difficulty: "" });
}

function removeRule(index: number) {
  rules.value.splice(index, 1);
}

function submitForm() {
  if (!name.value.trim()) return alert("请输入试卷名称");
  if (rules.value.length === 0) return alert("请至少添加一条规则");
  emit("submit", {
    name: name.value.trim(),
    durationMinutes: Number(durationMinutes.value),
    passScore: Number(passScore.value),
    rules: rules.value
      .filter((r) => r.count > 0)
      .map((r) => ({
        type: r.type,
        count: Number(r.count),
        subject: r.subject || undefined,
        knowledgePoint: r.knowledgePoint || undefined,
        difficulty: r.difficulty || undefined,
      })),
  });
}
</script>
