package com.linkjb.aimed.tools;

import com.linkjb.aimed.service.McpServerAdminService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class DynamicMcpTools {

    private final McpServerAdminService mcpServerAdminService;

    public DynamicMcpTools(McpServerAdminService mcpServerAdminService) {
        this.mcpServerAdminService = mcpServerAdminService;
    }

    @Tool(name = "查询可用MCP工具", value = "用于查看管理员在后台运行时启用的 MCP 工具清单。遇到天气、外部系统、实时数据等内置工具无法处理的问题时，先调用本工具查看可用 MCP 工具。")
    public String listAvailableMcpTools() {
        return mcpServerAdminService.describeEnabledToolsForAgent();
    }

    @Tool(name = "调用MCP工具", value = "用于调用管理员在后台运行时启用的 MCP 工具。调用前应已通过“查询可用MCP工具”确认工具名和参数 schema。argumentsJson 必须是 JSON object 字符串。")
    public String callMcpTool(
            @P(value = "MCP 工具名称；如果多个服务有同名工具，可使用 服务名称.工具名称") String toolName,
            @P(value = "工具参数 JSON object 字符串，例如 {\"city\":\"杭州\"}") String argumentsJson
    ) {
        return mcpServerAdminService.callEnabledToolForAgent(toolName, argumentsJson);
    }
}
