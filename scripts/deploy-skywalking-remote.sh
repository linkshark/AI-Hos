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
PASSWORD="${SERVER_PASS:-${DEPLOY_PASSWORD:-}}"
COMPOSE_FILE="${DEPLOY_SKYWALKING_COMPOSE_FILE:-$ROOT_DIR/docker-compose.skywalking.yml}"
NGINX_CONFIG_FILE="${DEPLOY_SKYWALKING_NGINX_CONFIG_FILE:-$ROOT_DIR/docker/skywalking/nginx.conf}"
EMBED_SCRIPT_FILE="${DEPLOY_SKYWALKING_EMBED_SCRIPT_FILE:-$ROOT_DIR/docker/skywalking/aihos-embed.js}"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "missing skywalking compose: $COMPOSE_FILE" >&2
  exit 1
fi
if [[ ! -f "$NGINX_CONFIG_FILE" ]]; then
  echo "missing skywalking nginx config: $NGINX_CONFIG_FILE" >&2
  exit 1
fi
if [[ ! -f "$EMBED_SCRIPT_FILE" ]]; then
  echo "missing skywalking embed script: $EMBED_SCRIPT_FILE" >&2
  exit 1
fi

if [[ -z "$PASSWORD" ]]; then
  echo "missing SERVER_PASS or DEPLOY_PASSWORD" >&2
  exit 1
fi

expect_run() {
  local mode="$1"
  shift
  EXPECT_MODE="$mode" \
  EXPECT_PASSWORD="$PASSWORD" \
  EXPECT_HOST="$HOST" \
  EXPECT_PORT="$PORT" \
  EXPECT_USER="$USER_NAME" \
  EXPECT_ARG1="${1:-}" \
  EXPECT_ARG2="${2:-}" \
  expect <<'EOF'
set timeout -1
set mode $env(EXPECT_MODE)
set password $env(EXPECT_PASSWORD)
set host $env(EXPECT_HOST)
set port $env(EXPECT_PORT)
set user $env(EXPECT_USER)
if {$mode eq "ssh"} {
  set cmd $env(EXPECT_ARG1)
  spawn ssh -o StrictHostKeyChecking=no -p $port $user@$host $cmd
} elseif {$mode eq "scp"} {
  set localPath $env(EXPECT_ARG1)
  set remotePath $env(EXPECT_ARG2)
  spawn scp -O -P $port -o StrictHostKeyChecking=no $localPath $user@$host:$remotePath
} else {
  puts stderr "unsupported mode: $mode"
  exit 1
}
expect {
  -re "Are you sure you want to continue connecting.*" {
    send "yes\r"
    exp_continue
  }
  -re ".*assword:.*" {
    send "$password\r"
    exp_continue
  }
  eof {
    catch wait result
    set exitCode [lindex $result 3]
    exit $exitCode
  }
}
EOF
}

echo "==> ensure remote directory"
expect_run ssh "mkdir -p '$REMOTE_DIR' '$REMOTE_DIR/docker/skywalking'"

echo "==> upload skywalking compose"
expect_run scp "$COMPOSE_FILE" "$REMOTE_DIR/docker-compose.skywalking.yml"

echo "==> upload skywalking assets"
expect_run scp "$NGINX_CONFIG_FILE" "$REMOTE_DIR/docker/skywalking/nginx.conf"
expect_run scp "$EMBED_SCRIPT_FILE" "$REMOTE_DIR/docker/skywalking/aihos-embed.js"

echo "==> deploy skywalking"
expect_run ssh "
  set -e;
  cd '$REMOTE_DIR';
  docker compose -f docker-compose.skywalking.yml up -d;
  docker compose -f docker-compose.skywalking.yml ps
"

echo "==> skywalking deployed"
