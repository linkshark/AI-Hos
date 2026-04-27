package com.linkjb.aimed.service;

import com.linkjb.aimed.service.knowledge.KnowledgeAttachmentService;
import com.linkjb.aimed.service.vision.VisionChatRoutingService;
import com.linkjb.aimed.service.vision.VisionPromptService;
import com.linkjb.aimed.store.MongoChatMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Base64;
import java.util.List;

@Service
public class VisionChatService {
    private static final Logger log = LoggerFactory.getLogger(VisionChatService.class);

    private final ChatModel onlineVisionChatModel;
    private final ChatModel localVisionChatModel;
    private final StreamingChatModel onlineVisionStreamingChatModel;
    private final StreamingChatModel localVisionStreamingChatModel;
    private final KnowledgeAttachmentService knowledgeAttachmentService;
    private final VisionPromptService visionPromptService;
    private final VisionChatRoutingService visionChatRoutingService;
    private final MongoChatMemoryStore mongoChatMemoryStore;

    public VisionChatService(@Qualifier("qwenVisionChatModel") ChatModel onlineVisionChatModel,
                             @Qualifier("localVisionChatModel") ChatModel localVisionChatModel,
                             @Qualifier("qwenVisionStreamingChatModel") StreamingChatModel onlineVisionStreamingChatModel,
                             @Qualifier("localVisionStreamingChatModel") StreamingChatModel localVisionStreamingChatModel,
                             KnowledgeAttachmentService knowledgeAttachmentService,
                             VisionPromptService visionPromptService,
                             VisionChatRoutingService visionChatRoutingService,
                             MongoChatMemoryStore mongoChatMemoryStore) {
        this.onlineVisionChatModel = onlineVisionChatModel;
        this.localVisionChatModel = localVisionChatModel;
        this.onlineVisionStreamingChatModel = onlineVisionStreamingChatModel;
        this.localVisionStreamingChatModel = localVisionStreamingChatModel;
        this.knowledgeAttachmentService = knowledgeAttachmentService;
        this.visionPromptService = visionPromptService;
        this.visionChatRoutingService = visionChatRoutingService;
        this.mongoChatMemoryStore = mongoChatMemoryStore;
    }

    public String analyze(Long memoryId, String message, MultipartFile[] files, String provider) throws IOException {
        long startedAt = System.nanoTime();
        int imageCount = countImages(files);
        int attachmentCount = files == null ? 0 : files.length;
        log.info("vision.chat.start provider={} memoryId={} attachments={} images={} chars={}",
                provider, memoryId, attachmentCount, imageCount, message == null ? 0 : message.length());
        String resolvedProvider = visionChatRoutingService.resolveProvider(provider);
        List<ChatMessage> messages = buildVisionMessages(memoryId, message, files);

        ChatResponse response = selectVisionChatModel(resolvedProvider).chat(messages);
        if (response == null || response.aiMessage() == null || !StringUtils.hasText(response.aiMessage().text())) {
            log.warn("vision.chat.empty provider={} memoryId={} durationMs={}", resolvedProvider, memoryId, durationMs(startedAt));
            return visionChatRoutingService.errorMessage(resolvedProvider);
        }
        persistConversation(memoryId, visionPromptService.effectiveUserSummary(message, files), response.aiMessage().text(), messages.get(0));
        log.info("vision.chat.complete provider={} memoryId={} answerChars={} durationMs={}",
                resolvedProvider, memoryId, response.aiMessage().text().length(), durationMs(startedAt));
        return response.aiMessage().text();
    }

    public Flux<String> analyzeStream(Long memoryId, String message, MultipartFile[] files, String provider) throws IOException {
        long startedAt = System.nanoTime();
        String resolvedProvider = visionChatRoutingService.resolveProvider(provider);
        List<ChatMessage> messages = buildVisionMessages(memoryId, message, files);
        String userSummary = visionPromptService.effectiveUserSummary(message, files);
        int imageCount = countImages(files);
        int attachmentCount = files == null ? 0 : files.length;
        log.info("vision.chat.stream.start provider={} memoryId={} attachments={} images={} chars={}",
                resolvedProvider, memoryId, attachmentCount, imageCount, message == null ? 0 : message.length());

        return Flux.create(sink -> {
            StringBuilder answer = new StringBuilder();
            try {
                selectVisionStreamingChatModel(resolvedProvider).chat(messages, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        if (partialResponse == null || partialResponse.isEmpty()) {
                            return;
                        }
                        answer.append(partialResponse);
                        sink.next(partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        String completeText = answer.toString();
                        if (!StringUtils.hasText(completeText)
                                && completeResponse != null
                                && completeResponse.aiMessage() != null
                                && StringUtils.hasText(completeResponse.aiMessage().text())) {
                            completeText = completeResponse.aiMessage().text();
                            sink.next(completeText);
                        }
                        if (!StringUtils.hasText(completeText)) {
                            completeText = visionChatRoutingService.errorMessage(resolvedProvider);
                            sink.next(completeText);
                            log.warn("vision.chat.stream.empty provider={} memoryId={} durationMs={}",
                                    resolvedProvider, memoryId, durationMs(startedAt));
                        } else {
                            log.info("vision.chat.stream.complete provider={} memoryId={} answerChars={} durationMs={}",
                                    resolvedProvider, memoryId, completeText.length(), durationMs(startedAt));
                        }
                        persistConversation(memoryId, userSummary, completeText, messages.get(0));
                        sink.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        completeWithFallback(sink, resolvedProvider, memoryId, startedAt, userSummary, messages.get(0), error);
                    }
                });
            } catch (Exception error) {
                completeWithFallback(sink, resolvedProvider, memoryId, startedAt, userSummary, messages.get(0), error);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void completeWithFallback(FluxSink<String> sink,
                                      String provider,
                                      Long memoryId,
                                      long startedAt,
                                      String userSummary,
                                      ChatMessage systemMessage,
                                      Throwable error) {
        String fallback = visionChatRoutingService.errorMessage(provider);
        log.error("vision.chat.stream.failed provider={} memoryId={} durationMs={}",
                provider, memoryId, durationMs(startedAt), error);
        sink.next(fallback);
        persistConversation(memoryId, userSummary, fallback, systemMessage);
        sink.complete();
    }

    private ChatModel selectVisionChatModel(String provider) {
        String resolvedProvider = visionChatRoutingService.resolveProvider(provider);
        if (visionChatRoutingService.isLocalProvider(resolvedProvider)) {
            return localVisionChatModel;
        }
        return onlineVisionChatModel;
    }

    private StreamingChatModel selectVisionStreamingChatModel(String provider) {
        String resolvedProvider = visionChatRoutingService.resolveProvider(provider);
        if (visionChatRoutingService.isLocalProvider(resolvedProvider)) {
            return localVisionStreamingChatModel;
        }
        return onlineVisionStreamingChatModel;
    }

    private List<ChatMessage> buildVisionMessages(Long memoryId, String message, MultipartFile[] files) throws IOException {
        List<dev.langchain4j.data.message.Content> contents = new ArrayList<>();
        contents.add(TextContent.from(visionPromptService.buildTextPrompt(message, files)));

        if (files == null) {
            return List.of(
                    SystemMessage.from(visionPromptService.loadPrompt(memoryId)),
                    UserMessage.from(contents)
            );
        }

        for (MultipartFile file : files) {
            if (file == null || !knowledgeAttachmentService.isImageAttachment(file)) {
                continue;
            }

            String base64Data = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = knowledgeAttachmentService.resolveMimeType(file);
            Image image = Image.builder()
                    .base64Data(base64Data)
                    .mimeType(mimeType)
                    .build();
            contents.add(ImageContent.from(image));
        }

        return List.of(
                SystemMessage.from(visionPromptService.loadPrompt(memoryId)),
                UserMessage.from(contents)
        );
    }

    private void persistConversation(Long memoryId, String userSummary, String answer, ChatMessage systemMessage) {
        List<ChatMessage> history = new LinkedList<>(mongoChatMemoryStore.getMessages(memoryId));
        if (history.isEmpty()) {
            history.add(systemMessage);
        }
        history.add(UserMessage.from(userSummary));
        history.add(AiMessage.from(answer));

        // 视觉会话只保留最近窗口，避免 Mongo 记忆无限增长并拖慢后续问答。
        if (history.size() > 20) {
            ChatMessage first = history.get(0);
            List<ChatMessage> trimmed = new LinkedList<>();
            if (first instanceof SystemMessage) {
                trimmed.add(first);
                trimmed.addAll(history.subList(Math.max(1, history.size() - 19), history.size()));
            } else {
                trimmed.addAll(history.subList(Math.max(0, history.size() - 20), history.size()));
            }
            history = trimmed;
        }
        mongoChatMemoryStore.updateMessages(memoryId, history);
    }

    private int countImages(MultipartFile[] files) {
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (MultipartFile file : files) {
            if (knowledgeAttachmentService.isImageAttachment(file)) {
                count++;
            }
        }
        return count;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
