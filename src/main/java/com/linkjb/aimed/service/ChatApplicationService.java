package com.linkjb.aimed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.bean.chat.ChatStreamMetadata;
import com.linkjb.aimed.bean.chat.ChatTraceStage;
import com.linkjb.aimed.assistant.LocalStreamingAiMedAgent;
import com.linkjb.aimed.assistant.OnlineFastAiMedAgent;
import com.linkjb.aimed.assistant.OnlineAiMedAgent;
import com.linkjb.aimed.bean.ChatProviderConfigResponse;
import com.linkjb.aimed.config.RequestTraceFilter;
import com.linkjb.aimed.config.TraceIdProvider;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.ConsumerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 聊天编排服务。
 *
 * 这个类不直接负责“大模型怎么回答”，而是负责把聊天请求编排成一条完整链路：
 * - 选择本地还是在线模型
 * - 处理附件增强
 * - 统一流式输出
 * - 把检索摘要转换成前端可消费的引用 metadata
 * - 把同一条请求的 traceId 和检索摘要挂回审计链路
 *
 * 也因此，这里最重要的不是 prompt 本身，而是“回答正文”和“RAG 摘要”如何在同一条流里稳定共存。
 */
@Service
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);
    public static final String LOCAL_OLLAMA = "LOCAL_OLLAMA";
    public static final String QWEN_ONLINE = "QWEN_ONLINE";
    public static final String QWEN_ONLINE_FAST = "QWEN_ONLINE_FAST";
    public static final String QWEN_ONLINE_DEEP = "QWEN_ONLINE_DEEP";
    public static final String STREAM_METADATA_MARKER = "\n\n[[AIMED_STREAM_METADATA]]";

    private final LocalStreamingAiMedAgent localStreamingAiMedAgent;
    private final OnlineAiMedAgent onlineAiMedAgent;
    private final OnlineFastAiMedAgent onlineFastAiMedAgent;
    private final KnowledgeBaseService knowledgeBaseService;
    private final HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService;
    private final VisionChatService visionChatService;
    private final TraceIdProvider traceIdProvider;
    private final ObjectMapper objectMapper;
    private final String defaultProvider;
    private final boolean localProviderEnabled;
    private final boolean onlineProviderEnabled;
    private final Map<String, ChatStreamMetadata> completedStreamMetadata = new ConcurrentHashMap<>();

    public ChatApplicationService(LocalStreamingAiMedAgent localStreamingAiMedAgent,
                                  OnlineAiMedAgent onlineAiMedAgent,
                                  OnlineFastAiMedAgent onlineFastAiMedAgent,
                                  KnowledgeBaseService knowledgeBaseService,
                                  HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService,
                                  VisionChatService visionChatService,
                                  TraceIdProvider traceIdProvider,
                                  ObjectMapper objectMapper,
                                  @Value("${app.provider.default:LOCAL_OLLAMA}") String defaultProvider,
                                  @Value("${app.provider.local-enabled:true}") boolean localProviderEnabled,
                                  @Value("${app.provider.online-enabled:true}") boolean onlineProviderEnabled) {
        this.localStreamingAiMedAgent = localStreamingAiMedAgent;
        this.onlineAiMedAgent = onlineAiMedAgent;
        this.onlineFastAiMedAgent = onlineFastAiMedAgent;
        this.knowledgeBaseService = knowledgeBaseService;
        this.hybridKnowledgeRetrieverService = hybridKnowledgeRetrieverService;
        this.visionChatService = visionChatService;
        this.traceIdProvider = traceIdProvider;
        this.objectMapper = objectMapper;
        this.defaultProvider = defaultProvider;
        this.localProviderEnabled = localProviderEnabled;
        this.onlineProviderEnabled = onlineProviderEnabled;
    }

    public Flux<String> chat(Long memoryId, String message, String modelProvider) {
        String provider = normalizeProvider(modelProvider);
        ChatRuntimeTrace runtimeTrace = new ChatRuntimeTrace(traceIdProvider.currentTraceId(), provider, System.nanoTime(), 0L, false);
        log.info("chat.request provider={} memoryId={} chars={} attachments=0", provider, memoryId, textLength(message));
        return startChat(provider, memoryId, message, message, runtimeTrace)
                .onErrorResume(error -> {
                    log.error("chat.request.failed provider={} memoryId={} chars={}", provider, memoryId, textLength(message), error);
                    return Flux.just(errorMessageForProvider(provider));
                });
    }

    public Flux<String> chatWithFiles(Long memoryId, String message, String modelProvider, MultipartFile[] files) {
        String provider = normalizeProvider(modelProvider);
        long requestStartedAt = System.nanoTime();
        log.info("chat.request provider={} memoryId={} chars={} attachments={}", provider, memoryId, textLength(message), fileCount(files));
        try {
            if (knowledgeBaseService.hasImageAttachments(files)) {
                return Flux.just(visionChatService.analyze(memoryId, message, files, provider));
            }
            String augmentedMessage = knowledgeBaseService.buildChatMessageWithAttachments(message, files);
            ChatRuntimeTrace runtimeTrace = new ChatRuntimeTrace(
                    traceIdProvider.currentTraceId(),
                    provider,
                    requestStartedAt,
                    durationMs(requestStartedAt),
                    fileCount(files) > 0
            );
            return startChat(provider, memoryId, augmentedMessage, message, runtimeTrace)
                    .onErrorResume(error -> {
                        log.error("chat.request.failed provider={} memoryId={} attachments={}", provider, memoryId, fileCount(files), error);
                        return Flux.just(errorMessageForProvider(provider));
                    });
        } catch (Exception error) {
            log.error("chat.attachment.parse.failed provider={} memoryId={} attachments={}", provider, memoryId, fileCount(files), error);
            return Flux.just("抱歉，上传文件暂时无法解析：" + error.getMessage());
        }
    }

    public ChatProviderConfigResponse providerConfig() {
        List<String> enabledProviders = new ArrayList<>(3);
        if (localProviderEnabled) {
            enabledProviders.add(LOCAL_OLLAMA);
        }
        if (onlineProviderEnabled) {
            enabledProviders.add(QWEN_ONLINE_FAST);
            enabledProviders.add(QWEN_ONLINE_DEEP);
        }
        return new ChatProviderConfigResponse(normalizeProvider(defaultProvider), enabledProviders);
    }

    private Flux<String> startChat(String provider,
                                   Long memoryId,
                                   String message,
                                   String summaryFallbackQuery,
                                   ChatRuntimeTrace runtimeTrace) {
        if (isOnlineProvider(provider)) {
            boolean isFastProvider = QWEN_ONLINE_FAST.equals(provider);
            runtimeTrace.setToolMode(isFastProvider ? "FAST" : "DEEP");
            Flux<String> source = isFastProvider
                    ? onlineFastAiMedAgent.chat(memoryId, message)
                    : onlineAiMedAgent.chat(memoryId, message);
            log.info("chat.online.path memoryId={} toolMode={} chars={}",
                    memoryId, isFastProvider ? "fast" : "deep", textLength(message));
            // 在线模型本身已经是 Flux，因此正文先自然流出，等流结束后再把检索摘要拼成一个 metadata 尾包。
            // 这样前端既能第一时间看到回答，也不会因为等引用而卡住首屏输出。
            return withFluxLogs(provider, memoryId, message, source, runtimeTrace)
                    .concatWith(Flux.defer(() -> {
                        HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary = resolveRetrievalSummary(provider, summaryFallbackQuery);
                        ChatStreamMetadata metadata = buildStreamMetadata(runtimeTrace, retrievalSummary);
                        rememberStreamMetadata(runtimeTrace.traceId(), metadata);
                        logRetrievalSummary(provider, memoryId, retrievalSummary);
                        String metadataChunk = encodeStreamMetadata(metadata);
                        return StringUtils.hasText(metadataChunk) ? Flux.just(metadataChunk) : Flux.empty();
                    }));
        }
        // 本地 Ollama 仍按 token 流输出，前端继续用统一的流式解码逻辑消费。
        // 这里多做了一层“最终文本兜底”，是因为本地模型偶发会出现 partial 很少、complete 里才有稳定正文的情况。
        TokenStream tokenStream = localStreamingAiMedAgent.chat(memoryId, message);
        return Flux.create(emitter -> {
            long startedAt = System.nanoTime();
            AtomicInteger chunkCount = new AtomicInteger();
            AtomicLong charCount = new AtomicLong();
            StringBuilder streamedText = new StringBuilder();
            runtimeTrace.setToolMode("STANDARD");
            String requestTraceId = runtimeTrace.traceId();
            log.info("model.stream.start provider={} memoryId={} chars={}", provider, memoryId, textLength(message));
            tokenStream
                    .onPartialResponse(tracedConsumer(requestTraceId, "chat.local.stream.partial", partial -> {
                        chunkCount.incrementAndGet();
                        charCount.addAndGet(partial == null ? 0 : partial.length());
                        if (runtimeTrace.firstTokenLatencyMs() < 0) {
                            runtimeTrace.setFirstTokenLatencyMs(durationMs(runtimeTrace.requestStartedAt()));
                            log.info("model.stream.first_chunk provider={} memoryId={} latencyMs={}",
                                    provider, memoryId, runtimeTrace.firstTokenLatencyMs());
                        }
                        if (partial != null) {
                            streamedText.append(partial);
                        }
                        emitter.next(partial);
                    }))
                    .onCompleteResponse(tracedConsumer(requestTraceId, "chat.local.stream.complete", response -> {
                        HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary = resolveRetrievalSummary(provider, summaryFallbackQuery);
                        ChatStreamMetadata metadata = buildStreamMetadata(runtimeTrace, retrievalSummary);
                        rememberStreamMetadata(requestTraceId, metadata);
                        logRetrievalSummary(provider, memoryId, retrievalSummary);
                        String finalText = finalResponseText(response);
                        // 如果本地模型最后没给出稳定正文，就退化成基于 citation 的简短总结，至少保证用户看到的是一句能读懂的话，
                        // 而不是“只有引用标签，没有回答正文”。
                        if (!StringUtils.hasText(finalText) && !StringUtils.hasText(streamedText.toString())) {
                            finalText = fallbackAnswerFromCitations(retrievalSummary);
                        }
                        if (shouldEmitFinalText(streamedText.toString(), finalText)) {
                            emitter.next(finalText);
                        }
                        log.info("model.stream.complete provider={} memoryId={} chunks={} chars={} durationMs={} finishReason={} outputTokens={}",
                                provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt),
                                finishReason(response), outputTokenCount(response));
                        String metadataChunk = encodeStreamMetadata(metadata);
                        if (StringUtils.hasText(metadataChunk)) {
                            emitter.next(metadataChunk);
                        }
                        emitter.complete();
                    }))
                    .onError(tracedConsumer(requestTraceId, "chat.local.stream.error", error -> {
                        log.error("model.stream.failed provider={} memoryId={} chunks={} chars={} durationMs={}",
                                provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt), error);
                        emitter.error(error);
                    }))
                    .start();
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private Flux<String> withFluxLogs(String provider, Long memoryId, String message, Flux<String> source, ChatRuntimeTrace runtimeTrace) {
        long startedAt = System.nanoTime();
        AtomicInteger chunkCount = new AtomicInteger();
        AtomicLong charCount = new AtomicLong();
        AtomicLong firstChunkLatencyMs = new AtomicLong(-1);
        log.info("model.stream.start provider={} memoryId={} chars={}", provider, memoryId, textLength(message));
        // 在线和本地最后都要汇总成同一套监控口径，所以这里把 chunk 数、字符数、耗时统一记下来。
        return source
                .doOnNext(chunk -> {
                    chunkCount.incrementAndGet();
                    charCount.addAndGet(chunk == null ? 0 : chunk.length());
                    if (firstChunkLatencyMs.compareAndSet(-1, durationMs(startedAt))) {
                        runtimeTrace.setFirstTokenLatencyMs(durationMs(runtimeTrace.requestStartedAt()));
                        log.info("model.stream.first_chunk provider={} memoryId={} latencyMs={}",
                                provider, memoryId, runtimeTrace.firstTokenLatencyMs());
                    }
                })
                .doOnComplete(() -> log.info("model.stream.complete provider={} memoryId={} chunks={} chars={} durationMs={}",
                        provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt)))
                .doOnError(error -> log.error("model.stream.failed provider={} memoryId={} chunks={} chars={} durationMs={}",
                        provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt), error));
    }

    public String normalizeProvider(String provider) {
        String requested = provider == null ? defaultProvider : provider;
        String normalized = requested.trim().toUpperCase(Locale.ROOT);
        if (QWEN_ONLINE.equals(normalized) || QWEN_ONLINE_FAST.equals(normalized)) {
            return onlineProviderEnabled ? QWEN_ONLINE_FAST : fallbackProvider();
        }
        if (QWEN_ONLINE_DEEP.equals(normalized)) {
            return onlineProviderEnabled ? QWEN_ONLINE_DEEP : fallbackProvider();
        }
        return localProviderEnabled ? LOCAL_OLLAMA : fallbackProvider();
    }

    private String fallbackProvider() {
        if (localProviderEnabled) {
            return LOCAL_OLLAMA;
        }
        if (onlineProviderEnabled) {
            return QWEN_ONLINE_FAST;
        }
        throw new IllegalStateException("未启用任何可用的大模型提供方，请检查 app.provider.* 配置");
    }

    private String errorMessageForProvider(String provider) {
        if (isOnlineProvider(provider)) {
            return "抱歉，当前千问在线服务暂时不可用，请稍后重试。若持续失败，请检查百炼网络连接。";
        }
        return "抱歉，当前本地 Ollama 模型暂时不可用，请检查本机 Ollama 服务和模型是否已启动。";
    }

    private void logRetrievalSummary(String provider, Long memoryId, HybridKnowledgeRetrieverService.RetrievalSummary summary) {
        if (summary == null) {
            return;
        }
        log.info("rag.retrieve.summary provider={} memoryId={} queryType={} keywordCount={} vectorCount={} mergedCount={} finalCitationCount={} emptyRecall={} durationMs={} topDocHashes={}",
                provider,
                memoryId,
                summary.queryType(),
                summary.retrievedCountKeyword(),
                summary.retrievedCountVector(),
                summary.mergedCount(),
                summary.finalHits().size(),
                summary.emptyRecall(),
                summary.durationMs(),
                summary.finalHits().stream().map(HybridKnowledgeRetrieverService.RetrievedChunk::fileHash).distinct().limit(5).toList());
    }

    private String encodeStreamMetadata(ChatStreamMetadata metadata) {
        try {
            // metadata 作为尾包塞回同一条流里，前端只要认这个 marker，就能把“回答正文”和“引用摘要”拆开显示。
            return STREAM_METADATA_MARKER + objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            log.warn("chat.stream.metadata.encode.failed", exception);
            return "";
        }
    }

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    private HybridKnowledgeRetrieverService.RetrievalSummary resolveRetrievalSummary(String provider, String fallbackQuery) {
        HybridKnowledgeRetrieverService.RetrievalSummary summary = hybridKnowledgeRetrieverService.consumeLastSummary();
        if (summary != null) {
            return summary;
        }
        // 正常情况下，Agent 内部的 ContentRetriever 会把摘要放进 ThreadLocal。
        // 只有在异常边界或本地模型特殊结束路径下，才需要这里兜底再查一次。
        HybridKnowledgeRetrieverService.RetrievalProfile profile =
                isOnlineProvider(provider)
                        ? HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE
                        : HybridKnowledgeRetrieverService.RetrievalProfile.LOCAL;
        return hybridKnowledgeRetrieverService.search(fallbackQuery, profile);
    }

    private boolean isOnlineProvider(String provider) {
        return QWEN_ONLINE_FAST.equals(provider) || QWEN_ONLINE_DEEP.equals(provider) || QWEN_ONLINE.equals(provider);
    }

    public ChatStreamMetadata consumeCompletedStreamMetadata(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return null;
        }
        return completedStreamMetadata.remove(traceId);
    }

    private void rememberStreamMetadata(String traceId, ChatStreamMetadata metadata) {
        if (!StringUtils.hasText(traceId) || metadata == null) {
            return;
        }
        completedStreamMetadata.put(traceId, metadata);
    }

    private int fileCount(MultipartFile[] files) {
        return files == null ? 0 : files.length;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private ChatStreamMetadata buildStreamMetadata(ChatRuntimeTrace runtimeTrace,
                                                   HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary) {
        ChatStreamMetadata metadata = hybridKnowledgeRetrieverService.toChatStreamMetadata(retrievalSummary);
        metadata.setTraceId(runtimeTrace.traceId());
        metadata.setProvider(runtimeTrace.provider());
        metadata.setToolMode(runtimeTrace.toolMode());
        metadata.setServerDurationMs(durationMs(runtimeTrace.requestStartedAt()));
        metadata.setFirstTokenLatencyMs(Math.max(0, runtimeTrace.firstTokenLatencyMs()));
        metadata.setTraceStages(buildTraceStages(runtimeTrace, retrievalSummary, metadata));
        return metadata;
    }

    private List<ChatTraceStage> buildTraceStages(ChatRuntimeTrace runtimeTrace,
                                                  HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary,
                                                  ChatStreamMetadata metadata) {
        List<ChatTraceStage> stages = new ArrayList<>();
        if (runtimeTrace.attachmentPreparationMs() > 0) {
            stages.add(new ChatTraceStage(
                    "attachments",
                    "附件解析",
                    runtimeTrace.attachmentPreparationMs(),
                    "DONE",
                    runtimeTrace.hasAttachments() ? "已完成附件预处理" : "无附件"
            ));
        }
        if (retrievalSummary != null) {
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
                        timings.vectorSkipped() ? "本次命中强关键词，已跳过向量阶段" : "执行向量检索"
                ));
                stages.add(new ChatTraceStage("retrieve_merge", "结果合并", timings.mergeDurationMs(), "DONE",
                        "merged=" + retrievalSummary.mergedCount()));
            }
        }
        long firstTokenLatencyMs = Math.max(0, metadata.getFirstTokenLatencyMs());
        long retrievalDurationMs = retrievalSummary == null ? 0 : retrievalSummary.durationMs();
        long modelWaitMs = Math.max(0, firstTokenLatencyMs - retrievalDurationMs);
        if (firstTokenLatencyMs > 0) {
            stages.add(new ChatTraceStage("model_wait", "模型首字等待", modelWaitMs, "DONE",
                    "从请求发出到首个响应块返回"));
        }
        long streamOutputMs = firstTokenLatencyMs > 0
                ? Math.max(0, metadata.getServerDurationMs() - runtimeTrace.attachmentPreparationMs() - firstTokenLatencyMs)
                : 0;
        if (streamOutputMs > 0) {
            stages.add(new ChatTraceStage("stream_output", "流式输出", streamOutputMs, "DONE",
                    "首字后持续输出正文"));
        }
        stages.add(new ChatTraceStage("server_total", "服务总耗时", metadata.getServerDurationMs(), "DONE",
                "provider=" + runtimeTrace.provider() + " / toolMode=" + runtimeTrace.toolMode()));
        return stages;
    }

    private String finishReason(ChatResponse response) {
        if (response == null || response.finishReason() == null) {
            return "UNKNOWN";
        }
        return response.finishReason().name();
    }

    private Integer outputTokenCount(ChatResponse response) {
        if (response == null || response.tokenUsage() == null) {
            return null;
        }
        return response.tokenUsage().outputTokenCount();
    }

    private String finalResponseText(ChatResponse response) {
        if (response == null || response.aiMessage() == null) {
            return "";
        }
        // 优先返回真正给用户看的 text；只有 text 缺失时，才退回 thinking，避免把“思考过程”当成主回答丢给前端。
        if (StringUtils.hasText(response.aiMessage().text())) {
            return response.aiMessage().text();
        }
        return response.aiMessage().thinking();
    }

    private boolean shouldEmitFinalText(String streamedText, String finalText) {
        if (!StringUtils.hasText(finalText)) {
            return false;
        }
        if (!StringUtils.hasText(streamedText)) {
            return true;
        }
        String normalizedStreamed = streamedText.trim();
        String normalizedFinal = finalText.trim();
        return !normalizedFinal.equals(normalizedStreamed) && !normalizedStreamed.endsWith(normalizedFinal);
    }

    private String fallbackAnswerFromCitations(HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary) {
        if (retrievalSummary == null || retrievalSummary.finalHits().isEmpty()) {
            return "当前模型没有产出稳定正文，但系统已完成检索。请稍后重试，或切换到千问在线。";
        }
        // 这是最后一级兜底：宁可给一个基于命中片段的短总结，也不要让用户只看到引用而完全看不到回答。
        List<String> snippets = retrievalSummary.finalHits().stream()
                .map(HybridKnowledgeRetrieverService.RetrievedChunk::preview)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(2)
                .toList();
        StringBuilder builder = new StringBuilder("根据已命中的知识资料，先给你一个简要总结：\n\n");
        for (int i = 0; i < snippets.size(); i++) {
            builder.append(i + 1).append(". ").append(snippets.get(i)).append('\n');
        }
        builder.append("\n如需更完整分析，建议重试一次，或切换到千问在线继续提问。");
        return builder.toString();
    }

    private <T> Consumer<T> tracedConsumer(String requestTraceId, String operationName, Consumer<T> delegate) {
        return ConsumerWrapper.of(value -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                // 本地模型的回调线程会脱离原始 Web 请求线程，这里把 trace 和日志上下文补回去，
                // 这样 SkyWalking span 与业务日志能继续挂在同一条链路上。
                ActiveSpan.setOperationName(operationName);
                String currentTraceId = traceIdProvider.currentTraceId();
                String effectiveTraceId = currentTraceId == null || currentTraceId.isBlank() ? requestTraceId : currentTraceId;
                if (effectiveTraceId != null && !effectiveTraceId.isBlank()) {
                    MDC.put(RequestTraceFilter.TRACE_ID_KEY, effectiveTraceId);
                } else {
                    MDC.remove(RequestTraceFilter.TRACE_ID_KEY);
                }
                delegate.accept(value);
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        });
    }

    private static final class ChatRuntimeTrace {
        private final String traceId;
        private final String provider;
        private final long requestStartedAt;
        private final long attachmentPreparationMs;
        private final boolean hasAttachments;
        private volatile long firstTokenLatencyMs = -1;
        private volatile String toolMode = "STANDARD";

        private ChatRuntimeTrace(String traceId,
                                 String provider,
                                 long requestStartedAt,
                                 long attachmentPreparationMs,
                                 boolean hasAttachments) {
            this.traceId = traceId;
            this.provider = provider;
            this.requestStartedAt = requestStartedAt;
            this.attachmentPreparationMs = attachmentPreparationMs;
            this.hasAttachments = hasAttachments;
        }

        private String traceId() {
            return traceId;
        }

        private String provider() {
            return provider;
        }

        private long requestStartedAt() {
            return requestStartedAt;
        }

        private long attachmentPreparationMs() {
            return attachmentPreparationMs;
        }

        private boolean hasAttachments() {
            return hasAttachments;
        }

        private long firstTokenLatencyMs() {
            return firstTokenLatencyMs;
        }

        private void setFirstTokenLatencyMs(long firstTokenLatencyMs) {
            this.firstTokenLatencyMs = firstTokenLatencyMs;
        }

        private String toolMode() {
            return toolMode;
        }

        private void setToolMode(String toolMode) {
            this.toolMode = toolMode;
        }
    }
}
