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

# 2. Frontend is static (green theme in backend/src/main/resources/static/)
#    No build step needed — files are served directly by Nginx
echo "$(date -Iseconds) Frontend: using static files (no build needed)" >> "$LOG_FILE"

# 3. Rebuild backend
echo "$(date -Iseconds) Building backend..." >> "$LOG_FILE"
cd "$APP_DIR/backend"
mvn -f pom.xml -DskipTests package -q >> "$LOG_FILE" 2>&1

# 4. Restart backend service
echo "$(date -Iseconds) Restarting backend service..." >> "$LOG_FILE"
sudo systemctl restart online-exam

# 5. Update Nginx config: change root to green theme static files
echo "$(date -Iseconds) Updating Nginx root path..." >> "$LOG_FILE"
NGINX_CONF="/etc/nginx/sites-available/online-exam"
if [ -f "$NGINX_CONF" ]; then
  # Replace the root directive to point to green theme static files
  sudo sed -i 's|root /opt/online-exam/dist;|root /opt/online-exam/backend/src/main/resources/static;|g' "$NGINX_CONF"
  # Remove the /assets/ caching block (Vite-specific, not needed)
  sudo sed -i '/location \/assets\//,/^    }/d' "$NGINX_CONF"
  sudo nginx -t >> "$LOG_FILE" 2>&1 && echo "$(date -Iseconds) Nginx config OK" >> "$LOG_FILE"
fi

# 6. Reload Nginx
sudo systemctl reload nginx 2>/dev/null || true

echo "$(date -Iseconds) === Auto-deploy completed ===" >> "$LOG_FILE"
