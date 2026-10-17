# 生产 SSH 更新部署

当前生产源站为 Oracle `129.151.25.15`，不再向旧 AWS 主机推送部署。项目目录 `/opt/online-exam`，服务 `online-exam`，后端实际端口 **8085**。

现有域名保持不变：
- `web.novo.ccwu.cc`：Oracle 的考试站点。
- `exam.iflare.cn`：腾讯云 `82.157.148.104` 代理至 `129.151.25.15:443`，SNI 使用 `web.novo.ccwu.cc` 并验证源站证书。

## PowerShell 连接

```powershell
ssh -i "D:\zhixun\.ssh_key" ubuntu@129.151.25.15
```

密钥文件只保留在本机，不复制到仓库、不输出内容。

## 推送并更新

先保存、测试、提交并推送项目改动。在服务器确认源码没有未保存更改；生产环境文件和生成静态资源需先备份，不要盲目重置或把环境文件提交 Git。

本地 Git Bash 可使用现有助手：

```bash
DEPLOY_SSH_KEY='D:/zhixun/.ssh_key' bash push-and-deploy.sh
```

可用 `DEPLOY_SERVER` 覆盖目标；默认 `ubuntu@129.151.25.15`。助手调用服务器现有更新脚本，不处理跨版本第一次部署前的未知本地修改。

服务器已更新到当前脚本版本后，可运行：

```bash
bash /opt/online-exam/update.sh
```

默认健康检查是 `http://127.0.0.1:8085/api/health`，可通过 `HEALTH_URL` 覆盖。不能用其他端口的根页面作为成功兜底。

## 验收

```bash
systemctl is-active online-exam nginx
curl -fsS http://127.0.0.1:8085/api/health
curl -fsS http://127.0.0.1:8085/api/ai/health
curl -fsS https://web.novo.ccwu.cc/api/health
curl -fsS https://exam.iflare.cn/api/health
```

需要同时比对线上前端入口文件、Oracle 的 `dist/index.html` 与部署提交，不能只凭进程启动或普通健康接口判断 AI 和代理链路成功。

首次将旧版本 Oracle 更新到本版本的步骤与回滚边界，参见 `docs/plans/2026-10-17-oracle-production-deployment.md`。不更换域名、不修改其他站点、不清空业务数据。
