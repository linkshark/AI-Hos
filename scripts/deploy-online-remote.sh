#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${BASH_SOURCE[0]:-}" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
elif [[ -n "${0:-}" && "$0" == */* ]]; then
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
else
  SCRIPT_DIR="${PWD}/scripts"
fi
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
HOST="${DEPLOY_HOST:-shenchaoqi.x3322.net}"
PORT="${DEPLOY_PORT:-13245}"
USER_NAME="${DEPLOY_USER:-shark}"
REMOTE_DIR="${DEPLOY_REMOTE_DIR:-/home/shark/AI-Hos}"
CONFIG_FILE="${DEPLOY_CONFIG_FILE:-$ROOT_DIR/config/application-online.yml}"
MYSQL_CONTAINER="${DEPLOY_MYSQL_CONTAINER:-aimed-mariadb}"
if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "missing online yaml: $CONFIG_FILE" >&2
  echo "expected: $ROOT_DIR/config/application-online.yml" >&2
  exit 1
fi

command -v ruby >/dev/null 2>&1 || {
  echo "ruby not found on local machine; this script uses ruby/psych to parse yaml" >&2
  exit 1
}
command -v rsync >/dev/null 2>&1 || {
  echo "rsync not found on local machine" >&2
  exit 1
}
command -v ssh >/dev/null 2>&1 || {
  echo "ssh not found on local machine" >&2
  exit 1
}

echo "==> validate online config"
eval "$(
  ruby - "$CONFIG_FILE" <<'RUBY'
require "yaml"
require "uri"
require "shellwords"

path = ARGV.fetch(0)
cfg = YAML.load_file(path) || {}

def require_value(value, label)
  raise "invalid online yaml: missing #{label}" if value.nil? || value.to_s.strip.empty?
  value
end

app = cfg.fetch("app", {})
spring = cfg.fetch("spring", {})
spring_data = spring.fetch("data", {})

chroma_url = require_value(app.dig("chroma", "base-url"), "app.chroma.base-url")
online_api_key = require_value(app.dig("online-chat", "api-key"), "app.online-chat.api-key")
embedding_base_url = require_value(app.dig("embedding", "base-url"), "app.embedding.base-url")
embedding_model_name = require_value(app.dig("embedding", "model-name"), "app.embedding.model-name")
vision_api_key = require_value(app.dig("vision-chat", "api-key"), "app.vision-chat.api-key")
datasource_url = require_value(spring.dig("datasource", "url"), "spring.datasource.url")
datasource_username = require_value(spring.dig("datasource", "username"), "spring.datasource.username")
datasource_password = require_value(spring.dig("datasource", "password"), "spring.datasource.password")
mongodb_uri = require_value(spring_data.dig("mongodb", "uri"), "spring.data.mongodb.uri")
redis_host = require_value(spring_data.dig("redis", "host"), "spring.data.redis.host")
redis_port = require_value(spring_data.dig("redis", "port"), "spring.data.redis.port")
redis_password = require_value(spring_data.dig("redis", "password"), "spring.data.redis.password")
mail_host = require_value(spring.dig("mail", "host"), "spring.mail.host")
mail_username = require_value(spring.dig("mail", "username"), "spring.mail.username")
mail_password = require_value(spring.dig("mail", "password"), "spring.mail.password")

jdbc_uri = URI(datasource_url.sub(/^jdbc:/, ""))
mongodb_main = mongodb_uri.sub(%r{^mongodb://}, "").split("/").first.to_s
mongodb_host_port = mongodb_main.split("@").last.to_s
mongo_host, mongo_port = mongodb_host_port.split(":", 2)
chroma_uri = URI(chroma_url)

db_name = jdbc_uri.path.to_s.sub(%r{^/}, "")
mysql_host = jdbc_uri.host.to_s
mysql_port = jdbc_uri.port.to_s
mongo_port = mongo_port.to_s
chroma_port = chroma_uri.port.to_s

raise "invalid online yaml: unresolved spring.datasource database name" if db_name.empty?
raise "invalid online yaml: unresolved spring.datasource port" if mysql_port.empty?
raise "invalid online yaml: unresolved spring.data.mongodb port" if mongo_port.empty?
raise "invalid online yaml: unresolved app.chroma.base-url port" if chroma_port.empty?

{
  "EMBEDDING_MODEL_NAME" => embedding_model_name,
  "MYSQL_USERNAME" => datasource_username,
  "MYSQL_PASSWORD" => datasource_password,
  "MYSQL_DATABASE" => db_name,
  "MYSQL_PORT" => mysql_port,
  "MONGO_PORT" => mongo_port,
  "REDIS_PORT" => redis_port.to_s,
  "CHROMA_PORT" => chroma_port
}.each do |key, value|
  puts "#{key}=#{Shellwords.shellescape(value.to_s)}"
end

warn "online yaml OK"
RUBY
)"

if [[ "${DEPLOY_VALIDATE_ONLY:-0}" == "1" ]]; then
  echo "==> validate only; skip remote deploy"
  exit 0
fi

CONTROL_PATH="$(mktemp -u "${TMPDIR:-/tmp}/aimed-ssh-ctl.XXXXXX")"

cleanup() {
  if [[ -n "${MASTER_STARTED:-}" ]]; then
    ssh -O exit -o StrictHostKeyChecking=no -o ControlPath="$CONTROL_PATH" -p "$PORT" "$REMOTE" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

REMOTE="$USER_NAME@$HOST"
COMMON_SSH_OPTS=(
  -o StrictHostKeyChecking=no
  -o ControlMaster=auto
  -o ControlPersist=10m
  -o ControlPath="$CONTROL_PATH"
  -p "$PORT"
)
SSH_CMD=(ssh "${COMMON_SSH_OPTS[@]}")
RSYNC_SSH="ssh -o StrictHostKeyChecking=no -o ControlMaster=auto -o ControlPersist=10m -o ControlPath=$CONTROL_PATH -p $PORT"

echo "==> open ssh control connection"
ssh -MNf "${COMMON_SSH_OPTS[@]}" "$REMOTE"
MASTER_STARTED=1

echo "==> ensure remote directory"
"${SSH_CMD[@]}" "$REMOTE" "mkdir -p '$REMOTE_DIR' '$REMOTE_DIR/config' '$REMOTE_DIR/data/knowledge-base'"

echo "==> sync project"
rsync -az --delete \
  --exclude '.git' \
  --exclude 'target' \
  --exclude 'data' \
  --exclude '.idea' \
  --exclude '.vscode' \
  --exclude '.DS_Store' \
  --exclude '.secrets' \
  --exclude 'config/application-local.yml' \
  --exclude 'config/application-online.yml' \
  --exclude 'aimed-ui/node_modules' \
  -e "$RSYNC_SSH" \
  "$ROOT_DIR/" "$REMOTE:$REMOTE_DIR/"

echo "==> sync knowledge-base files"
rsync -az --delete \
  -e "$RSYNC_SSH" \
  "$ROOT_DIR/data/knowledge-base/" "$REMOTE:$REMOTE_DIR/data/knowledge-base/"

echo "==> upload online config"
rsync -az -e "$RSYNC_SSH" "$CONFIG_FILE" "$REMOTE:$REMOTE_DIR/config/application-online.yml"

echo "==> verify shared embedding index"
"${SSH_CMD[@]}" "$REMOTE" "
  set -e
  docker ps --format '{{.Names}}' | grep -Fx '$MYSQL_CONTAINER' >/dev/null 2>&1 || {
    echo 'mysql container not found: $MYSQL_CONTAINER' >&2
    exit 1
  }
  MODEL_NAME='$EMBEDDING_MODEL_NAME'
  MISSING_MODEL=\$(docker exec '$MYSQL_CONTAINER' mysql -N -B -u'$MYSQL_USERNAME' -p'$MYSQL_PASSWORD' -D '$MYSQL_DATABASE' -e \"
    SELECT COUNT(*)
    FROM knowledge_file_status
    WHERE source = 'uploaded'
      AND processing_status = 'READY'
      AND (embedding_model_name IS NULL OR embedding_model_name <> '\$MODEL_NAME')
  \")
  MISSING_VECTOR=\$(docker exec '$MYSQL_CONTAINER' mysql -N -B -u'$MYSQL_USERNAME' -p'$MYSQL_PASSWORD' -D '$MYSQL_DATABASE' -e \"
    SELECT COUNT(*)
    FROM knowledge_chunk_index
    WHERE embedding IS NULL OR embedding = ''
  \")
  if [ \"\$MISSING_MODEL\" -ne 0 ] || [ \"\$MISSING_VECTOR\" -ne 0 ]; then
    echo \"shared embedding index is incomplete: model_mismatch=\$MISSING_MODEL missing_vectors=\$MISSING_VECTOR\" >&2
    echo \"please run local knowledge build with \$MODEL_NAME before deploying the online read-only node\" >&2
    exit 1
  fi
"

echo "==> remote preflight"
"${SSH_CMD[@]}" "$REMOTE" "
  set -e
  command -v curl >/dev/null 2>&1 || { echo 'curl not found on remote host' >&2; exit 1; }
  command -v nc >/dev/null 2>&1 || { echo 'nc not found on remote host' >&2; exit 1; }
  curl -fsS 'http://127.0.0.1:$CHROMA_PORT/api/v2/heartbeat' >/dev/null
  nc -z 127.0.0.1 '$MYSQL_PORT'
  nc -z 127.0.0.1 '$MONGO_PORT'
  nc -z 127.0.0.1 '$REDIS_PORT'
"

echo "==> deploy containers"
"${SSH_CMD[@]}" "$REMOTE" "
  set -e
  cd '$REMOTE_DIR'
  export DOCKER_BUILDKIT=1
  echo 'deploy mode: rsync source + remote docker compose build'
  docker compose -f docker-compose.online.yml up -d --build backend frontend
  docker compose -f docker-compose.online.yml ps
  for i in \$(seq 1 100); do
    if curl -fsS 'http://127.0.0.1/' >/dev/null && curl -fsS 'http://127.0.0.1/api/doc.html' >/dev/null; then
      exit 0
    fi
    sleep 3
  done
  echo 'online services did not become ready in time' >&2
  exit 1
"

echo "==> done"
