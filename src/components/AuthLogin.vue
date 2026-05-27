<template>
  <section class="login-shell">
    <div class="login-card">
      <div>
        <p class="eyebrow">ONLINE EXAM</p>
        <h1>在线考试系统</h1>
        <p class="muted">前端已重构为 Vue 3 + Vite + TypeScript，后端继续使用 Node.js 提供 API。</p>
      </div>
      <form class="form-grid" @submit.prevent="submitLogin">
        <label>
          <span>账号</span>
          <input v-model.trim="username" required />
        </label>
        <label>
          <span>密码</span>
          <input v-model.trim="password" type="password" required />
        </label>
        <button class="primary-btn wide" type="submit" :disabled="loading">
          {{ loading ? "登录中..." : "进入系统" }}
        </button>
      </form>
      <p class="message">{{ message }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";

const props = defineProps<{
  loading: boolean;
  message: string;
  defaultUsername?: string;
}>();

const emit = defineEmits<{
  submit: [payload: { username: string; password: string }];
}>();

const username = ref(props.defaultUsername || "admin");
const password = ref("123456");

watch(
  () => props.defaultUsername,
  (value) => {
    if (value) username.value = value;
  }
);

function submitLogin() {
  emit("submit", { username: username.value, password: password.value });
}
</script>
