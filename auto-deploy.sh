#!/bin/bash
#==============================================================
# Auto-deploy script (triggered by webhook on git push)
# Pulls latest code, rebuilds, and restarts services
#
# Features:
#   - Concurrent deploy prevention (lock file)
#   - Automatic backup & rollback on failure
#   - Health check after restart
#   - Structured logging
#==============================================================
set -euo pipefail

APP_DIR="/opt/online-exam"
BACKUP_DIR="/opt/online-exam-backups"
LOG_FILE="${APP_DIR}/deploy.log"
LOCK_FILE="/tmp/online-exam-deploy.lock"
JAR_NAME="online-exam-backend-1.0.0.jar"
HEALTH_URL="http://localhost:8080/api/health"
HEALTH_RETRIES=12
HEALTH_INTERVAL=5

timestamp() { date '+%Y-%m-%d %H:%M:%S'; }
log() {
    local msg="[$(timestamp)] [auto-deploy] $*"
    echo "$msg" >> "$LOG_FILE"
    echo "$msg"
}

#--------------------------------------------------------------
# Prevent concurrent deployments
#--------------------------------------------------------------
if [ -f "$LOCK_FILE" ]; then
    LOCK_PID=$(cat "$LOCK_FILE" 2>/dev/null || echo "")
    if [ -n "$LOCK_PID" ] && kill -0 "$LOCK_PID" 2>/dev/null; then
        log "Another deployment is in progress (PID: $LOCK_PID), skipping."
        exit 0
    else
        log "Stale lock file found, removing."
        rm -f "$LOCK_FILE"
    fi
fi
echo $$ > "$LOCK_FILE"
trap 'rm -f "$LOCK_FILE"' EXIT

log "=== Auto-deploy started ==="

#--------------------------------------------------------------
# Backup before update
#--------------------------------------------------------------
ROLLBACK_TAG="autodeploy-$(date +%Y%m%d-%H%M%S)"
sudo mkdir -p "${BACKUP_DIR}/${ROLLBACK_TAG}"
sudo chown "$(whoami):$(whoami)" "${BACKUP_DIR}/${ROLLBACK_TAG}" 2>/dev/null || true

[ -d "$APP_DIR/dist" ] && cp -r "$APP_DIR/dist" "${BACKUP_DIR}/${ROLLBACK_TAG}/dist" || true
[ -f "$APP_DIR/backend/target/${JAR_NAME}" ] && \
    cp "$APP_DIR/backend/target/${JAR_NAME}" "${BACKUP_DIR}/${ROLLBACK_TAG}/" || true

# Keep only last 5 auto-deploy backups
sudo ls -1dt "${BACKUP_DIR}"/autodeploy-* 2>/dev/null | tail -n +6 | sudo xargs -r rm -rf 2>/dev/null || true
log "Backup created: $ROLLBACK_TAG"

#--------------------------------------------------------------
# Rollback function
#--------------------------------------------------------------
do_rollback() {
    log "ROLLBACK: restoring from $ROLLBACK_TAG"
    if [ -d "${BACKUP_DIR}/${ROLLBACK_TAG}/dist" ]; then
        rm -rf "$APP_DIR/dist"
        sudo cp -r "${BACKUP_DIR}/${ROLLBACK_TAG}/dist" "$APP_DIR/dist"
        sudo chown -R "$(whoami):$(whoami)" "$APP_DIR/dist" 2>/dev/null || true
    fi
    if [ -f "${BACKUP_DIR}/${ROLLBACK_TAG}/${JAR_NAME}" ]; then
        mkdir -p "$APP_DIR/backend/target"
        sudo cp "${BACKUP_DIR}/${ROLLBACK_TAG}/${JAR_NAME}" "$APP_DIR/backend/target/${JAR_NAME}"
    fi
    sudo systemctl restart online-exam 2>/dev/null || true
    sudo systemctl reload nginx 2>/dev/null || true
    log "ROLLBACK complete"
}

#--------------------------------------------------------------
# 1. Pull latest code
#--------------------------------------------------------------
cd "$APP_DIR"
log "Pulling latest code..."
PREV_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
git fetch origin master
git reset --hard origin/master
NEW_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

if [ "$PREV_COMMIT" = "$NEW_COMMIT" ]; then
    log "Already up to date ($NEW_COMMIT), nothing to deploy."
    exit 0
fi
log "Code updated: $PREV_COMMIT -> $NEW_COMMIT"

#--------------------------------------------------------------
# 2. Build frontend
#--------------------------------------------------------------
log "Building frontend..."
cd "$APP_DIR"
npm config set registry https://registry.npmmirror.com
npm install --prefer-offline >> "$LOG_FILE" 2>&1
rm -rf dist

if ! npx vite build >> "$LOG_FILE" 2>&1; then
    log "ERROR: Frontend build failed! Rolling back."
    do_rollback
    exit 1
fi
log "Frontend built"

# Sync dist to backend static dir
rm -rf backend/src/main/resources/static
cp -r dist backend/src/main/resources/static
log "Dist synced to backend static dir"

#--------------------------------------------------------------
# 3. Build backend
#--------------------------------------------------------------
log "Building backend..."
cd "$APP_DIR/backend"
export MAVEN_OPTS="-Xmx512m -Xms256m"

if ! mvn -f pom.xml -DskipTests package -q >> "$LOG_FILE" 2>&1; then
    log "ERROR: Backend build failed! Rolling back."
    do_rollback
    exit 1
fi

if [ ! -f "target/${JAR_NAME}" ]; then
    log "ERROR: JAR not found after build! Rolling back."
    do_rollback
    exit 1
fi
log "Backend JAR built"

#--------------------------------------------------------------
# 4. Restart backend service
#--------------------------------------------------------------
log "Restarting backend service..."
sudo systemctl restart online-exam

# Health check
HEALTH_OK=false
for i in $(seq 1 $HEALTH_RETRIES); do
    if curl -sf "$HEALTH_URL" > /dev/null 2>&1 || curl -sf http://localhost:8080/ > /dev/null 2>&1; then
        HEALTH_OK=true
        break
    fi
    sleep "$HEALTH_INTERVAL"
done

if $HEALTH_OK; then
    log "Backend health check passed"
else
    log "ERROR: Health check failed after $((HEALTH_RETRIES * HEALTH_INTERVAL))s! Rolling back."
    do_rollback
    exit 1
fi

#--------------------------------------------------------------
# 5. Reload Nginx
#--------------------------------------------------------------
sudo systemctl reload nginx 2>/dev/null || true
log "Nginx reloaded"

log "=== Auto-deploy completed ($PREV_COMMIT -> $NEW_COMMIT) ==="
