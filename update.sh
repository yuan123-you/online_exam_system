#!/bin/bash
#==============================================================
# Online Exam System - Quick Update Script
# Use after initial deployment to pull latest code and rebuild
#
# Usage:
#   bash /opt/online-exam/update.sh              # full update
#   bash /opt/online-exam/update.sh --frontend   # frontend only
#   bash /opt/online-exam/update.sh --backend    # backend only
#==============================================================
set -euo pipefail

#--------------------------------------------------------------
# Configuration
#--------------------------------------------------------------
APP_DIR="/opt/online-exam"
BACKUP_DIR="/opt/online-exam-backups"
JAR_NAME="online-exam-backend-1.0.0.jar"
HEALTH_URL="http://localhost:8080/api/health"
HEALTH_RETRIES=12
HEALTH_INTERVAL=5
LOCK_FILE="${APP_DIR}/.update.lock"
DEPLOY_LOG="${APP_DIR}/update.log"
MIN_DISK_MB=1024

# Colors
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

info()    { echo -e "${BLUE}[INFO]${NC}  $*"; log "INFO  $*"; }
success() { echo -e "${GREEN}[ OK ]${NC} $*"; log "OK    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; log "WARN  $*"; }
error()   { echo -e "${RED}[FAIL]${NC} $*"; log "FAIL  $*"; }

timestamp() { date '+%Y-%m-%d %H:%M:%S'; }
log() {
    local msg="[$(timestamp)] $*"
    echo "$msg" >> "$DEPLOY_LOG" 2>/dev/null || true
}

#--------------------------------------------------------------
# Parse arguments
#--------------------------------------------------------------
UPDATE_FRONTEND=true
UPDATE_BACKEND=true

for arg in "$@"; do
    case $arg in
        --frontend) UPDATE_FRONTEND=true;  UPDATE_BACKEND=false ;;
        --backend)  UPDATE_FRONTEND=false; UPDATE_BACKEND=true  ;;
        --help|-h)
            echo "Usage: bash update.sh [--frontend|--backend]"
            echo "  (no args)  Update both frontend and backend"
            echo "  --frontend Update frontend only"
            echo "  --backend  Update backend only"
            exit 0
            ;;
        *) warn "Unknown option: $arg" ;;
    esac
done

#--------------------------------------------------------------
# Pre-flight: disk space check
#--------------------------------------------------------------
avail_mb=$(df -BM / | awk 'NR==2 {gsub("M",""); print $4}')
if [ "${avail_mb:-0}" -lt "$MIN_DISK_MB" ]; then
    echo -e "${RED}[FAIL] Insufficient disk space: ${avail_mb}MB available, need at least ${MIN_DISK_MB}MB${NC}"
    exit 1
fi

#--------------------------------------------------------------
# Pre-flight: environment check
#--------------------------------------------------------------
check_cmd() {
    if ! command -v "$1" &> /dev/null; then
        echo -e "${RED}[FAIL] Required command '$1' not found. Please install it first.${NC}"
        exit 1
    fi
}

if [ "$UPDATE_FRONTEND" = true ]; then
    check_cmd node
    check_cmd npm
fi
if [ "$UPDATE_BACKEND" = true ]; then
    check_cmd java
    check_cmd mvn
fi

#--------------------------------------------------------------
# Deployment lock (prevent concurrent updates)
#--------------------------------------------------------------
if [ -f "$LOCK_FILE" ]; then
    LOCK_PID=$(cat "$LOCK_FILE" 2>/dev/null || echo "")
    if [ -n "$LOCK_PID" ] && kill -0 "$LOCK_PID" 2>/dev/null; then
        error "Another update is running (PID: $LOCK_PID). Aborting."
        exit 1
    else
        warn "Stale lock file found, removing."
        rm -f "$LOCK_FILE"
    fi
fi
echo $$ > "$LOCK_FILE"
chmod 600 "$LOCK_FILE"

# Initialize variables used in trap before registering it
ROLLBACK_TAG=""
PREV_COMMIT=""
DEPLOY_FAILED=false

#--------------------------------------------------------------
# Rollback function (must be defined before trap)
#--------------------------------------------------------------
do_rollback() {
    warn "Attempting rollback to $ROLLBACK_TAG..."
    # Restore code to previous commit
    if [ -n "$PREV_COMMIT" ] && [ "$PREV_COMMIT" != "unknown" ]; then
        cd "$APP_DIR"
        git reset --hard "$PREV_COMMIT" 2>/dev/null || true
        info "Restored code to commit ${PREV_COMMIT}"
    fi
    if [ -d "$BACKUP_DIR/${ROLLBACK_TAG}/dist" ]; then
        sudo rm -rf "$APP_DIR/dist"
        sudo cp -r "$BACKUP_DIR/${ROLLBACK_TAG}/dist" "$APP_DIR/dist"
        sudo chown -R "$(whoami):$(whoami)" "$APP_DIR/dist"
        info "Restored dist/"
    fi
    if [ -f "$BACKUP_DIR/${ROLLBACK_TAG}/${JAR_NAME}" ]; then
        mkdir -p "$APP_DIR/backend/target"
        sudo cp "$BACKUP_DIR/${ROLLBACK_TAG}/${JAR_NAME}" "$APP_DIR/backend/target/${JAR_NAME}"
        info "Restored JAR"
    fi
    sudo systemctl restart online-exam 2>/dev/null || true
    sudo systemctl reload nginx 2>/dev/null || true
    warn "Rollback complete. Verify manually."
}

cleanup() {
    local exit_code=$?
    rm -f "$LOCK_FILE"
    if [ $exit_code -ne 0 ] && [ "$DEPLOY_FAILED" = false ]; then
        DEPLOY_FAILED=true
        error "Unexpected error (exit code: $exit_code)"
        if [ -n "$ROLLBACK_TAG" ]; then
            do_rollback
        fi
    fi
}
trap cleanup EXIT

UPDATE_START=$(date +%s)

echo ""
echo -e "${BOLD}=========================================="
echo "  Online Exam System - Update"
echo "  Time: $(timestamp)"
echo -e "==========================================${NC}"
echo ""
echo -e "  Scope: ${CYAN}$([ "$UPDATE_FRONTEND" = true ] && echo -n "Frontend " )$([ "$UPDATE_BACKEND" = true ] && echo -n "Backend")${NC}"
echo -e "  Disk:  ${CYAN}${avail_mb}MB available${NC}"
echo ""

# Step counting: preflight(1) + backup(1) + pull(1) + frontend steps + backend steps + restart(1)
TOTAL_STEPS=3  # preflight + backup + pull always run
[ "$UPDATE_FRONTEND" = true ] && TOTAL_STEPS=$((TOTAL_STEPS + 3))  # npm + build + sync
[ "$UPDATE_BACKEND"  = true ] && TOTAL_STEPS=$((TOTAL_STEPS + 2))  # build jar + health check
TOTAL_STEPS=$((TOTAL_STEPS + 1))  # restart services
CURRENT=0

next_step() {
    CURRENT=$((CURRENT + 1))
    echo -e "\n${CYAN}[${CURRENT}/${TOTAL_STEPS}]${NC} ${BOLD}$1${NC}"
}

#--------------------------------------------------------------
# Pre-flight: environment info
#--------------------------------------------------------------
next_step "Checking environment..."
if [ "$UPDATE_FRONTEND" = true ]; then
    info "Node: $(node -v) | npm: $(npm -v)"
fi
if [ "$UPDATE_BACKEND" = true ]; then
    info "Java: $(java -version 2>&1 | head -1)"
    info "Maven: $(mvn -version 2>&1 | head -1)"
fi
success "Environment OK (disk: ${avail_mb}MB)"

#--------------------------------------------------------------
# Backup
#--------------------------------------------------------------
ROLLBACK_TAG="update-$(date +%Y%m%d-%H%M%S)"
next_step "Creating backup..."

sudo mkdir -p "$BACKUP_DIR/${ROLLBACK_TAG}"
sudo chown "$(whoami):$(whoami)" "$BACKUP_DIR/${ROLLBACK_TAG}"
chmod 700 "$BACKUP_DIR/${ROLLBACK_TAG}"

if [ "$UPDATE_FRONTEND" = true ] && [ -d "$APP_DIR/dist" ]; then
    cp -r "$APP_DIR/dist" "$BACKUP_DIR/${ROLLBACK_TAG}/dist"
    info "Backed up dist/"
fi
if [ "$UPDATE_BACKEND" = true ] && [ -f "$APP_DIR/backend/target/${JAR_NAME}" ]; then
    cp "$APP_DIR/backend/target/${JAR_NAME}" "$BACKUP_DIR/${ROLLBACK_TAG}/"
    info "Backed up JAR"
fi
# Keep only last 5 backups
sudo ls -1dt "${BACKUP_DIR}"/update-* 2>/dev/null | tail -n +6 | sudo xargs -r rm -rf 2>/dev/null || true
success "Backup: $ROLLBACK_TAG"

#--------------------------------------------------------------
# Pull latest code
#--------------------------------------------------------------
next_step "Pulling latest code from GitHub..."
cd "$APP_DIR"

# Fix permissions on static dir (may be owned by root from previous sudo cp)
sudo chown -R "$(whoami):$(whoami)" "$APP_DIR/backend/src/main/resources/static" 2>/dev/null || true

PREV_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

# Check for local changes before force-pulling
LOCAL_CHANGES=$(git status --porcelain 2>/dev/null || true)
if [ -n "$LOCAL_CHANGES" ]; then
    warn "Local changes detected, stashing before pull..."
    git stash
fi

git fetch origin master
git reset --hard origin/master
NEW_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

if [ "$PREV_COMMIT" = "$NEW_COMMIT" ]; then
    success "Already up to date (${NEW_COMMIT})"
else
    success "Updated: ${PREV_COMMIT} -> ${NEW_COMMIT}"
fi

#--------------------------------------------------------------
# Build Frontend
#--------------------------------------------------------------
if [ "$UPDATE_FRONTEND" = true ]; then
    next_step "Installing npm dependencies..."
    cd "$APP_DIR"
    npm config set registry https://registry.npmmirror.com
    if npm install --prefer-offline >> "$DEPLOY_LOG" 2>&1; then
        success "Dependencies installed"
    else
        error "npm install failed! Check $DEPLOY_LOG for details."
        DEPLOY_FAILED=true
        do_rollback
        exit 1
    fi

    next_step "Building frontend..."
    sudo rm -rf dist
    if npx vite build >> "$DEPLOY_LOG" 2>&1; then
        success "Frontend built -> dist/"
    else
        error "Frontend build failed! Check $DEPLOY_LOG for details."
        DEPLOY_FAILED=true
        do_rollback
        exit 1
    fi

    next_step "Syncing dist to backend static resources..."
    sudo rm -rf backend/src/main/resources/static
    cp -r dist backend/src/main/resources/static
    # Also update the standalone dist directory for Nginx to serve
    success "Synced dist/ -> backend/src/main/resources/static/"
fi

#--------------------------------------------------------------
# Build Backend
#--------------------------------------------------------------
if [ "$UPDATE_BACKEND" = true ]; then
    next_step "Building backend JAR..."
    cd "$APP_DIR/backend"
    export MAVEN_OPTS="-Xmx512m -Xms256m"

    if mvn -f pom.xml -DskipTests package >> "$DEPLOY_LOG" 2>&1; then
        if [ -f "target/${JAR_NAME}" ]; then
            JAR_SIZE=$(du -h "target/${JAR_NAME}" | cut -f1)
            success "Backend JAR built (${JAR_SIZE})"
        else
            error "JAR not found after build! Check $DEPLOY_LOG for details."
            DEPLOY_FAILED=true
            do_rollback
            exit 1
        fi
    else
        error "Backend build failed! Check $DEPLOY_LOG for details."
        DEPLOY_FAILED=true
        do_rollback
        exit 1
    fi
fi

#--------------------------------------------------------------
# Restart services
#--------------------------------------------------------------
next_step "Restarting services..."

if [ "$UPDATE_BACKEND" = true ]; then
    sudo systemctl restart online-exam
    info "Backend restarting..."

    # Health check
    info "Running health check"
    HEALTH_OK=false
    for i in $(seq 1 $HEALTH_RETRIES); do
        if curl -sf "$HEALTH_URL" > /dev/null 2>&1 || curl -sf http://localhost:8080/ > /dev/null 2>&1; then
            HEALTH_OK=true
            break
        fi
        printf "."
        sleep "$HEALTH_INTERVAL"
    done
    echo ""

    if $HEALTH_OK; then
        success "Backend is healthy"
    else
        error "Health check failed after $((HEALTH_RETRIES * HEALTH_INTERVAL))s"
        DEPLOY_FAILED=true
        do_rollback
        exit 1
    fi
fi

# Reload Nginx (picks up new static files)
sudo systemctl reload nginx 2>/dev/null || sudo systemctl restart nginx 2>/dev/null || true
success "Nginx reloaded"

#--------------------------------------------------------------
# Done
#--------------------------------------------------------------
UPDATE_END=$(date +%s)
UPDATE_DURATION=$((UPDATE_END - UPDATE_START))

echo ""
echo -e "${GREEN}${BOLD}=========================================="
echo "  Update Complete! (${UPDATE_DURATION}s)"
echo -e "==========================================${NC}"
echo ""
echo -e "  Commit:  ${BOLD}${PREV_COMMIT} -> ${NEW_COMMIT}${NC}"
echo -e "  Status:  ${GREEN}running${NC}"
echo -e "  Log:     ${CYAN}${DEPLOY_LOG}${NC}"
echo ""
echo -e "  ${CYAN}Quick checks:${NC}"
echo "    curl https://web.novo.ccwu.cc/api/health"
echo "    sudo systemctl status online-exam"
echo "    sudo journalctl -u online-exam -f --no-pager -n 20"
echo ""
