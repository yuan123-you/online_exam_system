<template>
  <section class="register-content">
    <div v-if="success" class="register-success" role="status">
      <h2>注册成功</h2>
      <p>账号 {{ form.username }} 已创建，默认密码为 <strong>123456</strong>。</p>
      <p>登录后可在个人中心修改密码。</p>
      <button class="register-submit register-login" type="button" @click="emit('login', form.username)">前往登录</button>
    </div>
    <template v-else>
      <p v-if="error" class="register-error" role="alert">{{ error }}</p>
      <p v-if="optionsLoading" role="status">正在加载默认学院和班级…</p>
      <button v-if="optionsFailed" class="retry-button" type="button" @click="loadOptions">重新加载学院和班级</button>
      <form @submit.prevent="submit" class="register-form">
        <label for="register-username">登录账号<input id="register-username" v-model.trim="form.username" required minlength="4" maxlength="32" autocomplete="username" :disabled="submitting" placeholder="4至32位字母、数字或下划线" /></label>
        <label for="register-name">用户名<input id="register-name" v-model.trim="form.name" required maxlength="50" autocomplete="nickname" :disabled="submitting" placeholder="用于考试与成绩记录" /></label>
        <div class="register-defaults">
          <p v-if="defaultClass && defaultDepartment">自动归属：<strong>{{ defaultDepartment.name }} · {{ defaultClass.name }}</strong></p>
          <p>默认密码：<strong>123456</strong>，无需填写，登录后可修改。</p>
        </div>
        <button class="register-submit" type="submit" :disabled="submitting || optionsLoading || optionsFailed" :aria-busy="submitting">{{ submitting ? '正在创建账号…' : '创建学生账号' }}</button>
      </form>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getRegistrationOptions, registerStudent, type RegistrationOptions } from '@/api/client'
import { validateUsername } from '@/utils/validation'
const emit = defineEmits<{ login: [username: string] }>()
const form = reactive({ username: '', name: '' })
const submitting = ref(false), success = ref(false), error = ref('')
const optionsLoading = ref(true), optionsFailed = ref(false)
const options = ref<RegistrationOptions>({ departments: [], classes: [], defaultClassId: '' })
const defaultClass = computed(() => options.value.classes.find(item => item.id === options.value.defaultClassId))
const defaultDepartment = computed(() => options.value.departments.find(item => item.id === defaultClass.value?.departmentId))
async function loadOptions() {
  optionsLoading.value = true; optionsFailed.value = false; error.value = ''
  try {
    options.value = await getRegistrationOptions()
    if (!defaultClass.value || !defaultDepartment.value) throw new Error('默认班级暂不可用，请联系管理员。')
  } catch (e) {
    optionsFailed.value = true; error.value = e instanceof Error ? e.message : '无法加载学院和班级，请重试。'
  } finally { optionsLoading.value = false }
}
async function submit() {
  if (submitting.value || optionsLoading.value || optionsFailed.value) return
  error.value = ''
  const username = validateUsername(form.username)
  if (!username.valid) { error.value = username.message; return }
  if (!form.name || form.name.length > 50 || /[<>\p{Cc}]/u.test(form.name)) { error.value = '请填写有效用户名（最多50个字符）'; return }
  submitting.value = true
  try { await registerStudent({ ...form }); success.value = true }
  catch (e) { error.value = e instanceof Error ? e.message : '注册失败，请稍后重试。' }
  finally { submitting.value = false }
}
onMounted(loadOptions)
</script>
<style scoped>
.register-content { color:#203d32; }
.register-form { display:grid; gap:20px; }
label { display:grid; gap:9px; color:#203d32; font-size:14px; font-weight:600; }
input { box-sizing:border-box; width:100%; min-width:0; padding:14px; border:1px solid #a7c6b7; border-radius:10px; background:#fff; color:#203d32; font:inherit; }
input::placeholder { color:#62766c; opacity:1; font-weight:400; }
input:focus-visible,button:focus-visible { outline:3px solid #6bb69a; outline-offset:3px; }
.register-defaults { border:1px solid #c3dbcf; border-radius:10px; padding:12px 14px; background:#f0f7f3; color:#405d50; font-size:13px; line-height:1.7; }
.register-defaults p { margin:4px 0; }.register-defaults strong { color:#203d32; }
.register-submit { display:block; width:100%; padding:14px 20px; border:0; border-radius:10px; background:#287d63; color:white; font:inherit; font-weight:600; cursor:pointer; }
.register-submit:hover { background:#206a52; }.register-submit:disabled { opacity:.55; cursor:not-allowed; }
.register-error { color:#922b2b; background:#fff0ef; padding:14px; border-radius:8px; margin-bottom:18px; }
.register-success h2 { color:#206a52; }.register-success p { color:#203d32; line-height:1.8; margin:14px 0 20px; }
.retry-button { margin-bottom:18px; background:#fff; color:#206a52; border:1px solid #287d63; border-radius:8px; padding:10px 14px; cursor:pointer; }
</style>
