<template>
  <section class="login-shell">
    <!-- Left: project intro -->
    <div class="login-intro">
      <div class="intro-decor">
        <div class="decor-circle decor-circle-1"></div>
        <div class="decor-circle decor-circle-2"></div>
        <div class="decor-circle decor-circle-3"></div>
      </div>

      <div class="intro-content">
        <div class="intro-brand">
          <div class="intro-logo">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10v6M2 10l10-5 10 5-10 5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/></svg>
          </div>
          <h1>在线考试系统</h1>
          <p class="intro-desc">
            集题库管理、智能出题、自动阅卷、成绩分析于一体的新一代在线考试平台，
            为教师减负，为学生提效。
          </p>
        </div>

        <div class="intro-features">
          <div v-for="(feat, idx) in features" :key="feat.title" class="intro-feature-card" :style="{ animationDelay: idx * 80 + 'ms' }">
            <span class="feat-icon">{{ feat.icon }}</span>
            <div class="feat-body">
              <strong>{{ feat.title }}</strong>
              <p>{{ feat.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Right: login form -->
    <div class="login-panel">
      <div class="login-card">
        <div class="brand-block">
          <div class="brand-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
          </div>
          <p class="eyebrow">ONLINE EXAM</p>
          <h1>用户登录</h1>
          <p class="brand-subtitle">欢迎回来，请输入您的账号信息</p>
        </div>
        <form class="form-grid" @submit.prevent="submitLogin" autocomplete="off" data-lpignore="true">
          <div aria-hidden="true" style="position:absolute;left:-9999px;top:-9999px;overflow:hidden">
            <input type="text" name="__trap_user" tabindex="-1" autocomplete="username" />
            <input type="password" name="__trap_pass" tabindex="-1" autocomplete="current-password" />
          </div>

          <label class="form-field">
            <span class="field-label">账号</span>
            <div class="input-wrapper">
              <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              <input
                ref="usernameRef"
                v-model.trim="username"
                required
                placeholder="请输入用户名"
                name="login-id"
                autocomplete="off"
                data-lpignore="true"
                class="login-input"
                @input="clearError"
              />
            </div>
          </label>
          <label class="form-field">
            <span class="field-label">密码</span>
            <div class="input-wrapper">
              <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              <div class="password-input-wrap">
                <input
                  ref="passwordRef"
                  v-model.trim="password"
                  :type="passwordVisible ? 'text' : 'password'"
                  required
                  placeholder="请输入密码"
                  name="login-key"
                  autocomplete="new-password"
                  data-lpignore="true"
                  class="login-input"
                  @input="clearError"
                />
                <button type="button" class="password-toggle-btn" @click="passwordVisible = !passwordVisible" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'">
                  <svg v-if="!passwordVisible" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                </button>
              </div>
            </div>
          </label>
          <button class="login-submit-btn" type="submit" :disabled="loading">
            <span v-if="loading" class="login-spinner"></span>
            {{ loading ? "登录中..." : "进入系统" }}
          </button>
        </form>
      </div>
    </div>

    <BaseModal v-if="visibleMessage" @close="visibleMessage = ''">
      <template #header>
        <h3 style="margin:0;color:var(--danger);">登录失败</h3>
      </template>
      <p style="margin:0;line-height:1.6;">{{ visibleMessage }}</p>
      <button class="primary-btn wide" style="margin-top:20px;" type="button" @click="visibleMessage = ''">确定</button>
    </BaseModal>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from "vue"
import BaseModal from "@/components/common/BaseModal.vue"
import { validateUsername, validatePassword } from "@/utils/validation"

const features = [
  { icon: '📚', title: '智能题库管理', desc: '海量题目自由编排，按科目/知识点/难度多维度筛选，支持手动录入与 AI 批量生成。' },
  { icon: '🤖', title: 'AI 智能出题', desc: '接入大语言模型，一句话描述需求即可自动生成高质量题目与完整解析。' },
  { icon: '📝', title: '灵活组卷考试', desc: '支持手动组卷与自动组卷，考试时长、及格线、防作弊切屏均可灵活配置。' },
  { icon: '📊', title: '自动阅卷分析', desc: '客观题自动判分，主观题 AI 辅助评分，多维度成绩分析与知识点掌握度雷达图。' },
  { icon: '📖', title: '错题本回顾', desc: '自动收录考试错题，支持反复重做巩固薄弱知识点，学习路径一目了然。' },
  { icon: '🎯', title: 'AI 学习助手', desc: '自由对话的智能学伴，随时解答疑难、生成练习题，支持深度思考与流式输出。' },
]

const props = defineProps<{
  loading: boolean; message: string; defaultUsername?: string
}>()

const emit = defineEmits<{
  submit: [payload: { username: string; password: string }]
  clearMessage: []
}>()

const username = ref(props.defaultUsername ?? "")
const password = ref("")
const passwordVisible = ref(false)
const usernameRef = ref<HTMLInputElement | null>(null)
const passwordRef = ref<HTMLInputElement | null>(null)
const visibleMessage = ref("")

watch(() => props.message, (val) => { if (val) { visibleMessage.value = val; emit("clearMessage") } })

function submitLogin() {
  if (!username.value.trim()) { visibleMessage.value = '账号仅支持 4-20 位字母和数字，不能包含空格、下划线及任何特殊符号'; return }
  if (!password.value) { visibleMessage.value = '密码仅支持 6-18 位字母和数字，不能包含特殊符号'; return }
  const u = validateUsername(username.value); if (!u.valid) { visibleMessage.value = u.message; return }
  const p = validatePassword(password.value); if (!p.valid) { visibleMessage.value = p.message; return }
  emit("submit", { username: username.value, password: password.value })
}

function clearError() { emit("clearMessage") }

onMounted(() => nextTick(() => {
  if (!props.defaultUsername) { username.value = ""; if (usernameRef.value) usernameRef.value.value = "" }
  password.value = ""; if (passwordRef.value) passwordRef.value.value = ""
}))
</script>

<style scoped>
/* ===== Shell: Full-screen two-column layout ===== */
.login-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 30% 20%, rgba(61, 153, 128, 0.08), transparent 50%),
    radial-gradient(ellipse at 70% 80%, rgba(168, 218, 200, 0.25), transparent 50%),
    linear-gradient(165deg, #e8f5f0, #d4ece2);
}

/* ===== Left: Intro panel ===== */
.login-intro {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 56px;
  background: transparent;
  color: var(--ink);
  overflow: hidden;
}

/* Decorative floating circles */
.intro-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}
.decor-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;
  background: var(--primary);
}
.decor-circle-1 {
  width: 400px; height: 400px;
  top: -120px; left: -100px;
  animation: floatSlow 20s ease-in-out infinite;
}
.decor-circle-2 {
  width: 250px; height: 250px;
  bottom: -60px; right: -40px;
  animation: floatSlow 16s ease-in-out infinite reverse;
}
.decor-circle-3 {
  width: 150px; height: 150px;
  top: 40%; left: 60%;
  animation: floatSlow 12s ease-in-out infinite 3s;
}
@keyframes floatSlow {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(15px, -20px) scale(1.03); }
  66% { transform: translate(-10px, 15px) scale(0.97); }
}

.intro-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 520px;
}

/* Brand area */
.intro-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40px;
  animation: fadeInUp 0.6s var(--ease) both;
}

.intro-logo {
  width: 56px; height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(61, 153, 128, 0.2);
}

.intro-brand h1 {
  font-size: 28px; font-weight: 700;
  margin: 0 0 12px;
  color: var(--ink);
  letter-spacing: -0.02em;
}

.intro-desc {
  margin: 0; font-size: 14px;
  line-height: 1.7; color: var(--muted);
  text-align: center;
  max-width: 400px;
}

/* Feature cards */
.intro-features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 100%;
}

.intro-feature-card {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 12px 14px; border-radius: 12px;
  background: rgba(255,255,255,0.6);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(200, 221, 214, 0.5);
  text-align: left;
  transition: background 0.25s var(--ease), transform 0.2s var(--ease), border-color 0.25s var(--ease);
  animation: fadeInUp 0.5s var(--ease) both;
}
.intro-feature-card:hover {
  background: rgba(255,255,255,0.9);
  border-color: var(--line);
  transform: translateY(-2px);
}

.feat-icon {
  font-size: 18px; flex-shrink: 0;
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 8px;
  background: var(--primary-soft);
}

.feat-body strong {
  display: block; font-size: 13px; margin-bottom: 2px;
  color: var(--ink); font-weight: 600;
}
.feat-body p {
  margin: 0; font-size: 12px;
  color: var(--muted);
  line-height: 1.45;
}

/* ===== Right: Login panel ===== */
.login-panel {
  display: flex; align-items: center; justify-content: center;
  padding: 48px 56px;
  background: #eaf1ed;
}

.login-card {
  width: 100%; max-width: 420px;
  padding: 36px 32px;
  background: #fff;
  border-radius: 20px;
  box-shadow:
    0 1px 2px rgba(26, 58, 52, 0.04),
    0 4px 16px rgba(26, 58, 52, 0.06),
    0 12px 40px rgba(26, 58, 52, 0.04);
  animation: cardEnter 0.5s var(--ease) both;
  animation-delay: 0.1s;
}

@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* Brand block in card */
.brand-block {
  text-align: center;
  margin-bottom: 32px;
}

.brand-icon {
  width: 44px; height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  margin: 0 auto 16px;
  box-shadow: 0 4px 12px rgba(61, 153, 128, 0.25);
}

.brand-block h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: -0.01em;
}

.brand-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--muted);
  line-height: 1.5;
}

/* Form */
.form-grid {
  display: flex; flex-direction: column; gap: 20px;
}

.form-field {
  display: flex; flex-direction: column; gap: 6px;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-secondary);
  letter-spacing: 0.01em;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  color: var(--muted-light);
  pointer-events: none;
  transition: color 0.2s var(--ease);
  z-index: 1;
}

.login-input {
  width: 100%;
  padding: 13px 16px 13px 42px;
  border: 1.5px solid #e2e8e5;
  border-radius: 12px;
  font-size: 14px; font-family: inherit;
  background: #f8faf9;
  color: var(--ink);
  outline: none;
  transition: border-color 0.2s var(--ease), box-shadow 0.2s var(--ease), background 0.2s var(--ease);
}
.login-input::placeholder {
  color: var(--muted-light);
}
.login-input:hover {
  border-color: #c8ddd6;
  background: #f2f7f5;
}
.login-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-glow);
  background: #fff;
}
.login-input:focus ~ .input-icon,
.login-input:focus + .input-icon,
.input-wrapper:focus-within .input-icon {
  color: var(--primary);
}

/* Password input wrap */
.password-input-wrap {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
}

.password-input-wrap .login-input {
  padding-right: 44px;
}

.password-toggle-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: var(--muted-light);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: color 0.2s var(--ease), background 0.2s var(--ease);
}
.password-toggle-btn:hover {
  color: var(--primary);
  background: var(--primary-soft);
}

/* Submit button */
.login-submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 14px 24px;
  margin-top: 4px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: transform 0.15s var(--ease), box-shadow 0.2s var(--ease), opacity 0.2s var(--ease);
  box-shadow: 0 2px 8px rgba(61, 153, 128, 0.25);
  letter-spacing: 0.02em;
}
.login-submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(61, 153, 128, 0.35);
}
.login-submit-btn:active:not(:disabled) {
  transform: translateY(0) scale(0.98);
  box-shadow: 0 2px 6px rgba(61, 153, 128, 0.2);
}
.login-submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  box-shadow: none;
}

/* Spinner */
.login-spinner {
  display: inline-block;
  width: 16px; height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: loginSpin 0.7s linear infinite;
  margin-right: 8px;
  vertical-align: middle;
}
@keyframes loginSpin {
  to { transform: rotate(360deg); }
}

/* Fade-in-up animation */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== Responsive: Tablet ===== */
@media (max-width: 1024px) {
  .login-intro {
    padding: 40px 36px;
  }
  .intro-brand h1 {
    font-size: 24px;
  }
  .intro-features {
    gap: 8px;
  }
  .intro-feature-card {
    padding: 10px 12px;
  }
  .feat-body strong { font-size: 12px; }
  .feat-body p { font-size: 11px; }
  .login-panel {
    padding: 40px 36px;
  }
  .login-card {
    padding: 36px 32px;
  }
}

/* ===== Responsive: Mobile ===== */
@media (max-width: 768px) {
  .login-shell {
    grid-template-columns: 1fr;
    background: #eaf1ed;
  }
  .login-intro {
    display: none;
  }
  .login-panel {
    padding: 20px 16px;
    min-height: 100dvh;
  }
  .login-card {
    max-width: 400px;
    padding: 28px 24px;
    border-radius: 16px;
    box-shadow:
      0 1px 2px rgba(26, 58, 52, 0.04),
      0 4px 16px rgba(26, 58, 52, 0.06);
  }
  .brand-block {
    margin-bottom: 24px;
  }
  .brand-block h1 {
    font-size: 20px;
  }
  .form-grid {
    gap: 14px;
  }
  .login-submit-btn {
    padding: 12px 18px;
    font-size: 14px;
  }
}

/* ===== Responsive: Small mobile ===== */
@media (max-width: 400px) {
  .login-panel {
    padding: 12px 10px;
  }
  .login-card {
    padding: 20px 16px;
    border-radius: 14px;
  }
  .login-input {
    padding: 12px 14px 12px 38px;
    font-size: 13px;
  }
  .input-icon {
    left: 12px;
    width: 16px; height: 16px;
  }
}
</style>
