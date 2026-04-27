package com.linkjb.aimed.service.chat.mcp;

import com.linkjb.aimed.entity.dto.response.chat.ChatStreamEvent;
import com.linkjb.aimed.service.McpServerAdminService;
import com.linkjb.aimed.service.McpToolInvocationPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 两阶段执行服务。
 *
 * 它只负责“选工具并调用工具”，最终答案总结仍由聊天编排层决定，
 * 这样工具结果可以复用到本地/在线不同总结模型，而不会在这里绑死具体 prompt。
 */
@Service
public class McpExecutionService {

    private static final Logger log = LoggerFactory.getLogger(McpExecutionService.class);

    private final McpServerAdminService mcpServerAdminService;
    private final McpToolInvocationPlanner mcpToolInvocationPlanner;

    public McpExecutionService(McpServerAdminService mcpServerAdminService,
                               McpToolInvocationPlanner mcpToolInvocationPlanner) {
        this.mcpServerAdminService = mcpServerAdminService;
        this.mcpToolInvocationPlanner = mcpToolInvocationPlanner;
    }

    public McpExecutionResult execute(String message) {
        String toolContext = mcpServerAdminService == null ? "当前没有启用的 MCP 服务。" : mcpServerAdminService.describeEnabledToolsForAgent();
        long planStartedAt = System.nanoTime();
        McpToolInvocationPlanner.ToolInvocationPlan plan = mcpToolInvocationPlanner == null
                ? null
                : mcpToolInvocationPlanner.plan(message, toolContext);
        long planDurationMs = durationMs(planStartedAt);
        String plannedToolName = plan == null ? "" : plan.toolName();
        List<ChatStreamEvent> events = new ArrayList<>();
        if (plan == null || !StringUtils.hasText(plannedToolName)) {
            events.add(new ChatStreamEvent("MCP", "PLAN", "ERROR", "未匹配到工具",
                    "当前没有匹配的可用 MCP 工具", null, planDurationMs));
            return new McpExecutionResult(
                    "",
                    "status=NOT_FOUND\nmessage=当前没有匹配的可用 MCP 工具",
                    "NOT_FOUND",
                    "没有匹配的可用 MCP 工具",
                    planDurationMs,
                    0,
                    events
            );
        }

        events.add(new ChatStreamEvent("MCP", "PLAN", "DONE", "工具已选定",
                plannedToolName, plannedToolName, planDurationMs));
        events.add(new ChatStreamEvent("MCP", "CALL", "RUNNING", "工具执行中",
                plannedToolName, plannedToolName, 0));

        long callStartedAt = System.nanoTime();
        try {
            String toolResult = mcpServerAdminService.callEnabledToolForAgent(plannedToolName, plan.argumentsJson());
            long callDurationMs = durationMs(callStartedAt);
            String toolStatus = resolveMcpToolStatus(toolResult);
            String toolDetail = summarizeMcpToolResult(toolResult);
            events.add(new ChatStreamEvent("MCP", "CALL", toEventStatus(toolStatus), "工具执行完成",
                    toolDetail, plannedToolName, callDurationMs));
            return new McpExecutionResult(
                    plannedToolName,
                    toolResult,
                    toolStatus,
                    toolDetail,
                    planDurationMs,
                    callDurationMs,
                    events
            );
        } catch (Exception exception) {
            long callDurationMs = durationMs(callStartedAt);
            log.warn("mcp.tool.call.failed tool={} args={}", plannedToolName, plan.argumentsJson(), exception);
            events.add(new ChatStreamEvent("MCP", "CALL", "ERROR", "工具调用失败",
                    exception.getMessage(), plannedToolName, callDurationMs));
            return new McpExecutionResult(
                    plannedToolName,
                    "status=ERROR\ntool=" + plannedToolName + "\nmessage=" + exception.getMessage(),
                    "ERROR",
                    exception.getMessage(),
                    planDurationMs,
                    callDurationMs,
                    events
            );
        }
    }

    private String resolveMcpToolStatus(String toolResult) {
        if (!StringUtils.hasText(toolResult)) {
            return "EMPTY";
        }
        if (toolResult.startsWith("status=SUCCESS")) {
            return "SUCCESS";
        }
        if (toolResult.startsWith("status=NOT_FOUND")) {
            return "NOT_FOUND";
        }
        if (toolResult.startsWith("status=MISSING")) {
            return "MISSING";
        }
        if (toolResult.startsWith("status=ERROR")) {
            return "ERROR";
        }
        return "SUCCESS";
    }

    private String summarizeMcpToolResult(String toolResult) {
        if (!StringUtils.hasText(toolResult)) {
            return "工具未返回内容";
        }
        for (String line : toolResult.split("\\R")) {
            if (line.startsWith("message=")) {
                return line.substring("message=".length()).trim();
            }
        }
        for (String line : toolResult.split("\\R")) {
            if (line.startsWith("tool=")) {
                return line.substring("tool=".length()).trim();
            }
        }
        String compact = toolResult.replaceAll("\\s+", " ").trim();
        return compact.length() > 80 ? compact.substring(0, 80) + "..." : compact;
    }

    private String toEventStatus(String toolStatus) {
        return "SUCCESS".equals(toolStatus) ? "DONE" : "ERROR";
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
