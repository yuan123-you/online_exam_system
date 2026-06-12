## 在线考试系统 - 手动部署指南

本文档指导你从零开始，在 AWS Lightsail Ubuntu 服务器上手动部署在线考试系统。

---

### 前置准备

你需要一台 Ubuntu 服务器（推荐 22.04 或 24.04），并确保：

- 已开放防火墙 22（SSH）和 80（HTTP）端口
- 服务器有至少 2GB 内存和 20GB 磁盘
- 域名 DNS 的 A 记录已指向服务器公网 IP（可选，也可以直接用 IP 访问）

通过 SSH 连接到服务器后，先更新系统：

```bash
sudo apt-get update -y
sudo apt-get upgrade -y
```

---

### 第一步：安装 Java 21

后端使用 Spring Boot 3.3.5，要求 Java 21。Ubuntu 默认仓库没有 Java 21，需要添加 Eclipse Temurin 仓库：

```bash
sudo apt-get install -y curl wget gnupg2 software-properties-common apt-transport-https ca-certificates lsb-release

wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg

echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list

sudo apt-get update -y
sudo apt-get install -y temurin-21-jdk
```

验证安装：

```bash
java -version
# 应输出 openjdk version "21.x.x"
```

---

### 第二步：安装 Maven

Maven 用于编译后端 Java 项目：

```bash
sudo apt-get install -y maven
mvn -version
```

配置阿里云 Maven 镜像（加速依赖下载）：

```bash
mkdir -p ~/.m2
cat > ~/.m2/settings.xml << 'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>*</mirrorOf>
      <name>Alibaba Cloud Maven Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF
```

---

### 第三步：安装 Node.js

Node.js 用于构建前端（Vite 打包）：

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
```

验证安装：

```bash
node -v   # 应输出 v20.x.x
npm -v    # 应输出 10.x.x
```

配置 npm 镜像（加速包下载）：

```bash
npm config set registry https://registry.npmmirror.com
```

---

### 第四步：安装和配置 MySQL

安装 MySQL：

```bash
sudo apt-get install -y mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql
```

设置 root 密码（项目配置中默认使用 `123456`，你可以改成自己的）：

```bash
sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '123456';"
sudo mysql -u root -p'123456' -e "FLUSH PRIVILEGES;"
```

创建数据库：

```bash
sudo mysql -u root -p'123456' -e "CREATE DATABASE IF NOT EXISTS online_exam_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

验证数据库已创建：

```bash
sudo mysql -u root -p'123456' -e "SHOW DATABASES;"
# 应看到 online_exam_system
```

> 注意：如果你修改了 root 密码（不是 123456），后面启动后端时需要通过环境变量传入新密码。

---

### 第五步：从 GitHub 克隆项目

```bash
sudo mkdir -p /opt/online-exam
sudo chown ubuntu:ubuntu /opt/online-exam
git clone --depth 1 https://github.com/yuan123-you/online_exam_system.git /opt/online-exam
cd /opt/online-exam
```

此时目录结构大致如下：

```
/opt/online-exam/
├── backend/          # 后端 Spring Boot 项目
│   ├── pom.xml
│   └── src/
├── src/              # 前端 Vue 源码
├── package.json
├── vite.config.ts
└── deploy.sh         # 部署脚本（可忽略）
```

---

### 第六步：构建前端

```bash
cd /opt/online-exam
npm install
npx vite build
```

构建完成后，`dist/` 目录会生成，包含 `index.html` 和 `assets/` 文件夹，这就是前端的生产文件。

```bash
ls dist/
# 应看到 index.html  assets/
```

---

### 第七步：构建后端

```bash
cd /opt/online-exam/backend
mvn -f pom.xml -DskipTests package -q
```

构建完成后会生成 JAR 文件（约 23MB）：

```bash
ls -lh target/online-exam-backend-1.0.0.jar
```

> 首次构建需要下载依赖，大约 3-5 分钟。配置了阿里云镜像会快很多。

---

### 第八步：测试后端能否启动

先手动启动后端确认没有问题：

```bash
cd /opt/online-exam
java -jar backend/target/online-exam-backend-1.0.0.jar
```

等待几秒，看到类似 `Started OnlineExamApplication in X seconds` 的日志说明启动成功。然后按 `Ctrl+C` 停止。

如果出现数据库连接错误，检查 MySQL 是否正在运行以及密码是否正确：

```bash
sudo systemctl status mysql
sudo mysql -u root -p'123456' -e "SELECT 1;"
```

如果密码不是默认的 123456，启动时传入环境变量：

```bash
MYSQL_PASSWORD=你的密码 java -jar backend/target/online-exam-backend-1.0.0.jar
```

---

### 第九步：配置 systemd 服务（让后端后台运行并开机自启）

创建环境变量配置文件：

```bash
sudo tee /opt/online-exam/env.conf << 'EOF'
MYSQL_URL=jdbc:mysql://localhost:3306/online_exam_system?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
MYSQL_USER=root
MYSQL_PASSWORD=123456
PORT=8080
EOF
```

创建 systemd 服务文件：

```bash
JAVA_BIN=$(which java)

sudo tee /etc/systemd/system/online-exam.service << EOF
[Unit]
Description=Online Exam System Backend
After=network.target mysql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/online-exam
EnvironmentFile=/opt/online-exam/env.conf
ExecStart=${JAVA_BIN} -jar /opt/online-exam/backend/target/online-exam-backend-1.0.0.jar
Restart=always
RestartSec=10
StandardOutput=append:/opt/online-exam/backend.log
StandardError=append:/opt/online-exam/backend-err.log

[Install]
WantedBy=multi-user.target
EOF
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable online-exam
sudo systemctl start online-exam
```

等待 10 秒让 Spring Boot 完成启动，然后检查：

```bash
sudo systemctl status online-exam
# 应显示 active (running)

curl -s http://localhost:8080/ | head -5
# 有响应说明后端正常
```

查看实时日志：

```bash
sudo journalctl -u online-exam -f
```

---

### 第十步：安装和配置 Nginx

Nginx 作为反向代理，负责将前端静态文件和后端 API 统一暴露到 80 端口。

安装 Nginx：

```bash
sudo apt-get install -y nginx
```

创建站点配置（将 `web.novo.ccwu.cc` 替换为你的域名，没有域名就用 IP）：

```bash
sudo tee /etc/nginx/sites-available/online-exam << 'EOF'
server {
    listen 80;
    server_name web.novo.ccwu.cc 54.179.150.131;

    # 前端静态文件
    root /opt/online-exam/dist;
    index index.html;

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 120s;
        proxy_send_timeout 60s;
        client_max_body_size 10m;
    }

    # 静态资源缓存
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;
    gzip_min_length 1024;
}
EOF
```

启用站点并重启 Nginx：

```bash
sudo ln -sf /etc/nginx/sites-available/online-exam /etc/nginx/sites-enabled/online-exam
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t          # 测试配置语法
sudo systemctl restart nginx
sudo systemctl enable nginx
```

---

### 验证部署

在浏览器中访问 `http://你的域名` 或 `http://服务器IP`，应该能看到在线考试系统的前端页面。

验证后端 API：

```bash
curl http://localhost:8080/api/health
# 或通过 Nginx
curl http://localhost/api/health
```

---

### 日常运维命令速查

```bash
# 查看后端状态
sudo systemctl status online-exam

# 查看后端实时日志
sudo journalctl -u online-exam -f

# 重启后端
sudo systemctl restart online-exam

# 重启 Nginx
sudo systemctl restart nginx

# 查看 Nginx 错误日志
sudo tail -f /var/log/nginx/error.log

# 查看后端应用日志
tail -f /opt/online-exam/backend.log
```

---

### 代码更新后重新部署

当 GitHub 上的代码有更新时，依次执行：

```bash
# 拉取最新代码
cd /opt/online-exam && git pull origin master

# 重新构建前端
npm install && npx vite build

# 重新构建后端
cd backend && mvn -f pom.xml -DskipTests package -q

# 重启后端服务
sudo systemctl restart online-exam
```

或者直接运行更新脚本：

```bash
bash -c "$(curl -fsSL https://raw.githubusercontent.com/yuan123-you/online_exam_system/master/update.sh)"
```
