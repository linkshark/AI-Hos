package com.linkjb.aimed.entity.dto.response.chat;

import java.util.List;

public record ChatHistoryDetailResponse(
        Long memoryId,
        String title,
        String customTitle,
        String firstQuestion,
        String lastPreview,
        List<ChatHistoryMessageResponse> messages
) {
}
