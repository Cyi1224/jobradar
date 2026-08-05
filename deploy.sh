#!/bin/bash
# JobRadar 一键部署脚本
# 用法：本地执行  bash deploy.sh
# 作用：推送代码 → SSH 到服务器 → 拉取重启

set -e

SERVER_IP="123.207.41.105"
SERVER_USER="ubuntu"
SERVER_PASS="~2zFGxk{)AY/7"
PROJECT_DIR="/home/ubuntu/jobradar"

echo "=== JobRadar 部署 ==="

# 1. 推送到 GitHub
echo "[1/3] 推送到 GitHub..."
git push origin main

# 2. SSH 到服务器拉取代码
echo "[2/3] 服务器拉取更新..."
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" \
  "cd $PROJECT_DIR && sudo git pull" 2>/dev/null || {
    echo "  需要安装 sshpass，或用其他方式连接"
    exit 1
  }

# 3. 服务器重启 Docker
echo "[3/3] 服务器重启服务..."
sshpass -p "$SERVER_PASS" ssh "$SERVER_USER@$SERVER_IP" \
  "cd $PROJECT_DIR && sudo docker compose up -d --build" 2>/dev/null

echo "=== 部署完成 ==="
echo "https://jobradar.xin"
