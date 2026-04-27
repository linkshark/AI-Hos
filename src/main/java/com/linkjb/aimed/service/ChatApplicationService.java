package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.chat.ChatStreamEvent;
import com.linkjb.aimed.entity.dto.response.chat.ChatStreamMetadata;
import com.linkjb.aimed.assistant.LocalDirectAiMedAgent;
import com.linkjb.aimed.assistant.LocalStreamingAiMedAgent;
import com.linkjb.aimed.assistant.OnlineDirectAiMedAgent;
import com.linkjb.aimed.assistant.OnlineFastAiMedAgent;
import com.linkjb.aimed.assistant.OnlineFastDirectAiMedAgent;
import com.linkjb.aimed.assistant.OnlineAiMedAgent;
import com.linkjb.aimed.entity.dto.response.ChatProviderConfigResponse;
import com.linkjb.aimed.config.skywalk.RequestTraceFilter;
import com.linkjb.aimed.config.skywalk.TraceIdProvider;
import com.linkjb.aimed.service.chat.ChatMetadataService;
import com.linkjb.aimed.service.chat.ChatRoutingResult;
import com.linkjb.aimed.service.chat.ChatRoutingService;
import com.linkjb.aimed.service.chat.mcp.McpExecutionResult;
import com.linkjb.aimed.service.chat.mcp.McpExecutionService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.ConsumerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
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
    public static final String LOCAL_OMLX = "LOCAL_OMLX";
    /** 兼容旧前端缓存和历史请求值；当前本地模型实际走 OMLX。 */
    public static final String LOCAL_OLLAMA = "LOCAL_OLLAMA";
    public static final String QWEN_ONLINE = "QWEN_ONLINE";
    public static final String QWEN_ONLINE_FAST = "QWEN_ONLINE_FAST";
    public static final String QWEN_ONLINE_DEEP = "QWEN_ONLINE_DEEP";
    public static final String STREAM_EVENT_MARKER = "\n\n[[AIMED_STREAM_EVENT]]";
    public static final String STREAM_METADATA_MARKER = "\n\n[[AIMED_STREAM_METADATA]]";
    private static final String LOCAL_PROMPT_RESOURCE = "prompt-templates/aimed-prompt-template-local.txt";
    private static final String ONLINE_PROMPT_RESOURCE = "prompt-templates/aimed-prompt-template.txt";
    private static final String ONLINE_FAST_PROMPT_RESOURCE = "prompt-templates/aimed-prompt-template-online-fast.txt";
    private static final Map<String, String> PROMPT_TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private final LocalStreamingAiMedAgent localStreamingAiMedAgent;
    private final LocalDirectAiMedAgent localDirectAiMedAgent;
    private final OnlineAiMedAgent onlineAiMedAgent;
    private final OnlineDirectAiMedAgent onlineDirectAiMedAgent;
    private final OnlineFastAiMedAgent onlineFastAiMedAgent;
    private final OnlineFastDirectAiMedAgent onlineFastDirectAiMedAgent;
    private final KnowledgeBaseService knowledgeBaseService;
    private final HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService;
    private final VisionChatService visionChatService;
    private final TraceIdProvider traceIdProvider;
    private final ChatProviderPolicy chatProviderPolicy;
    private final ChatIntentAnalysisService chatIntentAnalysisService;
    private final ChatRoutingService chatRoutingService;
    private final ChatMetadataService chatMetadataService;
    private final McpExecutionService mcpExecutionService;
    private final StreamingChatModel localStreamingChatModel;
    private final StreamingChatModel onlineFastStreamingChatModel;
    private final StreamingChatModel onlineDeepStreamingChatModel;

    public ChatApplicationService(LocalStreamingAiMedAgent localStreamingAiMedAgent,
                                  LocalDirectAiMedAgent localDirectAiMedAgent,
                                  OnlineAiMedAgent onlineAiMedAgent,
                                  OnlineDirectAiMedAgent onlineDirectAiMedAgent,
                                  OnlineFastAiMedAgent onlineFastAiMedAgent,
                                  OnlineFastDirectAiMedAgent onlineFastDirectAiMedAgent,
                                  KnowledgeBaseService knowledgeBaseService,
                                  HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService,
                                  VisionChatService visionChatService,
                                  TraceIdProvider traceIdProvider,
                                  ChatIntentAnalysisService chatIntentAnalysisService,
                                  McpServerAdminService mcpServerAdminService,
                                  McpToolInvocationPlanner mcpToolInvocationPlanner,
                                  @Qualifier("localStreamingChatModel") StreamingChatModel localStreamingChatModel,
                                  @Qualifier("onlineFastStreamingChatModel") StreamingChatModel onlineFastStreamingChatModel,
                                  @Qualifier("onlineDeepStreamingChatModel") StreamingChatModel onlineDeepStreamingChatModel,
                                  ObjectMapper objectMapper,
                                  @Value("${app.provider.default:LOCAL_OMLX}") String defaultProvider,
                                  @Value("${app.provider.local-enabled:true}") boolean localProviderEnabled,
                                  @Value("${app.provider.online-enabled:true}") boolean onlineProviderEnabled) {
        this.localStreamingAiMedAgent = localStreamingAiMedAgent;
        this.localDirectAiMedAgent = localDirectAiMedAgent;
        this.onlineAiMedAgent = onlineAiMedAgent;
        this.onlineDirectAiMedAgent = onlineDirectAiMedAgent;
        this.onlineFastAiMedAgent = onlineFastAiMedAgent;
        this.onlineFastDirectAiMedAgent = onlineFastDirectAiMedAgent;
        this.knowledgeBaseService = knowledgeBaseService;
        this.hybridKnowledgeRetrieverService = hybridKnowledgeRetrieverService;
        this.visionChatService = visionChatService;
        this.traceIdProvider = traceIdProvider;
        this.chatIntentAnalysisService = chatIntentAnalysisService == null ? new ChatIntentAnalysisService() : chatIntentAnalysisService;
        this.chatRoutingService = new ChatRoutingService(this.chatIntentAnalysisService);
        this.chatMetadataService = new ChatMetadataService(objectMapper, hybridKnowledgeRetrieverService);
        this.mcpExecutionService = new McpExecutionService(mcpServerAdminService, mcpToolInvocationPlanner);
        this.localStreamingChatModel = localStreamingChatModel;
        this.onlineFastStreamingChatModel = onlineFastStreamingChatModel;
        this.onlineDeepStreamingChatModel = onlineDeepStreamingChatModel;
        this.chatProviderPolicy = new ChatProviderPolicy(defaultProvider, localProviderEnabled, onlineProviderEnabled);
    }

    public Flux<String> chat(Long memoryId, String message, String modelProvider) {
        String provider = normalizeProvider(modelProvider);
        ChatRuntimeTrace runtimeTrace = new ChatRuntimeTrace(traceIdProvider.currentTraceId(), provider, System.nanoTime(), 0L, false);
        log.info("chat.request provider={} memoryId={} chars={} attachments=0", provider, memoryId, textLength(message));
        return startChat(provider, memoryId, message, message, runtimeTrace)
                .onErrorResume(error -> {
                    log.error("chat.request.failed provider={} memoryId={} chars={}", provider, memoryId, textLength(message), error);
                    return Flux.just(chatProviderPolicy.errorMessageForProvider(provider));
                });
    }

    public Flux<String> chatWithFiles(Long memoryId, String message, String modelProvider, MultipartFile[] files) {
        String provider = normalizeProvider(modelProvider);
        long requestStartedAt = System.nanoTime();
        log.info("chat.request provider={} memoryId={} chars={} attachments={}", provider, memoryId, textLength(message), fileCount(files));
        try {
            if (knowledgeBaseService.hasImageAttachments(files)) {
                ChatRuntimeTrace runtimeTrace = new ChatRuntimeTrace(
                        traceIdProvider.currentTraceId(),
                        provider,
                        requestStartedAt,
                        durationMs(requestStartedAt),
                        fileCount(files) > 0
                );
                ChatIntentAnalysisService.ChatIntentResult visionIntentResult =
                        new ChatIntentAnalysisService.ChatIntentResult("VISION_QA", "VISION", false,
                                "图片问答走视觉模型，不触发知识库检索", "图片附件命中视觉问答链路", 0.95d);
                runtimeTrace.setToolMode("VISION");
                return withFluxLogs(provider, memoryId, visionChatService.analyzeStream(memoryId, message, files, provider), runtimeTrace)
                        .concatWith(Flux.defer(() -> emitMetadataChunk(provider, memoryId, message, message, runtimeTrace, visionIntentResult)))
                        .onErrorResume(error -> {
                            log.error("chat.request.failed provider={} memoryId={} attachments={}", provider, memoryId, fileCount(files), error);
                            return Flux.just(chatProviderPolicy.errorMessageForProvider(provider));
                        });
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
                        return Flux.just(chatProviderPolicy.errorMessageForProvider(provider));
                    });
        } catch (Exception error) {
            log.error("chat.attachment.parse.failed provider={} memoryId={} attachments={}", provider, memoryId, fileCount(files), error);
            return Flux.just("抱歉，上传文件暂时无法解析：" + error.getMessage());
        }
    }

    public ChatProviderConfigResponse providerConfig() {
        return chatProviderPolicy.providerConfig();
    }

    private Flux<String> startChat(String provider,
                                   Long memoryId,
                                   String message,
                                   String summaryFallbackQuery,
                                   ChatRuntimeTrace runtimeTrace) {
        ChatRoutingResult routingResult = chatRoutingService.analyze(summaryFallbackQuery);
        ChatIntentAnalysisService.ChatIntentResult intentResult = routingResult.intentResult();
        log.info("chat.intent provider={} memoryId={} intent={} routeTarget={} ragApplied={} reason={}",
                provider, memoryId, intentResult.intentType(), intentResult.routeTarget(), intentResult.ragRequired(), intentResult.reason());
        if (routingResult.mcpIntent()) {
            return startMcpChat(provider, memoryId, message, summaryFallbackQuery, runtimeTrace, intentResult);
        }
        if (chatProviderPolicy.isOnlineProvider(provider)) {
            return startOnlineChat(provider, memoryId, message, summaryFallbackQuery, runtimeTrace, intentResult);
        }
        return startLocalChat(provider, memoryId, message, summaryFallbackQuery, runtimeTrace, intentResult);
    }

    private Flux<String> startOnlineChat(String provider,
                                         Long memoryId,
                                         String message,
                                         String summaryFallbackQuery,
                                         ChatRuntimeTrace runtimeTrace,
                                         ChatIntentAnalysisService.ChatIntentResult intentResult) {
        boolean isFastProvider = QWEN_ONLINE_FAST.equals(provider);
        runtimeTrace.setToolMode(isFastProvider ? "FAST" : "DEEP");
        Flux<String> source = selectOnlineSource(isFastProvider, intentResult.ragRequired(), memoryId, message);
        log.info("chat.online.path memoryId={} toolMode={} ragApplied={} chars={}",
                memoryId, isFastProvider ? "fast" : "deep", intentResult.ragRequired(), textLength(message));
        // 在线模型本身已经是 Flux，因此正文先自然流出，等流结束后再把检索摘要拼成一个 metadata 尾包。
        // 这样前端既能第一时间看到回答，也不会因为等引用而卡住首屏输出。
        return withFluxLogs(provider, memoryId, source, runtimeTrace)
                .concatWith(Flux.defer(() -> emitMetadataChunk(provider, memoryId, message, summaryFallbackQuery, runtimeTrace, intentResult)));
    }

    private Flux<String> startLocalChat(String provider,
                                        Long memoryId,
                                        String message,
                                        String summaryFallbackQuery,
                                        ChatRuntimeTrace runtimeTrace,
                                        ChatIntentAnalysisService.ChatIntentResult intentResult) {
        // 本地模型仍按 token 流输出，前端继续用统一的流式解码逻辑消费。
        // 这里多做了一层“最终文本兜底”，是因为本地模型偶发会出现 partial 很少、complete 里才有稳定正文的情况。
        TokenStream tokenStream = selectLocalTokenStream(memoryId, message, intentResult);
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
                        HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary = resolveRetrievalSummary(
                                provider,
                                memoryId,
                                message,
                                summaryFallbackQuery,
                                intentResult
                        );
                        ChatStreamMetadata metadata = buildStreamMetadata(runtimeTrace, intentResult, retrievalSummary);
                        logRetrievalSummary(provider, memoryId, retrievalSummary);
                        String finalText = finalResponseText(response);
                        // 如果本地模型最后没给出稳定正文，就退化成基于 citation 的简短总结，至少保证用户看到的是一句能读懂的话，
                        // 而不是“只有引用标签，没有回答正文”。
                        if (!StringUtils.hasText(finalText) && !StringUtils.hasText(streamedText.toString())) {
                            finalText = intentResult.ragRequired()
                                    ? fallbackAnswerFromCitations(retrievalSummary, summaryFallbackQuery)
                                    : fallbackAnswerForSkippedRag(intentResult);
                        }
                        if (shouldEmitFinalText(streamedText.toString(), finalText)) {
                            emitter.next(finalText);
                        }
                        log.info("model.stream.complete provider={} memoryId={} chunks={} chars={} durationMs={} finishReason={} outputTokens={}",
                                provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt),
                                finishReason(response), outputTokenCount(response));
                        String metadataChunk = chatMetadataService.encodeWithMarker(STREAM_METADATA_MARKER, metadata);
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

    private Flux<String> emitMetadataChunk(String provider,
                                           Long memoryId,
                                           String retrievalQuery,
                                           String summaryFallbackQuery,
                                           ChatRuntimeTrace runtimeTrace,
                                           ChatIntentAnalysisService.ChatIntentResult intentResult) {
        HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary = resolveRetrievalSummary(
                provider,
                memoryId,
                retrievalQuery,
                summaryFallbackQuery,
                intentResult
        );
        ChatStreamMetadata metadata = buildStreamMetadata(runtimeTrace, intentResult, retrievalSummary);
        logRetrievalSummary(provider, memoryId, retrievalSummary);
        String metadataChunk = chatMetadataService.encodeWithMarker(STREAM_METADATA_MARKER, metadata);
        return StringUtils.hasText(metadataChunk) ? Flux.just(metadataChunk) : Flux.empty();
    }

    private Flux<String> startMcpChat(String provider,
                                      Long memoryId,
                                      String message,
                                      String summaryFallbackQuery,
                                      ChatRuntimeTrace runtimeTrace,
                                      ChatIntentAnalysisService.ChatIntentResult intentResult) {
        runtimeTrace.setToolMode("MCP");
        return emitStreamEvent(new ChatStreamEvent("MCP", "PLAN", "RUNNING", "工具规划中",
                        "正在选择最匹配的 MCP 工具", null, 0))
                .concatWith(Flux.defer(() -> executeMcpChat(provider, memoryId, message, summaryFallbackQuery, runtimeTrace, intentResult)));
    }

    private Flux<String> executeMcpChat(String provider,
                                        Long memoryId,
                                        String message,
                                        String summaryFallbackQuery,
                                        ChatRuntimeTrace runtimeTrace,
                                        ChatIntentAnalysisService.ChatIntentResult intentResult) {
        McpExecutionResult executionResult = mcpExecutionService.execute(message);
        runtimeTrace.recordMcpPlan(executionResult.planDurationMs(), executionResult.toolName());
        runtimeTrace.recordMcpCall(
                executionResult.callDurationMs(),
                executionResult.toolName(),
                executionResult.callStatus(),
                executionResult.callDetail()
        );
        Flux<String> eventStream = Flux.fromIterable(executionResult.events()).concatMap(this::emitStreamEvent);
        return eventStream.concatWith(summarizeMcpResult(
                provider,
                memoryId,
                message,
                message,
                summaryFallbackQuery,
                executionResult.toolResult(),
                runtimeTrace,
                intentResult
        ));
    }

    private Flux<String> summarizeMcpResult(String provider,
                                            Long memoryId,
                                            String originalMessage,
                                            String retrievalQuery,
                                            String summaryFallbackQuery,
                                            String toolResult,
                                            ChatRuntimeTrace runtimeTrace,
                                            ChatIntentAnalysisService.ChatIntentResult intentResult) {
        StreamingChatModel summaryModel = selectMcpSummaryModel(provider);
        String promptResource = selectPromptResource(provider);
        String summaryPrompt = buildMcpSummaryPrompt(originalMessage, toolResult);
        return streamSummaryModel(
                provider,
                memoryId,
                retrievalQuery,
                summaryFallbackQuery,
                runtimeTrace,
                intentResult,
                summaryModel,
                promptResource,
                summaryPrompt
        );
    }

    private Flux<String> streamSummaryModel(String provider,
                                            Long memoryId,
                                            String retrievalQuery,
                                            String summaryFallbackQuery,
                                            ChatRuntimeTrace runtimeTrace,
                                            ChatIntentAnalysisService.ChatIntentResult intentResult,
                                            StreamingChatModel model,
                                            String promptResource,
                                            String summaryPrompt) {
        List<ChatMessage> messages = List.of(
                SystemMessage.from(loadPromptTemplate(promptResource)),
                UserMessage.from(summaryPrompt)
        );
        return Flux.create(emitter -> {
            long startedAt = System.nanoTime();
            AtomicInteger chunkCount = new AtomicInteger();
            AtomicLong charCount = new AtomicLong();
            StringBuilder streamedText = new StringBuilder();
            String requestTraceId = runtimeTrace.traceId();
            log.info("mcp.summary.stream.start provider={} memoryId={} chars={}", provider, memoryId, summaryPrompt.length());
            model.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    tracedConsumer(requestTraceId, "chat.mcp.summary.partial", (String partial) -> {
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
                    }).accept(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    tracedConsumer(requestTraceId, "chat.mcp.summary.complete", (ChatResponse completedResponse) -> {
                        runtimeTrace.recordMcpResult(durationMs(startedAt));
                        HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary = resolveRetrievalSummary(
                                provider,
                                memoryId,
                                retrievalQuery,
                                summaryFallbackQuery,
                                intentResult
                        );
                        ChatStreamMetadata metadata = buildStreamMetadata(runtimeTrace, intentResult, retrievalSummary);
                        logRetrievalSummary(provider, memoryId, retrievalSummary);
                        String finalText = finalResponseText(completedResponse);
                        if (!StringUtils.hasText(finalText) && !StringUtils.hasText(streamedText.toString())) {
                            finalText = fallbackAnswerForSkippedRag(intentResult);
                        }
                        if (shouldEmitFinalText(streamedText.toString(), finalText)) {
                            emitter.next(finalText);
                        }
                        log.info("mcp.summary.stream.complete provider={} memoryId={} chunks={} chars={} durationMs={} finishReason={} outputTokens={}",
                                provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt),
                                finishReason(completedResponse), outputTokenCount(completedResponse));
                        String metadataChunk = chatMetadataService.encodeWithMarker(STREAM_METADATA_MARKER, metadata);
                        if (StringUtils.hasText(metadataChunk)) {
                            emitter.next(metadataChunk);
                        }
                        emitter.complete();
                    }).accept(response);
                }

                @Override
                public void onError(Throwable error) {
                    tracedConsumer(requestTraceId, "chat.mcp.summary.error", (Throwable throwable) -> {
                        runtimeTrace.recordMcpResult(durationMs(startedAt));
                        log.error("mcp.summary.stream.failed provider={} memoryId={} chunks={} chars={} durationMs={}",
                                provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt), throwable);
                        emitter.error(throwable);
                    }).accept(error);
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private TokenStream selectLocalTokenStream(Long memoryId,
                                               String message,
                                               ChatIntentAnalysisService.ChatIntentResult intentResult) {
        // 本地模型只区分 RAG 与直答两条稳定通道；首字优化放在 oMLX 配置层处理。
        if (intentResult != null && intentResult.ragRequired()) {
            return localStreamingAiMedAgent.chat(memoryId, message);
        }
        return localDirectAiMedAgent.chat(memoryId, message);
    }

    private StreamingChatModel selectMcpSummaryModel(String provider) {
        if (chatProviderPolicy.isOnlineProvider(provider)) {
            return QWEN_ONLINE_FAST.equals(provider) ? onlineFastStreamingChatModel : onlineDeepStreamingChatModel;
        }
        return localStreamingChatModel;
    }

    private String selectPromptResource(String provider) {
        if (chatProviderPolicy.isOnlineProvider(provider)) {
            return QWEN_ONLINE_FAST.equals(provider) ? ONLINE_FAST_PROMPT_RESOURCE : ONLINE_PROMPT_RESOURCE;
        }
        return LOCAL_PROMPT_RESOURCE;
    }

    private Flux<String> emitStreamEvent(ChatStreamEvent event) {
        String eventChunk = chatMetadataService.encodeEventWithMarker(STREAM_EVENT_MARKER, event);
        return StringUtils.hasText(eventChunk) ? Flux.just(eventChunk) : Flux.empty();
    }

    String buildMcpSummaryPrompt(String originalMessage, String toolResult) {
        return """
                当前用户问题：
                %s

                MCP 工具执行结果：
                %s

                回答要求：
                - 这不是第一次对话，不要打招呼，不要自我介绍。
                - 这轮不要使用知识库检索结果。
                - 不要再次调用任何工具。
                - 直接基于工具结果用中文简洁回答用户原问题。
                - 如果工具结果失败、未找到工具或参数不足，明确说明当前限制，并告诉用户下一步该怎么做。
                """.formatted(originalMessage, toolResult);
    }

    private Flux<String> selectOnlineSource(boolean isFastProvider,
                                            boolean ragRequired,
                                            Long memoryId,
                                            String message) {
        if (isFastProvider) {
            return ragRequired
                    ? onlineFastAiMedAgent.chat(memoryId, message)
                    : onlineFastDirectAiMedAgent.chat(memoryId, message);
        }
        return ragRequired
                ? onlineAiMedAgent.chat(memoryId, message)
                : onlineDirectAiMedAgent.chat(memoryId, message);
    }

    private Flux<String> withFluxLogs(String provider, Long memoryId, Flux<String> source, ChatRuntimeTrace runtimeTrace) {
        long startedAt = System.nanoTime();
        AtomicInteger chunkCount = new AtomicInteger();
        AtomicLong charCount = new AtomicLong();
        AtomicLong firstChunkLatencyMs = new AtomicLong(-1);
        log.info("model.stream.start provider={} memoryId={}", provider, memoryId);
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
        return chatProviderPolicy.normalizeProvider(provider);
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

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    HybridKnowledgeRetrieverService.RetrievalSummary resolveRetrievalSummary(String provider,
                                                                             Long memoryId,
                                                                             String retrievalQuery,
                                                                             String fallbackQuery,
                                                                             ChatIntentAnalysisService.ChatIntentResult intentResult) {
        if (intentResult != null && !intentResult.ragRequired()) {
            return null;
        }
        for (String candidateQuery : retrievalSummaryLookupQueries(retrievalQuery, fallbackQuery)) {
            if (!StringUtils.hasText(candidateQuery)) {
                continue;
            }
            HybridKnowledgeRetrieverService.RetrievalSummary summary = hybridKnowledgeRetrieverService.consumeLastSummary(memoryId, candidateQuery);
            if (summary != null) {
                return summary;
            }
        }
        // 正常情况下，检索摘要会先写入显式的 summary store；
        // 只有在异常边界或本地模型特殊结束路径下，才需要这里兜底再查一次。
        HybridKnowledgeRetrieverService.RetrievalProfile profile =
                chatProviderPolicy.isOnlineProvider(provider)
                        ? HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE
                        : HybridKnowledgeRetrieverService.RetrievalProfile.LOCAL;
        String effectiveQuery = StringUtils.hasText(retrievalQuery) ? retrievalQuery : fallbackQuery;
        return hybridKnowledgeRetrieverService.search(effectiveQuery, profile);
    }

    List<String> retrievalSummaryLookupQueries(String retrievalQuery, String fallbackQuery) {
        List<String> queries = new java.util.ArrayList<>(2);
        if (StringUtils.hasText(retrievalQuery)) {
            queries.add(retrievalQuery);
        }
        if (StringUtils.hasText(fallbackQuery) && !fallbackQuery.equals(retrievalQuery)) {
            queries.add(fallbackQuery);
        }
        return queries;
    }

    public ChatStreamMetadata consumeCompletedStreamMetadata(String traceId) {
        return chatMetadataService.consume(traceId);
    }

    private int fileCount(MultipartFile[] files) {
        return files == null ? 0 : files.length;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private ChatStreamMetadata buildStreamMetadata(ChatRuntimeTrace runtimeTrace,
                                                   ChatIntentAnalysisService.ChatIntentResult intentResult,
                                                   HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary) {
        ChatStreamMetadata metadata = chatMetadataService.buildAndRemember(
                runtimeTrace.traceId(),
                runtimeTrace.provider(),
                runtimeTrace.requestStartedAt(),
                runtimeTrace.attachmentPreparationMs(),
                runtimeTrace.hasAttachments(),
                runtimeTrace.firstTokenLatencyMs(),
                runtimeTrace.toolMode(),
                intentResult,
                retrievalSummary
        );
        appendMcpTraceStages(metadata, runtimeTrace);
        return metadata;
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

    String fallbackAnswerFromCitations(HybridKnowledgeRetrieverService.RetrievalSummary retrievalSummary, String query) {
        if (retrievalSummary == null || retrievalSummary.finalHits().isEmpty()) {
            return """
                    结论：
                    当前本地模型没有产出稳定正文，系统也没有检索到足够明确的知识依据，暂时不能给出具体判断。

                    建议：
                    - 请补充年龄、症状持续时间、体温或主要不适表现，便于进一步判断。
                    - 如果症状明显加重，建议及时到医院就诊，由医生结合查体和检查结果评估。

                    何时尽快就医：
                    出现高热不退、精神反应差、呼吸困难、持续呕吐腹泻、明显脱水、意识异常或抽搐时，应尽快就医。

                    说明：
                    本回答是本地模型兜底提示，不替代医生诊断或药师用药指导。
                    """;
        }
        List<String> citationTitles = retrievalSummary.finalHits().stream()
                .map(hit -> StringUtils.hasText(hit.title()) ? hit.title() : hit.documentName())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .toList();
        String normalizedQuery = query == null ? "" : query.trim();
        boolean childSymptom = normalizedQuery.matches(".*(小孩|小孩子|儿童|宝宝|婴儿|幼儿).*")
                && normalizedQuery.matches(".*(感冒|发烧|发热|鼻塞|咳嗽|咽痛|流涕|腹泻|呕吐).*");
        boolean diarrhea = normalizedQuery.matches(".*(腹泻|拉肚子|呕吐|吐|肚子疼|腹痛).*");

        StringBuilder builder = new StringBuilder();
        if (childSymptom) {
            builder.append("""
                    结论：
                    儿童出现感冒、发热、鼻塞、咳嗽或腹泻时，多数需要先观察精神状态、体温、呼吸和补液情况；不要自行叠加多种感冒药或随意使用抗菌药。

                    建议：
                    - 让孩子充分休息、少量多次饮水，饮食清淡，记录体温和症状变化。
                    - 退热药、止咳药或其他药物应按年龄、体重和说明书使用；婴幼儿或有基础病儿童建议先咨询医生或药师。
                    - 如果伴随咳嗽明显、喘息、持续高热、反复呕吐腹泻，建议到儿科或发热门诊评估。

                    何时尽快就医：
                    出现精神差、嗜睡或烦躁、呼吸急促或费力、抽搐、持续高热超过 3 天、尿量明显减少、皮疹、颈部僵硬、血便或明显脱水时，应尽快就医。

                    """);
        } else if (diarrhea) {
            builder.append("""
                    结论：
                    急性呕吐、腹泻或腹痛时，优先关注脱水和病情进展；多数轻症可先补液观察，但不能忽视高危信号。

                    建议：
                    - 少量多次补液，注意尿量、精神状态、发热和大便性状。
                    - 不建议自行使用抗菌药物；儿童、老人、孕产妇或有基础病者应降低就医阈值。
                    - 若近期有集体发病或接触类似患者，应注意手卫生、隔离和环境消毒。

                    何时尽快就医：
                    出现明显脱水、持续高热、血便、剧烈或持续腹痛、频繁呕吐无法进水、尿量明显减少、精神反应差时，应尽快就医。

                    """);
        } else {
            builder.append("""
                    结论：
                    本地模型没有产出稳定正文，系统已完成知识检索。根据当前检索结果，只能给出保守建议，不能直接替代医生诊断。

                    建议：
                    - 请补充年龄、症状持续时间、严重程度、既往病史和正在使用的药物。
                    - 如果问题涉及用药、儿童、孕产妇、老人或慢性病，建议结合医生或药师意见处理。
                    - 如症状持续或加重，建议到对应科室就诊评估。

                    何时尽快就医：
                    出现症状快速加重、意识异常、呼吸困难、胸痛、抽搐、明显脱水、持续高热或剧烈疼痛时，应尽快就医。

                    """);
        }
        builder.append("说明：\n");
        if (citationTitles.isEmpty()) {
            builder.append("本回答是本地模型兜底提示，未直接摘录原文片段，不替代医生诊断或药师用药指导。");
        } else {
            builder.append("本回答参考了已命中的知识资料：")
                    .append(String.join("、", citationTitles))
                    .append("。本回答未直接摘录原文片段，不替代医生诊断或药师用药指导。");
        }
        return builder.toString();
    }

    private String fallbackAnswerForSkippedRag(ChatIntentAnalysisService.ChatIntentResult intentResult) {
        if (intentResult == null) {
            return "当前请求没有触发知识库检索，但模型暂时没有产出稳定回答，请稍后再试。";
        }
        if ("MCP".equals(intentResult.routeTarget())) {
            return "当前工具类请求没有触发知识库检索，但工具暂时没有返回稳定结果，请稍后再试或检查后台 MCP 服务。";
        }
        if ("APPOINTMENT".equals(intentResult.routeTarget())) {
            return "当前挂号请求没有触发知识库检索，但工具暂时没有返回稳定结果。请补充姓名、身份证号、科室、日期和时间后再试。";
        }
        return "当前问题不需要知识库检索，但模型暂时没有产出稳定回答，请换一种说法再试。";
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

    private void appendMcpTraceStages(ChatStreamMetadata metadata, ChatRuntimeTrace runtimeTrace) {
        if (metadata == null || runtimeTrace == null || !"MCP".equals(runtimeTrace.toolMode())) {
            return;
        }
        List<com.linkjb.aimed.entity.dto.response.chat.ChatTraceStage> stages = metadata.getTraceStages();
        int insertIndex = Math.max(0, stages.size() - 1);
        if (runtimeTrace.mcpPlanDurationMs() > 0 || StringUtils.hasText(runtimeTrace.mcpToolName())) {
            stages.add(insertIndex++, new com.linkjb.aimed.entity.dto.response.chat.ChatTraceStage(
                    "mcp_plan",
                    "MCP 规划",
                    runtimeTrace.mcpPlanDurationMs(),
                    StringUtils.hasText(runtimeTrace.mcpToolName()) ? "DONE" : "SKIPPED",
                    StringUtils.hasText(runtimeTrace.mcpToolName()) ? runtimeTrace.mcpToolName() : "未匹配到工具"
            ));
        }
        if (runtimeTrace.mcpCallDurationMs() > 0 || StringUtils.hasText(runtimeTrace.mcpCallStatus())) {
            stages.add(insertIndex++, new com.linkjb.aimed.entity.dto.response.chat.ChatTraceStage(
                    "mcp_call",
                    "MCP 调用",
                    runtimeTrace.mcpCallDurationMs(),
                    "SUCCESS".equals(runtimeTrace.mcpCallStatus()) ? "DONE" : "ERROR",
                    runtimeTrace.mcpCallDetail()
            ));
        }
        if (runtimeTrace.mcpResultDurationMs() > 0) {
            stages.add(insertIndex, new com.linkjb.aimed.entity.dto.response.chat.ChatTraceStage(
                    "mcp_result",
                    "MCP 结果总结",
                    runtimeTrace.mcpResultDurationMs(),
                    "DONE",
                    "基于工具结果生成最终回答"
            ));
        }
    }

    private String loadPromptTemplate(String resourcePath) {
        return PROMPT_TEMPLATE_CACHE.computeIfAbsent(resourcePath, this::readPromptTemplate);
    }

    private String readPromptTemplate(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 prompt 模板: " + resourcePath, exception);
        }
    }

    private static final class ChatRuntimeTrace {
        private final String traceId;
        private final String provider;
        private final long requestStartedAt;
        private final long attachmentPreparationMs;
        private final boolean hasAttachments;
        private volatile long firstTokenLatencyMs = -1;
        private volatile String toolMode = "STANDARD";
        private volatile long mcpPlanDurationMs = 0;
        private volatile long mcpCallDurationMs = 0;
        private volatile long mcpResultDurationMs = 0;
        private volatile String mcpToolName = "";
        private volatile String mcpCallStatus = "";
        private volatile String mcpCallDetail = "";

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

        private void recordMcpPlan(long durationMs, String toolName) {
            this.mcpPlanDurationMs = Math.max(0, durationMs);
            this.mcpToolName = toolName == null ? "" : toolName;
        }

        private void recordMcpCall(long durationMs, String toolName, String status, String detail) {
            this.mcpCallDurationMs = Math.max(0, durationMs);
            this.mcpToolName = toolName == null ? this.mcpToolName : toolName;
            this.mcpCallStatus = status == null ? "" : status;
            this.mcpCallDetail = detail == null ? "" : detail;
        }

        private void recordMcpResult(long durationMs) {
            this.mcpResultDurationMs = Math.max(0, durationMs);
        }

        private long mcpPlanDurationMs() {
            return mcpPlanDurationMs;
        }

        private long mcpCallDurationMs() {
            return mcpCallDurationMs;
        }

        private long mcpResultDurationMs() {
            return mcpResultDurationMs;
        }

        private String mcpToolName() {
            return mcpToolName;
        }

        private String mcpCallStatus() {
            return mcpCallStatus;
        }

        private String mcpCallDetail() {
            return mcpCallDetail;
        }
    }

}
