<template>
  <AuthLogin
    v-if="!bootstrap"
    :loading="loginLoading"
    :message="loginMessage"
    default-username="admin"
    @submit="handleLogin"
  />

  <AppShell
    v-else
    :current-user="bootstrap.currentUser"
    :menu-items="menuItems"
    :active-menu="activeMenu"
    @change-menu="activeMenu = $event"
    @reload="loadData"
    @logout="handleLogout"
  >
    <!-- 管理员：数据总览 -->
    <template v-if="activeMenu === 'overview'">
      <section class="stats-grid compact-stats">
        <article class="stat-card compact-card">
          <span class="muted">学生</span>
          <strong>{{ studentCount }}</strong>
        </article>
        <article class="stat-card compact-card">
          <span class="muted">教师</span>
          <strong>{{ teacherCount }}</strong>
        </article>
        <article class="stat-card compact-card">
          <span class="muted">题库</span>
          <strong>{{ bootstrap.questions.length }}</strong>
        </article>
        <article class="stat-card compact-card">
          <span class="muted">考试</span>
          <strong>{{ bootstrap.exams.length }}</strong>
        </article>
      </section>
      <ChartCard
        title="题库分布"
        description="按科目统计题目数量"
        :option="subjectChartOption"
      />
    </template>

    <!-- 管理员：学生管理 -->
    <article v-else-if="activeMenu === 'students'" class="panel">
      <div class="section-title">
        <div>
          <h3>学生管理</h3>
          <p class="section-subtitle">共 {{ students.length }} 名学生</p>
        </div>
        <div class="section-actions">
          <button class="accent-btn" type="button" @click="showBatchImport('student')">批量导入</button>
          <button class="primary-btn" type="button" @click="openEditor('student', null)">新增学生</button>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>学号</th>
              <th>姓名</th>
              <th>班级</th>
              <th>专业</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="students.length === 0">
              <td colspan="5" style="text-align:center;color:var(--muted);padding:32px;">暂无学生数据</td>
            </tr>
            <tr v-for="user in students" :key="user.id">
              <td>{{ user.username }}</td>
              <td><strong>{{ user.name }}</strong></td>
              <td>{{ className(user.classId) }}</td>
              <td>{{ user.major || "-" }}</td>
              <td>
                <div class="action-row">
                  <button class="ghost-btn" type="button" @click="openEditor('student', user)">编辑</button>
                  <button class="danger-btn" type="button" @click="removeEntity('users', user.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 管理员：教师管理 -->
    <article v-else-if="activeMenu === 'teachers'" class="panel">
      <div class="section-title">
        <div>
          <h3>教师管理</h3>
          <p class="section-subtitle">共 {{ teachers.length }} 名教师</p>
        </div>
        <div class="section-actions">
          <button class="accent-btn" type="button" @click="showBatchImport('teacher')">批量导入</button>
          <button class="primary-btn" type="button" @click="openEditor('teacher', null)">新增教师</button>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>账号</th>
              <th>姓名</th>
              <th>学院</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="teachers.length === 0">
              <td colspan="4" style="text-align:center;color:var(--muted);padding:32px;">暂无教师数据</td>
            </tr>
            <tr v-for="user in teachers" :key="user.id">
              <td>{{ user.username }}</td>
              <td><strong>{{ user.name }}</strong></td>
              <td>{{ departmentName(user.departmentId) }}</td>
              <td>
                <div class="action-row">
                  <button class="ghost-btn" type="button" @click="openEditor('teacher', user)">编辑</button>
                  <button class="danger-btn" type="button" @click="removeEntity('users', user.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 管理员：组织管理 -->
    <section v-else-if="activeMenu === 'org'" class="org-column">
      <article class="panel org-panel">
        <div class="section-title">
          <div>
            <h3>学院管理</h3>
            <p class="section-subtitle">{{ bootstrap.departments.length }} 个学院</p>
          </div>
          <button class="primary-btn" type="button" @click="openEditor('department', null)">新增学院</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>名称</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-if="bootstrap.departments.length === 0">
                <td colspan="2" style="text-align:center;color:var(--muted);padding:32px;">暂无学院数据</td>
              </tr>
              <tr v-for="dept in bootstrap.departments" :key="dept.id">
                <td><strong>{{ dept.name }}</strong></td>
                <td>
                  <div class="action-row">
                    <button class="ghost-btn" type="button" @click="openEditor('department', dept)">编辑</button>
                    <button class="danger-btn" type="button" @click="removeEntity('departments', dept.id)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
      <article class="panel org-panel">
        <div class="section-title">
          <div>
            <h3>班级管理</h3>
            <p class="section-subtitle">{{ bootstrap.classes.length }} 个班级</p>
          </div>
          <button class="primary-btn" type="button" @click="openEditor('class', null)">新增班级</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>名称</th><th>专业</th><th>学院</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-if="bootstrap.classes.length === 0">
                <td colspan="4" style="text-align:center;color:var(--muted);padding:32px;">暂无班级数据</td>
              </tr>
              <tr v-for="cls in bootstrap.classes" :key="cls.id">
                <td><strong>{{ cls.name }}</strong></td>
                <td>{{ cls.major }}</td>
                <td>{{ departmentName(cls.departmentId) }}</td>
                <td>
                  <div class="action-row">
                    <button class="ghost-btn" type="button" @click="openEditor('class', cls)">编辑</button>
                    <button class="danger-btn" type="button" @click="removeEntity('classes', cls.id)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
    </section>

    <!-- 管理员：系统日志 -->
    <article v-else-if="activeMenu === 'logs'" class="panel">
      <div class="section-title">
        <div>
          <h3>系统日志</h3>
          <p class="section-subtitle">最近 {{ bootstrap.logs.length }} 条操作记录</p>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>详情</th></tr></thead>
          <tbody>
            <tr v-if="sortedLogs.length === 0">
              <td colspan="4" style="text-align:center;color:var(--muted);padding:32px;">暂无日志记录</td>
            </tr>
            <tr v-for="log in sortedLogs" :key="log.id">
              <td>{{ formatDate(log.time) }}</td>
              <td>{{ log.actorId }}</td>
              <td><span class="tag">{{ log.action }}</span></td>
              <td class="cell-sub">{{ log.detail }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 教师：题库管理 -->
    <article v-else-if="activeMenu === 'questions'" class="panel">
      <div class="section-title">
        <div>
          <h3>题库管理</h3>
          <p class="section-subtitle">共 {{ myQuestions.length }} 道题目</p>
        </div>
        <button class="primary-btn" type="button" @click="openEditor('question', null)">新增题目</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>题型</th><th>科目</th><th>知识点</th><th>难度</th><th>题目</th><th>分值</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="myQuestions.length === 0">
              <td colspan="7" style="text-align:center;color:var(--muted);padding:32px;">暂无题目，点击上方按钮新增</td>
            </tr>
            <tr v-for="q in myQuestions" :key="q.id">
              <td><span class="tag">{{ typeLabel(q.type) }}</span></td>
              <td>{{ q.subject }}</td>
              <td>{{ q.knowledgePoint }}</td>
              <td>{{ q.difficulty }}</td>
              <td>{{ q.title.slice(0, 40) }}{{ q.title.length > 40 ? "..." : "" }}</td>
              <td><strong style="color:var(--primary)">{{ q.score }}</strong></td>
              <td>
                <div class="action-row">
                  <button class="ghost-btn" type="button" @click="openEditor('question', q)">编辑</button>
                  <button class="danger-btn" type="button" @click="removeEntity('questions', q.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 教师：试卷管理 -->
    <article v-else-if="activeMenu === 'papers'" class="panel">
      <div class="section-title">
        <div>
          <h3>试卷管理</h3>
          <p class="section-subtitle">共 {{ myPapers.length }} 套试卷</p>
        </div>
        <div class="section-actions">
          <button class="accent-btn" type="button" @click="autoGenVisible = true">自动组卷</button>
          <button class="primary-btn" type="button" @click="openPaperForm(null)">新增试卷</button>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>试卷名称</th><th>题量</th><th>总分</th><th>及格线</th><th>时长</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="myPapers.length === 0">
              <td colspan="6" style="text-align:center;color:var(--muted);padding:32px;">暂无试卷，点击上方按钮新增</td>
            </tr>
            <tr v-for="p in myPapers" :key="p.id">
              <td><strong>{{ p.name }}</strong></td>
              <td>{{ p.questionIds.length }}</td>
              <td><strong style="color:var(--primary)">{{ p.totalScore }}</strong></td>
              <td>{{ p.passScore }}</td>
              <td>{{ p.durationMinutes }} 分钟</td>
              <td>
                <div class="action-row">
                  <button class="accent-btn" type="button" @click="previewPaper(p)">预览</button>
                  <button class="ghost-btn" type="button" @click="openPaperForm(p)">编辑</button>
                  <button class="danger-btn" type="button" @click="removeEntity('papers', p.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 教师：考试管理 -->
    <article v-else-if="activeMenu === 'exams'" class="panel">
      <div class="section-title">
        <div>
          <h3>考试管理</h3>
          <p class="section-subtitle">共 {{ myExams.length }} 场考试</p>
        </div>
        <button class="primary-btn" type="button" @click="openEditor('exam', null)">发布考试</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>考试名称</th><th>试卷</th><th>时间</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="myExams.length === 0">
              <td colspan="5" style="text-align:center;color:var(--muted);padding:32px;">暂无考试，点击上方按钮发布</td>
            </tr>
            <tr v-for="e in myExams" :key="e.id">
              <td><strong>{{ e.name }}</strong></td>
              <td>{{ e.paperName || "-" }}</td>
              <td>{{ formatDate(e.startTime) }}</td>
              <td><span class="tag">{{ e.statusText || "已发布" }}</span></td>
              <td>
                <div class="action-row">
                  <button class="accent-btn" type="button" @click="loadMonitorData(e.id); activeMenu = 'monitor'">监控</button>
                  <button class="ghost-btn" type="button" @click="openEditor('exam', e)">编辑</button>
                  <button class="danger-btn" type="button" @click="removeEntity('exams', e.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 教师：考试监控 -->
    <article v-else-if="activeMenu === 'monitor'" class="panel">
      <div class="section-title">
        <div>
          <h3>考试监控</h3>
          <p class="section-subtitle">实时查看考生答题状态</p>
        </div>
        <div class="section-actions">
          <select v-model="monitorExamId" @change="loadMonitorData(monitorExamId)" class="monitor-select">
            <option value="">选择考试</option>
            <option v-for="e in myExams" :key="e.id" :value="e.id">{{ e.name }}</option>
          </select>
          <button v-if="monitorExamId" class="accent-btn" type="button" @click="handleExportScores" :disabled="exportLoading">导出成绩</button>
          <button v-if="monitorExamId" class="ghost-btn" type="button" @click="loadQuestionAnalysisData(monitorExamId)">错题分析</button>
        </div>
      </div>
      <template v-if="monitorResult">
        <section class="stats-grid compact-stats">
          <article class="stat-card compact-card"><span class="muted">总人数</span><strong>{{ monitorResult.totalCount }}</strong></article>
          <article class="stat-card compact-card"><span class="muted">未开始</span><strong>{{ monitorResult.notStartedCount }}</strong></article>
          <article class="stat-card compact-card"><span class="muted">进行中</span><strong>{{ monitorResult.runningCount }}</strong></article>
          <article class="stat-card compact-card"><span class="muted">已交卷</span><strong>{{ monitorResult.submittedCount }}</strong></article>
          <article class="stat-card compact-card"><span class="muted">最高分</span><strong style="color:var(--primary)">{{ monitorResult.maxScore ?? "-" }}</strong></article>
          <article class="stat-card compact-card"><span class="muted">最低分</span><strong>{{ monitorResult.minScore ?? "-" }}</strong></article>
          <article class="stat-card compact-card"><span class="muted">平均分</span><strong>{{ monitorResult.avgScore ?? "-" }}</strong></article>
        </section>
        <div class="table-wrap">
          <table>
            <thead><tr><th>学生</th><th>班级</th><th>状态</th><th>得分</th><th>切屏</th><th>用时</th><th>可疑</th></tr></thead>
            <tbody>
              <tr v-for="s in monitorResult.students" :key="s.studentId">
                <td><strong>{{ s.studentName }}</strong></td>
                <td>{{ s.className }}</td>
                <td><span class="tag">{{ s.status }}</span></td>
                <td><strong style="color:var(--primary)">{{ s.score ?? "-" }}</strong></td>
                <td>{{ s.switchCount }}</td>
                <td>{{ s.usedTimeText || "-" }}</td>
                <td>
                  <span v-if="s.suspicious" class="tag danger-tag" :title="(s.suspiciousReasons || []).join(', ')">可疑</span>
                  <span v-else>-</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template v-if="questionAnalysisResult">
          <h4 style="margin:1.5rem 0 0.5rem">题目正确率分析</h4>
          <ChartCard title="题目正确率" description="每道题的正确率统计" :option="questionAnalysisChartOption" />
          <ChartCard title="知识点掌握分析" description="各知识点正确率" :option="kpAnalysisChartOption" />
          <div class="table-wrap" style="margin-top:1rem">
            <table>
              <thead><tr><th>题号</th><th>题型</th><th>知识点</th><th>作答人数</th><th>正确人数</th><th>正确率</th></tr></thead>
              <tbody>
                <tr v-for="(q, i) in questionAnalysisResult.questions" :key="q.questionId">
                  <td>{{ i + 1 }}</td>
                  <td><span class="tag">{{ typeLabel(q.type as any) }}</span></td>
                  <td>{{ q.knowledgePoint }}</td>
                  <td>{{ q.totalAttempts }}</td>
                  <td>{{ q.correctCount }}</td>
                  <td :style="{ color: q.correctRate < 60 ? 'var(--danger)' : 'var(--primary)' }">{{ q.correctRate }}%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </template>
      <div v-else class="empty-state">请选择一场考试查看监控数据</div>
    </article>

    <!-- 教师：阅卷中心 -->
    <article v-else-if="activeMenu === 'grading'" class="panel">
      <div class="section-title">
        <div>
          <h3>阅卷中心</h3>
          <p class="section-subtitle">待阅卷 {{ pendingGradeCount }} 份</p>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>考试</th><th>学生</th><th>状态</th><th>成绩</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="mySubmissions.length === 0">
              <td colspan="5" style="text-align:center;color:var(--muted);padding:32px;">暂无待阅卷答卷</td>
            </tr>
            <tr v-for="s in mySubmissions" :key="s.id">
              <td>{{ s.examName || "-" }}</td>
              <td><strong>{{ s.studentName }}</strong></td>
              <td><span class="tag">{{ s.status }}</span></td>
              <td><strong style="color:var(--primary)">{{ s.finalScore ?? s.autoScore ?? 0 }}</strong> / {{ s.totalScore ?? "-" }}</td>
              <td>
                <button class="primary-btn" type="button" @click="reviewSubmission(s)">查看 / 阅卷</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 教师：成绩分析 -->
    <section v-else-if="activeMenu === 'analysis'" class="two-column">
      <ChartCard
        title="成绩分布"
        description="各分数段人数统计"
        :option="scoreDistOption"
      />
      <ChartCard
        title="考试通过率"
        description="各场考试的通过比例"
        :option="passRateOption"
      />
      <ChartCard
        v-if="bootstrap && bootstrap.classes.length > 1"
        title="班级成绩对比"
        description="各班级平均分与最高分对比"
        :option="classComparisonOption"
      />
    </section>

    <!-- 学生：待考考试 -->
    <article v-else-if="activeMenu === 'available-exams'" class="panel">
      <div class="section-title">
        <div>
          <h3>待考考试</h3>
          <p class="section-subtitle">{{ availableExams.length }} 场考试可参加</p>
        </div>
      </div>
      <div v-if="availableExams.length === 0" class="empty-state">暂无待考考试</div>
      <div v-else class="card-grid">
        <article v-for="e in availableExams" :key="e.id" class="mini-item">
          <h4>{{ e.name }}</h4>
          <p>{{ formatDate(e.startTime) }} ~ {{ formatDate(e.endTime) }}</p>
          <button class="primary-btn" type="button" @click="startExam(e.id)">进入考试</button>
        </article>
      </div>
    </article>

    <!-- 学生：考试记录 -->
    <article v-else-if="activeMenu === 'my-exams'" class="panel">
      <div class="section-title">
        <div>
          <h3>考试记录</h3>
          <p class="section-subtitle">共 {{ mySubmissionRecords.length }} 条记录</p>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>考试</th><th>状态</th><th>成绩</th><th>排名</th><th>耗时</th></tr></thead>
          <tbody>
            <tr v-if="mySubmissionRecords.length === 0">
              <td colspan="5" style="text-align:center;color:var(--muted);padding:32px;">暂无考试记录</td>
            </tr>
            <tr v-for="s in mySubmissionRecords" :key="s.id">
              <td><strong>{{ s.examName || "-" }}</strong></td>
              <td><span class="tag">{{ s.status }}</span></td>
              <td><strong style="color:var(--primary)">{{ s.finalScore ?? s.autoScore ?? 0 }}</strong> / {{ s.totalScore ?? "-" }}</td>
              <td>{{ s.rank ? `${s.rank} / ${s.finishedCount}` : "-" }}</td>
              <td>{{ s.usedTimeText || "-" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 学生：成绩单 -->
    <section v-else-if="activeMenu === 'grades'" class="two-column">
      <article class="panel" style="margin-bottom:1rem">
        <div class="section-title">
          <div>
            <h3>成绩单</h3>
            <p class="section-subtitle">个人成绩趋势与知识点掌握分析</p>
          </div>
        </div>
        <div v-if="scoreTrendData.length === 0" class="empty-state">暂无已完成考试</div>
        <template v-else>
          <section class="stats-grid compact-stats">
            <article class="stat-card compact-card"><span class="muted">考试次数</span><strong>{{ scoreTrendData.length }}</strong></article>
            <article class="stat-card compact-card"><span class="muted">最高分</span><strong style="color:var(--primary)">{{ Math.max(...scoreTrendData.map(d => d.score)) }}</strong></article>
            <article class="stat-card compact-card"><span class="muted">平均分</span><strong>{{ Math.round(scoreTrendData.reduce((s, d) => s + d.score, 0) / scoreTrendData.length) }}</strong></article>
          </section>
        </template>
      </article>
      <ChartCard
        v-if="scoreTrendData.length > 0"
        title="成绩趋势"
        description="历次考试得分变化"
        :option="scoreTrendOption"
      />
      <ChartCard
        v-if="subjectMasteryData.length > 0"
        title="知识点掌握"
        description="各科目掌握率雷达图"
        :option="knowledgeRadarOption"
      />
    </section>

    <!-- 学生：错题本 -->
    <article v-else-if="activeMenu === 'wrong-book'" class="panel">
      <div class="section-title">
        <div>
          <h3>错题本</h3>
          <p class="section-subtitle">共 {{ filteredWrongBookEntries.length }} 道错题{{ wrongBookSubjectFilter ? ` (筛选: ${wrongBookSubjectFilter})` : "" }}</p>
        </div>
        <select v-model="wrongBookSubjectFilter" class="monitor-select">
          <option value="">全部科目</option>
          <option v-for="s in wrongBookSubjects" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
      <div v-if="filteredWrongBookEntries.length === 0" class="empty-state">暂无错题记录</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>科目</th><th>知识点</th><th>题型</th><th>题目</th><th>错误次数</th><th>重做次数</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="entry in filteredWrongBookEntries" :key="entry.id">
              <td>{{ entry.subject }}</td>
              <td>{{ entry.knowledgePoint }}</td>
              <td><span class="tag">{{ typeLabel(entry.type) }}</span></td>
              <td>{{ entry.title.slice(0, 40) }}{{ entry.title.length > 40 ? "..." : "" }}</td>
              <td><strong style="color:var(--danger)">{{ entry.wrongCount }}</strong></td>
              <td>{{ entry.retryCount }}</td>
              <td>
                <div class="action-row">
                  <button class="primary-btn" type="button" @click="retryWrongEntry(entry)">重做</button>
                  <button v-if="entry.removable" class="ghost-btn" type="button" @click="removeWrongEntry(entry.id)">移除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- 个人信息 -->
    <ProfilePanel
      v-else-if="activeMenu === 'profile'"
      :bootstrap="bootstrap"
      @submit="handlePasswordChange"
    />

    <!-- 默认占位 -->
    <article v-else class="panel">
      <div class="empty-state">请从左侧菜单选择功能模块</div>
    </article>
  </AppShell>

  <!-- Modals -->
  <EntityEditorModal
    v-if="editorState.visible"
    :kind="editorState.kind"
    :bootstrap="bootstrap!"
    :model="editorState.model"
    @close="editorState.visible = false"
    @submit="handleEntitySubmit"
  />

  <PaperFormModal
    v-if="paperFormVisible"
    :bootstrap="bootstrap!"
    :model="paperModel"
    @close="paperFormVisible = false"
    @submit="handlePaperSubmit"
  />

  <PaperPreviewModal
    v-if="previewPaperModel"
    :bootstrap="bootstrap!"
    :paper="previewPaperModel"
    @close="previewPaperModel = null"
  />

  <SubmissionReviewModal
    v-if="reviewingSubmission"
    :submission="reviewingSubmission"
    :can-grade="isTeacher"
    @close="reviewingSubmission = null"
    @grade="handleGrade"
  />

  <WrongBookRetryModal
    v-if="retryingEntry"
    :entry="retryingEntry"
    @close="retryingEntry = null"
    @submit="handleWrongRetry"
  />

  <BatchUserImportModal
    v-if="batchImportRole"
    :role="batchImportRole"
    :bootstrap="bootstrap!"
    @close="batchImportRole = null"
    @success="loadData"
  />

  <ExamSessionModal
    v-if="activeExam"
    :exam="activeExam"
    @close="activeExam = null"
    @submitted="handleExamSubmitted"
    @refreshed="loadData"
  />

  <AutoGenPaperModal
    v-if="autoGenVisible"
    :bootstrap="bootstrap!"
    @close="autoGenVisible = false"
    @submit="handleAutoGenerate"
  />

  <!-- Toast Notifications -->
  <div class="toast-container">
    <transition-group name="toast">
      <div v-for="t in toasts" :key="t.id" :class="['toast', 'toast-' + t.type]">
        <span class="toast-icon">{{ t.type === 'success' ? '✓' : t.type === 'error' ? '✕' : 'ℹ' }}</span>
        <span class="toast-msg">{{ t.message }}</span>
      </div>
    </transition-group>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import type { EChartsOption } from "echarts";
import type {
  BootstrapData,
  Exam,
  ExamDetail,
  MenuItem,
  Paper,
  Question,
  SubmissionReview,
  User,
  WrongBookEntry,
  Department,
  ClassRoom,
} from "./types";
import { formatDate, typeLabel } from "./utils/format";
import {
  login as apiLogin,
  loadBootstrap as apiLoadBootstrap,
  setCurrentAuthToken,
  createEntity,
  updateEntity,
  deleteEntity,
  changePassword,
  loadExamDetail,
  manualGrade,
  retryWrongBook,
  removeWrongBook,
  monitorExam,
  exportScores,
  questionAnalysis,
  autoGeneratePaper,
  scoreTrend,
  knowledgeRadar,
} from "./api/client";
import type {
  MonitorResult,
  ExportScoresResult,
  QuestionAnalysisResult,
  ScoreTrendItem,
  SubjectMastery,
  KnowledgePointMastery,
} from "./api/client";

import AppShell from "./components/AppShell.vue";
import AuthLogin from "./components/AuthLogin.vue";
import BaseModal from "./components/BaseModal.vue";
import BatchUserImportModal from "./components/BatchUserImportModal.vue";
import ChartCard from "./components/ChartCard.vue";
import EntityEditorModal from "./components/EntityEditorModal.vue";
import ExamSessionModal from "./components/ExamSessionModal.vue";
import PaperFormModal from "./components/PaperFormModal.vue";
import PaperPreviewModal from "./components/PaperPreviewModal.vue";
import ProfilePanel from "./components/ProfilePanel.vue";
import SubmissionReviewModal from "./components/SubmissionReviewModal.vue";
import WrongBookRetryModal from "./components/WrongBookRetryModal.vue";
import AutoGenPaperModal from "./components/AutoGenPaperModal.vue";

// ---- Toast notification system ----
interface Toast {
  id: number;
  message: string;
  type: "success" | "error" | "info";
}
let toastId = 0;
const toasts = ref<Toast[]>([]);

function showToast(message: string, type: "success" | "error" | "info" = "info") {
  const id = ++toastId;
  toasts.value.push({ id, message, type });
  setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }, 3000);
}

const bootstrap = ref<BootstrapData | null>(null);
const loginLoading = ref(false);
const loginMessage = ref("");
const activeMenu = ref("");

// Editor state
const editorState = reactive({
  visible: false,
  kind: "student" as "student" | "teacher" | "department" | "class" | "question" | "exam",
  model: null as any,
});

const paperFormVisible = ref(false);
const paperModel = ref<Paper | null>(null);
const previewPaperModel = ref<Paper | null>(null);
const reviewingSubmission = ref<SubmissionReview | null>(null);
const retryingEntry = ref<WrongBookEntry | null>(null);
const batchImportRole = ref<"student" | "teacher" | null>(null);
const activeExam = ref<ExamDetail | null>(null);

// New state for enhanced features
const monitorResult = ref<MonitorResult | null>(null);
const monitorExamId = ref("");
const exportLoading = ref(false);
const questionAnalysisResult = ref<QuestionAnalysisResult | null>(null);
const questionAnalysisExamId = ref("");
const scoreTrendData = ref<ScoreTrendItem[]>([]);
const subjectMasteryData = ref<SubjectMastery[]>([]);
const kpMasteryData = ref<KnowledgePointMastery[]>([]);
const wrongBookSubjectFilter = ref("");
const autoGenVisible = ref(false);

// Computed
const role = computed(() => bootstrap.value?.currentUser.role || "student");
const isAdmin = computed(() => role.value === "admin");
const isTeacher = computed(() => role.value === "teacher");
const isStudent = computed(() => role.value === "student");

const students = computed(() => bootstrap.value?.users.filter((u) => u.role === "student") || []);
const teachers = computed(() => bootstrap.value?.users.filter((u) => u.role === "teacher") || []);
const studentCount = computed(() => students.value.length);
const teacherCount = computed(() => teachers.value.length);

const myQuestions = computed(() => {
  const bs = bootstrap.value;
  if (!bs) return [];
  return bs.questions.filter((q) => q.teacherId === bs.currentUser.id);
});
const myPapers = computed(() => {
  const bs = bootstrap.value;
  if (!bs) return [];
  return bs.papers.filter((p) => p.teacherId === bs.currentUser.id);
});
const myExams = computed(() => {
  const bs = bootstrap.value;
  if (!bs) return [];
  return bs.exams.filter((e) => e.teacherId === bs.currentUser.id);
});
const mySubmissions = computed(() =>
  bootstrap.value?.submissions.filter((s) => {
    const exam = bootstrap.value?.exams.find((e) => e.id === s.examId);
    return exam?.teacherId === bootstrap.value?.currentUser.id;
  }) || []
);
const pendingGradeCount = computed(() => mySubmissions.value.filter((s) => s.status === "待阅卷").length);

const availableExams = computed(() =>
  bootstrap.value?.exams.filter((e) => {
    const user = bootstrap.value!.currentUser;
    return e.targetClassIds.includes(user.classId || "") && e.published;
  }) || []
);
const mySubmissionRecords = computed(() =>
  bootstrap.value?.submissions.filter((s) => s.studentId === bootstrap.value?.currentUser.id) || []
);

const sortedLogs = computed(() =>
  [...(bootstrap.value?.logs || [])].sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime())
);

const wrongBookSubjects = computed(() =>
  [...new Set((bootstrap.value?.wrongBookEntries || []).map((e) => e.subject))].sort()
);

const filteredWrongBookEntries = computed(() => {
  const entries = bootstrap.value?.wrongBookEntries || [];
  if (!wrongBookSubjectFilter.value) return entries;
  return entries.filter((e) => e.subject === wrongBookSubjectFilter.value);
});

// Student score trend chart option
const scoreTrendOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: "axis" },
  xAxis: { type: "category", data: scoreTrendData.value.map((d) => d.examName), axisLabel: { rotate: 15 } },
  yAxis: { type: "value", name: "分数" },
  series: [
    { name: "得分", type: "line", data: scoreTrendData.value.map((d) => d.score), smooth: true, itemStyle: { color: "#2563eb" } },
    { name: "总分", type: "line", data: scoreTrendData.value.map((d) => d.totalScore), lineStyle: { type: "dashed" }, itemStyle: { color: "#aaa" } },
    { name: "及格线", type: "line", data: scoreTrendData.value.map((d) => d.passScore), lineStyle: { type: "dotted" }, itemStyle: { color: "#dc2626" } },
  ],
  legend: { top: 0 },
  grid: { left: 50, right: 20, top: 40, bottom: 60 },
}));

// Student knowledge radar chart option
const knowledgeRadarOption = computed<EChartsOption>(() => ({
  tooltip: {},
  radar: {
    indicator: subjectMasteryData.value.map((d) => ({ name: d.subject, max: 100 })),
    shape: "polygon",
  },
  series: [{
    type: "radar",
    data: [{ value: subjectMasteryData.value.map((d) => d.mastery), name: "掌握率 (%)", areaStyle: { opacity: 0.15 } }],
    itemStyle: { color: "#2563eb" },
  }],
}));

// Menu items based on role
const menuItems = computed<MenuItem[]>(() => {
  if (isAdmin.value) {
    return [
      { key: "overview", label: "数据总览", description: "全局统计与概览" },
      { key: "students", label: "学生管理", description: "维护学生账号" },
      { key: "teachers", label: "教师管理", description: "维护教师账号" },
      { key: "org", label: "组织管理", description: "学院与班级" },
      { key: "logs", label: "系统日志", description: "操作记录" },
      { key: "profile", label: "个人信息", description: "修改密码" },
    ];
  }
  if (isTeacher.value) {
    return [
      { key: "overview", label: "教学看板", description: "教学数据概览" },
      { key: "questions", label: "题库管理", description: "维护题目" },
      { key: "papers", label: "试卷管理", description: "组卷与管理" },
      { key: "exams", label: "考试管理", description: "发布考试" },
      { key: "monitor", label: "考试监控", description: "实时查看考生状态" },
      { key: "grading", label: "阅卷中心", description: "评阅答卷" },
      { key: "analysis", label: "成绩分析", description: "统计与图表" },
      { key: "profile", label: "个人信息", description: "修改密码" },
    ];
  }
  return [
    { key: "available-exams", label: "待考考试", description: "查看可参加的考试" },
    { key: "my-exams", label: "考试记录", description: "历史成绩" },
    { key: "grades", label: "成绩单", description: "成绩趋势与知识掌握" },
    { key: "wrong-book", label: "错题本", description: "错题回顾与重做" },
    { key: "profile", label: "个人信息", description: "修改密码" },
  ];
});

// Chart options
const subjectChartOption = computed<EChartsOption>(() => {
  const subjects = new Map<string, number>();
  (bootstrap.value?.questions || []).forEach((q) => {
    subjects.set(q.subject, (subjects.get(q.subject) || 0) + 1);
  });
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: [...subjects.keys()] },
    yAxis: { type: "value" },
    series: [{ type: "bar", data: [...subjects.values()], itemStyle: { color: "#2563eb", borderRadius: [6, 6, 0, 0] } }],
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
  };
});

const scoreDistOption = computed<EChartsOption>(() => {
  const buckets = { "0-59": 0, "60-69": 0, "70-79": 0, "80-89": 0, "90-100": 0 };
  mySubmissions.value.forEach((s) => {
    const score = s.finalScore ?? s.autoScore ?? 0;
    if (score < 60) buckets["0-59"]++;
    else if (score < 70) buckets["60-69"]++;
    else if (score < 80) buckets["70-79"]++;
    else if (score < 90) buckets["80-89"]++;
    else buckets["90-100"]++;
  });
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: Object.keys(buckets) },
    yAxis: { type: "value", minInterval: 1 },
    series: [{ type: "bar", data: Object.values(buckets), itemStyle: { color: "#2563eb", borderRadius: [6, 6, 0, 0] } }],
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
  };
});

const passRateOption = computed<EChartsOption>(() => {
  const examMap = new Map<string, { pass: number; total: number }>();
  mySubmissions.value.forEach((s) => {
    const key = s.examName || s.examId;
    if (!examMap.has(key)) examMap.set(key, { pass: 0, total: 0 });
    const item = examMap.get(key)!;
    item.total++;
    if (s.passStatus === "通过" || (s.finalScore ?? s.autoScore ?? 0) >= (s.passScore ?? 60)) item.pass++;
  });
  const names = [...examMap.keys()];
  const rates = names.map((k) => {
    const v = examMap.get(k)!;
    return v.total > 0 ? Math.round((v.pass / v.total) * 100) : 0;
  });
  return {
    tooltip: { trigger: "axis", formatter: "{b}: {c}%" },
    xAxis: { type: "category", data: names },
    yAxis: { type: "value", max: 100, axisLabel: { formatter: "{value}%" } },
    series: [{ type: "bar", data: rates, itemStyle: { color: "#2563eb", borderRadius: [6, 6, 0, 0] } }],
    grid: { left: 50, right: 20, top: 20, bottom: 40 },
  };
});

// Class comparison chart
const classComparisonOption = computed<EChartsOption>(() => {
  const classScores = new Map<string, { scores: number[]; count: number }>();
  mySubmissions.value.forEach((s) => {
    if (s.status !== "已完成") return;
    const student = bootstrap.value?.users.find((u) => u.id === s.studentId);
    if (!student?.classId) return;
    const cls = bootstrap.value?.classes.find((c) => c.id === student.classId);
    const className = cls?.name || "未知班级";
    if (!classScores.has(className)) classScores.set(className, { scores: [], count: 0 });
    const data = classScores.get(className)!;
    data.scores.push(s.finalScore ?? s.autoScore ?? 0);
    data.count++;
  });
  const names = [...classScores.keys()];
  const avgScores = names.map((k) => {
    const d = classScores.get(k)!;
    return d.scores.length > 0 ? Math.round(d.scores.reduce((a, b) => a + b, 0) / d.scores.length * 10) / 10 : 0;
  });
  const maxScores = names.map((k) => {
    const d = classScores.get(k)!;
    return d.scores.length > 0 ? Math.max(...d.scores) : 0;
  });
  return {
    tooltip: { trigger: "axis" },
    legend: { top: 0 },
    xAxis: { type: "category", data: names },
    yAxis: { type: "value", name: "分数" },
    series: [
      { name: "平均分", type: "bar", data: avgScores, itemStyle: { color: "#2563eb", borderRadius: [4, 4, 0, 0] } },
      { name: "最高分", type: "bar", data: maxScores, itemStyle: { color: "#60a5fa", borderRadius: [4, 4, 0, 0] } },
    ],
    grid: { left: 50, right: 20, top: 40, bottom: 40 },
  };
});

// Helpers
function className(classId?: string) {
  return classId ? bootstrap.value?.classes.find((c) => c.id === classId)?.name || "-" : "-";
}

function departmentName(deptId?: string) {
  return deptId ? bootstrap.value?.departments.find((d) => d.id === deptId)?.name || "-" : "-";
}

// Actions
async function handleLogin(payload: { username: string; password: string }) {
  loginLoading.value = true;
  loginMessage.value = "";
  try {
    const result = await apiLogin(payload.username, payload.password);
    setCurrentAuthToken(result.user.id);
    await loadData();
    if (menuItems.value.length > 0) {
      activeMenu.value = menuItems.value[0].key;
    }
    showToast("登录成功，欢迎回来！", "success");
  } catch (err: any) {
    loginMessage.value = err?.message || "登录失败，请检查账号密码。";
  } finally {
    loginLoading.value = false;
  }
}

async function loadData() {
  try {
    bootstrap.value = await apiLoadBootstrap();
  } catch (err: any) {
    if (bootstrap.value) {
      // silent refresh failure
    } else {
      setCurrentAuthToken("");
    }
  }
}

function handleLogout() {
  setCurrentAuthToken("");
  bootstrap.value = null;
  activeMenu.value = "";
  loginMessage.value = "";
}

function openEditor(kind: typeof editorState.kind, model: any) {
  editorState.kind = kind;
  editorState.model = model;
  editorState.visible = true;
}

async function handleEntitySubmit(payload: { entity: string; record: Record<string, unknown> }) {
  try {
    if (payload.record.id) {
      await updateEntity(payload.entity, payload.record);
      showToast("更新成功", "success");
    } else {
      await createEntity(payload.entity, payload.record);
      showToast("创建成功", "success");
    }
    editorState.visible = false;
    await loadData();
  } catch (err: any) {
    showToast(err?.message || "操作失败", "error");
  }
}

async function removeEntity(entity: string, id: string) {
  let msg = "确定要删除该记录吗？";
  if (entity === "departments") msg = "确定要删除该学院吗？学院下有班级或师生时将无法删除。";
  else if (entity === "classes") msg = "确定要删除该班级吗？班级下有学生时将无法删除，关联考试的目标班级将被自动清理。";
  if (!confirm(msg)) return;
  try {
    await deleteEntity(entity, id);
    showToast("删除成功", "success");
    await loadData();
  } catch (err: any) {
    showToast(err?.message || "删除失败", "error");
  }
}

function openPaperForm(model: Paper | null) {
  paperModel.value = model;
  paperFormVisible.value = true;
}

async function handlePaperSubmit(payload: { entity: string; record: Record<string, unknown> }) {
  try {
    if (payload.record.id) {
      await updateEntity(payload.entity, payload.record);
      showToast("试卷更新成功", "success");
    } else {
      await createEntity(payload.entity, payload.record);
      showToast("试卷创建成功", "success");
    }
    paperFormVisible.value = false;
    await loadData();
  } catch (err: any) {
    showToast(err?.message || "保存失败", "error");
  }
}

function previewPaper(paper: Paper) {
  previewPaperModel.value = paper;
}

function reviewSubmission(submission: SubmissionReview) {
  reviewingSubmission.value = submission;
}

async function handleGrade(payload: { submissionId: string; scores: Record<string, number> }) {
  try {
    await manualGrade(payload.submissionId, payload.scores);
    reviewingSubmission.value = null;
    showToast("阅卷完成", "success");
    await loadData();
  } catch (err: any) {
    showToast(err?.message || "阅卷失败", "error");
  }
}

async function startExam(examId: string) {
  try {
    const detail = await loadExamDetail(examId);
    activeExam.value = detail;
  } catch (err: any) {
    showToast(err?.message || "无法进入考试", "error");
  }
}

function handleExamSubmitted() {
  activeExam.value = null;
  showToast("交卷成功！", "success");
  loadData();
}

function retryWrongEntry(entry: WrongBookEntry) {
  retryingEntry.value = entry;
}

async function handleWrongRetry(payload: { entryId: string; answer: string[] }) {
  try {
    await retryWrongBook(payload.entryId, payload.answer);
    retryingEntry.value = null;
    showToast("重做提交成功", "success");
    await loadData();
  } catch (err: any) {
    showToast(err?.message || "重做失败", "error");
  }
}

async function removeWrongEntry(entryId: string) {
  if (!confirm("确定从错题本中移除吗？")) return;
  try {
    await removeWrongBook(entryId);
    showToast("已从错题本移除", "success");
    await loadData();
  } catch (err: any) {
    showToast(err?.message || "移除失败", "error");
  }
}

async function handlePasswordChange(payload: { oldPassword: string; newPassword: string }) {
  try {
    await changePassword(payload.oldPassword, payload.newPassword);
    showToast("密码修改成功", "success");
  } catch (err: any) {
    showToast(err?.message || "修改失败", "error");
  }
}

function showBatchImport(role: "student" | "teacher") {
  batchImportRole.value = role;
}

// --- Enhanced feature handlers ---

async function loadMonitorData(examId: string) {
  if (!examId) { monitorResult.value = null; return; }
  try {
    monitorResult.value = await monitorExam(examId);
    monitorExamId.value = examId;
  } catch (err: any) {
    showToast(err?.message || "加载监控数据失败", "error");
  }
}

async function handleExportScores() {
  if (!monitorExamId.value) return;
  exportLoading.value = true;
  try {
    const result = await exportScores(monitorExamId.value);
    // Generate CSV and trigger download
    const headers = ["学号", "姓名", "班级", "状态", "得分", "总分", "及格线", "排名"];
    const rows = result.rows.map((r) => [r.username, r.studentName, r.className, r.status, r.score ?? "", r.totalScore, r.passScore, r.rank ?? ""].join(","));
    const csv = "\uFEFF" + [headers.join(","), ...rows].join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${result.examName}_成绩表.csv`;
    a.click();
    URL.revokeObjectURL(url);
    showToast("成绩导出成功", "success");
  } catch (err: any) {
    showToast(err?.message || "导出失败", "error");
  } finally {
    exportLoading.value = false;
  }
}

async function loadQuestionAnalysisData(examId: string) {
  if (!examId) { questionAnalysisResult.value = null; return; }
  try {
    questionAnalysisResult.value = await questionAnalysis(examId);
    questionAnalysisExamId.value = examId;
  } catch (err: any) {
    showToast(err?.message || "加载分析数据失败", "error");
  }
}

const questionAnalysisChartOption = computed<EChartsOption>(() => {
  if (!questionAnalysisResult.value) return {};
  const data = questionAnalysisResult.value.questions;
  return {
    tooltip: { trigger: "axis", formatter: (params: any) => {
      const p = params[0]; return `${p.name}<br/>正确率: ${p.value}%`;
    }},
    xAxis: { type: "category", data: data.map((_, i) => `第${i + 1}题`), axisLabel: { rotate: 30 } },
    yAxis: { type: "value", max: 100, axisLabel: { formatter: "{value}%" } },
    series: [{
      type: "bar",
      data: data.map((d) => ({
        value: d.correctRate,
        itemStyle: { color: d.correctRate >= 60 ? "#2563eb" : "#dc2626", borderRadius: [4, 4, 0, 0] },
      })),
    }],
    grid: { left: 50, right: 20, top: 20, bottom: 50 },
  };
});

const kpAnalysisChartOption = computed<EChartsOption>(() => {
  if (!questionAnalysisResult.value) return {};
  const kpData = questionAnalysisResult.value.knowledgePointAnalysis;
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: kpData.map((d) => d.knowledgePoint), axisLabel: { rotate: 25 } },
    yAxis: { type: "value", max: 100, axisLabel: { formatter: "{value}%" } },
    series: [{
      type: "bar",
      data: kpData.map((d) => ({
        value: d.correctRate,
        itemStyle: { color: d.correctRate >= 60 ? "#2563eb" : "#dc2626", borderRadius: [4, 4, 0, 0] },
      })),
    }],
    grid: { left: 50, right: 20, top: 20, bottom: 60 },
  };
});

async function handleAutoGenerate(payload: { name: string; durationMinutes: number; passScore: number; rules: Array<{ type: string; count: number; subject?: string; knowledgePoint?: string; difficulty?: string }> }) {
  try {
    await autoGeneratePaper(payload);
    autoGenVisible.value = false;
    await loadData();
    showToast("自动组卷成功！", "success");
  } catch (err: any) {
    showToast(err?.message || "自动组卷失败", "error");
  }
}

async function loadStudentGrades() {
  try {
    const [trendRes, radarRes] = await Promise.all([scoreTrend(), knowledgeRadar()]);
    scoreTrendData.value = trendRes.trend;
    subjectMasteryData.value = radarRes.subjectMastery;
    kpMasteryData.value = radarRes.knowledgePointMastery;
  } catch {
    // silently fail
  }
}

// Question analysis chart options for exam analysis page
const analysisQuestionChartOption = computed<EChartsOption>(() => {
  if (!questionAnalysisResult.value) return {};
  const data = questionAnalysisResult.value.questions;
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: data.map((_, i) => `第${i + 1}题`), axisLabel: { rotate: 30 } },
    yAxis: { type: "value", max: 100, axisLabel: { formatter: "{value}%" } },
    series: [{ type: "bar", data: data.map((d) => ({ value: d.correctRate, itemStyle: { color: d.correctRate >= 60 ? "#2563eb" : "#dc2626", borderRadius: [4, 4, 0, 0] } })) }],
    grid: { left: 50, right: 20, top: 20, bottom: 50 },
  };
});

onMounted(() => {
  // Auto-login check - no auto-login, show login screen
});

// Watch menu changes to load data
watch(activeMenu, (newVal) => {
  if (newVal === "grades" && isStudent.value) {
    loadStudentGrades();
  }
  if (newVal === "monitor" && !monitorExamId.value && myExams.value.length > 0) {
    monitorExamId.value = myExams.value[0].id;
    loadMonitorData(monitorExamId.value);
  }
});
</script>
