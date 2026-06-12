#!/bin/bash
#==============================================================
# Auto-deploy script (triggered by webhook on git push)
# Pulls latest code, rebuilds, and restarts services
#==============================================================
set -e

APP_DIR="/opt/online-exam"
LOG_FILE="${APP_DIR}/deploy.log"
LOCK_FILE="/tmp/online-exam-deploy.lock"

# Prevent concurrent deployments
if [ -f "$LOCK_FILE" ]; then
  echo "$(date -Iseconds) Another deployment is in progress, skipping." >> "$LOG_FILE"
  exit 0
fi
touch "$LOCK_FILE"
trap "rm -f $LOCK_FILE" EXIT

echo "$(date -Iseconds) === Auto-deploy started ===" >> "$LOG_FILE"

# 1. Pull latest code
cd "$APP_DIR"
echo "$(date -Iseconds) Pulling latest code..." >> "$LOG_FILE"
git pull origin master >> "$LOG_FILE" 2>&1

# 2. Copy green theme frontend files to dist/ (Nginx serves from here)
echo "$(date -Iseconds) Updating frontend files..." >> "$LOG_FILE"
mkdir -p "$APP_DIR/dist"
cp "$APP_DIR/backend/src/main/resources/static/index.html" "$APP_DIR/dist/index.html"
cp "$APP_DIR/backend/src/main/resources/static/styles.css" "$APP_DIR/dist/styles.css"
cp "$APP_DIR/backend/src/main/resources/static/app.js" "$APP_DIR/dist/app.js"
echo "$(date -Iseconds) Frontend files updated." >> "$LOG_FILE"

# 3. Rebuild backend
echo "$(date -Iseconds) Building backend..." >> "$LOG_FILE"
cd "$APP_DIR/backend"
mvn -f pom.xml -DskipTests package -q >> "$LOG_FILE" 2>&1

# 4. Restart backend service
echo "$(date -Iseconds) Restarting backend service..." >> "$LOG_FILE"
sudo systemctl restart online-exam

# 5. Reload Nginx (to pick up any changed static assets)
sudo systemctl reload nginx 2>/dev/null || true

echo "$(date -Iseconds) === Auto-deploy completed ===" >> "$LOG_FILE"
