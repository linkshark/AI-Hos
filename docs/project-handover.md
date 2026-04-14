# AiHos 项目接手与架构说明

## 1. 项目定位

AiHos 是一个面向医院场景的智能问答与知识库运营平台，核心目标不是做一个单纯聊天入口，而是把“登录权限、会话隔离、附件问答、RAG 检索、知识发布、科室归属、审计追踪、部署观测”串成一条可管理的业务链路。

当前版本适合用于面试演示和内部试运行前验证：主流程已经具备，后续正式生产还需要继续补强医院真实系统集成、用户科室授权、评测闭环和更完善的运营配置。

## 2. 核心模块

### 认证与权限

- 后端入口主要在 `AuthController`、`SecurityConfig`、`JwtAuthenticationFilter`。
- 用户角色当前按 `PATIENT / DOCTOR / ADMIN` 处理。
- 默认策略是接口默认拒绝，只显式开放登录、注册、刷新、健康检查等入口。
- 知识库管理、管理后台、知识运营和 MCP 管理都要求管理员权限。
- 管理员自助注册默认关闭，只有设置 `APP_ADMIN_REGISTER_ENABLED=true` 且提供 `APP_ADMIN_REGISTER_INVITE_TOKEN` 时，注册页勾选管理员权限才会生效。
- 普通用户进入知识库管理会在前端提示“普通用户无此权限!”。

### 会话与聊天

- 聊天入口在 `ChatController`，核心编排在 `ChatApplicationService`。
- `memoryId` 全链路按 Long 使用，由服务端创建并绑定当前用户。
- `ChatSessionUserBindingService` 负责会话归属绑定和校验，避免用户复用其他人的 `memoryId`。
- 可见历史由 `ChatHistoryService` 和 Mongo 可见历史存储维护，支持恢复、重命名、置顶、隐藏删除和导出。
- 前端聊天页在 `ChatWindow.vue`，支持登录过期提示、Markdown 渲染、引用卡片、管理员链路耗时查看。

### 模型与附件

- 模型档位分为 `LOCAL_OLLAMA`、`QWEN_ONLINE_FAST`、`QWEN_ONLINE_DEEP`。
- 旧值 `QWEN_ONLINE` 在后端归一化为 `QWEN_ONLINE_FAST`，前端本地缓存也会迁移。
- 附件支持点击和拖拽上传，最多 3 个，单个最大 1MB，总大小最大 2MB。
- 图片超过 1MB 时前端会尝试压缩；压缩失败或非图片超限会直接提示。
- 图片类附件走视觉分析链路；普通文档会被解析后拼入聊天上下文。

### 知识库与 RAG

- 知识库入口在 `KnowledgeController`，主服务是 `KnowledgeBaseService`。
- 知识文件生命周期包括 `DRAFT / PROCESSING / READY / PUBLISHED / FAILED / ARCHIVED`。
- 上传后先解析、切片、embedding，管理员发布后才参与检索；归档后不再参与检索。
- `PUT /aimed/knowledge/files/{hash}` 有两种语义：传 `content` 是正文编辑并重建；不传 `content` 只保存 metadata，不重新 embedding。
- 切片和文件状态分别落在 `knowledge_chunk_index` 和 `knowledge_file_status`，metadata 保存会同步两层数据。

### 混合检索与诊断

- 检索核心在 `HybridKnowledgeRetrieverService`。
- 在线和本地都执行关键词召回 + 向量召回 + 混合排序，只是最终返回数量不同。
- 在线档位：关键词 8 + 向量 8，最终取 6。
- 本地档位：关键词 8 + 向量 8，最终取 4。
- 管理后台“知识运营”页提供检索诊断，可查看规范化 query、关键词 token、布尔查询、关键词命中、向量命中、最终排序和分数拆解。
- 当前排序权重只展示，不提供页面编辑；后续可把权重配置化。

### 科室 metadata

- 科室入口使用已有 `DeptInfoController` 和 `DeptInfoService`。
- 知识文档的科室字段统一存 `deptCode`，通用存 `GENERAL`，页面显示“通用”。
- 新上传和历史默认文档都按 `GENERAL` 兜底。
- 父科室覆盖子科室的判断已在后端服务中预留，但当前没有接入用户科室授权过滤。

### 管理后台与知识运营

- 管理后台前端在 `AdminConsolePage.vue`。
- 当前包含用户管理、MCP 管理、知识运营、审计日志四个主要工作面。
- MCP 管理支持管理员配置 Streamable HTTP MCP 服务，Agent 通过 `DynamicMcpTools` 在运行时查询已启用工具并调用 `tools/call`。
- 知识运营不是普通编辑入口，它用于管理员批量补齐缺失检索字段和排查检索召回。
- metadata 回填默认只补空值或占位字段，不覆盖管理员手工维护内容。

### 审计与链路观测

- 审计入口在 `AuditLogService` 和管理后台审计页。
- 聊天完成后会记录 provider、memoryId、检索摘要、首字等待、服务端耗时、trace timeline 和 TraceId。
- 管理员可以在聊天页和后台看到链路耗时；普通用户不可见。
- SkyWalking TraceId 可跳转到原生链路页面排查。

## 3. 关键数据流

### 普通问答

1. 前端创建或恢复会话，拿到服务端签发的 `memoryId`。
2. 用户发送消息，前端携带 `memoryId`、provider、可选附件。
3. 后端校验登录态和会话归属。
4. `ChatApplicationService` 根据 provider 选择本地或在线 agent。
5. Agent 触发 RAG 检索，检索结果进入模型上下文。
6. 模型流式输出回答，最后追加一段前端可解析的引用和链路 metadata。
7. 后端记录可见历史和审计摘要。

### 动态 MCP 工具调用

1. 管理员在后台新增并启用 Streamable HTTP MCP 服务。
2. 后台测试会执行 `initialize -> notifications/initialized -> tools/list` 并保存最近状态。
3. Agent 需要实时工具时先调用“查询可用MCP工具”，后端运行时读取已启用服务的工具清单。
4. Agent 再调用“调用MCP工具”，后端执行 `tools/call` 并把结果返回给模型。
5. 工具调用记录以 `MCP_TOOL_CALL` 写入审计日志。

### 知识入库

1. 管理员上传知识文件。
2. 后端解析正文、切片、构建 embedding，并写入 `knowledge_file_status` 与 `knowledge_chunk_index`。
3. 文件进入 `READY`，管理员确认后发布为 `PUBLISHED`。
4. 只有 `PUBLISHED` 切片参与问答检索。
5. metadata-only 保存只刷新检索字段，不重建正文和向量。

### 检索诊断

1. 管理员在知识运营页输入 query 和档位。
2. 后端执行与问答一致的关键词召回、向量召回和最终排序。
3. 响应返回 tokens、boolean query、三类命中结果、分数规则和 score breakdown。
4. 页面展示用于排查，不影响用户问答主链路。

## 4. 本地运行与验证

常规验证命令：

```bash
mvn test

cd aimed-ui
npm run build
npm run test:ui
```

本地启动参考：

- 后端：`mvn spring-boot:run`
- 前端：`cd aimed-ui && npm run dev`
- 详细配置：`docs/local-run.md`

## 5. 后续优化方向

- 用户科室授权：把用户/医生的授权科室接入检索过滤，复用当前父科室覆盖子科室方法。
- 评测闭环：建立标准问题集，记录命中率、引用率、拒答率、平均耗时和人工满意度。
- 检索权重后台配置：把当前诊断页展示的计分规则做成管理员可编辑配置。
- 模型客户端优化：如果在线首字等待仍慢，再接入厂商专属参数和更细的流式客户端能力。
- 文档与部署：继续沉淀线上 Nginx、静态资源、API 代理和 SkyWalking 排障手册。
- MCP 权限：当前只做到管理员配置、普通用户间接调用已启用服务，后续可以补工具级权限和参数脱敏。
