#!/bin/bash
#==============================================================
# Online Exam System - Update Script
# Pulls latest code from GitHub and rebuilds/restarts services
#==============================================================
set -e

APP_DIR="/opt/online-exam"

echo "=========================================="
echo "  Online Exam System - Update"
echo "=========================================="

# 1. Pull latest code
echo ""
echo "[1/4] Pulling latest code from GitHub..."
cd "$APP_DIR"
git pull origin master

# 2. Rebuild frontend
echo ""
echo "[2/4] Rebuilding frontend..."
cd "$APP_DIR"
npm install
npx vite build
echo "  Frontend updated."

# 3. Rebuild backend
echo ""
echo "[3/4] Rebuilding backend..."
cd "$APP_DIR/backend"
mvn -f pom.xml -DskipTests package -q
echo "  Backend JAR rebuilt."

# 4. Restart backend service
echo ""
echo "[4/4] Restarting backend service..."
sudo systemctl restart online-exam

echo ""
echo "=========================================="
echo "  Update Complete!"
echo "=========================================="
echo ""
echo "  Check status:  sudo systemctl status online-exam"
echo "  View logs:     sudo journalctl -u online-exam -f"
echo ""
