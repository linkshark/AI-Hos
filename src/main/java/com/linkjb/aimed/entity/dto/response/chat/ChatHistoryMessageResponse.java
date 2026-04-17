package com.linkjb.aimed.entity.dto.response.chat;

import com.linkjb.aimed.entity.vo.KnowledgeCitationItem;

import java.util.ArrayList;
import java.util.List;

public record ChatHistoryMessageResponse(
        boolean user,
        String content,
        List<ChatHistoryAttachmentResponse> attachments,
        List<KnowledgeCitationItem> citations
) {
    public ChatHistoryMessageResponse {
        attachments = attachments == null ? new ArrayList<>() : attachments;
        citations = citations == null ? new ArrayList<>() : citations;
    }
}
