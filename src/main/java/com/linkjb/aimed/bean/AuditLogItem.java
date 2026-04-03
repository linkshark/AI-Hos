package com.linkjb.aimed.bean;

import java.time.LocalDateTime;

public record AuditLogItem(
        Long id,
        Long actorUserId,
        String actorLabel,
        String actorRole,
        String actionType,
        String targetType,
        String targetId,
        String summary,
        String traceId,
        Long memoryId,
        String provider,
        Long durationMs,
        Boolean hasAttachments,
        LocalDateTime createdAt
) {
}
