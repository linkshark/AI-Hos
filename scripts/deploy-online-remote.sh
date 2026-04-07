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
if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "missing online yaml: $CONFIG_FILE" >&2
  echo "expected: $ROOT_DIR/config/application-online.yml" >&2
  exit 1
fi

echo "==> validate online config"
python3 - "$CONFIG_FILE" <<'PY'
import sys, yaml
path = sys.argv[1]
cfg = yaml.safe_load(open(path, 'r', encoding='utf-8')) or {}

def require(value, label):
    if not value:
        raise SystemExit(f"invalid online yaml: missing {label}")

app = cfg.get("app", {})
spring = cfg.get("spring", {})
require(app.get("chroma", {}).get("base-url"), "app.chroma.base-url")
require(app.get("online-chat", {}).get("api-key"), "app.online-chat.api-key")
require(app.get("embedding", {}).get("base-url"), "app.embedding.base-url")
require(app.get("embedding", {}).get("model-name"), "app.embedding.model-name")
require(app.get("vision-chat", {}).get("api-key"), "app.vision-chat.api-key")
require(spring.get("datasource", {}).get("url"), "spring.datasource.url")
require(spring.get("datasource", {}).get("username"), "spring.datasource.username")
require(spring.get("datasource", {}).get("password"), "spring.datasource.password")
require(spring.get("data", {}).get("mongodb", {}).get("uri"), "spring.data.mongodb.uri")
require(spring.get("data", {}).get("redis", {}).get("host"), "spring.data.redis.host")
require(spring.get("data", {}).get("redis", {}).get("password"), "spring.data.redis.password")
require(spring.get("mail", {}).get("host"), "spring.mail.host")
require(spring.get("mail", {}).get("username"), "spring.mail.username")
require(spring.get("mail", {}).get("password"), "spring.mail.password")
print("online yaml OK")
PY

EMBEDDING_MODEL_NAME="$(python3 - "$CONFIG_FILE" <<'PY'
import sys, yaml
cfg = yaml.safe_load(open(sys.argv[1], 'r', encoding='utf-8')) or {}
print(((cfg.get("app") or {}).get("embedding") or {}).get("model-name") or "")
PY
)"

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
  MODEL_NAME='$EMBEDDING_MODEL_NAME'
  MISSING_MODEL=\$(docker exec aimed-mariadb mysql -N -B -ushark -pShark1996! -D aimed -e \"
    SELECT COUNT(*)
    FROM knowledge_file_status
    WHERE source = 'uploaded'
      AND processing_status = 'READY'
      AND (embedding_model_name IS NULL OR embedding_model_name <> '\$MODEL_NAME')
  \")
  MISSING_VECTOR=\$(docker exec aimed-mariadb mysql -N -B -ushark -pShark1996! -D aimed -e \"
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
  curl -fsS 'http://127.0.0.1:8000/api/v2/heartbeat' >/dev/null
  nc -z 127.0.0.1 13306
  nc -z 127.0.0.1 27018
  nc -z 127.0.0.1 6379
"

echo "==> deploy containers"
"${SSH_CMD[@]}" "$REMOTE" "
  set -e
  cd '$REMOTE_DIR'
  export DOCKER_BUILDKIT=1
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
