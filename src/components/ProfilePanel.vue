<template>
  <section class="two-column">
    <article class="panel">
      <div class="section-head">
        <div>
          <h3>基础信息</h3>
          <p class="muted">当前登录账号与所属信息。</p>
        </div>
      </div>
      <div class="detail-list">
        <div class="detail-item"><span>姓名</span><strong>{{ currentUser.name }}</strong></div>
        <div class="detail-item"><span>账号</span><strong>{{ currentUser.username }}</strong></div>
        <div class="detail-item"><span>身份</span><strong>{{ roleLabel(currentUser.role) }}</strong></div>
        <div class="detail-item"><span>班级 / 学院</span><strong>{{ classLabel }}</strong></div>
      </div>
    </article>

    <article class="panel">
      <div class="section-head">
        <div>
          <h3>修改密码</h3>
          <p class="muted">密码长度至少 6 位。</p>
        </div>
      </div>
      <form class="form-grid" @submit.prevent="submitForm">
        <label>
          <span>原密码</span>
          <input v-model.trim="oldPassword" type="password" required />
        </label>
        <label>
          <span>新密码</span>
          <input v-model.trim="newPassword" type="password" minlength="6" required />
        </label>
        <button class="primary-btn" type="submit">保存修改</button>
      </form>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { BootstrapData } from "../types";
import { roleLabel } from "../utils/format";

const props = defineProps<{
  bootstrap: BootstrapData;
}>();

const emit = defineEmits<{
  submit: [payload: { oldPassword: string; newPassword: string }];
}>();

const oldPassword = ref("");
const newPassword = ref("");

const currentUser = computed(() => props.bootstrap.currentUser);
const classLabel = computed(() => {
  const user = currentUser.value;
  if (user.classId) {
    return props.bootstrap.classes.find((item) => item.id === user.classId)?.name || "-";
  }
  if (user.departmentId) {
    return props.bootstrap.departments.find((item) => item.id === user.departmentId)?.name || "-";
  }
  return "-";
});

function submitForm() {
  emit("submit", { oldPassword: oldPassword.value, newPassword: newPassword.value });
  oldPassword.value = "";
  newPassword.value = "";
}
</script>
