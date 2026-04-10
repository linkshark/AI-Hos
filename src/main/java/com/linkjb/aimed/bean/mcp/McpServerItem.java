package com.linkjb.aimed.bean.mcp;

import java.time.LocalDateTime;
import java.util.List;

public record McpServerItem(
        Long id,
        String name,
        String transportType,
        String baseUrl,
        String description,
        boolean enabled,
        int connectTimeoutMs,
        List<McpServerHeaderItem> headers,
        String lastStatus,
        String lastError,
        String serverName,
        String serverVersion,
        Integer toolsCount,
        LocalDateTime lastCheckedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
