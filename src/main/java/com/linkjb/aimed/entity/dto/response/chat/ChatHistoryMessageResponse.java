package com.linkjb.aimed.entity.dto.response.chat;

import com.linkjb.aimed.entity.vo.KnowledgeCitationItem;

import java.util.ArrayList;
import java.util.List;

public record ChatHistoryMessageResponse(
        boolean user,
        String content,
        List<ChatHistoryAttachmentResponse> attachments,
        List<KnowledgeCitationItem> citations,
        String traceId,
        String provider,
        String toolMode,
        String intentType,
        String routeTarget,
        boolean ragApplied,
        String ragSkipReason,
        long serverDurationMs,
        long firstTokenLatencyMs,
        List<ChatTraceStage> traceStages
) {
    public ChatHistoryMessageResponse(boolean user,
                                      String content,
                                      List<ChatHistoryAttachmentResponse> attachments,
                                      List<KnowledgeCitationItem> citations) {
        this(user, content, attachments, citations, "", "", "", "", "", false, "", 0L, 0L, new ArrayList<>());
    }

    public ChatHistoryMessageResponse {
        attachments = attachments == null ? new ArrayList<>() : attachments;
        citations = citations == null ? new ArrayList<>() : citations;
        traceId = traceId == null ? "" : traceId;
        provider = provider == null ? "" : provider;
        toolMode = toolMode == null ? "" : toolMode;
        intentType = intentType == null ? "" : intentType;
        routeTarget = routeTarget == null ? "" : routeTarget;
        ragSkipReason = ragSkipReason == null ? "" : ragSkipReason;
        traceStages = traceStages == null ? new ArrayList<>() : traceStages;
    }
}
