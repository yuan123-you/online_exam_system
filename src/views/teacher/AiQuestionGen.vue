<template>
  <article class="panel">
    <div class="section-title">
      <div>
        <h3>AI 智能出题</h3>
        <p class="section-subtitle">
          使用 AI 模型生成题目 | 题库: {{ store.aiQuotaUsed }}/{{ store.aiQuotaUsed + store.aiQuotaRemaining }}
        </p>
      </div>
      <button class="ghost-btn" type="button" @click="$router.push('/questions')">返回题库</button>
    </div>

    <!-- Mode Toggle -->
    <div class="mode-toggle">
      <button type="button" :class="['mode-btn', { active: mode === 'prompt' }]" @click="mode = 'prompt'">自定义提示词</button>
      <button type="button" :class="['mode-btn', { active: mode === 'form' }]" @click="mode = 'form'">预设参数</button>
    </div>

    <!-- Custom Prompt Mode -->
    <form v-if="mode === 'prompt'" class="ai-settings-form" @submit.prevent="generate">
      <label class="prompt-label">
        <span>请输入出题需求</span>
        <textarea v-model="customPrompt" rows="4" placeholder="例如：帮我出10道CSS的单选题目，包含选择器、盒模型、布局等知识点，难度适中，每题附带详细解析"></textarea>
      </label>
      <div class="prompt-tips">
        <span>示例提示词：</span>
        <button type="button" class="tip-chip" @click="customPrompt = '帮我出10道CSS的单选题目，包含选择器、盒模型、布局等知识点，难度适中'">CSS 单选题</button>
        <button type="button" class="tip-chip" @click="customPrompt = '出5道关于JavaScript闭包和作用链的多选题，难度较难，附详细解析'">JS 多选题</button>
        <button type="button" class="tip-chip" @click="customPrompt = '生成8道数据结构与算法的判断题，涵盖排序、查找、树的知识点'">数据结构判断题</button>
        <button type="button" class="tip-chip" @click="customPrompt = '出5道Python基础编程题，包含输入输出、循环、函数等，附参考答案和解析'">Python 编程题</button>
      </div>
      <div class="action-row" style="margin-top:12px;">
        <button class="primary-btn" type="submit" :disabled="store.aiLoading">
          {{ store.aiLoading ? 'AI 生成中...' : 'AI 生成题目' }}
        </button>
      </div>
    </form>

    <!-- Form Mode -->
    <form v-else class="ai-settings-form" @submit.prevent="generate">
      <div class="form-grid">
        <label>
          <span>科目</span>
          <select v-model="subject">
            <option value="">请选择科目</option>
            <option v-for="s in subjectOptions" :key="s" :value="s">{{ s }}</option>
          </select>
        </label>
        <label>
          <span>知识点</span>
          <input v-model="knowledgePoint" placeholder="如：循环结构、面向对象..." />
        </label>
        <label>
          <span>题型</span>
          <select v-model="type">
            <option value="single">单选题</option>
            <option value="multiple">多选题</option>
            <option value="judge">判断题</option>
            <option value="fill">填空题</option>
            <option value="short">简答题</option>
            <option value="coding">编程题</option>
          </select>
        </label>
        <label>
          <span>难度</span>
          <select v-model="difficulty">
            <option value="easy">简单</option>
            <option value="medium">中等</option>
            <option value="hard">困难</option>
          </select>
        </label>
        <label>
          <span>数量 (1-10)</span>
          <input v-model.number="count" type="number" min="1" max="10" />
        </label>
      </div>
      <div class="action-row" style="margin-top:12px;">
        <button class="primary-btn" type="submit" :disabled="store.aiLoading">
          {{ store.aiLoading ? 'AI 生成中...' : 'AI 生成题目' }}
        </button>
      </div>
    </form>

    <!-- Streaming Mode Toggle -->
    <div class="mode-toggle" style="margin-bottom:12px;">
      <button type="button" :class="['mode-btn', { active: !streamMode }]" @click="streamMode = false">普通模式</button>
      <button type="button" :class="['mode-btn', { active: streamMode }]" @click="streamMode = true">流式模式 (深度思考)</button>
    </div>

    <!-- Loading / Streaming Live Display -->
    <div v-if="store.aiLoading" class="ai-loading">
      <div v-if="streamMode && store.streamingActive" class="streaming-box">
        <div v-if="store.streamingReasoning" class="streaming-reasoning">
          <h5>🤔 AI 思考中...</h5>
          <pre class="streaming-text">{{ store.streamingReasoning }}</pre>
        </div>
        <div v-if="store.streamingContent" class="streaming-content">
          <h5>📝 生成内容</h5>
          <pre class="streaming-text">{{ store.streamingContent }}</pre>
        </div>
        <div class="streaming-cursor">▋</div>
      </div>
      <p v-else>AI 正在生成题目，请稍候...</p>
    </div>

    <!-- Question Preview List -->
    <div v-if="store.aiQuestions.length > 0" class="ai-preview">
      <div class="section-title" style="margin-top:16px;">
        <h4>预览（已选 {{ selectedCount }}/{{ store.aiQuestions.length }}）</h4>
        <div class="action-row">
          <button class="primary-btn" type="button" :disabled="selectedCount === 0 || store.aiLoading" @click="importSelected">
            导入选中到题库
          </button>
          <button class="ghost-btn" type="button" @click="discard">放弃所有</button>
        </div>
      </div>

      <div v-for="(q, idx) in store.aiQuestions" :key="q.id || idx" class="ai-question-card">
        <div class="ai-question-header">
          <label class="ai-checkbox">
            <input type="checkbox" :checked="selected[idx]" @change="toggleSelect(idx)" />
            <span>第 {{ idx + 1 }} 题</span>
          </label>
          <span class="tag">{{ typeLabel(q.type as any) }}</span>
          <span class="tag">{{ difficultyLabel(q.difficulty) }}</span>
          <span class="tag">{{ q.score }} 分</span>
          <button class="ghost-btn" type="button" @click="toggleEdit(idx)">
            {{ editingIdx === idx ? '完成编辑' : '编辑' }}
          </button>
        </div>

        <!-- View Mode -->
        <div v-if="editingIdx !== idx" class="ai-question-body">
          <p class="ai-title">{{ q.title }}</p>
          <ul v-if="q.options && q.options.length > 0" class="ai-options">
            <li v-for="(opt, oi) in q.options" :key="oi">{{ opt }}</li>
          </ul>
          <p class="ai-answer"><strong>答案：</strong>{{ (q.answer || []).join('、') || '-' }}</p>
          <p class="ai-explanation"><strong>解析：</strong>{{ q.explanation || '暂无解析' }}</p>
        </div>

        <!-- Edit Mode -->
        <div v-else class="ai-question-edit">
          <label>
            <span>题目</span>
            <textarea v-model="q.title" rows="3"></textarea>
          </label>
          <label v-if="q.options && q.options.length > 0">
            <span>选项（每行一个）</span>
            <textarea v-model="optionsText[idx]" rows="4"></textarea>
          </label>
          <label>
            <span>答案（逗号分隔）</span>
            <input v-model="answerText[idx]" />
          </label>
          <label>
            <span>解析</span>
            <textarea v-model="q.explanation" rows="2"></textarea>
          </label>
          <label>
            <span>分值</span>
            <input v-model.number="q.score" type="number" min="1" max="100" />
          </label>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-if="!store.aiLoading && store.aiQuestions.length === 0" class="ai-empty">
      <p>{{ mode === 'prompt' ? '输入出题需求后点击生成' : '请设置参数后点击"AI 生成题目"按钮' }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { typeLabel } from '@/utils/format'
import type { AiQuestion } from '@/api/client'

const store = useAppStore()

const mode = ref<'prompt' | 'form'>('prompt')
const streamMode = ref(false)
const customPrompt = ref('')
const subject = ref('')
const knowledgePoint = ref('')
const type = ref('single')
const difficulty = ref('medium')
const count = ref(5)
const editingIdx = ref(-1)

const selected = reactive<Record<number, boolean>>({})
const optionsText = reactive<Record<number, string>>({})
const answerText = reactive<Record<number, string>>({})

// Initialize all as selected when questions change
watch(() => store.aiQuestions.length, (len) => {
  for (let i = 0; i < len; i++) {
    if (selected[i] === undefined) selected[i] = true
  }
})

const subjectOptions = computed(() => {
  const subjects = new Set<string>()
  store.myQuestions.forEach(q => { if (q.subject) subjects.add(q.subject) })
  if (subjects.size === 0) {
    ['Java', 'Python', '数据结构', '计算机网络', '操作系统', '数据库', '计算机基础'].forEach(s => subjects.add(s))
  }
  return [...subjects].sort()
})

const selectedCount = computed(() => {
  return Object.values(selected).filter(Boolean).length
})

function difficultyLabel(d: string) {
  return { easy: '简单', medium: '中等', hard: '困难' }[d] || d
}

function toggleSelect(idx: number) {
  selected[idx] = !selected[idx]
}

function toggleEdit(idx: number) {
  if (editingIdx.value === idx) {
    // Save edits
    const q = store.aiQuestions[idx]
    if (optionsText[idx] !== undefined) {
      q.options = optionsText[idx].split('\n').map(s => s.trim()).filter(Boolean)
    }
    if (answerText[idx] !== undefined) {
      q.answer = answerText[idx].split(',').map(s => s.trim()).filter(Boolean)
    }
    editingIdx.value = -1
  } else {
    const q = store.aiQuestions[idx]
    optionsText[idx] = (q.options || []).join('\n')
    answerText[idx] = (q.answer || []).join(', ')
    editingIdx.value = idx
  }
}

async function generate() {
  if (mode.value === 'prompt') {
    if (!customPrompt.value.trim()) {
      store.showToast('请输入出题需求', 'error')
      return
    }
  } else {
    if (!subject.value) {
      store.showToast('请选择科目', 'error')
      return
    }
  }

  const params: Record<string, any> = {}
  if (mode.value === 'prompt') {
    params.customPrompt = customPrompt.value.trim()
    params.subject = subject.value || '计算机基础'
    params.type = type.value
    params.difficulty = difficulty.value
    params.count = count.value
  } else {
    params.subject = subject.value
    params.knowledgePoint = knowledgePoint.value
    params.type = type.value
    params.difficulty = difficulty.value
    params.count = count.value
  }

  try {
    if (streamMode.value) {
      store.handleAiGenerateQuestionsStream(params as any)
    } else {
      await store.handleAiGenerateQuestions(params as any)
    }
  } catch (err) {
    // Error already handled by store (toast)
  }
}

function discard() {
  store.clearAiQuestions()
  editingIdx.value = -1
  Object.keys(selected).forEach(k => delete selected[Number(k)])
}

async function importSelected() {
  const selectedQuestions: AiQuestion[] = []
  store.aiQuestions.forEach((q, idx) => {
    if (selected[idx]) selectedQuestions.push(q)
  })
  if (selectedQuestions.length === 0) return
  await store.handleAiImportQuestions(selectedQuestions)
}
</script>

<style scoped>
.mode-toggle {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  background: var(--bg-alt, #f5f5f5);
  border-radius: 8px;
  padding: 4px;
  width: fit-content;
}

.mode-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-btn.active {
  background: var(--card-bg, #fff);
  color: var(--text);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.ai-settings-form {
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--muted);
}

.form-grid label input,
.form-grid label select {
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
  background: var(--bg);
  color: var(--text);
}

.prompt-label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.prompt-label span {
  font-size: 13px;
  color: var(--muted);
  font-weight: 500;
}

.prompt-label textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  line-height: 1.6;
  background: var(--bg);
  color: var(--text);
  transition: border-color 0.2s;
}

.prompt-label textarea:focus {
  outline: none;
  border-color: var(--primary);
}

.prompt-tips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.prompt-tips > span {
  font-size: 12px;
  color: var(--muted);
}

.tip-chip {
  padding: 4px 12px;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: var(--bg);
  color: var(--text);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.tip-chip:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: color-mix(in srgb, var(--primary) 6%, transparent);
}

.ai-loading {
  text-align: center;
  padding: 40px 20px;
  color: var(--primary);
  font-size: 15px;
}

/* Streaming live display */
.streaming-box {
  text-align: left;
  max-width: 100%;
  overflow: hidden;
}

.streaming-reasoning,
.streaming-content {
  margin-bottom: 16px;
}

.streaming-reasoning h5,
.streaming-content h5 {
  font-size: 14px;
  margin-bottom: 8px;
  color: var(--text);
}

.streaming-text {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  background: var(--bg-alt, #f5f5f5);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
  max-height: 400px;
  overflow-y: auto;
  text-align: left;
  color: var(--text);
}

.streaming-reasoning .streaming-text {
  color: #8b5cf6;
  border-color: #8b5cf640;
  background: #f5f3ff;
}

.streaming-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  font-size: 20px;
  color: var(--primary);
  padding-left: 4px;
}

@keyframes blink {
  50% { opacity: 0; }
}

.ai-empty {
  text-align: center;
  padding: 40px 20px;
  color: var(--muted);
}

.ai-preview {
  margin-top: 8px;
}

.ai-question-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  background: var(--card-bg, var(--bg));
}

.ai-question-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.ai-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  cursor: pointer;
}

.ai-question-body .ai-title {
  font-weight: 500;
  margin-bottom: 8px;
  line-height: 1.5;
}

.ai-options {
  list-style: none;
  padding: 0;
  margin: 0 0 8px 0;
}

.ai-options li {
  padding: 4px 12px;
  margin-bottom: 4px;
  border-radius: 4px;
  background: var(--bg-alt, #f5f5f5);
  font-size: 14px;
}

.ai-answer, .ai-explanation {
  font-size: 13px;
  color: var(--muted);
  margin-top: 4px;
}

.ai-question-edit {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-question-edit label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--muted);
}

.ai-question-edit label input,
.ai-question-edit label textarea {
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
  background: var(--bg);
  color: var(--text);
  font-family: inherit;
}
</style>
