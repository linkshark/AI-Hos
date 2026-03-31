#!/usr/bin/env bash
set -euo pipefail

# 独立数据容器优先读取当前目录下的 .env/.env.online，避免重建后与应用配置脱节。
if [[ -f ".env.online" ]]; then
  set -a
  source .env.online
  set +a
elif [[ -f ".env" ]]; then
  set -a
  source .env
  set +a
fi

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-CHANGE_ME_DB_PASSWORD}"
MYSQL_DATABASE="${MYSQL_DATABASE:-aimed}"
MYSQL_APP_USERNAME="${MYSQL_APP_USERNAME:-shark}"
MYSQL_APP_PASSWORD="${MYSQL_APP_PASSWORD:-CHANGE_ME_DB_PASSWORD}"
MYSQL_HOST_PORT="${MYSQL_HOST_PORT:-13306}"
TZ_NAME="${TZ_NAME:-Asia/Shanghai}"

MONGO_ROOT_USERNAME="${MONGO_ROOT_USERNAME:-shark}"
MONGO_ROOT_PASSWORD="${MONGO_ROOT_PASSWORD:-CHANGE_ME_DB_PASSWORD}"
MONGO_HOST_PORT="${MONGO_HOST_PORT:-27018}"

docker rm -f aimed-mariadb aimed-mongo >/dev/null 2>&1 || true

docker run -d \
  --name aimed-mariadb \
  --restart unless-stopped \
  -p "${MYSQL_HOST_PORT}:3306" \
  -e MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}" \
  -e MYSQL_DATABASE="${MYSQL_DATABASE}" \
  -e MYSQL_USER="${MYSQL_APP_USERNAME}" \
  -e MYSQL_PASSWORD="${MYSQL_APP_PASSWORD}" \
  -e TZ="${TZ_NAME}" \
  -v ai-hos_aimed-online-mysql-data:/var/lib/mysql \
  -v "$(pwd)/sql/init_aimed.sql:/docker-entrypoint-initdb.d/01-init_aimed.sql:ro" \
  -v /etc/localtime:/etc/localtime:ro \
  -v /etc/timezone:/etc/timezone:ro \
  mariadb:10.11 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci

docker run -d \
  --name aimed-mongo \
  --restart unless-stopped \
  -p "${MONGO_HOST_PORT}:27017" \
  -e MONGO_INITDB_ROOT_USERNAME="${MONGO_ROOT_USERNAME}" \
  -e MONGO_INITDB_ROOT_PASSWORD="${MONGO_ROOT_PASSWORD}" \
  -e TZ="${TZ_NAME}" \
  -v ai-hos_aimed-online-mongo-data:/data/db \
  -v /etc/localtime:/etc/localtime:ro \
  -v /etc/timezone:/etc/timezone:ro \
  mongo:4.4

echo "started aimed-mariadb on ${MYSQL_HOST_PORT}"
echo "started aimed-mongo on ${MONGO_HOST_PORT}"
