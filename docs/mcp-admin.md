# MCP 接入管理

## 当前实现

- 后台路径：`管理后台 -> MCP 接入`
- 当前支持的传输类型：`Streamable HTTP`
- 管理员可执行的动作：新增、编辑、删除、启用、测试 MCP 服务，并查看远端 `tools/list` 返回的工具清单。
- Agent 运行时能力：聊天 Agent 已接入 `DynamicMcpTools` 网关，会在运行中读取已启用 MCP 服务并按工具名调用 `tools/call`。
- 内置示例：系统内置天气 MCP 入口，可作为动态 MCP 调用链路的演示样例。

## 为什么先只支持 Streamable HTTP

这一版优先解决“管理员能把远端 MCP 服务接进来，并让 Agent 在运行时动态发现和调用工具”这个核心问题，而不是一次性把 `stdio / SSE / 本地命令托管 / OAuth` 全部做进系统。

这样做的好处是：

- 运维边界更清晰，远端服务由独立 MCP 服务自己负责运行。
- 安全面更小，后端不托管任意本地命令。
- 面试演示链路完整：后台配置 -> 连通性验证 -> Agent 查询工具 -> Agent 调用工具 -> 审计留痕。

## 后台测试流程

点击“测试”时，后台会按 MCP 的最小闭环顺序主动探测：

1. `initialize`
2. `notifications/initialized`
3. `tools/list`

如果三步都走通，就把最近一次状态、远端服务名、服务版本、工具数量回写到 `mcp_server_config`，并同步落管理员审计日志。

## Agent 运行时调用流程

1. Agent 遇到天气、外部系统、实时数据等内置工具无法处理的问题时，先调用“查询可用MCP工具”。
2. `DynamicMcpTools` 通过 `McpServerAdminService` 读取当前启用的 Streamable HTTP MCP 服务。
3. 后端对每个服务执行 `initialize -> notifications/initialized -> tools/list`，返回工具名、说明和 `inputSchema`。
4. Agent 选择工具后调用“调用MCP工具”，后端再执行 `tools/call`。
5. 工具调用结果以文本形式返回给 Agent，用于继续生成最终回答。
6. 每次 Agent 调用 MCP 工具都会记录 `MCP_TOOL_CALL` 审计日志。

工具名匹配做了容错处理：支持精确工具名、大小写差异、下划线/连字符差异、从 `tool=xxx` 描述文本中提取工具名，以及天气类中文意图兜底匹配。

## 配置字段说明

- `服务名称`：后台展示名称，也可用于 `服务名称.工具名称` 的精确调用。
- `服务地址`：远端 MCP HTTP 地址。
- `说明`：给管理员自己看的用途备注。
- `超时`：连接与请求超时，单位毫秒。
- `请求头`：一行一个 `Key: Value`，用于简单鉴权或网关转发。
- `启用`：只有启用后的 Streamable HTTP 服务才会被 Agent 运行时发现。

## 安全边界

- 只有管理员可以新增、编辑、删除和测试 MCP 服务。
- 普通用户不能配置 MCP 服务，只能在聊天中间接使用管理员已启用的工具。
- 后端当前不托管本地 `stdio` 命令型 MCP，避免把任意命令执行能力暴露给业务系统。
- Agent 工具调用会落审计日志，便于追踪哪个 MCP 服务和工具被调用过。

## 当前边界

这一版暂不支持：

- OAuth 或更复杂的认证协商。
- 本地 `stdio` 型 MCP 服务托管。
- MCP 市场/模板中心。
- 按用户、角色或科室对 MCP 工具做更细粒度授权。

## 设计参考

当前后台接入方式主要参考了这几类开源项目的做法：

- [modelcontextprotocol/inspector](https://github.com/modelcontextprotocol/inspector)：参考 `initialize -> tools/list` 探测链路。
- [open-webui/open-webui](https://github.com/open-webui/open-webui)：参考“管理员配置远端服务 + 后台测试可用性”的产品形态。
- [ravitemer/mcp-hub](https://github.com/ravitemer/mcp-hub)：参考“把多个 MCP 服务做成统一管理入口”的方向。

## 后续建议

如果后面开始在业务里大量接 MCP，建议按这个顺序推进：

1. 增加 MCP 工具级权限，例如按角色或用户组允许调用。
2. 增加工具调用参数脱敏和结果截断，避免外部服务返回超长或敏感内容。
3. 评估是否接入更多 transport 和服务模板。
