# MCP 接入管理

## 当前实现
- 后台路径：`管理后台 -> MCP 接入`
- 当前支持的传输类型：`Streamable HTTP`
- 管理员可执行的动作：
  - 新增 MCP 服务
  - 编辑服务配置
  - 删除服务配置
  - 测试连接
  - 查看远端返回的工具清单

## 为什么先只支持 Streamable HTTP
这一版优先解决“管理员能把远端 MCP 服务接进来并验证通不通”这个核心问题，
而不是一次性把 `stdio / SSE / 本地命令托管 / OAuth` 全部做进系统。

这样做的好处是：
- 运维边界更清晰
- 安全面更小
- 先把最常见的远端 MCP 服务接入需求做稳定

等后面真实接入场景增加，再继续扩 transport 类型。

## 测试流程
当前点击“测试”时，后台会按 MCP 的最小闭环顺序做一次主动探测：

1. `initialize`
2. `notifications/initialized`
3. `tools/list`

如果三步都走通，就把：
- 最近一次状态
- 远端服务名
- 服务版本
- 工具数量

回写到 `mcp_server_config`，并同步落管理员审计日志。

## 配置字段说明
- `服务名称`：后台展示名称
- `服务地址`：远端 MCP HTTP 地址
- `说明`：给管理员自己看的用途备注
- `超时`：连接与请求超时，单位毫秒
- `请求头`：一行一个 `Key: Value`
- `启用`：仅作为后台配置启用标记，不代表系统已经自动把它挂进 Agent

## 当前边界
这一版做的是“接入管理”和“连通性验证”，还没有做：
- 把 MCP 工具自动注入聊天 Agent
- 对接 OAuth 或更复杂的认证协商
- 本地 stdio 型 MCP 服务托管
- MCP 市场/模板中心

## 设计参考
当前后台接入方式主要参考了这几类开源项目的做法：
- [modelcontextprotocol/inspector](https://github.com/modelcontextprotocol/inspector)
  重点参考了它对 `initialize -> tools/list` 探测链路的最小实现思路。
- [open-webui/open-webui](https://github.com/open-webui/open-webui)
  重点参考了“管理员配置远端服务 + 后台测试可用性”的产品形态。
- [ravitemer/mcp-hub](https://github.com/ravitemer/mcp-hub)
  重点参考了“把多个 MCP 服务做成统一管理入口”的方向。

## 后续建议
如果后面真的开始在业务里大量接 MCP，下一步建议按这个顺序推进：

1. 先把“配置管理”升级成“配置 + 权限 + 可用性状态”
2. 再做“Agent 能选择性挂载 MCP 工具”
3. 最后再评估是否要支持更多 transport 和服务模板
