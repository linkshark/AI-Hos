package com.linkjb.aimed.entity.dto.response.chat;

public record ChatHistoryItemResponse(
        Long memoryId,
        String title,
        String customTitle,
        String firstQuestion,
        String preview,
        String updatedAt,
        Long updatedAtEpochMillis,
        Integer messageCount,
        boolean pinned
) {
}
