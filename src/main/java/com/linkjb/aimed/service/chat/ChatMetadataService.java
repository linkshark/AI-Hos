package com.linkjb.aimed.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.chat.ChatStreamEvent;
import com.linkjb.aimed.entity.dto.response.chat.ChatStreamMetadata;
import com.linkjb.aimed.entity.dto.response.chat.ChatTraceStage;
import com.linkjb.aimed.service.ChatIntentAnalysisService;
import com.linkjb.aimed.service.HybridKnowledgeRetrieverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天流式 metadata/event 编码与暂存服务。
 *
 * 聊天正文仍按 token/chunk 输出，这里只负责把链路追踪和引用摘要编码回同一条流，
 * 并把完成态 metadata 缓存在 traceId 维度，供 controller 在审计落库阶段读取。
 */
@Service
public class ChatMetadataService {

    private static final Logger log = LoggerFactory.getLogger(ChatMetadataService.class);

    private final ObjectMapper objectMapper;
    private final HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService;
    private final Map<String, ChatStreamMetadata> completedStreamMetadata = new ConcurrentHashMap<>();

    public ChatMetadataService(ObjectMapper objectMapper,
                               HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService) {
        this.objectMapper = objectMapper;
        this.hybridKnowledgeRetrieverService = hybridKnowledgeRetrieverService;
    }

    public String encodeWithMarker(String marker, ChatStreamMetadata metadata) {
        try {
            return marker + objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            log.warn("chat.stream.metadata.encode.failed", exception);
            return "";
        }
    }

    public String encodeEventWithMarker(String marker, ChatStreamEvent event) {
        try {
            return marker + objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            log.warn("chat.stream.event.encode.failed", exception);
            return "";
        }
    }

    public ChatStreamMetadata buildAndRemember(String traceId,
                                               String provider,
                                               long requestStartedAt,
                                               long attachmentPreparationMs,
                                               boolean hasAttachments,
                                               long firstTokenLatencyMs,
                                               String toolMode,
                                               ChatIntentAnalysisService.ChatIntentResult intentResult,
                                               HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary) {
        ChatStreamMetadata metadata = hybridKnowledgeRetrieverService.toChatStreamMetadata(retrievalSummary);
        metadata.setTraceId(traceId);
        metadata.setProvider(provider);
        metadata.setToolMode(toolMode);
        metadata.setIntentType(intentResult == null ? "UNKNOWN" : intentResult.intentType());
        metadata.setRouteTarget(intentResult == null ? "UNKNOWN" : intentResult.routeTarget());
        metadata.setRagApplied(intentResult == null || intentResult.ragRequired());
        metadata.setRagSkipReason(intentResult == null ? "" : intentResult.ragSkipReason());
        metadata.setServerDurationMs(durationMs(requestStartedAt));
        metadata.setFirstTokenLatencyMs(Math.max(0, firstTokenLatencyMs));
        metadata.setTraceStages(buildTraceStages(
                provider,
                toolMode,
                attachmentPreparationMs,
                hasAttachments,
                firstTokenLatencyMs,
                intentResult,
                retrievalSummary,
                metadata
        ));
        remember(traceId, metadata);
        return metadata;
    }

    public ChatStreamMetadata consume(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return null;
        }
        return completedStreamMetadata.remove(traceId);
    }

    private void remember(String traceId, ChatStreamMetadata metadata) {
        if (!StringUtils.hasText(traceId) || metadata == null) {
            return;
        }
        completedStreamMetadata.put(traceId, metadata);
    }

    private List<ChatTraceStage> buildTraceStages(String provider,
                                                  String toolMode,
                                                  long attachmentPreparationMs,
                                                  boolean hasAttachments,
                                                  long firstTokenLatencyMs,
                                                  ChatIntentAnalysisService.ChatIntentResult intentResult,
                                                  HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary,
                                                  ChatStreamMetadata metadata) {
        List<ChatTraceStage> stages = new ArrayList<>();
        if (intentResult != null) {
            stages.add(new ChatTraceStage(
                    "intent_analysis",
                    "意图分析",
                    0,
                    "DONE",
                    intentResult.intentType()
                            + " / " + intentResult.routeTarget()
                            + " / RAG " + (intentResult.ragRequired() ? "执行" : "跳过")
            ));
        }
        if (attachmentPreparationMs > 0) {
            stages.add(new ChatTraceStage(
                    "attachments",
                    "附件解析",
                    attachmentPreparationMs,
                    "DONE",
                    hasAttachments ? "已完成附件预处理" : "无附件"
            ));
        }
        if (retrievalSummary != null) {
            if (retrievalSummary.rewriteInfo() != null && retrievalSummary.rewriteInfo().rewriteApplied()) {
                stages.add(new ChatTraceStage(
                        "query_rewrite",
                        "Query 改写",
                        retrievalSummary.rewriteInfo().durationMs(),
                        "DONE",
                        "effectiveQuery=" + retrievalSummary.rewriteInfo().effectiveQuery()
                ));
            }
            HybridKnowledgeRetrieverService.RetrievalTimings timings = retrievalSummary.timings();
            if (timings != null) {
                stages.add(new ChatTraceStage(
                        "retrieve_total",
                        "检索总耗时",
                        timings.retrievalDurationMs(),
                        "DONE",
                        "关键词 " + retrievalSummary.retrievedCountKeyword()
                                + " / 向量 " + retrievalSummary.retrievedCountVector()
                                + " / 最终 " + retrievalSummary.finalHits().size()
                ));
                stages.add(new ChatTraceStage(
                        "retrieve_keyword",
                        "关键词召回",
                        timings.keywordDurationMs(),
                        "DONE",
                        "queryType=" + retrievalSummary.queryType()
                ));
                stages.add(new ChatTraceStage(
                        "retrieve_vector",
                        "向量召回",
                        timings.vectorDurationMs(),
                        timings.vectorStatus(),
                        switch (timings.vectorStatus()) {
                            case "DEGRADED" -> "向量召回超时，已降级";
                            case "ERROR" -> "向量召回异常，已降级";
                            case "SKIPPED" -> "未执行向量检索";
                            default -> "执行向量检索";
                        }
                ));
                stages.add(new ChatTraceStage(
                        "retrieve_merge",
                        "结果合并",
                        timings.mergeDurationMs(),
                        "DONE",
                        "merged=" + retrievalSummary.mergedCount()
                ));
            }
        }
        if (retrievalSummary == null && intentResult != null && !intentResult.ragRequired()) {
            stages.add(new ChatTraceStage(
                    "retrieve_total",
                    "检索总耗时",
                    0,
                    "SKIPPED",
                    intentResult.ragSkipReason()
            ));
        }
        long normalizedFirstTokenLatencyMs = Math.max(0, firstTokenLatencyMs);
        long retrievalDurationMs = retrievalSummary == null || retrievalSummary.timings() == null
                ? 0
                : retrievalSummary.timings().retrievalDurationMs();
        long modelWaitMs = Math.max(0, normalizedFirstTokenLatencyMs - retrievalDurationMs);
        if (normalizedFirstTokenLatencyMs > 0) {
            stages.add(new ChatTraceStage("model_wait", "模型首字等待", modelWaitMs, "DONE",
                    "从请求发出到首个响应块返回"));
        }
        long streamOutputMs = normalizedFirstTokenLatencyMs > 0
                ? Math.max(0, metadata.getServerDurationMs() - attachmentPreparationMs - normalizedFirstTokenLatencyMs)
                : 0;
        if (streamOutputMs > 0) {
            stages.add(new ChatTraceStage("stream_output", "流式输出", streamOutputMs, "DONE",
                    "首字后持续输出正文"));
        }
        stages.add(new ChatTraceStage("server_total", "服务总耗时", metadata.getServerDurationMs(), "DONE",
                "provider=" + provider + " / toolMode=" + toolMode));
        return stages;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
