package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.KnowledgeCitationItem;
import com.linkjb.aimed.bean.PagedResponse;
import com.linkjb.aimed.bean.chat.ChatHistoryAttachmentResponse;
import com.linkjb.aimed.bean.chat.ChatHistoryDetailResponse;
import com.linkjb.aimed.bean.chat.ChatHistoryItemResponse;
import com.linkjb.aimed.bean.chat.ChatHistoryMessageResponse;
import com.linkjb.aimed.bean.chat.ChatVisibleHistoryDocument;
import com.linkjb.aimed.entity.ChatSessionOwner;
import com.linkjb.aimed.store.MongoChatMemoryStore;
import com.linkjb.aimed.store.MongoVisibleChatHistoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ChatHistoryService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int LEGACY_CONTEXT_LENGTH_THRESHOLD = 500;
    private static final int ATTACHMENT_PREVIEW_MAX_EDGE = 240;

    private final ChatSessionOwnerService chatSessionOwnerService;
    private final MongoChatMemoryStore mongoChatMemoryStore;
    private final MongoVisibleChatHistoryStore mongoVisibleChatHistoryStore;
    private final ChatSessionUserBindingService chatSessionUserBindingService;

    public ChatHistoryService(ChatSessionOwnerService chatSessionOwnerService,
                              MongoChatMemoryStore mongoChatMemoryStore,
                              MongoVisibleChatHistoryStore mongoVisibleChatHistoryStore,
                              ChatSessionUserBindingService chatSessionUserBindingService) {
        this.chatSessionOwnerService = chatSessionOwnerService;
        this.mongoChatMemoryStore = mongoChatMemoryStore;
        this.mongoVisibleChatHistoryStore = mongoVisibleChatHistoryStore;
        this.chatSessionUserBindingService = chatSessionUserBindingService;
    }

    public Long createSession(Long userId) {
        return chatSessionUserBindingService.createSession(userId);
    }

    public void recordUserMessage(Long userId, Long memoryId, String content) {
        recordUserMessage(userId, memoryId, content, null);
    }

    public void recordUserMessage(Long userId, Long memoryId, String content, MultipartFile[] files) {
        if (userId == null || memoryId == null || !StringUtils.hasText(content)) {
            return;
        }
        // 这里落的是“用户实际看到的提问”，而不是给模型拼装后的增强 prompt。
        // 这样历史恢复、导出和左侧标题才能始终回到用户语义，而不是把 RAG 内部上下文暴露出来。
        ChatVisibleHistoryDocument history = loadOrCreateVisibleHistory(userId, memoryId);
        history.getMessages().add(new ChatHistoryMessageResponse(
                true,
                content.trim(),
                buildAttachments(files),
                List.of()
        ));
        refreshVisibleHistorySummary(history);
        mongoVisibleChatHistoryStore.save(history);
        touchOwner(memoryId);
    }

    public void recordAssistantMessage(Long memoryId, String content, List<KnowledgeCitationItem> citations) {
        Long userId = chatSessionUserBindingService.resolveUserId(memoryId);
        if (userId == null || memoryId == null || (!StringUtils.hasText(content) && (citations == null || citations.isEmpty()))) {
            return;
        }
        // 模型记忆仍由 MongoChatMemoryStore 服务于 LangChain4j；
        // 历史展示只认这套 visible history，避免后续再把 SystemMessage / 工具入参恢复到聊天窗口。
        ChatVisibleHistoryDocument history = loadOrCreateVisibleHistory(userId, memoryId);
        history.getMessages().add(new ChatHistoryMessageResponse(
                false,
                StringUtils.hasText(content) ? content.trim() : "",
                List.of(),
                cloneCitations(citations)
        ));
        refreshVisibleHistorySummary(history);
        if (!hasMeaningfulVisibleContent(history)) {
            return;
        }
        mongoVisibleChatHistoryStore.save(history);
        touchOwner(memoryId);
    }

    public PagedResponse<ChatHistoryItemResponse> listHistory(Long userId,
                                                              int page,
                                                              int size,
                                                              String keyword) {
        List<ChatSessionOwner> owners = chatSessionOwnerService.listVisibleByUserId(userId);
        List<ChatHistoryItemResponse> items = new ArrayList<>();
        for (ChatSessionOwner owner : owners) {
            ChatVisibleHistoryDocument history = loadVisibleHistory(owner.getUserId(), owner.getMemoryId(), false);
            if (history == null || history.getMessages().isEmpty() || !hasMeaningfulVisibleContent(history)) {
                continue;
            }
            if (!StringUtils.hasText(history.getFirstQuestion())) {
                continue;
            }
            String defaultTitle = StringUtils.hasText(history.getFirstQuestion())
                    ? abbreviate(singleLine(history.getFirstQuestion()), 22)
                    : summarizeTitle(history.getMessages());
            if (!StringUtils.hasText(defaultTitle) || "未命名对话".equals(defaultTitle)) {
                continue;
            }
            String displayTitle = StringUtils.hasText(owner.getCustomTitle()) ? owner.getCustomTitle().trim() : defaultTitle;
            if (!matchesKeyword(keyword, displayTitle, owner.getCustomTitle(), history.getFirstQuestion())) {
                continue;
            }
            String preview = StringUtils.hasText(history.getLastPreview())
                    ? history.getLastPreview()
                    : summarizePreview(history.getMessages());
            items.add(new ChatHistoryItemResponse(
                    owner.getMemoryId(),
                    displayTitle,
                    owner.getCustomTitle(),
                    history.getFirstQuestion(),
                    preview,
                    formatDateTime(owner.getUpdatedAt() != null ? owner.getUpdatedAt() : owner.getCreatedAt()),
                    toEpochMillis(owner.getUpdatedAt() != null ? owner.getUpdatedAt() : owner.getCreatedAt()),
                    history.getMessages().size(),
                    Boolean.TRUE.equals(owner.getPinned())
            ));
        }
        items.sort(Comparator
                .comparing(ChatHistoryItemResponse::pinned, Comparator.reverseOrder())
                .thenComparing(ChatHistoryItemResponse::updatedAtEpochMillis, Comparator.nullsLast(Comparator.reverseOrder())));

        int safeSize = Math.max(1, size);
        int safePage = Math.max(1, page);
        int fromIndex = Math.min((safePage - 1) * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());
        return new PagedResponse<>(items.size(), safePage, safeSize, items.subList(fromIndex, toIndex));
    }

    public ChatHistoryDetailResponse detail(Long userId, Long memoryId, boolean includeInternalContent) {
        ChatSessionOwner owner = loadVisibleOwner(memoryId, userId);
        if (includeInternalContent) {
            return buildDebugDetail(owner);
        }
        ChatVisibleHistoryDocument history = loadVisibleHistory(userId, memoryId, includeInternalContent);
        if (history == null || !hasMeaningfulVisibleContent(history)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前历史会话不存在或已删除");
        }
        List<ChatHistoryMessageResponse> visibleMessages = history == null ? List.of() : history.getMessages();
        String defaultTitle = StringUtils.hasText(history == null ? null : history.getFirstQuestion())
                ? abbreviate(singleLine(history.getFirstQuestion()), 22)
                : summarizeTitle(visibleMessages);
        String displayTitle = StringUtils.hasText(owner.getCustomTitle()) ? owner.getCustomTitle().trim() : defaultTitle;
        return new ChatHistoryDetailResponse(
                memoryId,
                displayTitle,
                owner.getCustomTitle(),
                history == null ? null : history.getFirstQuestion(),
                history == null ? null : history.getLastPreview(),
                visibleMessages
        );
    }

    public ChatHistoryItemResponse rename(Long userId, Long memoryId, String title) {
        ChatSessionOwner owner = loadVisibleOwner(memoryId, userId);
        String normalizedTitle = title == null ? null : title.trim();
        chatSessionOwnerService.rename(memoryId, normalizedTitle);
        owner.setCustomTitle(normalizedTitle);
        owner.setUpdatedAt(LocalDateTime.now());
        return listItem(owner);
    }

    public ChatHistoryItemResponse pin(Long userId, Long memoryId, boolean pinned) {
        ChatSessionOwner owner = loadVisibleOwner(memoryId, userId);
        LocalDateTime now = LocalDateTime.now();
        chatSessionOwnerService.updatePinned(memoryId, pinned);
        owner.setPinned(pinned);
        owner.setPinnedAt(pinned ? now : null);
        owner.setUpdatedAt(now);
        return listItem(owner);
    }

    public void hide(Long userId, Long memoryId) {
        loadVisibleOwner(memoryId, userId);
        chatSessionOwnerService.hide(memoryId);
    }

    private ChatVisibleHistoryDocument loadVisibleHistory(Long userId, Long memoryId, boolean includeInternalContent) {
        ChatVisibleHistoryDocument history = mongoVisibleChatHistoryStore.get(memoryId);
        if (history != null) {
            return history;
        }
        // 旧会话按需懒迁移：只有用户真的点开这条历史时，才尝试从旧 memory 做一次 best-effort 清洗。
        ChatVisibleHistoryDocument migrated = migrateLegacyHistory(userId, memoryId, includeInternalContent);
        if (migrated != null && !migrated.getMessages().isEmpty()) {
            mongoVisibleChatHistoryStore.save(migrated);
            return migrated;
        }
        return history;
    }

    private ChatHistoryDetailResponse buildDebugDetail(ChatSessionOwner owner) {
        ChatVisibleHistoryDocument visibleHistory = loadVisibleHistory(owner.getUserId(), owner.getMemoryId(), false);
        List<ChatHistoryMessageResponse> rawMessages = buildDebugMessages(owner.getMemoryId());
        if (rawMessages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前历史会话不存在或已删除");
        }
        String defaultTitle = visibleHistory != null && StringUtils.hasText(visibleHistory.getFirstQuestion())
                ? abbreviate(singleLine(visibleHistory.getFirstQuestion()), 22)
                : summarizeTitle(rawMessages);
        String displayTitle = StringUtils.hasText(owner.getCustomTitle()) ? owner.getCustomTitle().trim() : defaultTitle;
        return new ChatHistoryDetailResponse(
                owner.getMemoryId(),
                displayTitle,
                owner.getCustomTitle(),
                visibleHistory == null ? null : visibleHistory.getFirstQuestion(),
                visibleHistory == null ? null : visibleHistory.getLastPreview(),
                rawMessages
        );
    }

    private List<ChatHistoryMessageResponse> buildDebugMessages(Long memoryId) {
        List<ChatHistoryMessageResponse> items = new ArrayList<>();
        for (ChatMessage message : mongoChatMemoryStore.getMessages(memoryId)) {
            if (message instanceof UserMessage userMessage) {
                items.add(new ChatHistoryMessageResponse(true, userMessage.singleText(), List.of(), List.of()));
                continue;
            }
            if (message instanceof SystemMessage systemMessage) {
                items.add(new ChatHistoryMessageResponse(false, "[System]\n" + systemMessage.text(), List.of(), List.of()));
                continue;
            }
            if (message instanceof AiMessage aiMessage) {
                String content = StringUtils.hasText(aiMessage.text()) ? aiMessage.text() : aiMessage.thinking();
                if (StringUtils.hasText(content)) {
                    items.add(new ChatHistoryMessageResponse(false, content, List.of(), List.of()));
                }
            }
        }
        return items;
    }

    private ChatVisibleHistoryDocument loadOrCreateVisibleHistory(Long userId, Long memoryId) {
        ChatVisibleHistoryDocument history = mongoVisibleChatHistoryStore.get(memoryId);
        if (history != null) {
            if (history.getUserId() == null) {
                history.setUserId(userId);
            }
            return history;
        }
        ChatVisibleHistoryDocument created = new ChatVisibleHistoryDocument();
        created.setMemoryId(memoryId);
        created.setUserId(userId);
        created.setMessages(new ArrayList<>());
        return created;
    }

    private ChatVisibleHistoryDocument migrateLegacyHistory(Long userId, Long memoryId, boolean includeInternalContent) {
        List<ChatMessage> source = mongoChatMemoryStore.getMessages(memoryId);
        List<ChatHistoryMessageResponse> items = new ArrayList<>();
        for (ChatMessage message : source) {
            if (message instanceof SystemMessage) {
                continue;
            }
            if (message instanceof UserMessage userMessage) {
                String content = sanitizeLegacyUserContent(userMessage.singleText(), includeInternalContent);
                if (StringUtils.hasText(content)) {
                    items.add(new ChatHistoryMessageResponse(true, content.trim(), List.of(), List.of()));
                }
                continue;
            }
            if (message instanceof AiMessage aiMessage) {
                String content = StringUtils.hasText(aiMessage.text()) ? aiMessage.text() : aiMessage.thinking();
                if (StringUtils.hasText(content)) {
                    items.add(new ChatHistoryMessageResponse(false, content.trim(), List.of(), List.of()));
                }
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        ChatVisibleHistoryDocument history = new ChatVisibleHistoryDocument();
        history.setMemoryId(memoryId);
        history.setUserId(userId);
        history.setMessages(items);
        refreshVisibleHistorySummary(history);
        return history;
    }

    private String sanitizeLegacyUserContent(String content, boolean includeDebugDetails) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String normalized = content.trim();
        // 历史恢复默认只保留用户当时看到的提问，RAG 包装层和附件增强提示都在这里统一剥掉。
        normalized = stripInternalPromptScaffolding(normalized);
        normalized = stripAttachmentSummary(normalized);
        if (!includeDebugDetails && looksLikeInjectedKnowledge(normalized)) {
            return "";
        }

        if (includeDebugDetails) {
            return normalized;
        }

        return normalized;
    }

    private String stripInternalPromptScaffolding(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String normalized = content.trim();
        String marker = "[用户问题]";
        int markerIndex = normalized.lastIndexOf(marker);
        if (markerIndex >= 0) {
            String suffix = normalized.substring(markerIndex + marker.length()).trim();
            if (StringUtils.hasText(suffix)) {
                normalized = suffix;
            }
        }

        String englishMarker = "Answer using the following information:";
        int englishMarkerIndex = normalized.lastIndexOf(englishMarker);
        if (englishMarkerIndex >= 0) {
            String suffix = normalized.substring(englishMarkerIndex + englishMarker.length()).trim();
            if (StringUtils.hasText(suffix)) {
                normalized = suffix;
            }
        }

        int hcsbEndIndex = normalized.lastIndexOf("</hcsb>");
        if (hcsbEndIndex >= 0) {
            String suffix = normalized.substring(hcsbEndIndex + "</hcsb>".length()).trim();
            if (StringUtils.hasText(suffix)) {
                normalized = suffix;
            }
        }

        return normalized;
    }

    private String stripAttachmentSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        int attachmentIndex = content.indexOf("\n附件：");
        if (attachmentIndex > 0) {
            String visibleQuestion = content.substring(0, attachmentIndex).trim();
            if (StringUtils.hasText(visibleQuestion)) {
                return visibleQuestion;
            }
        }
        return content;
    }

    private void touchOwner(Long memoryId) {
        chatSessionOwnerService.touch(memoryId);
    }

    private boolean looksLikeInjectedKnowledge(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String normalized = content.trim();
        int newlineCount = 0;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '\n') {
                newlineCount++;
            }
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.length() > LEGACY_CONTEXT_LENGTH_THRESHOLD
                || newlineCount > 8
                || lower.contains("医院系统集成解决方案")
                || lower.contains("answer using the following information")
                || lower.contains("证据等级")
                || lower.contains("推荐 a")
                || lower.contains("推荐 b");
    }

    private List<ChatHistoryAttachmentResponse> buildAttachments(MultipartFile[] files) {
        List<ChatHistoryAttachmentResponse> attachments = new ArrayList<>();
        if (files == null) {
            return attachments;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            ChatHistoryAttachmentResponse attachment = new ChatHistoryAttachmentResponse();
            String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed";
            String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
            String extension = extensionOf(filename);
            boolean image = contentType.startsWith("image/");
            attachment.setName(filename);
            attachment.setSize(file.getSize());
            attachment.setContentType(contentType);
            attachment.setExtension(extension);
            attachment.setImage(image);
            attachment.setKindLabel(image ? "图片" : "文件");
            if (image) {
                try {
                    ThumbnailPreview preview = buildThumbnailPreview(file.getBytes(), contentType);
                    attachment.setPreviewUrl(preview.dataUrl());
                    attachment.setPreviewWidth(preview.width());
                    attachment.setPreviewHeight(preview.height());
                } catch (IOException exception) {
                    attachment.setPreviewUrl("");
                }
            }
            attachments.add(attachment);
        }
        return attachments;
    }

    private List<KnowledgeCitationItem> cloneCitations(List<KnowledgeCitationItem> citations) {
        List<KnowledgeCitationItem> items = new ArrayList<>();
        if (citations == null) {
            return items;
        }
        for (KnowledgeCitationItem citation : citations) {
            if (citation == null) {
                continue;
            }
            KnowledgeCitationItem copy = new KnowledgeCitationItem();
            copy.setFileHash(citation.getFileHash());
            copy.setDocumentName(citation.getDocumentName());
            copy.setSegmentId(citation.getSegmentId());
            copy.setSnippet(citation.getSnippet());
            copy.setUpdatedAt(citation.getUpdatedAt());
            copy.setEffectiveAt(citation.getEffectiveAt());
            copy.setVersion(citation.getVersion());
            copy.setRetrievalType(citation.getRetrievalType());
            items.add(copy);
        }
        return items;
    }

    private void refreshVisibleHistorySummary(ChatVisibleHistoryDocument history) {
        if (history == null) {
            return;
        }
        history.setFirstQuestion(findFirstQuestion(history.getMessages()));
        history.setLastPreview(summarizePreview(history.getMessages()));
        LocalDateTime now = LocalDateTime.now();
        history.setUpdatedAt(formatDateTime(now));
        history.setUpdatedAtEpochMillis(toEpochMillis(now));
    }

    private String findFirstQuestion(List<ChatHistoryMessageResponse> messages) {
        for (ChatHistoryMessageResponse message : messages) {
            if (message.user()
                    && (StringUtils.hasText(message.content())
                    || (message.attachments() != null && !message.attachments().isEmpty()))) {
                if (StringUtils.hasText(message.content())) {
                    return message.content().trim();
                }
                return "附件对话";
            }
        }
        return null;
    }

    private boolean hasMeaningfulVisibleContent(ChatVisibleHistoryDocument history) {
        return history != null && hasMeaningfulVisibleMessages(history.getMessages());
    }

    private boolean hasMeaningfulVisibleMessages(List<ChatHistoryMessageResponse> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return messages.stream().anyMatch(message ->
                message != null
                        && message.user()
                        && (StringUtils.hasText(message.content())
                        || (message.attachments() != null && !message.attachments().isEmpty()))
        );
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String summarizeTitle(List<ChatHistoryMessageResponse> messages) {
        for (ChatHistoryMessageResponse message : messages) {
            if (message.user() && StringUtils.hasText(message.content())) {
                return abbreviate(singleLine(message.content()), 22);
            }
        }
        return "未命名对话";
    }

    private boolean matchesKeyword(String keyword, String displayTitle, String customTitle, String firstQuestion) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        // 历史标题支持“自定义标题为空”的旧数据，不能再用 List.of(...) 包 null，
        // 否则一旦用户开始搜索，后端会直接 500，左侧历史就会表现成“搜索后全空白”。
        return Arrays.asList(displayTitle, customTitle, firstQuestion).stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalizedKeyword));
    }

    private String summarizePreview(List<ChatHistoryMessageResponse> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        ChatHistoryMessageResponse last = messages.get(messages.size() - 1);
        return abbreviate(singleLine(last.content()), 40);
    }

    private String singleLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private Long toEpochMillis(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private ChatSessionOwner loadVisibleOwner(Long memoryId, Long userId) {
        chatSessionUserBindingService.validateOwnership(memoryId, userId);
        ChatSessionOwner owner = chatSessionOwnerService.findByMemoryId(memoryId);
        if (owner == null || Boolean.TRUE.equals(owner.getHidden())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前历史会话不存在或已删除");
        }
        return owner;
    }

    private ChatHistoryItemResponse listItem(ChatSessionOwner owner) {
        ChatVisibleHistoryDocument history = loadVisibleHistory(owner.getUserId(), owner.getMemoryId(), false);
        if (history == null || !hasMeaningfulVisibleContent(history)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前历史会话不存在或已删除");
        }
        List<ChatHistoryMessageResponse> visibleMessages = history == null ? List.of() : history.getMessages();
        String defaultTitle = StringUtils.hasText(history == null ? null : history.getFirstQuestion())
                ? abbreviate(singleLine(history.getFirstQuestion()), 22)
                : summarizeTitle(visibleMessages);
        String displayTitle = StringUtils.hasText(owner.getCustomTitle()) ? owner.getCustomTitle().trim() : defaultTitle;
        return new ChatHistoryItemResponse(
                owner.getMemoryId(),
                displayTitle,
                owner.getCustomTitle(),
                history == null ? null : history.getFirstQuestion(),
                history == null ? summarizePreview(visibleMessages) : history.getLastPreview(),
                formatDateTime(owner.getUpdatedAt() != null ? owner.getUpdatedAt() : owner.getCreatedAt()),
                toEpochMillis(owner.getUpdatedAt() != null ? owner.getUpdatedAt() : owner.getCreatedAt()),
                visibleMessages.size(),
                Boolean.TRUE.equals(owner.getPinned())
        );
    }

    /**
     * 历史记录里的图片只需要一个“看得出是什么”的轻量缩略图，不需要把原图整张 base64 存进 Mongo。
     * 否则用户频繁传图后，visible history 会迅速膨胀，历史恢复和导出都会越来越慢。
     */
    private ThumbnailPreview buildThumbnailPreview(byte[] content, String contentType) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
        if (source == null) {
            return new ThumbnailPreview("", null, null);
        }

        int width = source.getWidth();
        int height = source.getHeight();
        int maxEdge = Math.max(width, height);
        if (maxEdge <= ATTACHMENT_PREVIEW_MAX_EDGE) {
            return new ThumbnailPreview(
                    "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(content),
                    width,
                    height
            );
        }

        double ratio = (double) ATTACHMENT_PREVIEW_MAX_EDGE / maxEdge;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(scaled, "jpg", outputStream);
            return new ThumbnailPreview(
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray()),
                    targetWidth,
                    targetHeight
            );
        }
    }

    private record ThumbnailPreview(String dataUrl, Integer width, Integer height) {
    }
}
