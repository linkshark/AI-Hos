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
        String queryType,
        Integer retrievedCountKeyword,
        Integer retrievedCountVector,
        Integer mergedCount,
        Integer finalCitationCount,
        Boolean emptyRecall,
        String topDocHashes,
        Long durationMs,
        Boolean hasAttachments,
        LocalDateTime createdAt
) {
}
