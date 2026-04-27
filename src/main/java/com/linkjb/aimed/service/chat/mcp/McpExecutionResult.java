package com.linkjb.aimed.service.chat.mcp;

import com.linkjb.aimed.entity.dto.response.chat.ChatStreamEvent;

import java.util.List;

/**
 * 一次 MCP 规划与调用的显式结果。
 */
public record McpExecutionResult(
        String toolName,
        String toolResult,
        String callStatus,
        String callDetail,
        long planDurationMs,
        long callDurationMs,
        List<ChatStreamEvent> events
) {
}
