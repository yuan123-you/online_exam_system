<template>
  <section class="login-shell">
    <!-- Left: project intro -->
    <div class="login-intro">
      <div class="intro-brand">
        <div class="intro-logo">
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10v6M2 10l10-5 10 5-10 5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/></svg>
        </div>
        <h1>在线考试系统</h1>
        <p class="intro-desc">
          集题库管理、智能出题、自动阅卷、成绩分析于一体的新一代在线考试平台，
          为教师减负，为学生提效。
        </p>
      </div>

      <div class="intro-features">
        <div v-for="feat in features" :key="feat.title" class="intro-feature-card">
          <span class="feat-icon">{{ feat.icon }}</span>
          <div class="feat-body">
            <strong>{{ feat.title }}</strong>
            <p>{{ feat.desc }}</p>
          </div>
        </div>
      </div>

    </div>

    <!-- Right: login form -->
    <div class="login-panel">
      <div class="login-card card">
        <div class="brand-block">
          <p class="eyebrow">ONLINE EXAM</p>
          <h1>用户登录</h1>
        </div>
        <form class="form-grid" @submit.prevent="submitLogin" autocomplete="off" data-lpignore="true">
          <div aria-hidden="true" style="position:absolute;left:-9999px;top:-9999px;overflow:hidden">
            <input type="text" name="__trap_user" tabindex="-1" autocomplete="username" />
            <input type="password" name="__trap_pass" tabindex="-1" autocomplete="current-password" />
          </div>

          <label>
            <span>账号</span>
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
          </label>
          <label>
            <span>密码</span>
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
          </label>
          <button class="primary-btn wide login-submit-btn" type="submit" :disabled="loading">
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
  if (!username.value.trim()) { visibleMessage.value = '账号仅支持 6-20 位字母和数字，不能包含空格、下划线及任何特殊符号'; return }
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
.login-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  position: relative;
  background:
    radial-gradient(ellipse at 30% 20%, rgba(61, 153, 128, 0.08), transparent 50%),
    radial-gradient(ellipse at 70% 80%, rgba(168, 218, 200, 0.25), transparent 50%),
    linear-gradient(165deg, #e8f5f0, #d4ece2);
}

/* ===== Logo badge at top of left panel ===== */
.intro-logo {
  width: 60px; height: 60px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--primary), #1e7a5e);
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  margin-bottom: 24px;
}

/* ===== Left: intro ===== */
.login-intro {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 64px 80px;
}

.intro-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 36px;
}

.intro-features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 0;
  width: 100%;
  max-width: 560px;
}

.intro-feature-card {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 14px 16px; border-radius: 10px;
  background: rgba(255,255,255,0.6);
  transition: background 0.2s, transform 0.15s;
  text-align: left;
}

.intro-footer {
  display: flex;
  justify-content: center;
  margin-top: auto;
  width: 100%;
  max-width: 500px;
}

.intro-stats {
  display: flex; gap: 32px; justify-content: center;
  padding-top: 18px; width: 100%;
  border-top: 1px solid rgba(0,0,0,0.08);
}

.intro-brand h1 {
  font-size: 32px; font-weight: 700;
  margin: 0 0 12px;
  color: var(--text);
}

.intro-desc {
  margin: 0; font-size: 15px;
  line-height: 1.7; color: var(--muted);
}

.intro-feature-card:hover {
  background: rgba(255,255,255,0.9);
  transform: translateY(-1px);
}

.feat-icon {
  font-size: 20px; flex-shrink: 0;
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 8px;
  background: var(--bg);
}

.feat-body strong { display: block; font-size: 14px; margin-bottom: 3px; color: var(--text); }
.feat-body p { margin: 0; font-size: 13px; color: var(--muted); line-height: 1.4; }

.stat-item { display: flex; flex-direction: column; gap: 2px; }
.stat-num { font-size: 20px; font-weight: 700; color: var(--primary); }
.stat-label { font-size: 12px; color: var(--muted); }

/* ===== Right: login panel ===== */
.login-panel {
  display: flex; align-items: center; justify-content: center;
  padding: 64px 80px;
  background: #eaf1ed;
}

.login-card {
  width: 100%; max-width: 460px;
  padding: 48px 52px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.06);
}

.form-grid {
  display: flex; flex-direction: column; gap: 18px;
}

.form-grid label { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--muted); }

.login-input {
  padding: 14px 16px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  font-size: 15px; font-family: inherit;
  background: #f9fafb;
  color: var(--text);
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.login-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(61,153,128,0.1);
  background: #fff;
}

.primary-btn.wide { margin-top: 4px; }

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .login-shell { grid-template-columns: 1fr; }
  .login-intro { display: none; }
  .login-panel { padding: 32px 24px; }
}
</style>
