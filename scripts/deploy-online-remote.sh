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
"${SSH_CMD[@]}" "$REMOTE" "mkdir -p '$REMOTE_DIR' '$REMOTE_DIR/config'"

echo "==> sync project"
rsync -az --delete \
  --exclude '.git' \
  --exclude 'target' \
  --exclude 'data' \
  --exclude '.idea' \
  --exclude '.vscode' \
  --exclude '.DS_Store' \
  --exclude 'config/application-local.yml' \
  --exclude 'config/application-online.yml' \
  --exclude 'aimed-ui/node_modules' \
  -e "$RSYNC_SSH" \
  "$ROOT_DIR/" "$REMOTE:$REMOTE_DIR/"

echo "==> upload online config"
rsync -az -e "$RSYNC_SSH" "$CONFIG_FILE" "$REMOTE:$REMOTE_DIR/config/application-online.yml"

echo "==> deploy containers"
"${SSH_CMD[@]}" "$REMOTE" "
  set -e
  cd '$REMOTE_DIR'
  export DOCKER_BUILDKIT=1
  docker compose -f docker-compose.online.yml up -d --build backend frontend
  docker compose -f docker-compose.online.yml ps
"

echo "==> done"
