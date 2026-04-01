# AiMed 纯在线部署指南

这版部署方式分成两层：

- `mariadb` 和 `mongo` 作为服务器上的独立容器长期运行
- IDEA 里的 [AI-Med.run.xml](/Users/shenchaoqi/codex/AiMed/.run/AI-Med.run.xml) 运行远程部署脚本，只负责重建项目自己的 `backend` / `frontend`

这样你以后每次点 IDEA 的运行配置：

- 不会重建数据库
- 不会重建 Mongo
- 不会清理数据库和 Mongo 的数据卷
- 只更新你项目本身的镜像和容器

## 1. 数据服务单独部署

数据库和 Mongo 单独部署脚本：

- [deploy-data-services.sh](/Users/shenchaoqi/codex/AiMed/scripts/deploy-data-services.sh)

在服务器项目目录执行一次：

```bash
cd /home/shark/AI-Hos
./scripts/deploy-data-services.sh
```

默认配置：

- MariaDB 用户名：`shark`
- MariaDB 对外端口：`13306`
- Mongo 用户名：`shark`
- Mongo 对外端口：`27018`

这两个容器是长期运行的基础设施，不需要每次部署项目都重建。
脚本会复用已有 Docker 卷，不会因为你更新项目镜像而清空数据库数据。

## 2. 应用 compose 的职责

[docker-compose.online.yml](/Users/shenchaoqi/codex/AiMed/docker-compose.online.yml) 现在只保留：

- `backend`
- `frontend`

后端通过 `host.docker.internal` 连接服务器上独立运行的：

- MariaDB `13306`
- MongoDB `27018`

## 3. 私有 YAML 配置

现在不再依赖 `.env` / `.env.online`。项目启动时会自动读取：

- 基础配置 [application.yml](/Users/shenchaoqi/codex/AiMed/src/main/resources/application.yml)
- 本地 gitignore 私有文件 [application-local.yml](/Users/shenchaoqi/codex/AiMed/config/application-local.yml)
- 线上 gitignore 私有文件 [application-online.yml](/Users/shenchaoqi/codex/AiMed/config/application-online.yml)

远程部署脚本会把本地 [application-online.yml](/Users/shenchaoqi/codex/AiMed/config/application-online.yml) 上传到服务器 `config/` 目录，然后再执行 `docker compose up -d --build`。

## 4. IDEA 里的运行方式

[AI-Med.run.xml](/Users/shenchaoqi/codex/AiMed/.run/AI-Med.run.xml) 现在是 Shell Script 类型。

你每次点击它时，预期动作应该是：

- 先把项目同步到服务器 `/home/shark/AI-Hos`
- 上传私有配置 `config/application-online.yml`
- 只构建并运行 `backend` / `frontend`

## 5. 服务器上的数据服务环境

服务器上独立 MariaDB / Mongo 当前使用：

- MariaDB 端口：`13306`
- MariaDB 用户名：`shark`
- MongoDB 端口：`27018`
- MongoDB 用户名：`shark`
- 四个容器统一时区：`Asia/Shanghai`

## 6. 访问和对外转发端口

当前端口建议：

- 站点：服务器 `80`，由你的外部网关转发到公网 `8888`
- MariaDB：`13306`
- MongoDB：`27018`

按你现在的网关方式，推荐这样转发：

- 外部 `8888` -> 服务器 `80`
- 外部数据库端口 -> 服务器 `13306`
- 外部 Mongo 端口 -> 服务器 `27018`

## 7. 数据不会被清理的条件

后续你只要遵守这两条：

```bash
不要执行 docker compose down -v
```

```bash
不要手动删除独立数据容器的 volume
```

当前独立容器复用的是这两个 Docker 卷：

- `ai-hos_aimed-online-mysql-data`
- `ai-hos_aimed-online-mongo-data`

那数据库和 Mongo 数据就不会被清理。

普通项目更新只会影响：

- `backend`
- `frontend`

不会影响：

- `aimed-mariadb`
- `aimed-mongo`

## 8. 推荐的实际使用方式

第一次：

1. 把项目上传到服务器
2. 在服务器执行一次 `./scripts/deploy-data-services.sh`
3. 在服务器创建 `/home/shark/.config/ai-hos/env`
4. 在 IDEA 里点 `AI-Med`

后续更新：

1. 如果你手动删掉了项目镜像或项目容器，没关系
2. 直接在 IDEA 里点 `AI-Med`
3. 数据库和 Mongo 不动，只重建项目自己的镜像和容器
