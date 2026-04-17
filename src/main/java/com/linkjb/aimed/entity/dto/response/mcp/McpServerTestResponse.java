package com.linkjb.aimed.entity.dto.response.mcp;

import com.linkjb.aimed.entity.vo.mcp.McpServerToolItem;

import java.time.LocalDateTime;
import java.util.List;

public record McpServerTestResponse(
        boolean success,
        String transportType,
        String serverName,
        String serverVersion,
        Integer toolsCount,
        List<McpServerToolItem> tools,
        String message,
        LocalDateTime checkedAt
) {
}
