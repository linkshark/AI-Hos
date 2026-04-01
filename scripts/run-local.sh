#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${BASH_SOURCE[0]:-}" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
else
  SCRIPT_DIR="${PWD}/scripts"
fi
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOCAL_CONFIG="${LOCAL_CONFIG_FILE:-$ROOT_DIR/config/application-local.yml}"

if [[ ! -f "$LOCAL_CONFIG" ]]; then
  echo "missing local yaml: $LOCAL_CONFIG" >&2
  echo "expected: $ROOT_DIR/config/application-local.yml" >&2
  exit 1
fi

cd "$ROOT_DIR"
exec mvn -q -DskipTests spring-boot:run
