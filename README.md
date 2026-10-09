# 在线考试系统

面向教学场景的前后端分离在线考试平台，基于 **Vue 3、TypeScript、Spring Boot 与 MySQL** 构建。围绕管理员、教师和学生三类角色，将题库建设、试卷编排、考试发布、在线答题、阅卷反馈与错题学习串联为完整业务流程，并提供 AI 出题与练习辅助。

> 本仓库包含开发与演示数据。AI 生成内容及阅卷建议需要人工复核；演示账号和初始化数据不应直接用于生产环境。

## 导航

- [功能概览](#功能概览)
- [技术架构](#技术架构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [开发与验证](#开发与验证)
- [部署注意事项](#部署注意事项)
- [常见问题](#常见问题)
- [项目文档](#项目文档)

## 功能概览

| 角色 | 主要能力 |
| --- | --- |
| 管理员 | 数据总览、学生与教师管理、批量用户导入、学院与班级管理、系统日志、考试管理 |
| 教师 | 题库维护、AI 出题、手动与自动组卷、考试发布与监控、阅卷、成绩分析、班级对比、Excel 导出 |
| 学生 | 自助注册、在线答题、倒计时与答案保存、成绩查询、错题复习、AI 刷题、练习记录与个人信息维护 |
| 通用 | 角色路由、站内通知、浅色 / 深色 / 跟随系统主题 |

题库覆盖单选、多选、判断、填空、简答与编程六类题型。客观题自动判分与人工阅卷协同使用；AI 阅卷结果作为参考，不等同于最终成绩。

### 当前业务实现

- **学生注册**：`AuthService.registerStudent` 固定创建学生角色，校验用户名、密码与学院 / 班级关系；不接受客户端自行指定管理角色。
- **考试发布版本**：`StoreService` 在发布写入流程中捕获试卷与题目内容，保存到 `exam_snapshot`。`ExamContent` 校验试卷归属、重复题目、分值与总分一致性。
- **历史内容保护**：已发布或已有提交的考试若缺少快照，标记为 `MISSING`；需要原始内容的业务拒绝使用当前题库替代。已有成绩的展示与原始内容恢复是不同流程。
- **身份与数据访问**：`SessionTokenService` 提供带签名与 12 小时有效期的会话令牌，`AuthInterceptor` 解析请求身份；业务服务按角色与数据归属执行检查。
- **AI 题目处理**：`AiQuestionPolicy` 和 `ChoiceAnswers` 处理题型约束与选择题答案规范化。结构合法不代表模型生成的知识内容一定正确。

## 技术架构

| 层次 | 技术与职责 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia；负责页面交互与状态管理 |
| 可视化与内容展示 | ECharts、KaTeX、PDF.js |
| 后端 | Java 21、Spring Boot 3.3.5、Spring JDBC；负责业务校验、数据访问与 API |
| 数据存储 | MySQL 8.x；建表与演示数据位于后端资源目录 |
| 文件导出 | Apache POI；用于 Excel 成绩导出 |
| AI 接入 | 后端调用模型接口，支持出题、练习对话与阅卷辅助 |
| 测试 | Vitest、Vue Test Utils、JUnit / Maven |

```text
浏览器（Vue 3）
  └── /api → Spring Boot
               ├── Controller / Service → Repository → MySQL
               ├── AI 服务调用 → 模型接口
               └── Apache POI → Excel 导出
```

开发环境由 Vite 将 `/api` 代理到 `http://127.0.0.1:8080`。构建时，Vue 产物复制到 Spring Boot 的 `static/` 目录，可由后端统一提供页面与 API。

### 目录结构

```text
.
├── src/                         # Vue 前端：页面、组件、路由、状态与 API
├── public/                      # 前端公共资源
├── backend/
│   ├── pom.xml                  # 后端依赖与构建配置
│   └── src/
│       ├── main/java/com/onlineexam/
│       │   ├── controller/      # API 入口
│       │   ├── service/         # 考试、阅卷、分析与 AI 业务
│       │   ├── repository/      # JDBC 数据访问
│       │   ├── entity/          # 数据实体
│       │   └── config/          # 鉴权、跨域等配置
│       ├── main/resources/      # application.yml、SQL 与静态页面
│       └── test/                # 后端测试
├── db/migrations/               # 既有数据库的增量迁移脚本
├── scripts/                     # 启动、烟雾测试与构建一致性检查
├── tests/                       # 其他验证资源
├── docs/                        # 设计、测试、用户手册与部署资料
└── package.json                 # 前端依赖与项目命令
```

## 快速开始

### 1. 准备环境

- **Node.js 22.22.2+**：满足当前 Vite 工具链的运行要求。
- **JDK 21** 与可用的 **Maven**：确认 `java`、`mvn` 已加入 PATH。
- **MySQL 8.x**：创建隔离的开发数据库，或授予开发账号建库权限。

以下命令均从仓库根目录执行。安装前可用 `node --version`、`java -version`、`mvn -version` 检查环境。

### 2. 安装依赖并配置数据库

```bash
npm ci
```

以下示例仅展示非敏感连接参数；`MYSQL_USER` 与 `MYSQL_PASSWORD` 须通过受控环境另行注入。

PowerShell 示例：

```powershell
$env:MYSQL_URL = "jdbc:mysql://localhost:3306/online_exam_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&tinyInt1isBit=false"
```

Bash 示例：

```bash
export MYSQL_URL='jdbc:mysql://localhost:3306/online_exam_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&tinyInt1isBit=false'
```

使用上述连接时，应先创建 `online_exam_system` 数据库。默认连接包含 `createDatabaseIfNotExist=true`，仅在账号具有对应权限时可以自动建库。

**初始化机制**：当前配置启用 `spring.sql.init.mode=always`，启动时执行 `backend/src/main/resources/schema.sql` 和 `data.sql`。后者包含演示数据及 upsert 操作，请使用独立开发库，避免覆盖已有业务数据。无需执行不存在的根目录 `database.sql`。

### 3. 启动应用

```bash
npm start
```

该命令依次构建前端、编译后端、生成依赖 classpath，再启动 Java 进程。保持终端运行，访问：

| 入口 | 地址 |
| --- | --- |
| 应用页面（后端托管） | `http://localhost:8080` |
| 健康检查 | `http://localhost:8080/api/health` |

需要前端热更新时，在第二个终端执行：

```bash
npm run dev:web
```

访问 `http://localhost:5173`，并保持后端运行。如果修改后端 `PORT`，也应同步调整 `vite.config.ts` 的代理目标。

### 4. 账号与凭据管理

管理员与教师账号由授权管理流程维护；学生可通过 `/register` 注册。

数据库凭据须由本地进程环境或部署平台的秘密管理功能注入。种子账号不包含可用密码；新管理员可通过 `ADMIN_USERNAME` 与 `ADMIN_PASSWORD` 显式初始化，使用尚不存在的用户名，密码至少 8 个字符且不超过 72 个 UTF-8 字节。初始化器不修改既有账号密码。既有环境中的默认账号须另行重置，源码修改不会自动轮换数据库凭据。

## 源码索引

| 说明 | 当前实现入口 |
| --- | --- |
| 显式管理员初始化 | [AdminAccountInitializer](backend/src/main/java/com/onlineexam/config/AdminAccountInitializer.java) |
| 注册与登录 | [AuthService](backend/src/main/java/com/onlineexam/service/AuthService.java) |
| 服务端会话 | [SessionTokenService](backend/src/main/java/com/onlineexam/service/SessionTokenService.java) |
| 存储与考试发布 | [StoreService](backend/src/main/java/com/onlineexam/StoreService.java) |
| 考试内容与版本规则 | [ExamContent](backend/src/main/java/com/onlineexam/service/ExamContent.java) |
| 提交与阅卷 | [SubmissionService](backend/src/main/java/com/onlineexam/service/SubmissionService.java) |
| AI 题目约束 | [AiQuestionPolicy](backend/src/main/java/com/onlineexam/service/AiQuestionPolicy.java) |
| 脚本与配置 | [package.json](package.json)、[application.yml](backend/src/main/resources/application.yml)、[Vite 配置](vite.config.ts) |

## 配置说明

配置入口为 `backend/src/main/resources/application.yml`。通过项目的 `npm start` 启动时，`scripts/run-backend.js` 还会读取根目录 `.env`，已有进程环境变量优先。

| 环境变量 | 用途 | 默认行为 |
| --- | --- | --- |
| `PORT` | 后端监听端口 | `8080` |
| `MYSQL_URL` | MySQL JDBC 连接 | 本机 `online_exam_system` 数据库 |
| `MYSQL_USER` | 数据库账号 | 通过环境变量配置独立数据库账号 |
| `MYSQL_PASSWORD` | 数据库密码 | 空值，按本机环境设置 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 首次创建管理员，均须显式注入 | 都未设置时跳过；不重置已有账号 |
| `AUTH_SESSION_SECRET` | 会话签名密钥，至少 32 字节 | 未设置时启动随机生成，重启后旧令牌失效；多实例需使用同一密钥 |
| `AI_API_KEY` | 模型 API 密钥 | 空值；使用 AI 前需要配置 |
| `AI_MODEL` | 模型名称 | `glm-4.7-flash`（限流时自动降级到 `glm-4-flash`） |
| `AI_CONCURRENT_LIMIT` | AI 请求并发上限 | `10` |
| `CORS_ALLOWED_ORIGINS` | 允许的前端来源 | 参见配置文件；部署时按实际域名收敛 |

模型接口地址由 `ai.api-url` 配置。不要将 `.env`、数据库密码或模型密钥提交到版本库。

## 开发与验证

| 命令 | 说明 |
| --- | --- |
| `npm start` | 构建前端并启动后端 |
| `npm run dev:web` | 前端开发服务器 |
| `npm run build:web` | 构建前端，并覆盖后端 `static/` 目录 |
| `npm run backend:build` | 编译后端并生成 classpath；不打包 JAR，也不运行测试 |
| `npm run preview:web` | 预览前端构建产物 |
| `npm test` | 运行前端 Vitest 测试 |
| `npm run test:coverage` | 前端测试覆盖率 |
| `npm run test:smoke` | 数据一致性烟雾测试 |
| `npm run verify:build` | 检查前端构建与后端静态资源一致性 |
| `npm run predeploy` | 构建前端并执行一致性检查 |

后端测试与打包命令（仓库根目录）：

```bash
mvn -s .mvn/settings.xml -f backend/pom.xml test
mvn -s .mvn/settings.xml -f backend/pom.xml package
```

测试应使用隔离配置与测试数据。`-DskipTests` 会跳过测试；需要验证时请运行上述测试命令。

## 部署注意事项

1. **先备份再迁移**：`CREATE TABLE IF NOT EXISTS` 不会为旧表自动增加字段。升级前检查 [增量迁移目录](db/migrations/)，按实际版本核对脚本，不要盲目重复执行。
2. **保护历史考试数据**：考试快照迁移仅建立结构，不应从当前题库推断或重建历史版本。
3. **核对初始化策略**：生产环境不能未经检查地沿用演示 `data.sql` 的每次启动写入行为。
4. **保持前后端一致**：`build:web` 会覆盖后端静态目录，手工部署前执行 `npm run predeploy`，不要在生成目录中维护源码。
5. **隔离敏感资源**：使用 HTTPS、独立数据库账号与受限跨域来源；不公开数据库、模型密钥或默认管理账号。

详细步骤见 [手动部署指南](docs/manual-deploy-guide.md) 和 [部署说明文档](docs/06.部署说明文档.docx)。

## 常见问题

| 现象 | 排查方向 |
| --- | --- |
| 数据库连接失败 | 确认 MySQL 已启动、账号权限与 `MYSQL_*` 配置正确；首次启动账号需要建库权限或已建库 |
| 升级后提示字段缺失 | 对照 `db/migrations/` 检查旧库结构，备份后按版本执行迁移 |
| 前端请求失败 | 确认后端在 `8080` 运行，Vite 的 `/api` 代理与实际端口一致 |
| AI 请求失败或超时 | 检查 `AI_API_KEY`、接口地址、模型名称、网络连通性与服务日志；不要输出密钥 |
| 页面仍显示旧版本 | 重新构建并检查静态资源一致性，再检查部署目录与浏览器缓存 |
| 启动提示 classpath 缺失 | 执行 `npm run backend:build`，或使用完整的 `npm start` 流程 |

## 项目文档

| 文档 | 内容 |
| --- | --- |
| [需求规格说明书](docs/01.需求规格说明书.docx) | 业务需求与功能范围 |
| [系统设计文档](docs/02.系统设计文档.docx) | 系统设计与模块说明 |
| [测试报告](docs/03.测试报告.docx) | 测试记录 |
| [用户手册](docs/04.用户手册.docx) | 各角色使用说明 |
| [项目总结报告](docs/05.项目总结报告.docx) | 项目总结 |
| [部署说明文档](docs/06.部署说明文档.docx) | 部署流程 |
| [答辩演示文稿](docs/07.答辩PPT.pptx) | 项目展示资料 |
| [安全审计报告](docs/08.安全审计报告.md) | 历史安全审计记录 |
| [全阶段测试报告](docs/08.测试报告.md) | 分阶段测试记录 |
| [八阶段交付总结](docs/09.八阶段交付总结.md) | 交付总结 |

文档中的历史记录以其对应版本为准；配置与命令以当前仓库文件为准。

敏感信息回归检查：在仓库根目录运行 `python tests/test_secret_hygiene.py`。该检查针对当前跟踪文件、固定配置与忽略规则，不替代历史扫描或凭据轮换。
