package com.linkjb.aimed.bean.chat;

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
