# AiHos 本地运行说明

本文档对应当前仓库的本地开发环境，目标是把后端、Mongo、MySQL、本地 Ollama 与前端一起跑通，并保留千问在线作为可切换入口。

## 1. 环境前提

- Java 21 优先；本机若继续使用 Java 25，先尝试直接运行，若有兼容问题再切回 JDK 21
- Maven 3.9+
- Node.js 20+
- Docker Desktop
- 本地 MySQL 已启动
- 本地 Redis 已启动，或通过 Docker 启动

## 2. 本地依赖配置

### 2.1 MySQL

推荐先复制 [application-local.example.yml](/Users/shenchaoqi/codex/AiMed/config/application-local.example.yml) 为 [application-local.yml](/Users/shenchaoqi/codex/AiMed/config/application-local.yml)。  
项目启动时会自动按顺序读取：

- 基础配置 [application.yml](/Users/shenchaoqi/codex/AiMed/src/main/resources/application.yml)
- 线上私有配置 [application-online.yml](/Users/shenchaoqi/codex/AiMed/config/application-online.yml)
- 本地私有配置 [application-local.yml](/Users/shenchaoqi/codex/AiMed/config/application-local.yml)

如果本地和线上两份都存在，本地配置优先覆盖。当前本地私有 YAML 已按“本地应用直连家里服务器数据服务”准备，外网端口为：

- MariaDB: `33306`
- MongoDB: `37018`
- Redis: `36379`

如果你想继续手动 export，也仍然可以，但默认推荐直接写进本地私有 YAML：

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/aimed?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export SPRING_DATASOURCE_USERNAME='root'
export SPRING_DATASOURCE_PASSWORD='你的MySQL密码'
export SERVER_ADDRESS='0.0.0.0'
```

初始化数据库：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p < sql/init_aimed.sql
```

### 2.2 MongoDB

使用 Docker 启动本地 MongoDB 和 Redis：

```bash
docker compose up -d mongodb redis
```

默认连接串：

```bash
export SPRING_DATA_MONGODB_URI='mongodb://localhost:27017/chat_memory_db'
```

Redis 默认连接：

```bash
export SPRING_DATA_REDIS_HOST='127.0.0.1'
export SPRING_DATA_REDIS_PORT='6379'
export SPRING_DATA_REDIS_PASSWORD='你的Redis密码'
```

### 2.3 千问在线

千问在线入口读取：

```bash
export BL_KEY='你的百炼 API Key'
```

登录注册新增了 JWT 和邮件验证码能力，开发环境建议同时补这组变量：

```bash
export JWT_SECRET='请替换为一串足够长的随机字符串'
export JWT_ACCESS_EXPIRES='PT30M'
export JWT_REFRESH_EXPIRES='P14D'
export MAIL_MOCK_ENABLED='true'
export MAIL_HOST='smtp.qq.com'
export MAIL_PORT='465'
export MAIL_USERNAME='你的QQ邮箱'
export MAIL_PASSWORD='你的QQ邮箱授权码'
export MAIL_FROM='你的QQ邮箱'
```

当前更推荐直接把这组配置写到 [application-local.yml](/Users/shenchaoqi/codex/AiMed/config/application-local.yml)，这样本地启动不再依赖 shell 环境。

### 2.4 本地 Ollama Embedding

RAG 向量模型默认改为本机 Ollama，不再依赖百炼 `text-embedding-v3`：

```bash
export EMBEDDING_BASE_URL='http://localhost:11434'
export EMBEDDING_MODEL_NAME='bge-m3:latest'
```

本机已安装 Ollama 时可先确认模型存在：

```bash
ollama list
```

当前版本已经把知识库切片和向量落到 MySQL：

- 文件首次入库时才会做切分和 embedding
- 应用重启时会优先从数据库恢复切片和向量
- 只有切片缺某一类向量时，才会按当前开关补算缺失部分

如果你希望完全不使用本地 embedding，可直接关闭：

```bash
export APP_EMBEDDING_LOCAL_ENABLED='false'
export APP_EMBEDDING_ONLINE_ENABLED='true'
export ONLINE_EMBEDDING_MODEL_NAME='text-embedding-v4'
```

### 2.5 本地主聊天模型

聊天主模型默认改为本机 Ollama，推荐这台机器使用：

```bash
export LOCAL_CHAT_BASE_URL='http://localhost:11434'
export LOCAL_CHAT_MODEL_NAME='qwen2.5:3b'
```

### 2.6 本地视觉模型

图片分析默认也可以走本机 Ollama，推荐这台机器使用：

```bash
export LOCAL_VISION_BASE_URL='http://localhost:11434'
export LOCAL_VISION_MODEL_NAME='qwen2.5vl:7b'
```

首次使用前建议确认模型已存在：

```bash
ollama show qwen2.5vl:7b
```

当前项目策略：

- 默认主聊天模型：本地 `qwen2.5:3b`
- 默认图片分析模型：本地 `qwen2.5vl:7b`
- RAG embedding：本地 `bge-m3:latest`
- 千问在线：作为可切换入口保留，主要用于需要更高回答质量或在线视觉兜底的场景

本地模型如果出现“回答说到一半就停了”，优先检查本地输出上限。当前默认建议至少：

```bash
export LOCAL_CHAT_NUM_PREDICT='1024'
export LOCAL_CHAT_TIMEOUT='PT180S'
```

如果你本机显存或内存足够，且经常要输出较长的医学解释，可以继续把 `LOCAL_CHAT_NUM_PREDICT` 提到 `1536` 或 `2048`。

模型选择说明：

- `qwen2.5:3b` 是当前默认离线文本模型，响应更快，也不会像思维链模型那样在当前流式链路里空转
- `qwen2.5vl:7b` 支持中文 OCR 和通用视觉理解，适合病历截图、检查单照片、报告图片这类离线分析场景
- 如果部署到无 GPU 服务器，建议把主提供方切到在线，并关闭本地模型开关

## 3. 启动后端

在仓库根目录执行：

```bash
mvn spring-boot:run
```

当前本地默认也会通过 SkyWalking Java Agent 把链路上报到远端 SkyWalking：

- 服务名：`aihos`
- 实例名：`aihos-local`
- OAP gRPC 端点：`shenchaoqi.x3322.net:11800`

日志默认输出为人类可读格式。如果你想先不引入 ELK、但又希望日志便于后续搜索，可以直接切到结构化 JSON：

```bash
SPRING_PROFILES_ACTIVE=json-logs mvn spring-boot:run
```

如果还希望同时落盘：

```bash
SPRING_PROFILES_ACTIVE=json-logs,file-logs LOG_FILE_NAME=logs/aihos.log mvn spring-boot:run
```

启动成功后可以访问：

- Knife4j: [http://localhost:8080/doc.html](http://localhost:8080/doc.html)
- 接口: `POST /aimed/chat`
- 认证:
  - `POST /aimed/auth/register/send-code`
  - `POST /aimed/auth/register`
  - `POST /aimed/auth/login`
  - `POST /aimed/auth/refresh`
  - `POST /aimed/auth/logout`
  - `GET /aimed/auth/me`

如果你希望一次性带齐本地离线相关环境变量，可以使用：

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/aimed?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export SPRING_DATASOURCE_USERNAME='root'
export SPRING_DATASOURCE_PASSWORD='你的MySQL密码'
export SPRING_DATA_MONGODB_URI='mongodb://localhost:27017/chat_memory_db'
export SPRING_DATA_REDIS_HOST='127.0.0.1'
export SPRING_DATA_REDIS_PORT='6379'
export SPRING_DATA_REDIS_PASSWORD='你的Redis密码'
export SERVER_ADDRESS='0.0.0.0'
export BL_KEY='你的百炼 API Key'
export JWT_SECRET='请替换为一串足够长的随机字符串'
export EMBEDDING_BASE_URL='http://localhost:11434'
export EMBEDDING_MODEL_NAME='bge-m3:latest'
export LOCAL_CHAT_BASE_URL='http://localhost:11434'
export LOCAL_CHAT_MODEL_NAME='qwen2.5:3b'
export LOCAL_VISION_BASE_URL='http://localhost:11434'
export LOCAL_VISION_MODEL_NAME='qwen2.5vl:7b'
mvn spring-boot:run
```

如果你要部署到纯在线服务器，推荐这一组：

```bash
export APP_PROVIDER_DEFAULT='QWEN_ONLINE_FAST'
export APP_PROVIDER_LOCAL_ENABLED='false'
export APP_PROVIDER_ONLINE_ENABLED='true'
export APP_EMBEDDING_LOCAL_ENABLED='false'
export APP_EMBEDDING_ONLINE_ENABLED='true'
export ONLINE_EMBEDDING_MODEL_NAME='text-embedding-v4'
```

这组配置的特点是：

- 聊天、视觉、RAG 都走在线模型
- 不再依赖宿主机 Ollama
- token 消耗相对可控，适合小流量上线

示例请求：

```bash
curl -N \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:8080/aimed/chat \
  -d '{"memoryId":1,"message":"你好，请介绍一下医院信息"}'
```

## 4. 启动前端

前端使用 Vite 代理 `/api` 到后端，默认目标地址是 `http://localhost:8080`，也可以通过环境变量覆盖：

```bash
cd aimed-ui
export VITE_API_BASE_URL='http://localhost:8080'
export VITE_DEV_HOST='0.0.0.0'
export VITE_DEV_PORT='5173'
npm install
npm run dev
```

启动后访问：

- 前端页面: [http://localhost:5173](http://localhost:5173)

## 4.1 局域网访问

当前项目已默认支持局域网访问：

- 后端默认监听 `0.0.0.0:8080`
- 前端 Vite 开发服务默认监听 `0.0.0.0:5173`

先查看你本机的局域网 IP，例如：

```bash
ipconfig getifaddr en0
```

假设返回 `192.168.20.123`，则同一局域网内其它设备可以通过以下地址访问：

- 前端页面: `http://192.168.20.123:5173`
- 后端文档: `http://192.168.20.123:8080/doc.html`

说明：

- 前端仍通过本机 Vite 代理访问后端，所以 `VITE_API_BASE_URL` 保持 `http://localhost:8080` 即可
- 若你换了 Wi-Fi 网卡，可把 `en0` 改成实际网卡名
- 若系统防火墙拦截，请允许 Java 和 Node.js 接收入站连接

## 5. 本地联调验证

### 5.1 聊天接口

- 打开前端页面后，会先显示一条静态欢迎消息，不会立即触发本地模型推理
- 页面能持续收到流式回复，说明前后端联调正常
- 页面顶部可以在 `本地 Ollama` 和 `千问在线` 之间切换
- 本地 Ollama 模式下：
  - 文本问答走 `qwen2.5:3b`
  - 图片分析走 `qwen2.5vl:7b`
- 千问在线模式下：
  - 文本和图片继续走百炼兼容接口

### 5.2 Mongo 聊天记忆

```bash
docker exec -it aimed-mongo mongosh chat_memory_db --eval 'db.chat_messages.find().pretty()'
```

### 5.3 MySQL 预约数据

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p -D aimed -e 'select * from appointment;'
```

## 6. 当前实现说明

- RAG 已切换为本地内存向量库
- 应用启动时会自动加载 `src/main/resources/knowledge` 下的 `.md`、`.txt`、`.pdf` 文档
- 运行中可通过 `POST /aimed/knowledge/upload` 动态上传知识文件，支持 `pdf/doc/docx/md/txt/csv/rtf/html/xml/odt/ods/odp/xls/xlsx/ppt/pptx`
- 运行中上传的知识文件会保存到 `data/knowledge-base`，重启后会自动重新加载
- 上传接口在文件保存完成后会立即返回 `QUEUED`，后续解析、RAG 切分、向量化由后台线程池异步完成
- 知识库管理页当前使用低频轮询刷新处理状态，不再依赖单独的 WebSocket 通知链
- 知识库文件状态会同步写入 MySQL 表 `knowledge_file_status`，即使应用重启也能保留当前处理状态和基础元数据
- 向量数据仅驻留内存，重启后会重新加载
- Redis 负责邮箱验证码、refresh token、access token 黑名单
- Mongo 负责持久化聊天记忆
- MySQL 负责用户数据、预约挂号数据、知识库元数据
- 图片附件不会写入知识库，只参与当前这轮对话分析

### 6.1 上传知识文件

示例：

```bash
curl -X POST http://localhost:8080/aimed/knowledge/upload \
  -F 'files=@/path/to/knowledge.md' \
  -F 'files=@/path/to/manual.pdf'
```

典型返回：

```json
{
  "accepted": 1,
  "items": [
    {
      "status": "QUEUED",
      "message": "文件已上传，正在后台解析并构建 RAG 切分",
      "hash": "..."
    }
  ]
}
```

查看已上传的知识文件：

```bash
curl http://localhost:8080/aimed/knowledge/files
```

如需跳过启动时的知识库预加载，避免本地 Ollama 在开发阶段被启动任务占满，可临时加：

```bash
export KNOWLEDGE_BASE_BOOTSTRAP_ENABLED='false'
```

如果本地 Ollama 偶发出现 `I/O error on POST request for "http://localhost:11434/api/embed": null`，当前版本已经做了两层兜底：

- embedding 请求默认最多重试 `3` 次
- 知识文件向量化改为串行提交，避免启动预加载和新上传任务并发打满本地 Ollama
- 对超大文档会自动放大切分粒度，并把 embedding 分批提交，避免单次请求超时
- 重启时优先从数据库恢复既有向量，不再默认重新请求 embedding 模型

相关环境变量：

```bash
export EMBEDDING_MAX_ATTEMPTS='3'
export EMBEDDING_RETRY_DELAY='PT2S'
export EMBEDDING_BATCH_SIZE='24'
export KNOWLEDGE_CHUNK_SIZE='1000'
export KNOWLEDGE_CHUNK_OVERLAP='150'
export KNOWLEDGE_MAX_CHUNK_SIZE='4000'
export KNOWLEDGE_MAX_SEGMENTS_PER_DOCUMENT='1200'
```

## 7. 常见问题

### 7.1 `BL_KEY` 未设置

表现：启动时报模型鉴权或 Bean 初始化错误。

处理：重新导出环境变量后再启动后端。

### 7.2 Mongo 连接失败

表现：启动时报 `MongoTimeoutException` 或聊天记忆写入失败。

处理：

```bash
docker compose ps
docker compose up -d mongodb
```

### 7.3 MySQL 连接失败

表现：启动时报 JDBC 连接错误或 `appointment` 表不存在。

处理：

- 确认本地 MySQL 已启动
- 确认账号密码和端口正确
- 重新执行 `sql/init_aimed.sql`

### 7.4 前端请求不到后端

表现：浏览器里 `/api/aimed/chat` 报 404 或代理失败。

处理：

- 确认后端在 `8080` 端口正常运行
- 确认 `VITE_API_BASE_URL` 指向正确地址
- 重启 `npm run dev`

## 8. Java 25 兼容性兜底

如果本机 Java 25 运行异常，可以直接用 JDK 17 容器启动后端：

```bash
docker run --rm \
  --name aimed-backend-build \
  -p 8080:8080 \
  -e BL_KEY \
  -e EMBEDDING_BASE_URL \
  -e EMBEDDING_MODEL_NAME \
  -e SPRING_DATASOURCE_URL \
  -e SPRING_DATASOURCE_USERNAME \
  -e SPRING_DATASOURCE_PASSWORD \
  -e SPRING_DATA_MONGODB_URI \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-17 \
  mvn spring-boot:run
```
