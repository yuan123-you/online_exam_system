# 当前项目代码审查与修复报告

日期：2026-10-28（Asia/Shanghai）
对象：当前 `online_system` 工作区，起始提交 `8645d0a0b47f7652dd350fbd8b753b33cb2d4bc8`。

## 1. 范围与方法

- 使用本机已安装的 Alibaba OpenCodeReview：`ocr scan --preview` 建立完整源码清单，`ocr delegate rule` 提取 Java / TypeScript 规则，由当前会话结合调用链和测试直接审查；未把项目交给该 CLI 的外部模型服务。
- 最初的范围清单包含 160 个文件：74 Java、30 TypeScript、47 Vue、9 JavaScript；工具标记其中 151 个可审查。另单独检查根目录部署 Webhook、认证配置、构建脚本和既有安全验证。
- 重点追踪认证和角色隔离、考试会话/交卷/判分、练习持久化、AI 流式生命周期、班级分析异步状态、富文本转义和部署签名校验。清单是范围索引，不代表逐行证明所有文件不存在缺陷。
- 修复前先执行现有测试和类型检查；针对实际问题新增失败测试，再修改生产代码。没有重构无关模块、提交、推送或部署。

## 2. 已修复问题

| 优先级 | 问题与影响 | 最小修复 | 回归证据 |
| --- | --- | --- | --- |
| P1 | 部署 Webhook 未配置密钥时直接放行，可未经认证触发部署；畸形签名可能使恒定时间比较抛异常 | 缺密钥时拒绝请求；先验证签名类型和 64 位十六进制格式 | 7 个隔离 Node 测试，修复前 4 个失败；不真实监听、不执行部署 |
| P1 | 练习持久化的多个写入异常被吞掉，`@Transactional` 方法正常返回后提交部分数据：题目替换丢失原题、保存部分答案、半提交评分、删除遗留孤儿数据 | 保留现有事务边界，让 JDBC 异常传播到代理以回滚，由全局异常处理返回错误 | 真实 H2 + Spring 事务代理测试；注入第二次写入、会话元数据更新和删除失败，检查原数据恢复 |
| P1 | 单选题和判断题只比较第一个答案，提交多个选项也可能被判正确 | 要求实际答案与参考答案都恰好只有一个，再比较选项 | 2 个漏洞复现测试 + 1 个原选项字母兼容测试 |
| P2 | 练习 API 声明支持字符串答案，但保存接口在类型检查前强转为 List，抛 ClassCastException；交卷时的最新字符串答案被忽略 | 单处规范化数组/非空字符串，供单题保存、批量保存和提交复用 | 3 个字符串保存/判分回归测试 |
| P2 | 快速切换班级分析对应的考试时，旧请求覆盖新结果；清空选择或新请求失败时仍可能显示旧考试数据 | 请求序号隔离结果、错误和 loading 清理；选择变化时清空旧结果 | 4 个乱序、取消选择、旧错误与新请求失败测试，修复前全部失败 |
| P1 | 全局结束判断优先于已持久化的教师延时，导致有效延时学生不能继续保存/交卷；也遮蔽已交卷会话状态 | 先判断已有终态；全局结束后仅放行已有进行中、带有效教师延时且个人截止尚未到期的会话 | 9 个真实服务/控制器流程测试，修复前 6 个失败；涵盖新会话和过期延时拒绝 |
| P1 | 到期请求可以提交新答案；等待数据库行锁跨过截止后仍可写入；浏览器离线会使会话一直未结算 | 用户已确认只结算服务器已保存答案；行锁取得后再次检查服务端时间，再执行已有 revision CAS；请求/恢复/后台统一结算 | 真实 H2 事务、行锁竞争、保存答案判分、幂等、缺失版本、失败隔离及后台结算回归 |
| P2 | 浏览器时间偏差或尚未同步的教师延时导致过早自动交卷，且到期后仍可继续编辑/自动保存 | 超时请求仅探测服务端到期状态；有效会话返回权威剩余时间；冻结到期输入，停止晚到保存及重复终态 | 6 个弹窗测试 + 2 个 API 序列化测试 |
| P2 | 项目 TypeScript 检查失败，普通构建没有暴露这些类型错误 | 使用准确 ComputedRef 类型、试卷问题类型守卫、EChartsOption 约束，并移除测试中不符合 ES2020 lib 的 `.at()`；新增 typecheck 命令 | 修复前 `vue-tsc --noEmit` 退出 2；修复后通过，未放宽 strict / target |

主要代码路径：

- `auto-deploy-webhook.js`
- `backend/src/main/java/com/onlineexam/service/PracticeSessionService.java`
- `backend/src/main/java/com/onlineexam/service/SubmissionService.java`
- `backend/src/main/java/com/onlineexam/service/ExamService.java`
- `backend/src/main/java/com/onlineexam/service/ExpiredSubmissionScheduler.java`
- `backend/src/main/java/com/onlineexam/StoreService.java`
- `backend/src/main/java/com/onlineexam/controller/SubmissionController.java`
- `src/components/student/ExamSessionModal.vue`
- `src/views/teacher/ClassAnalysisView.vue`
- `src/composables/useResponsiveLayout.ts`
- `src/components/teacher/PaperPreviewModal.vue`

累计新增回归检查 74 项：后端 49 项、前端 14 项、Webhook 7 项、依赖版本/锁文件检查 4 项。本轮确认到期策略后新增 26 个后端和 8 个前端回归，覆盖行锁、当前数据库排期与缓存竞争、后台结算、有效延时和浏览器时钟偏差。最新全量数量见下表。两轮独立只读复核均没有发现新增生产逻辑缺陷；第一轮指出的两个失败分支覆盖缺口已经补测。第二轮复核明确只覆盖当时的依赖与会话生命周期补丁，不替代全项目安全证明。本轮新增到期策略的两次独立复核分派均因模型容量失败，未得到独立结论；最终采用当前会话人工复核及真实事务/行锁/缓存竞争回归，不把分派尝试当成审查通过。

## 3. 依赖审计（用户要求全面修复后已继续处理）

官方 npm 审计从最初 **14 个受影响节点 → 第一轮 3 个 → 当前 0 个**。最新审计退出码为 0，info/low/moderate/high/critical 均为 0；该结果仅代表本次 npm 审计，不代表已完成 Maven SCA 或渗透测试。

- 第一轮已更新 Vue / server-renderer 到 3.5.44、Vite 到 8.3.5、Vitest / coverage-v8 / mocker 到 4.1.12，并修复 brace-expansion、glob、nanoid、PostCSS、source-map-js 等传递依赖。
- **KaTeX 0.17.0 → 0.18.10**：升级前用继承 trust 选项的真实渲染测试复现 GHSA-238p-pmpm-9mq7，升级后不再错误启用可信 HTML；补测常见分式、积分、矩阵渲染。
- **happy-dom 20.10.3 → 21.0.4**：修复 GHSA-834v-8h66-3h79，升级后全量前端测试通过。
- **移除未使用的 pdfjs-dist 5.6.205**：git grep 确认应用源码、服务端、公共资源与脚本均无调用，仅 package.json 声明；移除而不是引入新 PDF 功能或接受自动降级。包和依赖节点都从锁文件中移除。
- 新增 `test:dependencies` 命令，防止已修复的版本退回，并校验 package.json 与锁文件根依赖一致。

依赖安装与验证使用本机已经安装的 Node 24 / npm 11，避免旧 npm 10 的 edgesOut 解析器异常；关闭 lifecycle scripts。仅逐条命令覆盖 registry，未修改全局 Node/npm 或 registry 配置，也没有执行 audit fix --force。

## 4. 验证

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 后端全量 | JDK 21 下 `mvn -s .mvn/settings.xml -f backend/pom.xml test` | 747 通过，0 失败、0 错误、0 跳过 |
| 前端全量 | `npm test` | 150 通过，12 个测试文件 |
| 类型检查 | `npm run typecheck` | 通过，退出 0 |
| Webhook | `npm run test:webhook` | 7 通过 |
| 依赖回归检查 | `npm run test:dependencies` | 4 通过 |
| 官方 npm 审计 | `npm audit --registry=https://registry.npmjs.org` | 0 告警，退出 0 |
| 凭据卫生 | `python -B -m unittest discover -s tests -p 'test_*.py'` | 8 通过 |
| 前端生产构建 | `npx vite build --outDir backend/target/review-web-20261028-expiry` | 通过，退出 0 |
| 补丁空白检查 | `git diff --check` | 通过，退出 0 |

构建输出放入被忽略的 `backend/target/`，没有覆盖已跟踪的 `backend/src/main/resources/static/`。实际发布前仍应运行项目现有的前端构建及构建一致性流程。

## 5. 未擅自调整的业务规则与限制

1. **考试到期策略已经用户确认并实现。** 以服务端有效个人截止为准；到期只结算服务端最后成功保存的答案和切屏记录，不接受晚到新答案、客户端时钟或额外宽限。教师有效延时保留。普通持久化截止只可收紧，重新进入不会自动续期。自动提交时间记录为有效截止；客观题自动判分，主观题进入待阅卷。重复提交不重复判分。后台默认每批完成后间隔 5 秒再次检查，浏览器关闭或离线不再永久阻止结算；缺失截止/发布版本时拒绝伪造成绩。最终写入在数据库行锁取得后重新读取持久化个人截止、教师延时和当前有效考试排期，再检查服务端时间并执行既有 revision CAS，阻止跨截止晚到写入、旧排期缓存误放行/提前结算、过期快照覆盖教师延期。
2. **学生注册的默认密码属于既定业务要求。** 当前默认密码策略在 `docs/operations/2026-10-08-simple-registration.md` 中有明确记录，相关测试也固定这一行为。本次没有擅自修改；如面向非演示环境开放，应单独决定首次改密/随机初始凭据策略。
3. 未连接生产数据库、部署服务器或真实外部 AI 服务；没有运行会写入真实业务数据的烟雾脚本，也没有进行浏览器端端到端考试演练或 Maven 依赖漏洞扫描。单元/集成测试通过不等于没有其他漏洞。
4. 构建存在既有大资源分块提示（ECharts）；更新后的 Vite 还提示未来 native 配置加载方式的兼容性。当前配置与构建均可用，未为将来版本做无关配置重构。

## 6. 本地证据

测试/审计日志及 OCR 范围清单位于本机临时目录，文件名前缀为 `online-review-` / `online-system-ocr-scan-20261028`，不含真实密钥。实现计划：`docs/superpowers/plans/2026-10-28-code-review-repair.md`。

### 最新执行状态

本轮审查中已确认的问题以及用户最终确认的到期策略均已实现并验证；原待确认步骤已关闭。后端 747、前端 150、Webhook 7、依赖检查 4、凭据卫生 8 全部通过；类型检查、生产构建、补丁空白检查通过；官方 npm 审计为 0。前端构建输出在被忽略的 backend/target/review-web-20261028-expiry，未覆盖已跟踪静态目录。修改仍在当前工作区，未提交、推送或部署。

这不是全项目无漏洞证明：既有归档只读策略和已明确的默认密码业务规则保持不变，未连接生产数据或真实 AI 服务，未运行真实浏览器端到端考试演练或 Maven 依赖漏洞扫描；本轮独立模型复核因容量不可用的限制如上如实记录。

## 后续发布授权

用户随后明确要求推送全部项目更改并部署。发布计划见 docs/operations/2026-10-28-reviewed-release.md。此前“未提交/推送/部署、未连接生产数据”的描述记录的是代码审查阶段；后续发布仅在授权下备份和检查生产状态，不用真实业务数据运行测试。精确提交、备份路径、部署构建指纹及健康验收结果以本轮部署回执和聊天结论为准。启动禁止种子 SQL，新到期结算任务按已批准规则处理已过期会话。
