# AiMed 生产部署

这套配置适合把项目作为一个小型独立服务部署到单机或局域网服务器。

部署结构：

- `frontend`：Vue 打包后的静态站点 + Nginx 反向代理
- `backend`：Spring Boot 应用
- `mongodb`：聊天记忆和知识库状态
- `MySQL`：沿用你已有的实例
- `Ollama`：沿用宿主机已运行的本地模型，不再放进 Docker

## 1. 准备环境

宿主机需要先准备好：

- Docker Desktop 或 Docker Engine + Compose
- 本地可用的 MySQL
- 本地可用的 Ollama

建议先确认宿主机 Ollama 可用：

```bash
ollama list
curl http://localhost:11434/api/tags
```

## 2. 准备环境变量

复制模板：

```bash
cp .env.prod.example .env.prod
```

按实际情况修改：

- `BL_KEY`
- `SPRING_DATASOURCE_URL`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `LOCAL_CHAT_MODEL_NAME`
- `EMBEDDING_MODEL_NAME`
- `LOCAL_VISION_MODEL_NAME`

说明：

- 如果只想离线使用本地 Ollama，`BL_KEY` 可以先留空
- 如果需要切换到千问在线入口，必须配置 `BL_KEY`
- Docker 容器默认通过 `host.docker.internal:11434` 访问宿主机 Ollama

## 3. 启动服务

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

查看状态：

```bash
docker compose -f docker-compose.prod.yml ps
```

查看日志：

```bash
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f frontend
```

## 4. 访问地址

默认情况下：

- 前端首页：`http://服务器IP/`
- 知识库页：`http://服务器IP/knowledge`
- 后端文档：`http://服务器IP/api/doc.html`

说明：

- 对外只暴露前端 `80` 端口
- Nginx 会把 `/api/*` 和 `/ws/*` 自动转发到后端

## 5. 升级与重启

拉最新代码后执行：

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

停止服务：

```bash
docker compose -f docker-compose.prod.yml down
```

如果只想停止容器但保留数据卷，上面命令就够了。

如果要连 Mongo 数据卷一起删除：

```bash
docker compose -f docker-compose.prod.yml down -v
```

## 6. 反向代理说明

前端镜像内部已经带了 Nginx 配置，能力包括：

- 前端静态资源服务
- `/api/` 反代后端
- `/ws/` 反代 websocket
- 支持流式聊天响应
- 支持大文件上传

配置文件位置：

- `aimed-ui/deploy/nginx.conf`

## 7. 常见问题

### 1. 容器里连不上 Ollama

先在宿主机确认：

```bash
curl http://localhost:11434/api/tags
```

再进后端容器测试：

```bash
docker exec -it aimed-prod-backend sh
```

容器内执行：

```bash
wget -qO- http://host.docker.internal:11434/api/tags
```

如果失败，优先检查：

- Ollama 是否正在宿主机运行
- Docker Desktop 是否支持 `host.docker.internal`

### 2. 千问在线模式不可用

通常是 `BL_KEY` 没有配置或失效。

### 3. 前端能打开但接口报错

优先检查：

- `docker compose logs -f backend`
- 响应头里的 `X-Trace-Id`
- 后端日志中同一个 `traceId` 的完整链路
