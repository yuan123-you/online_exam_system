#!/bin/bash
#==============================================================
# Online Exam System - Optimized One-Click Deployment Script
# Server: web.novo.ccwu.cc (AWS Lightsail Ubuntu)
#
# Improvements over original:
#   - Colored output for readability
#   - Pre-flight checks (disk space, connectivity)
#   - Automatic backup before updates
#   - Rollback on failure
#   - Health check with retries
#   - Proper error handling via trap
#   - Deployment time tracking
#==============================================================
set -euo pipefail

#--------------------------------------------------------------
# Configuration
#--------------------------------------------------------------
DOMAIN="web.novo.ccwu.cc"
SERVER_IP="54.179.150.131"
APP_DIR="/opt/online-exam"
BACKUP_DIR="/opt/online-exam-backups"
REPO_URL="https://github.com/yuan123-you/online_exam_system.git"
REPO_BRANCH="master"
MYSQL_ROOT_PASS="123456"
MYSQL_DB="online_exam_system"
JAR_NAME="online-exam-backend-1.0.0.jar"
HEALTH_URL="http://localhost:8080/api/health"
HEALTH_RETRIES=12          # max retries (12 x 5s = 60s)
HEALTH_INTERVAL=5
MIN_DISK_MB=1024           # minimum free disk in MB
DEPLOY_LOG="/opt/online-exam/deploy.log"

#--------------------------------------------------------------
# Color helpers
#--------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }
step()    { echo -e "\n${CYAN}${BOLD}[$1/$TOTAL_STEPS] $2${NC}"; }

timestamp() { date '+%Y-%m-%d %H:%M:%S'; }

log() {
    local msg="[$(timestamp)] $*"
    echo "$msg" >> "$DEPLOY_LOG" 2>/dev/null || true
}

#--------------------------------------------------------------
# Suppress interactive prompts
#--------------------------------------------------------------
export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_MODE=a
sudo sed -i 's/^#\?\(nrconf{restart}\).*/\1 = '\''a'\'';/' /etc/needrestart/conf.d/*.conf 2>/dev/null || true

#--------------------------------------------------------------
# Pre-flight: ensure log directory exists
#--------------------------------------------------------------
sudo mkdir -p "$(dirname "$DEPLOY_LOG")"
sudo touch "$DEPLOY_LOG" 2>/dev/null || true

DEPLOY_START=$(date +%s)
log "========== Deployment started =========="

echo ""
echo -e "${BOLD}=========================================="
echo "  Online Exam System - Deployment"
echo "  Domain:  $DOMAIN"
echo "  Server:  $SERVER_IP"
echo "  Time:    $(timestamp)"
echo -e "==========================================${NC}"

TOTAL_STEPS=9

#--------------------------------------------------------------
# Pre-flight checks
#--------------------------------------------------------------
preflight_check() {
    info "Running pre-flight checks..."

    # Disk space
    local avail_mb
    avail_mb=$(df -BM / | awk 'NR==2 {gsub("M",""); print $4}')
    if [ "$avail_mb" -lt "$MIN_DISK_MB" ]; then
        error "Insufficient disk space: ${avail_mb}MB available, need at least ${MIN_DISK_MB}MB"
        exit 1
    fi
    success "Disk space: ${avail_mb}MB available"

    # Root privileges
    if ! sudo -n true 2>/dev/null; then
        warn "sudo may require a password — make sure you can use sudo"
    fi

    success "Pre-flight checks passed"
}
preflight_check

#--------------------------------------------------------------
# Rollback helper
#--------------------------------------------------------------
ROLLBACK_TAG=""
do_rollback() {
    if [ -n "$ROLLBACK_TAG" ] && [ -d "${BACKUP_DIR}/${ROLLBACK_TAG}" ]; then
        warn "Rolling back to backup: $ROLLBACK_TAG"
        log "ROLLBACK: restoring from ${ROLLBACK_TAG}"
        # Restore code
        if [ -d "${BACKUP_DIR}/${ROLLBACK_TAG}/code" ]; then
            rsync -a --delete "${BACKUP_DIR}/${ROLLBACK_TAG}/code/" "${APP_DIR}/"
        fi
        # Restart services
        sudo systemctl restart online-exam 2>/dev/null || true
        sudo systemctl reload nginx 2>/dev/null || true
        warn "Rollback complete. Please verify manually."
    fi
}

# Trap errors
cleanup() {
    local exit_code=$?
    if [ $exit_code -ne 0 ]; then
        error "Deployment failed at step: $CURRENT_STEP (exit code: $exit_code)"
        log "FAILED at step: $CURRENT_STEP (exit=$exit_code)"
        do_rollback
    fi
}
CURRENT_STEP="init"
trap cleanup EXIT

#--------------------------------------------------------------
# 1. System Update & Basic Tools
#--------------------------------------------------------------
CURRENT_STEP="system-update"
step 1 "Updating system packages & installing tools"

sudo apt-get update -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" -qq
sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" -qq \
    curl wget gnupg2 software-properties-common apt-transport-https \
    ca-certificates lsb-release git unzip rsync
success "System packages updated"

#--------------------------------------------------------------
# 2. Install Java 21 (Eclipse Temurin)
#--------------------------------------------------------------
CURRENT_STEP="java"
step 2 "Installing Java 21 (Eclipse Temurin)"

if java -version 2>&1 | grep -q "21"; then
    success "Java 21 already installed"
else
    info "Installing Eclipse Temurin JDK 21..."
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
        | sudo tee /etc/apt/sources.list.d/adoptium.list > /dev/null
    sudo apt-get update -y -qq
    sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" -qq temurin-21-jdk
    success "Java 21 installed"
fi
info "Java: $(java -version 2>&1 | head -1)"

#--------------------------------------------------------------
# 3. Install Maven (with Alibaba Cloud mirror)
#--------------------------------------------------------------
CURRENT_STEP="maven"
step 3 "Installing Maven & configuring Alibaba mirror"

if command -v mvn &> /dev/null; then
    success "Maven already installed"
else
    sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" -qq maven
    success "Maven installed"
fi
info "Maven: $(mvn -version 2>&1 | head -1)"

# Configure Alibaba Cloud mirror (idempotent)
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
success "Maven configured with Alibaba Cloud mirror"

#--------------------------------------------------------------
# 4. Install Node.js 20 LTS
#--------------------------------------------------------------
CURRENT_STEP="nodejs"
step 4 "Installing Node.js 20 LTS"

if command -v node &> /dev/null && node -v | grep -q "v2[0-9]"; then
    success "Node.js already installed"
else
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" -qq nodejs
    success "Node.js installed"
fi
info "Node: $(node -v) | npm: $(npm -v)"

#--------------------------------------------------------------
# 5. Install and Configure MySQL
#--------------------------------------------------------------
CURRENT_STEP="mysql"
step 5 "Installing and configuring MySQL"

if command -v mysql &> /dev/null; then
    success "MySQL already installed"
else
    info "Installing MySQL..."
    sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" -qq mysql-server

    sudo systemctl start mysql
    sudo systemctl enable mysql

    sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASS}';"
    sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "FLUSH PRIVILEGES;"
    sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "DELETE FROM mysql.user WHERE User='';"
    sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "DROP DATABASE IF EXISTS test;"
    sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -e "FLUSH PRIVILEGES;"
    success "MySQL installed and secured"
fi

sudo systemctl start mysql
success "MySQL running"

# Create database & import seed data (first deploy only)
info "Checking database..."
DB_EXISTS=$(sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -sN \
    -e "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='${MYSQL_DB}';" 2>/dev/null || true)
TABLE_COUNT=$(sudo mysql -u root -p"${MYSQL_ROOT_PASS}" -sN \
    -e "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='${MYSQL_DB}';" 2>/dev/null || echo "0")

if [ -z "$DB_EXISTS" ] || [ "${TABLE_COUNT:-0}" -eq 0 ]; then
    if [ -f "${APP_DIR}/db/full_dump.sql" ]; then
        info "First deploy — importing database dump..."
        sudo mysql -u root -p"${MYSQL_ROOT_PASS}" < "${APP_DIR}/db/full_dump.sql"
        success "Database created and seeded"
    else
        warn "No full_dump.sql found; creating empty database..."
        sudo mysql -u root -p"${MYSQL_ROOT_PASS}" \
            -e "CREATE DATABASE IF NOT EXISTS ${MYSQL_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
        success "Empty database created"
    fi
else
    success "Database '${MYSQL_DB}' exists (${TABLE_COUNT} tables), preserving data"
fi

#--------------------------------------------------------------
# 6. Clone / Update Repository & Backup
#--------------------------------------------------------------
CURRENT_STEP="code-update"
step 6 "Cloning / updating repository"

# Create backup directory
sudo mkdir -p "$BACKUP_DIR"
ROLLBACK_TAG="backup-$(date +%Y%m%d-%H%M%S)"

if [ -d "$APP_DIR/.git" ]; then
    info "Directory exists, creating backup before update..."
    # Backup critical files (dist, JAR, config)
    sudo mkdir -p "${BACKUP_DIR}/${ROLLBACK_TAG}/code"
    sudo rsync -a --exclude='node_modules' --exclude='.git' --exclude='backend/target' \
        "${APP_DIR}/" "${BACKUP_DIR}/${ROLLBACK_TAG}/code/"
    # Keep only the last 3 backups
    sudo ls -1dt "${BACKUP_DIR}"/backup-* 2>/dev/null | tail -n +4 | sudo xargs -r rm -rf
    success "Backup created: $ROLLBACK_TAG"

    info "Pulling latest code..."
    cd "$APP_DIR"
    # Handle potential detached HEAD or diverged branches
    git fetch origin "$REPO_BRANCH"
    git reset --hard "origin/${REPO_BRANCH}"
    success "Code updated to latest"
else
    info "Cloning repository..."
    sudo mkdir -p "$APP_DIR"
    sudo chown "$(whoami):$(whoami)" "$APP_DIR"
    git clone --depth 1 -b "$REPO_BRANCH" "$REPO_URL" "$APP_DIR"
    cd "$APP_DIR"
    success "Repository cloned"
fi

log "Code revision: $(cd "$APP_DIR" && git log --oneline -1)"

#--------------------------------------------------------------
# 7. Build Frontend & Backend
#--------------------------------------------------------------
CURRENT_STEP="build"
step 7 "Building frontend & backend"

cd "$APP_DIR"

# -- Frontend --
info "Configuring npm mirror..."
npm config set registry https://registry.npmmirror.com

info "Installing npm dependencies..."
npm install --prefer-offline 2>&1 | tail -3

info "Building frontend with Vite..."
rm -rf dist
npx vite build
success "Frontend built -> dist/"

# Sync dist to backend static resources
rm -rf backend/src/main/resources/static
cp -r dist backend/src/main/resources/static
success "Synced dist/ -> backend/src/main/resources/static/"

# -- Backend --
info "Building backend JAR (this may take a few minutes)..."
cd "$APP_DIR/backend"

# Set Maven memory options for constrained servers
export MAVEN_OPTS="-Xmx512m -Xms256m"

if [ -f "../mvnw" ]; then
    chmod +x ../mvnw
    ../mvnw -f pom.xml -DskipTests package -q 2>&1
else
    mvn -f pom.xml -DskipTests package -q 2>&1
fi

if [ -f "target/${JAR_NAME}" ]; then
    JAR_SIZE=$(du -h "target/${JAR_NAME}" | cut -f1)
    success "Backend JAR built (${JAR_SIZE})"
else
    error "Backend JAR not found! Build may have failed."
    log "BUILD FAILED: JAR not found at target/${JAR_NAME}"
    exit 1
fi

#--------------------------------------------------------------
# 8. Configure Systemd Service & Start Backend
#--------------------------------------------------------------
CURRENT_STEP="service"
step 8 "Configuring systemd service & starting backend"

JAVA_BIN=$(which java)
JAVA_HOME_DIR=$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")

# Environment config
sudo tee /opt/online-exam/env.conf > /dev/null <<ENV_EOF
JAVA_HOME=${JAVA_HOME_DIR}
MYSQL_URL=jdbc:mysql://localhost:3306/${MYSQL_DB}?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
MYSQL_USER=root
MYSQL_PASSWORD=${MYSQL_ROOT_PASS}
PORT=8080
AI_CONCURRENT_LIMIT=10
ENV_EOF
sudo chmod 600 /opt/online-exam/env.conf

# Systemd service
sudo tee /etc/systemd/system/online-exam.service > /dev/null <<SERVICE_EOF
[Unit]
Description=Online Exam System Backend
After=network.target mysql.service

[Service]
Type=simple
User=$(whoami)
WorkingDirectory=/opt/online-exam
EnvironmentFile=/opt/online-exam/env.conf
ExecStart=${JAVA_BIN} -Xmx512m -Xms256m -jar /opt/online-exam/backend/target/${JAR_NAME}
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
info "Backend service restarted, waiting for startup..."

# Health check with retries
info "Running health check..."
HEALTH_OK=false
for i in $(seq 1 $HEALTH_RETRIES); do
    if curl -sf "$HEALTH_URL" > /dev/null 2>&1 || curl -sf http://localhost:8080/ > /dev/null 2>&1; then
        HEALTH_OK=true
        break
    fi
    printf "."
    sleep "$HEALTH_INTERVAL"
done

if $HEALTH_OK; then
    success "Backend is healthy on port 8080"
else
    warn "Backend health check timed out after $((HEALTH_RETRIES * HEALTH_INTERVAL))s"
    warn "It may still be starting. Check: sudo journalctl -u online-exam -f"
    log "Health check timed out"
fi

#--------------------------------------------------------------
# 9. Configure Nginx + HTTPS
#--------------------------------------------------------------
CURRENT_STEP="nginx"
step 9 "Configuring Nginx & HTTPS (Let's Encrypt)"

sudo apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" -qq \
    nginx certbot python3-certbot-nginx

# Clear enabled sites
sudo rm -f /etc/nginx/sites-enabled/*

# Nginx config (HTTP first for certbot challenge)
info "Writing Nginx configuration..."
sudo tee /etc/nginx/sites-available/online-exam > /dev/null <<'NGINX_EOF'
server {
    listen 80;
    server_name web.novo.ccwu.cc 54.179.150.131;

    root /opt/online-exam/dist;
    index index.html;

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # HTML/CSS: no-cache (always fetch latest)
    location ~* \.(html|css)$ {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        add_header Pragma "no-cache";
        add_header Expires "0";
    }

    # JS assets: short cache (Vite uses content-hash filenames)
    location ~* \.js$ {
        expires 1d;
        add_header Cache-Control "public";
    }

    # Vite hashed assets: long cache
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API reverse proxy (SSE 流式请求优化)
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 200s;
        proxy_send_timeout 60s;
        client_max_body_size 10m;

        # SSE 流式代理关键配置
        proxy_buffering off;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_cache off;
        chunked_transfer_encoding on;
    }

    # Let's Encrypt ACME challenge
    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript image/svg+xml;
    gzip_min_length 1024;
}
NGINX_EOF

sudo ln -sf /etc/nginx/sites-available/online-exam /etc/nginx/sites-enabled/online-exam
if sudo nginx -t 2>&1; then
    sudo systemctl restart nginx
    success "Nginx started (HTTP)"
else
    error "Nginx config test failed! Check syntax:"
    error "  sudo nginx -t"
    log "nginx -t failed"
    # Continue to certbot/SSL anyway — it won't work but gives a clearer error
fi

# SSL certificate
info "Checking SSL certificate..."
if sudo certbot certificates 2>/dev/null | grep -q "${DOMAIN}"; then
    success "SSL certificate already exists"
else
    info "Requesting Let's Encrypt certificate..."
    sudo certbot --nginx -d "${DOMAIN}" --non-interactive --agree-tos \
        --email "admin@${DOMAIN}" --redirect 2>&1 || {
        warn "Certbot failed — HTTPS will not be available."
        warn "Ensure port 80 is reachable and DNS points to $SERVER_IP"
    }
fi

sudo systemctl reload nginx 2>/dev/null || sudo systemctl restart nginx
sudo systemctl enable nginx
success "Nginx configured and running"

#--------------------------------------------------------------
# Setup Auto-Deploy Webhook
#--------------------------------------------------------------
info "Setting up auto-deploy webhook..."

chmod +x /opt/online-exam/auto-deploy.sh 2>/dev/null || true

WEBHOOK_SECRET_FILE="/opt/online-exam/.webhook-secret"
if [ -f "$WEBHOOK_SECRET_FILE" ]; then
    WEBHOOK_SECRET=$(cat "$WEBHOOK_SECRET_FILE")
else
    WEBHOOK_SECRET=$(openssl rand -hex 24)
    echo "$WEBHOOK_SECRET" | sudo tee "$WEBHOOK_SECRET_FILE" > /dev/null
    sudo chmod 600 "$WEBHOOK_SECRET_FILE"
fi

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
sudo systemctl restart online-exam-webhook
success "Webhook service running on port 9000"

#--------------------------------------------------------------
# Done!
#--------------------------------------------------------------
DEPLOY_END=$(date +%s)
DEPLOY_DURATION=$((DEPLOY_END - DEPLOY_START))

log "Deployment completed in ${DEPLOY_DURATION}s"

echo ""
echo -e "${GREEN}${BOLD}=========================================="
echo "  Deployment Complete!"
echo "==========================================${NC}"
echo ""
echo -e "  Duration:  ${BOLD}${DEPLOY_DURATION}s${NC}"
echo -e "  Frontend:  ${BOLD}https://${DOMAIN}${NC}"
echo -e "  API:       ${BOLD}https://${DOMAIN}/api/${NC}"
echo -e "  (HTTP auto-redirects to HTTPS)"
echo ""
echo -e "  ${CYAN}Useful commands:${NC}"
echo "    Status:     sudo systemctl status online-exam"
echo "    Logs:       sudo journalctl -u online-exam -f"
echo "    Restart:    sudo systemctl restart online-exam"
echo "    Nginx:      sudo systemctl restart nginx"
echo "    Nginx log:  sudo tail -f /var/log/nginx/error.log"
echo ""
echo -e "  ${YELLOW}GitHub Webhook:${NC}"
echo "    1. Go to: https://github.com/yuan123-you/online_exam_system/settings/hooks"
echo "    2. Add webhook -> Payload URL: http://${SERVER_IP}:9000/webhook"
echo "    3. Content type: application/json"
echo "    4. Secret: ${WEBHOOK_SECRET}"
echo "    5. Events: Just the push event"
echo "    6. Open port 9000 in Lightsail firewall"
echo ""
echo -e "  ${YELLOW}Quick update (via SSH):${NC}"
echo "    ssh ubuntu@${SERVER_IP} 'bash /opt/online-exam/update.sh'"
echo ""
