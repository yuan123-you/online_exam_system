# Debug Session: server-internal-error

**Status**: [OPEN]
**Session ID**: server-internal-error
**Created**: 2026-06-16

## Symptoms
- 页面内部显示"服务器内部错误"
- 所有信息未能正常同步
- AI助手功能不可用

## Environment
- Production URL: https://web.novo.ccwu.cc/
- Backend: Spring Boot (Java)
- Frontend: Vue.js

## Hypotheses
1. **H1**: 后端 API 返回 500 错误，可能是数据库连接问题或查询失败
2. **H2**: AI 服务配置错误（API key、endpoint、模型配置），导致 AI 助手不可用
3. **H3**: 前端 API 客户端 baseURL 配置错误，导致无法正确调用后端 API
4. **H4**: 数据库事务或查询失败，导致信息同步失败
5. **H5**: 环境变量缺失或配置文件错误

## Evidence Collection

### API Health Check Results
- `/api/health`: ✅ OK - `{"status":"ok","time":"2026-06-15T08:34:37.435521874Z","storage":"mysql"}`
- `/api/bootstrap`: ⚠️ Returns empty arrays - `{"currentUser":"","departments":[],"classes":[],"users":[],"questions":[],"papers":[],"exams":[],"submissions":[],"wrongBookEntries":[],"logs":[]}`
- `/api/ai/health`: ✅ OK - AI service configured correctly

### Root Cause Analysis

**Confirmed Issue**: Database initialization disabled

In [application.yml](file:///d:/Codex%20Web/20252160A1025游源/backend/src/main/resources/application.yml#L14-L17):
```yaml
spring:
  sql:
    init:
      mode: never  # ❌ This prevents schema.sql and data.sql from executing
      encoding: UTF-8
      continue-on-error: true
```

**Impact**:
- `schema.sql` and `data.sql` are NOT executed on startup
- Database tables may exist (from deploy.sh full_dump.sql), but if no dump file exists, tables are empty
- Bootstrap returns empty arrays because database has no data

**AI Service Status**:
- ✅ API key configured (using default value from application.yml)
- ✅ Model: glm-4-flash
- ✅ Circuit breakers: all CLOSED (healthy)
- AI assistant should work if user logs in correctly

## Fix Plan

### Fix 1: Enable Database Initialization
Change `spring.sql.init.mode` from `never` to `always` (for first deploy) or `embedded` (for embedded DB only)

### Fix 2: Ensure Database Has Data
Option A: Import data.sql manually via MySQL command
Option B: Change init mode to execute schema.sql and data.sql on startup

## Timeline
- 2026-06-16: Session created, root cause identified - database init disabled