package com.linkjb.aimed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.chat.ChatStreamMetadata;
import com.linkjb.aimed.entity.dto.response.chat.ChatTraceStage;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天流式尾包 metadata 的组装与暂存器。
 *
 * 聊天正文仍按 token/chunk 流出，这里只负责在流尾部追加统一 metadata，
 * 并把审计链路需要的 metadata 暂存在 traceId 维度，供 Controller 在 doFinally 阶段取走。
 */
final class ChatStreamMetadataAssembler {

    private final ObjectMapper objectMapper;
    private final HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService;
    private final Logger log;
    private final Map<String, ChatStreamMetadata> completedStreamMetadata = new ConcurrentHashMap<>();

    ChatStreamMetadataAssembler(ObjectMapper objectMapper,
                                HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService,
                                Logger log) {
        this.objectMapper = objectMapper;
        this.hybridKnowledgeRetrieverService = hybridKnowledgeRetrieverService;
        this.log = log;
    }

    String encodeWithMarker(String marker, ChatStreamMetadata metadata) {
        try {
            // metadata 作为尾包塞回同一条流里，前端只要认这个 marker，就能把“回答正文”和“引用摘要”拆开显示。
            return marker + objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            log.warn("chat.stream.metadata.encode.failed", exception);
        }
        return "";
    }

    ChatStreamMetadata buildAndRemember(String traceId,
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

    ChatStreamMetadata consume(String traceId) {
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
            stages.add(new ChatTraceStage(
                    "retrieve",
                    "知识检索",
                    retrievalSummary.durationMs(),
                    "DONE",
                    "关键词 " + retrievalSummary.retrievedCountKeyword()
                            + " / 向量 " + retrievalSummary.retrievedCountVector()
                            + " / 最终 " + retrievalSummary.finalHits().size()
            ));
            if (timings != null) {
                stages.add(new ChatTraceStage("retrieve_keyword", "关键词召回", timings.keywordDurationMs(), "DONE",
                        "queryType=" + retrievalSummary.queryType()));
                stages.add(new ChatTraceStage(
                        "retrieve_vector",
                        "向量召回",
                        timings.vectorDurationMs(),
                        timings.vectorSkipped() ? "SKIPPED" : "DONE",
                        timings.vectorSkipped() ? "未执行向量检索" : "执行向量检索"
                ));
                stages.add(new ChatTraceStage("retrieve_merge", "结果合并", timings.mergeDurationMs(), "DONE",
                        "merged=" + retrievalSummary.mergedCount()));
            }
        }
        if (retrievalSummary == null && intentResult != null && !intentResult.ragRequired()) {
            stages.add(new ChatTraceStage(
                    "retrieve",
                    "知识检索",
                    0,
                    "SKIPPED",
                    intentResult.ragSkipReason()
            ));
        }
        long normalizedFirstTokenLatencyMs = Math.max(0, firstTokenLatencyMs);
        long retrievalDurationMs = retrievalSummary == null ? 0 : retrievalSummary.durationMs();
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
