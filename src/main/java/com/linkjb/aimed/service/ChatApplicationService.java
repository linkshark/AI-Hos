package com.linkjb.aimed.service;

import com.linkjb.aimed.assistant.LocalStreamingAiMedAgent;
import com.linkjb.aimed.assistant.OnlineAiMedAgent;
import com.linkjb.aimed.bean.ChatProviderConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);
    public static final String LOCAL_OLLAMA = "LOCAL_OLLAMA";
    public static final String QWEN_ONLINE = "QWEN_ONLINE";

    private final LocalStreamingAiMedAgent localStreamingAiMedAgent;
    private final OnlineAiMedAgent onlineAiMedAgent;
    private final KnowledgeBaseService knowledgeBaseService;
    private final VisionChatService visionChatService;
    private final String defaultProvider;
    private final boolean localProviderEnabled;
    private final boolean onlineProviderEnabled;

    public ChatApplicationService(LocalStreamingAiMedAgent localStreamingAiMedAgent,
                                  OnlineAiMedAgent onlineAiMedAgent,
                                  KnowledgeBaseService knowledgeBaseService,
                                  VisionChatService visionChatService,
                                  @Value("${app.provider.default:LOCAL_OLLAMA}") String defaultProvider,
                                  @Value("${app.provider.local-enabled:true}") boolean localProviderEnabled,
                                  @Value("${app.provider.online-enabled:true}") boolean onlineProviderEnabled) {
        this.localStreamingAiMedAgent = localStreamingAiMedAgent;
        this.onlineAiMedAgent = onlineAiMedAgent;
        this.knowledgeBaseService = knowledgeBaseService;
        this.visionChatService = visionChatService;
        this.defaultProvider = defaultProvider;
        this.localProviderEnabled = localProviderEnabled;
        this.onlineProviderEnabled = onlineProviderEnabled;
    }

    public Flux<String> chat(Long memoryId, String message, String modelProvider) {
        String provider = normalizeProvider(modelProvider);
        log.info("chat.request provider={} memoryId={} chars={} attachments=0", provider, memoryId, textLength(message));
        return startChat(provider, memoryId, message)
                .onErrorResume(error -> {
                    log.error("chat.request.failed provider={} memoryId={} chars={}", provider, memoryId, textLength(message), error);
                    return Flux.just(errorMessageForProvider(provider));
                });
    }

    public Flux<String> chatWithFiles(Long memoryId, String message, String modelProvider, MultipartFile[] files) {
        String provider = normalizeProvider(modelProvider);
        log.info("chat.request provider={} memoryId={} chars={} attachments={}", provider, memoryId, textLength(message), fileCount(files));
        try {
            if (knowledgeBaseService.hasImageAttachments(files)) {
                return Flux.just(visionChatService.analyze(memoryId, message, files, provider));
            }
            String augmentedMessage = knowledgeBaseService.buildChatMessageWithAttachments(message, files);
            return startChat(provider, memoryId, augmentedMessage)
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
        List<String> enabledProviders = new ArrayList<>(2);
        if (localProviderEnabled) {
            enabledProviders.add(LOCAL_OLLAMA);
        }
        if (onlineProviderEnabled) {
            enabledProviders.add(QWEN_ONLINE);
        }
        return new ChatProviderConfigResponse(normalizeProvider(defaultProvider), enabledProviders);
    }

    private Flux<String> startChat(String provider, Long memoryId, String message) {
        if (QWEN_ONLINE.equals(provider)) {
            return withFluxLogs(provider, memoryId, message, onlineAiMedAgent.chat(memoryId, message));
        }
        // 本地模型仍按 token 流输出，前端继续用统一的流式解码逻辑消费。
        return Flux.create(emitter -> {
            long startedAt = System.nanoTime();
            AtomicInteger chunkCount = new AtomicInteger();
            AtomicLong charCount = new AtomicLong();
            log.info("model.stream.start provider={} memoryId={} chars={}", provider, memoryId, textLength(message));
            localStreamingAiMedAgent.chat(memoryId, message)
                    .onPartialResponse(partial -> {
                        chunkCount.incrementAndGet();
                        charCount.addAndGet(partial == null ? 0 : partial.length());
                        emitter.next(partial);
                    })
                    .onCompleteResponse(ignored -> {
                        log.info("model.stream.complete provider={} memoryId={} chunks={} chars={} durationMs={}",
                                provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt));
                        emitter.complete();
                    })
                    .onError(error -> {
                        log.error("model.stream.failed provider={} memoryId={} chunks={} chars={} durationMs={}",
                                provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt), error);
                        emitter.error(error);
                    })
                    .start();
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private Flux<String> withFluxLogs(String provider, Long memoryId, String message, Flux<String> source) {
        long startedAt = System.nanoTime();
        AtomicInteger chunkCount = new AtomicInteger();
        AtomicLong charCount = new AtomicLong();
        log.info("model.stream.start provider={} memoryId={} chars={}", provider, memoryId, textLength(message));
        return source
                .doOnNext(chunk -> {
                    chunkCount.incrementAndGet();
                    charCount.addAndGet(chunk == null ? 0 : chunk.length());
                })
                .doOnComplete(() -> log.info("model.stream.complete provider={} memoryId={} chunks={} chars={} durationMs={}",
                        provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt)))
                .doOnError(error -> log.error("model.stream.failed provider={} memoryId={} chunks={} chars={} durationMs={}",
                        provider, memoryId, chunkCount.get(), charCount.get(), durationMs(startedAt), error));
    }

    public String normalizeProvider(String provider) {
        String requested = provider == null ? defaultProvider : provider;
        String normalized = requested.trim().toUpperCase(Locale.ROOT);
        if (QWEN_ONLINE.equals(normalized)) {
            return onlineProviderEnabled ? QWEN_ONLINE : fallbackProvider();
        }
        return localProviderEnabled ? LOCAL_OLLAMA : fallbackProvider();
    }

    private String fallbackProvider() {
        if (localProviderEnabled) {
            return LOCAL_OLLAMA;
        }
        if (onlineProviderEnabled) {
            return QWEN_ONLINE;
        }
        throw new IllegalStateException("未启用任何可用的大模型提供方，请检查 app.provider.* 配置");
    }

    private String errorMessageForProvider(String provider) {
        if (QWEN_ONLINE.equals(provider)) {
            return "抱歉，当前千问在线服务暂时不可用，请稍后重试。若持续失败，请检查百炼网络连接。";
        }
        return "抱歉，当前本地 Ollama 模型暂时不可用，请检查本机 Ollama 服务和模型是否已启动。";
    }

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    private int fileCount(MultipartFile[] files) {
        return files == null ? 0 : files.length;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
