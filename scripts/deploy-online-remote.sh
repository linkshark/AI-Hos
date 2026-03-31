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
REMOTE_SECRET_ENV="${DEPLOY_REMOTE_SECRET_ENV:-/home/shark/.config/ai-hos/env}"
ENV_FILE=""
if [[ -n "${DEPLOY_ENV_FILE:-}" ]]; then
  ENV_FILE="$DEPLOY_ENV_FILE"
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "missing env file: $ENV_FILE" >&2
    exit 1
  fi
elif [[ -f "$ROOT_DIR/.env" ]]; then
  ENV_FILE="$ROOT_DIR/.env"
elif [[ -f "$ROOT_DIR/.env.online" ]]; then
  ENV_FILE="$ROOT_DIR/.env.online"
fi
SANITIZED_ENV_FILE="$(mktemp "${TMPDIR:-/tmp}/aimed-online-env.XXXXXX")"
CONTROL_PATH="$(mktemp -u "${TMPDIR:-/tmp}/aimed-ssh-ctl.XXXXXX")"

cleanup() {
  rm -f "$SANITIZED_ENV_FILE"
  if [[ -n "${MASTER_STARTED:-}" ]]; then
    ssh -O exit -o StrictHostKeyChecking=no -o ControlPath="$CONTROL_PATH" -p "$PORT" "$REMOTE" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if [[ -n "$ENV_FILE" ]]; then
  grep -vE '^(BL_KEY|VISION_API_KEY)=' "$ENV_FILE" > "$SANITIZED_ENV_FILE"
else
  echo "==> no local env file found, keep remote .env.online as-is"
fi

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
"${SSH_CMD[@]}" "$REMOTE" "mkdir -p '$REMOTE_DIR' '$(dirname "$REMOTE_SECRET_ENV")'"

echo "==> sync project"
rsync -az --delete \
  --exclude '.git' \
  --exclude 'target' \
  --exclude 'data' \
  --exclude '.idea' \
  --exclude '.vscode' \
  --exclude '.DS_Store' \
  --exclude '.env.online' \
  --exclude '.env.prod' \
  --exclude 'aimed-ui/node_modules' \
  -e "$RSYNC_SSH" \
  "$ROOT_DIR/" "$REMOTE:$REMOTE_DIR/"

if [[ -n "$ENV_FILE" ]]; then
  echo "==> upload env file"
  rsync -az -e "$RSYNC_SSH" "$SANITIZED_ENV_FILE" "$REMOTE:$REMOTE_DIR/.env.online"
else
  echo "==> skip env upload"
fi

echo "==> deploy containers"
"${SSH_CMD[@]}" "$REMOTE" "
  set -e
  cd '$REMOTE_DIR'
  export DOCKER_BUILDKIT=1
  if [ ! -f '$REMOTE_SECRET_ENV' ]; then
    echo 'missing remote secret env: $REMOTE_SECRET_ENV' >&2
    exit 1
  fi
  set -a
  . '$REMOTE_SECRET_ENV'
  set +a
  docker compose -f docker-compose.online.yml --env-file .env.online up -d --build backend frontend
  docker compose -f docker-compose.online.yml --env-file .env.online ps
"

echo "==> done"
