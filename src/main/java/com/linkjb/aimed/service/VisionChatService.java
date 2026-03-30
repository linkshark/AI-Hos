package com.linkjb.aimed.service;

import com.linkjb.aimed.store.MongoChatMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Base64;
import java.util.List;

@Service
public class VisionChatService {
    private static final Logger log = LoggerFactory.getLogger(VisionChatService.class);
    public static final String LOCAL_OLLAMA = "LOCAL_OLLAMA";
    public static final String QWEN_ONLINE = "QWEN_ONLINE";

    private final ChatModel onlineVisionChatModel;
    private final ChatModel localVisionChatModel;
    private final KnowledgeBaseService knowledgeBaseService;
    private final MongoChatMemoryStore mongoChatMemoryStore;
    private final Resource promptTemplateResource;

    public VisionChatService(@Qualifier("qwenVisionChatModel") ChatModel onlineVisionChatModel,
                             @Qualifier("localVisionChatModel") ChatModel localVisionChatModel,
                             KnowledgeBaseService knowledgeBaseService,
                             MongoChatMemoryStore mongoChatMemoryStore,
                             @org.springframework.beans.factory.annotation.Value("classpath:prompt-templates/aimed-prompt-template.txt") Resource promptTemplateResource) {
        this.onlineVisionChatModel = onlineVisionChatModel;
        this.localVisionChatModel = localVisionChatModel;
        this.knowledgeBaseService = knowledgeBaseService;
        this.mongoChatMemoryStore = mongoChatMemoryStore;
        this.promptTemplateResource = promptTemplateResource;
    }

    public String analyze(Long memoryId, String message, MultipartFile[] files, String provider) throws IOException {
        long startedAt = System.nanoTime();
        int imageCount = countImages(files);
        int attachmentCount = files == null ? 0 : files.length;
        log.info("vision.chat.start provider={} memoryId={} attachments={} images={} chars={}",
                provider, memoryId, attachmentCount, imageCount, message == null ? 0 : message.length());
        List<dev.langchain4j.data.message.Content> contents = new ArrayList<>();
        String textPrompt = buildTextPrompt(message, files);
        contents.add(TextContent.from(textPrompt));

        for (MultipartFile file : files) {
            if (!knowledgeBaseService.isImageAttachment(file)) {
                continue;
            }

            String base64Data = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = knowledgeBaseService.resolveMimeType(file);
            Image image = Image.builder()
                    .base64Data(base64Data)
                    .mimeType(mimeType)
                    .build();
            contents.add(ImageContent.from(image));
        }

        List<ChatMessage> messages = List.of(
                SystemMessage.from(loadPrompt(memoryId)),
                UserMessage.from(contents)
        );

        ChatResponse response = selectVisionChatModel(provider).chat(messages);
        if (response == null || response.aiMessage() == null || !StringUtils.hasText(response.aiMessage().text())) {
            log.warn("vision.chat.empty provider={} memoryId={} durationMs={}", provider, memoryId, durationMs(startedAt));
            return errorMessage(provider);
        }
        persistConversation(memoryId, effectiveUserSummary(message, files), response.aiMessage().text(), messages.get(0));
        log.info("vision.chat.complete provider={} memoryId={} answerChars={} durationMs={}",
                provider, memoryId, response.aiMessage().text().length(), durationMs(startedAt));
        return response.aiMessage().text();
    }

    private ChatModel selectVisionChatModel(String provider) {
        if (LOCAL_OLLAMA.equals(provider)) {
            return localVisionChatModel;
        }
        return onlineVisionChatModel;
    }

    private String errorMessage(String provider) {
        if (LOCAL_OLLAMA.equals(provider)) {
            return "抱歉，本地 Ollama 视觉模型暂时无法完成图片分析，请检查 `qwen2.5vl:7b` 是否已拉取并正常运行。";
        }
        return "抱歉，我暂时无法完成图片分析，请稍后重试。";
    }

    private String buildTextPrompt(String message, MultipartFile[] files) throws IOException {
        String effectiveMessage = StringUtils.hasText(message) ? message : "请结合我上传的图片或材料进行分析并回答。";
        String attachmentContext = knowledgeBaseService.buildChatAttachmentTextContext(files);

        StringBuilder builder = new StringBuilder();
        builder.append("用户上传了图片或材料，请优先基于附件内容回答。\n")
                .append("如果图片或材料信息不足，请明确说明不确定性；不要编造诊断结果。\n")
                .append("如涉及病情判断，请区分：观察建议、就诊建议、急诊风险提示。\n");

        if (StringUtils.hasText(attachmentContext)) {
            builder.append('\n').append(attachmentContext).append('\n');
        }

        builder.append("\n[用户问题]\n").append(effectiveMessage);
        return builder.toString();
    }

    private String loadPrompt(Long memoryId) throws IOException {
        String prompt = StreamUtils.copyToString(promptTemplateResource.getInputStream(), StandardCharsets.UTF_8);
        return prompt.replace("{{current_date}}", LocalDate.now().toString())
                + "\n7、如果用户上传了图片、病历、检验单或检查报告，请优先结合附件内容进行分析，但必须明确说明你的回答不能替代医生面诊。"
                + "\n8、如果图片中存在可能提示急症、危险信号或需要线下复诊的情况，请优先给出风险提示。"
                + "\n9、当前会话ID为 " + memoryId + "。";
    }

    private String effectiveUserSummary(String message, MultipartFile[] files) {
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.hasText(message) ? message : "请结合我上传的图片或材料进行分析并回答。");

        List<String> attachmentNames = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty() && StringUtils.hasText(file.getOriginalFilename())) {
                    attachmentNames.add(file.getOriginalFilename());
                }
            }
        }
        if (!attachmentNames.isEmpty()) {
            builder.append("\n附件：").append(String.join("，", attachmentNames));
        }
        return builder.toString();
    }

    private void persistConversation(Long memoryId, String userSummary, String answer, ChatMessage systemMessage) {
        List<ChatMessage> history = new LinkedList<>(mongoChatMemoryStore.getMessages(memoryId));
        if (history.isEmpty()) {
            history.add(systemMessage);
        }
        history.add(UserMessage.from(userSummary));
        history.add(AiMessage.from(answer));

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
            if (knowledgeBaseService.isImageAttachment(file)) {
                count++;
            }
        }
        return count;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
