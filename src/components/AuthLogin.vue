<template>
  <section class="login-shell">
    <div class="login-hero">
      <div class="login-hero-content">
        <div class="login-hero-badge">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10v6M2 10l10-5 10 5-10 5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/></svg>
          ONLINE EXAM SYSTEM
        </div>
        <h1>在线考试系统</h1>
        <p>高效、安全、智能的在线考试管理平台，支持管理员、教师、学生多端协同。</p>
        <div class="login-hero-features">
          <div class="login-hero-feature">
            <span class="login-hero-feature-dot"></span>
            管理员 · 学生教师管理、组织架构、系统日志
          </div>
          <div class="login-hero-feature">
            <span class="login-hero-feature-dot"></span>
            教师 · 题库组卷、考试发布、智能阅卷
          </div>
          <div class="login-hero-feature">
            <span class="login-hero-feature-dot"></span>
            学生 · 在线答题、成绩查看、错题重练
          </div>
        </div>
      </div>
    </div>
    <div class="login-form-side">
      <div class="login-card">
        <div class="login-card-header">
          <h1>欢迎回来</h1>
          <p class="muted">请登录您的账号以继续</p>
        </div>
        <form class="login-form" @submit.prevent="submitLogin">
          <label>
            <span>账号</span>
            <input v-model.trim="username" required placeholder="请输入用户名" autocomplete="username" />
          </label>
          <label>
            <span>密码</span>
            <input v-model.trim="password" type="password" required placeholder="请输入密码" autocomplete="current-password" />
          </label>
          <button class="login-submit-btn" type="submit" :disabled="loading">
            {{ loading ? "登录中..." : "登 录" }}
          </button>
        </form>
        <p class="login-message">{{ message }}</p>
      </div>
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
