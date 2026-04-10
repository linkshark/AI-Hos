package com.linkjb.aimed.bean.chat;

import com.linkjb.aimed.bean.KnowledgeCitationItem;

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
