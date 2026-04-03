# Chroma 部署

Chroma 通过单个 Docker 容器部署到服务器，直接监听服务器 `8000` 端口。

外网访问：

- `http://<你的域名或IP>:17666`

默认远程部署命令：

```bash
./scripts/deploy-chroma-remote.sh
```

验证：

```bash
curl http://shenchaoqi.x3322.net:17666/api/v2/heartbeat
```

如果返回 `200` 和 heartbeat JSON，说明公网访问和 Chroma 服务都正常。

部署参数：

- `CHROMA_IMAGE`
- `CHROMA_HTTP_PORT`
- `CHROMA_REMOTE_DIR`
- `CHROMA_CONTAINER_NAME`
- `CHROMA_VOLUME_NAME`

当前方案不做身份验证，适合你现在这套“公网直连 + 网关端口转发”的使用方式。
