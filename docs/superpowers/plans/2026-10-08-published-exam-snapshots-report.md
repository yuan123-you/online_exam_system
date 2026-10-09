# 在线考试：发布版本冻结重构记录

日期：2026-10-08。本轮落实用户已确认规则：考试发布后题干、选项、标准答案、分值及试卷配置冻结；后续题库编辑不影响该考试及已保存成绩。不是三个项目全面重构完成声明。

## 实际重构

1. 新增独立 exam_snapshot 表；版本与考试记录分开，避免把标准答案放进考试列表响应。
2. StoreService 的所有运行时考试保存入口统一进入发布事务：锁定考试、试卷和题目，校验引用、归属和分值，再原子保存考试/快照。现有快照只读取，不覆盖。
3. 新增 ExamContent，集中处理冻结版本与草稿内容的解析；消费者不再各自决定使用当前题库还是历史版本。
4. 学生取题、选项乱序、答题时限、自动评分、成绩审查、题目分析、班级分析及导出接入统一版本来源。
5. AI 阅卷在权限校验后、模型/熔断调用前检查版本可用性；仍是评分建议，不改写最终成绩。
6. 学生响应隐藏标准答案；教师返回的嵌套对象也与私有版本隔离，修改响应不会改变快照。
7. 缺少原始版本的历史考试不自动用当前题库回填。有学生提交记录的已撤回考试也不是全新草稿。保存的成绩/详情保留，未知及格标准显示待恢复。
8. 在发布事务完成后再次失效缓存，覆盖事务期间已发生的缓存回填；这不是对现有整个可变 Store 缓存架构的全面修复。

没有改前端。保留之前未提交的认证、注册、AI 导入及 schema.sql 改动；没有提交、推送或部署。

## 验证证据

使用本机 JDK 21，符合项目构建版本。

- 新增 30 项测试：23 项发布/取题/评分/HTTP/分析/导出回归、6 项隔离 H2 事务测试、1 项迁移与初始化 schema 一致性测试。
- 原始发布回归：12 项中 9 失败、1 错误；原始事务回归：3 项中 2 失败、1 错误。修复后通过。
- 复查新增的历史撤回、班级分析、AI 阅卷和导出场景也先运行失败测试，再修改对应入口。
- 最终针对性验证：98 项全部通过，0 失败、0 错误。
- 最终全量回归：310 项，295 通过、9 失败、6 错误。与改造前全量日志比较，失败用例集合相同，没有新增或删除失败身份。
- 原有失败：AuthServiceTest 5 项；EntityCrudServiceTest 4 项；AnalysisServiceTest 6 项 Mockito 无用 stub 错误。本轮未通过禁用测试或降低断言规避它们。
- 变更文件 diff --check 无空白错误；只读代码复查提出的三个遗漏已复现并修正，复查未发现其限定范围内剩余重要问题。

日志（位于构建输出，不作为代码提交）：
- `D:\Codex Web\online_system\backend\target\exam-snapshot-acceptance.log`
- `D:\Codex Web\online_system\backend\target\exam-snapshot-full-regression.log`

## 主要文件

- `D:\Codex Web\online_system\backend\src\main\java\com\onlineexam\service\ExamContent.java`
- `D:\Codex Web\online_system\backend\src\main\java\com\onlineexam\StoreService.java`
- ExamService、SubmissionService、AnalysisService、AiService 的阅卷入口。
- ExamController、ClassAnalysisController、ExcelExportService、GlobalExceptionHandler。
- `D:\Codex Web\online_system\backend\src\main\resources\schema.sql`（仅新增快照表，保留既有改动）。
- `D:\Codex Web\online_system\db\migrations\2026-10-08-exam-snapshot.sql`。
- 新增 PublishedExamSnapshotTest、ExamSnapshotTransactionTest、ExamSnapshotSchemaTest；调整现有成功场景的发布版本测试数据。
- backend/pom.xml 仅新增测试范围 H2 依赖，没有新增生产数据库或替换框架。

## 尚不能直接上线的原因

没有连接真实 MySQL、调用真实 AI 或修改生产数据。H2 MySQL 模式证明隔离测试中的事务行为，不代替目标 MySQL 8 的锁、JSON 和部署验收。

历史版本恢复必须来源于可信备份/原始试卷，不能伪造。种子考试及已有历史考试需要盘点。旧数据中曾发布又撤回且没有幸存提交记录的情况，不能仅靠现有字段识别，仍需上线前人工核对。缺失版本的考试取题、继续答题、分析或重新自动阅卷会返回明确冲突错误，因此必须先评估在途考试，不能直接切换线上版本。

部署/回滚边界详见：`D:\Codex Web\online_system\docs\superpowers\plans\2026-10-08-published-exam-snapshots-deployment.md`。

## 后续业务重构仍待推进

- 考试提交/自动交卷/人工阅卷/成绩发布的状态流；缓存与数据库读写一致性。
- AI 出题候选结果校验、入库规则、练习会话及评分建议模块的职责分离。
- 错题本再练习的题目版本策略。
- AI Mall 的交易/AI 动作状态和知讯的内容/搜索同步重构。
- 全部基线失败、真实数据库验收、历史数据恢复以及最终多角色端到端验收。
