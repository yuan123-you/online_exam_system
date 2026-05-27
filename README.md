# 在线考试系统

项目已调整为 `Spring Boot + Vue3 + MySQL` 架构：

- 后端：`backend/`，Spring Boot 3 + JDBC，默认端口 `8080`
- 前端：`src/`，Vue3 + Vite，默认端口 `5173`
- 数据库：MySQL，数据库名 `online_exam_system`
- 数据迁移：当前 `data/store.json` 已转换为 `database.sql` 和 `backend/src/main/resources/data.sql`

## 目录结构

```text
online exam system/
├─ backend/                         # Spring Boot 后端
│  └─ src/main/resources/
│     ├─ schema.sql                 # MySQL 建表
│     └─ data.sql                   # 从 data/store.json 迁移出的数据
├─ src/                             # Vue3 前端
├─ public/                          # 静态样式资源
├─ data/store.json                  # 原始 JSON 数据备份
├─ scripts/generate-mysql-migration.js
├─ database.sql                     # 可直接导入 MySQL 的完整建库、建表、数据 SQL
└─ package.json
```

## MySQL 配置

默认连接配置在 `backend/src/main/resources/application.yml`：

```yaml
MYSQL_URL=jdbc:mysql://localhost:3306/online_exam_system?createDatabaseIfNotExist=true...
MYSQL_USER=root
MYSQL_PASSWORD=you123
```

本机密码不一致时，用环境变量覆盖：

```powershell
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="你的MySQL密码"
npm start
```

也可以手动导入完整迁移 SQL：

```powershell
mysql -u root -p < database.sql
```

## 常用命令

```bash
npm run migrate:mysql   # 重新从 data/store.json 生成 MySQL 数据 SQL
npm run backend:build   # 编译 Spring Boot 后端
npm start               # 启动 Spring Boot API
npm run dev:web         # 启动 Vue3 前端
npm run build:web       # 构建 Vue3 前端
```

访问地址：

- 前端开发环境：`http://localhost:5173`
- 后端健康检查：`http://localhost:8080/api/health`

## 演示账号

- 管理员：`admin / 123456`
- 教师：`teacher / 123456`
- 学生：`2023001 / 123456`
