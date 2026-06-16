#!/bin/bash
#==============================================================
# Local Helper: Push to GitHub + SSH Update Server
# Run from your LOCAL machine (not the server)
#
# Usage:
#   bash push-and-deploy.sh              # commit all + push + deploy
#   bash push-and-deploy.sh "fix: bug"   # custom commit message
#   bash push-and-deploy.sh --skip-push  # only trigger remote update
#   bash push-and-deploy.sh --help       # show usage
#==============================================================
set -euo pipefail

SERVER="ubuntu@54.179.150.131"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BRANCH="master"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[ OK ]${NC} $*"; }
error()   { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

# Parse args
SKIP_PUSH=false
COMMIT_MSG=""

show_help() {
    echo "Usage: bash push-and-deploy.sh [OPTIONS] [\"commit message\"]"
    echo ""
    echo "Options:"
    echo "  --skip-push   Skip git commit/push, only trigger remote update"
    echo "  --help, -h    Show this help message"
    echo ""
    echo "Examples:"
    echo "  bash push-and-deploy.sh                    # auto commit message"
    echo "  bash push-and-deploy.sh \"fix: login bug\"   # custom commit message"
    echo "  bash push-and-deploy.sh --skip-push        # only update server"
}

for arg in "$@"; do
    case $arg in
        --skip-push)   SKIP_PUSH=true ;;
        --help|-h)     show_help; exit 0 ;;
        --*)           echo "Unknown option: $arg"; show_help; exit 1 ;;
        *)             COMMIT_MSG="$arg" ;;
    esac
done

cd "$PROJECT_DIR"

echo ""
echo -e "${BOLD}=========================================="
echo "  Push & Deploy"
echo "==========================================${NC}"
echo ""

#--------------------------------------------------------------
# Step 1: Git push
#--------------------------------------------------------------
if [ "$SKIP_PUSH" = false ]; then
    info "Checking local changes..."

    if [ -n "$(git status --porcelain)" ]; then
        if [ -z "$COMMIT_MSG" ]; then
            COMMIT_MSG="update: $(date '+%Y-%m-%d %H:%M')"
        fi

        info "Committing changes: $COMMIT_MSG"
        git add -A
        git commit -m "$COMMIT_MSG"
        success "Committed"
    else
        success "No local changes to commit"
    fi

    info "Pushing to origin/$BRANCH..."
    if git push origin "$BRANCH"; then
        success "Pushed to GitHub"
    else
        error "Git push failed! Check your network/auth and try again."
    fi
else
    info "Skipping git push (--skip-push)"
fi

#--------------------------------------------------------------
# Step 2: SSH into server and run update
#--------------------------------------------------------------
info "Connecting to server via SSH..."
echo ""

if ssh "$SERVER" "bash /opt/online-exam/update.sh"; then
    echo ""
    success "Remote update finished!"
else
    echo ""
    error "Remote update failed! SSH into the server to investigate."
fi

echo ""
