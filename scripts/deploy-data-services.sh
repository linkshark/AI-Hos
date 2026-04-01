#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE="${DATA_CONFIG_FILE:-./config/application-online.yml}"

yaml_value_in_block() {
  local file="$1"
  local block_indent="$2"
  local block_name="$3"
  local key_indent="$4"
  local key_name="$5"
  awk -v block_indent="$block_indent" -v block_name="$block_name" -v key_indent="$key_indent" -v key_name="$key_name" '
    $0 ~ ("^" block_indent block_name ":") {in_block=1; next}
    in_block && $0 ~ ("^" key_indent key_name ":") {
      sub("^" key_indent key_name ": ", "", $0)
      print $0
      exit
    }
    in_block && $0 ~ "^[^[:space:]]" {in_block=0}
  ' "$file"
}

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "missing data config yaml: $CONFIG_FILE" >&2
  exit 1
fi

MYSQL_URL="$(yaml_value_in_block "$CONFIG_FILE" '  ' 'datasource' '    ' 'url')"
MYSQL_APP_USERNAME="$(yaml_value_in_block "$CONFIG_FILE" '  ' 'datasource' '    ' 'username')"
MYSQL_APP_PASSWORD="$(yaml_value_in_block "$CONFIG_FILE" '  ' 'datasource' '    ' 'password')"
MONGO_URI="$(yaml_value_in_block "$CONFIG_FILE" '    ' 'mongodb' '      ' 'uri')"
REDIS_USERNAME="$(yaml_value_in_block "$CONFIG_FILE" '    ' 'redis' '      ' 'username')"
REDIS_HOST_PORT="$(yaml_value_in_block "$CONFIG_FILE" '    ' 'redis' '      ' 'port')"
REDIS_PASSWORD="$(yaml_value_in_block "$CONFIG_FILE" '    ' 'redis' '      ' 'password')"

MYSQL_DATABASE="${MYSQL_URL#*//*/}"
MYSQL_DATABASE="${MYSQL_DATABASE%%\?*}"
MYSQL_HOST_PORT="${MYSQL_URL#*://*:}"
MYSQL_HOST_PORT="${MYSQL_HOST_PORT%%/*}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-$MYSQL_APP_PASSWORD}"
TZ_NAME="${TZ_NAME:-Asia/Shanghai}"

MONGO_AUTH="${MONGO_URI#mongodb://}"
MONGO_AUTH="${MONGO_AUTH%%@*}"
MONGO_ROOT_USERNAME="${MONGO_AUTH%%:*}"
MONGO_ROOT_PASSWORD="${MONGO_AUTH#*:}"
MONGO_HOST_PORT="${MONGO_URI#*@*:}"
MONGO_HOST_PORT="${MONGO_HOST_PORT%%/*}"

docker rm -f aimed-mariadb aimed-mongo aimed-redis >/dev/null 2>&1 || true

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

docker run -d \
  --name aimed-redis \
  --restart unless-stopped \
  -p "${REDIS_HOST_PORT}:6379" \
  -e TZ="${TZ_NAME}" \
  -v ai-hos_aimed-online-redis-data:/data \
  -v /etc/localtime:/etc/localtime:ro \
  -v /etc/timezone:/etc/timezone:ro \
  redis:7-alpine \
  redis-server --appendonly yes --requirepass "${REDIS_PASSWORD}"

echo "started aimed-mariadb on ${MYSQL_HOST_PORT}"
echo "started aimed-mongo on ${MONGO_HOST_PORT}"
echo "started aimed-redis on ${REDIS_HOST_PORT}"
