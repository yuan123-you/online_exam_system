<template>
  <BaseModal @close="$emit('close')">
    <template #header>
      <div>
        <h3>{{ dialogTitle }}</h3>
        <p class="muted" style="font-size:13px;margin-top:4px;">{{ isEdit ? "更新现有数据记录" : "新增一条业务记录" }}</p>
      </div>
    </template>

    <form class="form-grid" @submit.prevent="submitForm">
      <template v-if="kind === 'student' || kind === 'teacher'">
        <label><span>账号</span><input v-model.trim="form.username" required placeholder="请输入账号" /></label>
        <label><span>姓名</span><input v-model.trim="form.name" required placeholder="请输入姓名" /></label>
        <label><span>密码</span><input v-model.trim="form.password" required placeholder="初始密码" /></label>
        <label v-if="kind === 'student'">
          <span>班级</span>
          <select v-model="form.classId" required>
            <option v-for="item in bootstrap.classes" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
        <label v-if="kind === 'student'">
          <span>专业</span>
          <input v-model.trim="form.major" required placeholder="请输入专业" />
        </label>
        <label v-else>
          <span>学院</span>
          <select v-model="form.departmentId" required>
            <option v-for="item in bootstrap.departments" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
      </template>

      <template v-else-if="kind === 'department'">
        <label><span>学院名称</span><input v-model.trim="form.name" required placeholder="请输入学院名称" /></label>
      </template>

      <template v-else-if="kind === 'class'">
        <label><span>班级名称</span><input v-model.trim="form.name" required placeholder="请输入班级名称" /></label>
        <label><span>专业</span><input v-model.trim="form.major" required placeholder="请输入专业" /></label>
        <label>
          <span>学院</span>
          <select v-model="form.departmentId" required>
            <option v-for="item in bootstrap.departments" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
      </template>

      <template v-else-if="kind === 'question'">
        <label>
          <span>题型</span>
          <select v-model="form.type" required>
            <option value="single">单选题</option>
            <option value="multiple">多选题</option>
            <option value="judge">判断题</option>
            <option value="fill">编程填空题</option>
            <option value="short">简答题</option>
            <option value="coding">编程题</option>
          </select>
        </label>
        <label><span>科目</span><input v-model.trim="form.subject" required placeholder="如：高等数学" /></label>
        <label><span>知识点</span><input v-model.trim="form.knowledgePoint" required placeholder="如：微积分" /></label>
        <label>
          <span>难度</span>
          <select v-model="form.difficulty" required>
            <option v-for="item in difficultyOptions()" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label class="span-2"><span>题目</span><textarea v-model.trim="form.title" required placeholder="请输入题目内容"></textarea></label>
        <label class="span-2"><span>选项（使用 | 分隔）</span><input v-model.trim="form.optionsText" :disabled="!requiresOptions" placeholder="选项A | 选项B | 选项C" /></label>
        <label class="span-2"><span>答案（使用 | 分隔）</span><textarea v-model.trim="form.answerText" required placeholder="请输入正确答案"></textarea></label>
        <label><span>分值</span><input v-model.number="form.score" type="number" min="1" required /></label>
      </template>

      <template v-else-if="kind === 'exam'">
        <label><span>考试名称</span><input v-model.trim="form.name" required placeholder="请输入考试名称" /></label>
        <label>
          <span>试卷</span>
          <select v-model="form.paperId" required>
            <option v-for="item in bootstrap.papers" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
        <label>
          <span>班级</span>
          <select v-model="form.targetClassId" required>
            <option v-for="item in bootstrap.classes" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
        <label><span>开始时间</span><input v-model="form.startTime" type="datetime-local" required /></label>
        <label><span>结束时间</span><input v-model="form.endTime" type="datetime-local" required /></label>
        <label><span>切屏上限</span><input v-model.number="form.antiCheatLimit" type="number" min="0" required /></label>
      </template>

      <div class="action-row" style="justify-content:flex-end;margin-top:8px;">
        <button class="ghost-btn" type="button" @click="$emit('close')">取消</button>
        <button class="primary-btn" type="submit">保存</button>
      </div>
    </form>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import BaseModal from "./BaseModal.vue";
import type { BootstrapData, Exam, Question, User, Department, ClassRoom } from "../types";
import { difficultyOptions, splitByBar, toDateTimeLocalValue } from "../utils/format";

type EditorKind = "student" | "teacher" | "department" | "class" | "question" | "exam";

const props = defineProps<{
  kind: EditorKind;
  bootstrap: BootstrapData;
  model?: User | Department | ClassRoom | Question | Exam | null;
}>();

const emit = defineEmits<{
  close: [];
  submit: [payload: { entity: string; record: Record<string, unknown> }];
}>();

const form = reactive<Record<string, any>>({});

const isEdit = computed(() => Boolean(props.model));
const requiresOptions = computed(() => ["single", "multiple", "judge"].includes(form.type));

const dialogTitle = computed(() => {
  const prefix = isEdit.value ? "编辑" : "新增";
  return {
    student: `${prefix}学生`,
    teacher: `${prefix}教师`,
    department: `${prefix}学院`,
    class: `${prefix}班级`,
    question: `${prefix}题目`,
    exam: `${prefix}考试`,
  }[props.kind];
});

function resetForm() {
  Object.assign(form, {
    id: props.model && "id" in props.model ? props.model.id : "",
    username: props.model && "username" in props.model ? props.model.username : "",
    name: props.model && "name" in props.model ? props.model.name : "",
    password: props.model && "password" in props.model ? props.model.password || "123456" : "123456",
    classId: props.model && "classId" in props.model ? props.model.classId || props.bootstrap.classes[0]?.id || "" : props.bootstrap.classes[0]?.id || "",
    major: props.model && "major" in props.model ? props.model.major || "" : "",
    departmentId:
      props.model && "departmentId" in props.model ? props.model.departmentId || props.bootstrap.departments[0]?.id || "" : props.bootstrap.departments[0]?.id || "",
    type: props.model && "type" in props.model ? props.model.type : "single",
    subject: props.model && "subject" in props.model ? props.model.subject : "",
    knowledgePoint: props.model && "knowledgePoint" in props.model ? props.model.knowledgePoint : "",
    difficulty: props.model && "difficulty" in props.model ? props.model.difficulty : "中",
    title: props.model && "title" in props.model ? props.model.title : "",
    optionsText: props.model && "options" in props.model ? (props.model.options || []).join(" | ") : "",
    answerText: props.model && "answer" in props.model ? (props.model.answer || []).join(" | ") : "",
    score: props.model && "score" in props.model ? props.model.score : 5,
    paperId: props.model && "paperId" in props.model ? props.model.paperId : props.bootstrap.papers[0]?.id || "",
    targetClassId:
      props.model && "targetClassIds" in props.model ? props.model.targetClassIds?.[0] || props.bootstrap.classes[0]?.id || "" : props.bootstrap.classes[0]?.id || "",
    startTime: props.model && "startTime" in props.model ? toDateTimeLocalValue(props.model.startTime) : "",
    endTime: props.model && "endTime" in props.model ? toDateTimeLocalValue(props.model.endTime) : "",
    antiCheatLimit: props.model && "antiCheatLimit" in props.model ? props.model.antiCheatLimit : 3,
  });
}

watch(() => [props.kind, props.model], resetForm, { immediate: true });

function submitForm() {
  if (props.kind === "student" || props.kind === "teacher") {
    emit("submit", {
      entity: "users",
      record: {
        id: form.id || undefined,
        role: props.kind,
        username: form.username,
        name: form.name,
        password: form.password,
        classId: props.kind === "student" ? form.classId : undefined,
        major: props.kind === "student" ? form.major : undefined,
        departmentId: props.kind === "teacher" ? form.departmentId : undefined,
      },
    });
    return;
  }

  if (props.kind === "department") {
    emit("submit", { entity: "departments", record: { id: form.id || undefined, name: form.name } });
    return;
  }

  if (props.kind === "class") {
    emit("submit", {
      entity: "classes",
      record: { id: form.id || undefined, name: form.name, major: form.major, departmentId: form.departmentId },
    });
    return;
  }

  if (props.kind === "question") {
    emit("submit", {
      entity: "questions",
      record: {
        id: form.id || undefined,
        type: form.type,
        subject: form.subject,
        knowledgePoint: form.knowledgePoint,
        difficulty: form.difficulty,
        title: form.title,
        options: requiresOptions.value ? splitByBar(form.optionsText) : [],
        answer: splitByBar(form.answerText),
        score: Number(form.score),
      },
    });
    return;
  }

  emit("submit", {
    entity: "exams",
    record: {
      id: form.id || undefined,
      name: form.name,
      paperId: form.paperId,
      targetClassIds: [form.targetClassId],
      startTime: new Date(form.startTime).toISOString(),
      endTime: new Date(form.endTime).toISOString(),
      antiCheatLimit: Number(form.antiCheatLimit),
      published: true,
    },
  });
}
</script>
