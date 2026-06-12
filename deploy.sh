#!/bin/bash
#==============================================================
# Online Exam System - One-Click Deployment Script
# Server: web.novo.ccwu.cc (AWS Lightsail Ubuntu)
#==============================================================
set -e

DOMAIN="web.novo.ccwu.cc"
APP_DIR="/opt/online-exam"
REPO_URL="https://github.com/yuan123-you/online_exam_system.git"
MYSQL_ROOT_PASS="123456"
MYSQL_DB="online_exam_system"

# Suppress all interactive prompts and auto-restart dialogs
export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_MODE=a
sudo sed -i 's/^#\?\(nrconf{restart}\).*/\1 = '\''a'\'';/' /etc/needrestart/conf.d/*.conf 2>/dev/null || true

echo "=========================================="
echo "  Online Exam System Deployment"
echo "  Domain: $DOMAIN"
echo "=========================================="

#--------------------------------------------------------------
# 1. System Update & Basic Tools
#--------------------------------------------------------------
echo ""
echo "[1/8] Updating system packages..."
sudo apt-get update -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold"
sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" curl wget gnupg2 software-properties-common apt-transport-https ca-certificates lsb-release git unzip

#--------------------------------------------------------------
# 2. Install Java 21 (Eclipse Temurin)
#--------------------------------------------------------------
echo ""
echo "[2/8] Installing Java 21..."
if java -version 2>&1 | grep -q "21"; then
    echo "  Java 21 already installed, skipping."
else
    # Add Adoptium (Eclipse Temurin) repository
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
    sudo apt-get update -y
    sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" temurin-21-jdk
fi
echo "  Java version: $(java -version 2>&1 | head -1)"

#--------------------------------------------------------------
# 3. Install Maven (with Alibaba Cloud mirror for speed)
#--------------------------------------------------------------
echo ""
echo "[3/8] Installing Maven..."
if command -v mvn &> /dev/null; then
    echo "  Maven already installed, skipping."
else
    sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" maven
fi
echo "  Maven version: $(mvn -version 2>&1 | head -1)"

# Configure Alibaba Cloud Maven mirror for faster downloads
mkdir -p ~/.m2
cat > ~/.m2/settings.xml <<'MAVEN_SETTINGS'
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
MAVEN_SETTINGS
echo "  Maven configured with Alibaba Cloud mirror."

#--------------------------------------------------------------
# 4. Install Node.js 20 LTS
#--------------------------------------------------------------
echo ""
echo "[4/8] Installing Node.js..."
if command -v node &> /dev/null && node -v | grep -q "v2[0-9]"; then
    echo "  Node.js already installed, skipping."
else
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" nodejs
fi
echo "  Node version: $(node -v)"
echo "  npm version: $(npm -v)"

#--------------------------------------------------------------
# 5. Install and Configure MySQL
#--------------------------------------------------------------
echo ""
echo "[5/8] Installing and configuring MySQL..."

if command -v mysql &> /dev/null; then
    echo "  MySQL already installed, skipping."
else
    sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" mysql-server

    # Start MySQL
    sudo systemctl start mysql
    sudo systemctl enable mysql

    # Set root password and secure installation
    sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASS}';"
    sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "FLUSH PRIVILEGES;"

    # Remove anonymous users and test database
    sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "DELETE FROM mysql.user WHERE User='';"
    sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "DROP DATABASE IF EXISTS test;"
    sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "FLUSH PRIVILEGES;"
fi

# Ensure MySQL is running
sudo systemctl start mysql

# Create the database
sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "CREATE DATABASE IF NOT EXISTS ${MYSQL_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Import seed data (departments, classes, users, questions)
echo "  Importing seed data..."
sudo mysql -u root -p"${MYSQL_ROOT_PASS}" "${MYSQL_DB}" < /opt/online-exam/backend/src/main/resources/schema.sql 2>/dev/null || true
sudo mysql -u root -p"${MYSQL_ROOT_PASS}" "${MYSQL_DB}" < /opt/online-exam/backend/src/main/resources/data.sql 2>/dev/null || true
sudo mysql -u root -p"${MYSQL_ROOT_PASS}" "${MYSQL_DB}" < /opt/online-exam/scripts/bulk-questions.sql 2>/dev/null || true
echo "  MySQL configured. Database '${MYSQL_DB}' created and seeded."

#--------------------------------------------------------------
# 6. Clone Repository and Build
#--------------------------------------------------------------
echo ""
echo "[6/8] Cloning repository and building application..."

# Clone or update the repository
if [ -d "$APP_DIR" ]; then
    echo "  Directory exists, pulling latest code..."
    cd "$APP_DIR"
    git pull origin master
else
    echo "  Cloning repository..."
    sudo mkdir -p "$APP_DIR"
    sudo chown ubuntu:ubuntu "$APP_DIR"
    git clone --depth 1 "$REPO_URL" "$APP_DIR"
    cd "$APP_DIR"
fi

# Build Frontend
echo ""
echo "  Building frontend..."
cd "$APP_DIR"

# Configure npm registry mirror for speed
npm config set registry https://registry.npmmirror.com

npm install
npx vite build
echo "  Frontend built to dist/"

# Build Backend
echo ""
echo "  Building backend (this may take a few minutes)..."
cd "$APP_DIR/backend"

# Use Maven Wrapper if available, otherwise use system maven
if [ -f "../mvnw" ]; then
    chmod +x ../mvnw
    ../mvnw -f pom.xml -DskipTests package -q
else
    mvn -f pom.xml -DskipTests package -q
fi

if [ -f "target/online-exam-backend-1.0.0.jar" ]; then
    echo "  Backend JAR built successfully."
else
    echo "  ERROR: Backend JAR not found! Build may have failed."
    exit 1
fi

#--------------------------------------------------------------
# 7. Create Systemd Service for Backend
#--------------------------------------------------------------
echo ""
echo "[7/8] Configuring backend service..."

# Detect Java path
JAVA_BIN=$(which java)
JAVA_HOME_DIR=$(dirname $(dirname $(readlink -f $JAVA_BIN)))

# Create environment file for the service
sudo tee /opt/online-exam/env.conf > /dev/null <<ENV_EOF
JAVA_HOME=${JAVA_HOME_DIR}
MYSQL_URL=jdbc:mysql://localhost:3306/online_exam_system?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
MYSQL_USER=root
MYSQL_PASSWORD=123456
PORT=8080
ENV_EOF

# Create systemd service file
sudo tee /etc/systemd/system/online-exam.service > /dev/null <<SERVICE_EOF
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
SERVICE_EOF

sudo systemctl daemon-reload
sudo systemctl enable online-exam
sudo systemctl restart online-exam

echo "  Backend service started. Waiting for startup..."
sleep 10

# Check if backend is running
if curl -s http://localhost:8080/api/health > /dev/null 2>&1 || curl -s http://localhost:8080/ > /dev/null 2>&1; then
    echo "  Backend is running on port 8080."
else
    echo "  Backend may still be starting up. Check logs with: sudo journalctl -u online-exam -f"
fi

#--------------------------------------------------------------
# 8. Configure Nginx
#--------------------------------------------------------------
echo ""
echo "[8/8] Configuring Nginx..."

sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" nginx

# Create Nginx configuration
sudo tee /etc/nginx/sites-available/online-exam > /dev/null <<NGINX_EOF
server {
    listen 80;
    server_name ${DOMAIN} 54.179.150.131;

    # Frontend static files
    root /opt/online-exam/dist;
    index index.html;

    # Frontend routes - SPA fallback
    location / {
        try_files \$uri \$uri/ /index.html;
    }

    # API reverse proxy to Spring Boot backend
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;

        # Increase timeout for long-running requests
        proxy_connect_timeout 60s;
        proxy_read_timeout 120s;
        proxy_send_timeout 60s;

        # Increase body size for file uploads
        client_max_body_size 10m;
    }

    # Cache static assets
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;
    gzip_min_length 1024;
}
NGINX_EOF

# Enable the site
sudo ln -sf /etc/nginx/sites-available/online-exam /etc/nginx/sites-enabled/online-exam
sudo rm -f /etc/nginx/sites-enabled/default

# Test and reload Nginx
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl enable nginx

echo ""
echo "=========================================="
echo "  Deployment Complete!"
echo "=========================================="
echo ""
echo "  Frontend: http://${DOMAIN}"
echo "  Backend API: http://${DOMAIN}/api/"
echo ""
echo "  Useful commands:"
echo "    Check backend status:  sudo systemctl status online-exam"
echo "    View backend logs:     sudo journalctl -u online-exam -f"
echo "    Restart backend:       sudo systemctl restart online-exam"
echo "    Restart Nginx:         sudo systemctl restart nginx"
echo "    View Nginx logs:       sudo tail -f /var/log/nginx/error.log"
echo ""

#--------------------------------------------------------------
# 9. Setup Auto-Deploy Webhook (listens for GitHub push events)
#--------------------------------------------------------------
echo ""
echo "[9/9] Setting up auto-deploy webhook..."

# Make auto-deploy script executable
chmod +x /opt/online-exam/auto-deploy.sh

# Generate a random webhook secret if not already set
WEBHOOK_SECRET_FILE="/opt/online-exam/.webhook-secret"
if [ -f "$WEBHOOK_SECRET_FILE" ]; then
  WEBHOOK_SECRET=$(cat "$WEBHOOK_SECRET_FILE")
else
  WEBHOOK_SECRET=$(openssl rand -hex 24)
  echo "$WEBHOOK_SECRET" | sudo tee "$WEBHOOK_SECRET_FILE" > /dev/null
  sudo chmod 600 "$WEBHOOK_SECRET_FILE"
fi

# Create webhook systemd service
WEBHOOK_BIN=$(which node)
sudo tee /etc/systemd/system/online-exam-webhook.service > /dev/null <<WEBHOOK_SERVICE_EOF
[Unit]
Description=Online Exam Auto-Deploy Webhook
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/online-exam
Environment=WEBHOOK_PORT=9000
Environment=WEBHOOK_SECRET=${WEBHOOK_SECRET}
ExecStart=${WEBHOOK_BIN} /opt/online-exam/auto-deploy-webhook.js
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
WEBHOOK_SERVICE_EOF

sudo systemctl daemon-reload
sudo systemctl enable online-exam-webhook
sudo systemctl start online-exam-webhook

echo ""
echo "  Auto-deploy webhook is running on port 9000."
echo "  Webhook secret: ${WEBHOOK_SECRET}"
echo ""
echo "  =========================================="
echo "  NEXT: Configure GitHub Webhook"
echo "  =========================================="
echo "  1. Go to: https://github.com/yuan123-you/online_exam_system/settings/hooks"
echo "  2. Click 'Add webhook'"
echo "  3. Payload URL:  http://54.179.150.131:9000/webhook"
echo "  4. Content type: application/json"
echo "  5. Secret:       ${WEBHOOK_SECRET}"
echo "  6. Events:       Just the push event"
echo "  7. Click 'Add webhook'"
echo ""
echo "  After this, every 'git push' to GitHub will"
echo "  automatically update the server!"
echo ""
echo "  NOTE: Make sure port 9000 is open in your"
echo "  Lightsail firewall (Networking > Firewall >"
echo "  Add rule > Custom TCP > Port 9000)."
echo "=========================================="

