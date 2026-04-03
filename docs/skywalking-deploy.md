# SkyWalking 接入与部署

当前项目的链路追踪接入方式：

- 后端统一通过 Spring Boot OTLP tracing 配置上报到 SkyWalking
- SkyWalking 服务名：`aihos`
- 本地实例名：`aihos-local`
- 在线实例名：`aihos-online`
- 服务器上单独运行 SkyWalking `BanyanDB + OAP + OTel Collector + UI`

## 1. 当前版本

- SkyWalking OAP / UI：`10.2.0`
- BanyanDB：`0.8.0`

## 2. 后端接入方式

Spring Boot tracing 基础配置在 [application.yml](/Users/shenchaoqi/codex/AiMed/src/main/resources/application.yml)：

- `management.tracing.sampling.probability=1.0`
- `management.otlp.tracing.transport=http`
- `management.otlp.tracing.export.enabled=true`

本地配置在 [application-local.yml](/Users/shenchaoqi/codex/AiMed/config/application-local.yml)：

- `spring.application.name=aihos`
- `management.otlp.tracing.endpoint=http://shenchaoqi.x3322.net:12800/v1/traces`

在线配置在 [application-online.yml](/Users/shenchaoqi/codex/AiMed/config/application-online.yml)：

- `spring.application.name=aihos`
- `management.otlp.tracing.endpoint=http://aimed-skywalking-oap:11800`
- `management.otlp.tracing.transport=grpc`

SkyWalking OAP 在 [docker-compose.skywalking.yml](/Users/shenchaoqi/codex/AiMed/docker-compose.skywalking.yml) 中额外开启了：

- `SW_OTEL_RECEIVER_ENABLED_HANDLERS=otlp-metrics,otlp-logs,otlp-traces`
- `SW_RECEIVER_ZIPKIN=default`
- `SW_QUERY_ZIPKIN=default`

OTel Collector 配置在：

- [otel-collector-config.yaml](/Users/shenchaoqi/codex/AiMed/docker/skywalking/otel-collector-config.yaml)
  - 本地开发机通过外网 `12800 -> 4318` 走 Collector
  - 在线容器直接通过 Docker 网络走 OAP `11800`

## 3. TraceId 对齐

[RequestTraceFilter.java](/Users/shenchaoqi/codex/AiMed/src/main/java/com/linkjb/aimed/config/RequestTraceFilter.java)
会优先把 Spring Boot 当前 span 的 traceId 写入：

- 日志 MDC
- 响应头 `X-Trace-Id`
- 审计日志里的 `traceId`

如果当前请求还没建立 span，再回退到原来的请求头或本地生成值。

## 4. 服务器部署

SkyWalking 独立 compose 文件：

- [docker-compose.skywalking.yml](/Users/shenchaoqi/codex/AiMed/docker-compose.skywalking.yml)

远程部署脚本：

- [deploy-skywalking-remote.sh](/Users/shenchaoqi/codex/AiMed/scripts/deploy-skywalking-remote.sh)

默认端口：

- BanyanDB gRPC：`17912`
- BanyanDB HTTP：`17913`
- OAP gRPC：`11800`
- OTel Collector HTTP：`4318`
- OAP HTTP（宿主机调试）：`12801`
- SkyWalking UI：`9200`
- 对外转发给本地开发机的 OTel HTTP 接收端口：`12800 -> 4318`

执行：

```bash
cd /Users/shenchaoqi/codex/AiMed
SERVER_PASS=你的服务器密码 ./scripts/deploy-skywalking-remote.sh
```

再执行你现有的在线部署脚本，让线上后端加载新的 Spring Boot tracing 配置：

```bash
cd /Users/shenchaoqi/codex/AiMed
./scripts/deploy-online-remote.sh
```

## 5. 验证

部署完成后：

1. 打开 `http://服务器IP:9200`
2. 访问一次智能问答接口
3. 在 SkyWalking UI 中查看服务 `aihos`，再按实例区分 `aihos-online` / `aihos-local`
4. 对照后台审计日志中的 `traceId` 与接口错误响应头 `X-Trace-Id`
