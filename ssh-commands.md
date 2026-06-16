## SSH 一键更新部署命令

以下命令在**本地电脑终端**中执行（非服务器上）。

---

### 最常用：推送代码 + 自动更新服务器

```bash
# 从项目根目录执行（先 cd 到项目目录）
ssh ubuntu@54.179.150.131 'bash /opt/online-exam/update.sh'
```

---

### 完整工作流：Git Push + SSH 远程更新

```bash
cd "D:\Codex Web\20252160A1025游源" && git add -A && git commit -m "update: $(date +%Y-%m-%d)" && git push origin master && ssh ubuntu@54.179.150.131 'bash /opt/online-exam/update.sh'
```

---

### 只更新前端

```bash
ssh ubuntu@54.179.150.131 'bash /opt/online-exam/update.sh --frontend'
```

### 只更新后端

```bash
ssh ubuntu@54.179.150.131 'bash /opt/online-exam/update.sh --backend'
```

---

### 使用本地辅助脚本（推荐，自动 commit + push + 远程更新）

```bash
bash push-and-deploy.sh                    # 自动 commit 消息
bash push-and-deploy.sh "fix: 修复登录bug"  # 自定义 commit 消息
bash push-and-deploy.sh --skip-push        # 跳过 push，只触发远程更新
```

---

### 服务器端直接操作（SSH 登录后）

```bash
# 完整更新
bash /opt/online-exam/update.sh

# 查看后端状态
sudo systemctl status online-exam

# 查看实时日志
sudo journalctl -u online-exam -f

# 手动重启后端
sudo systemctl restart online-exam

# 重新加载 Nginx
sudo systemctl reload nginx

# 查看部署日志
tail -100 /opt/online-exam/deploy.log
```

---

### 紧急回滚（服务器上）

```bash
# 查看最近的备份
ls -lt /opt/online-exam-backups/

# 手动恢复某个备份
BACKUP_NAME="update-20250616-120000"
sudo cp -r /opt/online-exam-backups/$BACKUP_NAME/dist /opt/online-exam/dist
sudo cp /opt/online-exam-backups/$BACKUP_NAME/online-exam-backend-1.0.0.jar /opt/online-exam/backend/target/
sudo systemctl restart online-exam
sudo systemctl reload nginx
```

---

### 完整重新部署（从零开始）

```bash
ssh ubuntu@54.179.150.131 'bash -s' < deploy.sh
```
