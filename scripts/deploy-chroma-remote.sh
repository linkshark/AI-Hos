#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${SERVER_PASS:-}" && -f "${HOME}/.zshrc" ]]; then
  # shellcheck disable=SC1090
  source "${HOME}/.zshrc" >/dev/null 2>&1 || true
fi

HOST="${CHROMA_DEPLOY_HOST:-shenchaoqi.x3322.net}"
PORT="${CHROMA_DEPLOY_PORT:-13245}"
USER_NAME="${CHROMA_DEPLOY_USER:-shark}"
REMOTE_DIR="${CHROMA_REMOTE_DIR:-/home/shark/chroma}"
CHROMA_HTTP_PORT="${CHROMA_HTTP_PORT:-8000}"
CHROMA_IMAGE="${CHROMA_IMAGE:-chromadb/chroma:1.5.3}"
CHROMA_CONTAINER_NAME="${CHROMA_CONTAINER_NAME:-chroma-db}"
CHROMA_VOLUME_NAME="${CHROMA_VOLUME_NAME:-chroma-data}"

if [[ -z "${SERVER_PASS:-}" ]]; then
  echo "missing SERVER_PASS (and ~/.zshrc fallback did not provide it)" >&2
  exit 1
fi

export HOST PORT USER_NAME REMOTE_DIR CHROMA_HTTP_PORT CHROMA_IMAGE CHROMA_CONTAINER_NAME CHROMA_VOLUME_NAME SERVER_PASS

python3 - <<'PY'
import os
import sys

try:
    import paramiko
except ImportError:
    print("missing python module: paramiko", file=sys.stderr)
    print("install with: python3 -m pip install --user paramiko", file=sys.stderr)
    sys.exit(1)

host = os.environ["HOST"]
port = int(os.environ["PORT"])
user = os.environ["USER_NAME"]
password = os.environ["SERVER_PASS"]
remote_dir = os.environ["REMOTE_DIR"]
http_port = os.environ["CHROMA_HTTP_PORT"]
image = os.environ["CHROMA_IMAGE"]
container_name = os.environ["CHROMA_CONTAINER_NAME"]
volume_name = os.environ["CHROMA_VOLUME_NAME"]
gateway_name = "chroma-gateway"
network_name = "chroma-net"

nginx_conf = f"""
server {{
    listen 80;
    server_name _;

    client_max_body_size 64m;

    location / {{
        if ($request_method = OPTIONS) {{
            add_header Access-Control-Allow-Origin "*" always;
            add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
            add_header Access-Control-Allow-Headers "Authorization, Content-Type, X-Requested-With" always;
            add_header Access-Control-Max-Age 86400 always;
            add_header Content-Length 0;
            add_header Content-Type text/plain;
            return 204;
        }}

        add_header Access-Control-Allow-Origin "*" always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Authorization, Content-Type, X-Requested-With" always;

        proxy_pass http://{container_name}:8000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_connect_timeout 60s;
    }}
}}
""".strip() + "\n"

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(hostname=host, port=port, username=user, password=password, timeout=20)

stdin, stdout, stderr = client.exec_command(f"mkdir -p '{remote_dir}'", timeout=30)
if stdout.channel.recv_exit_status() != 0:
    print(stderr.read().decode("utf-8", "replace"), file=sys.stderr)
    client.close()
    sys.exit(1)

sftp = client.open_sftp()
remote_conf_path = f"{remote_dir}/nginx.conf"
with sftp.file(remote_conf_path, "w") as remote_file:
    remote_file.write(nginx_conf)
sftp.close()

commands = [
    (
        "deploy chroma container",
        "bash -lc 'set -euo pipefail; "
        f"docker network create \"{network_name}\" >/dev/null 2>&1 || true; "
        f"docker pull \"{image}\"; "
        "docker pull nginx:1.27-alpine >/dev/null; "
        f"docker rm -f \"{gateway_name}\" >/dev/null 2>&1 || true; "
        f"docker rm -f \"{container_name}\" >/dev/null 2>&1 || true; "
        f"docker volume create \"{volume_name}\" >/dev/null; "
        f"docker run -d --name \"{container_name}\" --restart unless-stopped "
        f"--network \"{network_name}\" "
        "-e IS_PERSISTENT=TRUE "
        "-e PERSIST_DIRECTORY=/data "
        "-e ANONYMIZED_TELEMETRY=FALSE "
        "-e ALLOW_RESET=FALSE "
        f"-v \"{volume_name}:/data\" "
        f"\"{image}\" >/dev/null; "
        f"docker run -d --name \"{gateway_name}\" --restart unless-stopped "
        f"--network \"{network_name}\" "
        f"-p \"{http_port}:80\" "
        f"-v \"{remote_conf_path}:/etc/nginx/conf.d/default.conf:ro\" "
        "nginx:1.27-alpine >/dev/null'"
    ),
    (
        "verify remote local access",
        f"bash -lc 'curl -fsS http://127.0.0.1:{http_port}/api/v2/heartbeat || "
        f"curl -fsS http://127.0.0.1:{http_port}/api/v1/heartbeat'"
    ),
    (
        "verify browser preflight",
        f"bash -lc 'test \"$(curl -sS -o /dev/null -w \"%{{http_code}}\" -X OPTIONS http://127.0.0.1:{http_port}/api/v2/tenants/default_tenant/databases/default_database/collections "
        "-H \"Origin: http://localhost:3000\" "
        "-H \"Access-Control-Request-Method: GET\" "
        "-H \"Access-Control-Request-Headers: content-type\")\" = \"204\"'"
    ),
]

for label, command in commands:
    print(f"==> {label}")
    stdin, stdout, stderr = client.exec_command(command, timeout=120)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.strip())
    if err.strip():
        print(err.strip(), file=sys.stderr)
    if code != 0:
        client.close()
        sys.exit(code)

client.close()
print("==> done")
PY
