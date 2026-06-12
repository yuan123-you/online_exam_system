# 在线考试系统

基于 **Spring Boot + Vue 3 + MySQL** 的在线考试系统，支持管理员、教师、学生三端协同，覆盖题库建设、组卷发布、在线答题、自动阅卷、成绩分析全流程。

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + ECharts |
| 后端 | Spring Boot 3.3.5 + Java 21 + JDBC |
| 数据库 | MySQL 8.x |
| 独立版 | Node.js + 原生 HTML/CSS/JS |

## 目录结构

```text
online-exam-system/
├─ backend/                         # Spring Boot 后端
│  ├─ pom.xml                       # Maven 项目配置
│  └─ src/main/
│     ├─ java/com/onlineexam/
│     │  ├─ ApiController.java      # REST API 控制器（~780行）
│     │  ├─ StoreService.java       # 数据访问服务（~350行）
│     │  └─ OnlineExamApplication.java
│     └─ resources/
│        ├─ application.yml          # 应用配置
│        ├─ schema.sql               # MySQL 建表脚本
│        ├─ data.sql                 # 数据初始化脚本
│        └─ static/                  # 独立版前端（app.js ~3800行）
├─ src/                             # Vue 3 前端
│  ├─ App.vue                       # 根组件（页面调度、状态管理）
│  ├─ main.ts                       # Vue 入口
│  ├─ types.ts                      # TypeScript 类型定义
│  ├─ api/client.ts                 # API 请求封装
│  ├─ utils/                        # 工具函数
│  └─ components/                   # 12 个 Vue 组件
├─ public/                          # 静态资源
├─ docs/                            # 项目文档（7份）
├─ scripts/                         # 工具脚本（8个）
├─ database.sql                     # 完整 MySQL 建库脚本
├─ vite.config.ts                   # Vite 配置
└─ package.json                     # 项目配置
```

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < database.sql
```

### 2. 配置 MySQL 连接

默认配置在 `backend/src/main/resources/application.yml`：

```yaml
MYSQL_URL: jdbc:mysql://localhost:3306/online_exam_system?createDatabaseIfNotExist=true
MYSQL_USER: root
MYSQL_PASSWORD: 123456
```

本机密码不一致时，用环境变量覆盖：

```powershell
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="你的MySQL密码"
npm start
```

### 3. 启动后端

```bash
npm run backend:build   # 编译
npm start               # 启动
```

### 4. 启动前端

```bash
npm install             # 安装依赖
npm run dev:web         # 启动 Vue 3 开发服务器
```

### 5. 访问

- Vue 3 前端：`http://localhost:5173`
- 独立版（原生 HTML）：`http://localhost:8080`
- 后端 API：`http://localhost:8080/api/health`

## 常用命令

```bash
npm start               # 启动 Spring Boot 后端
npm run dev:web         # 启动 Vue 3 前端
npm run build:web       # 构建 Vue 3 生产版本
npm run backend:build   # 编译 Spring Boot JAR
npm run migrate:mysql   # 从 store.json 生成 MySQL 迁移 SQL
npm run seed:bank       # 生成题库数据
npm run test:smoke      # 数据一致性烟雾测试
```

## 演示账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | admin | 123456 |
| 教师 | teacher | 123456 |
| 学生 | 2023001 | 123456 |

## 核心功能

**管理员**：数据总览、学生管理（含批量导入）、教师管理、组织管理、系统日志

**教师**：题库管理（6种题型）、试卷组卷、考试发布、自动+人工阅卷、成绩分析图表

**学生**：在线考试（倒计时、自动保存、防作弊）、考试记录、错题本、个人信息

## 项目文档

- [01.需求规格说明书](docs/01.需求规格说明书.md)
- [02.系统设计文档](docs/02.系统设计文档.md)
- [03.测试报告](docs/03.测试报告.md)
- [04.用户手册](docs/04.用户手册.md)
- [05.项目总结报告](docs/05.项目总结报告.md)
- [06.部署说明文档](docs/06.部署说明文档.md)
- [07.答辩PPT提纲](docs/07.答辩PPT提纲.md)
