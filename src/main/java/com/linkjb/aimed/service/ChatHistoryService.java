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
import com.linkjb.aimed.store.MongoVisibleChatHistoryStore.VisibleHistorySummary;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int LEGACY_CONTEXT_LENGTH_THRESHOLD = 500;
    private static final int ATTACHMENT_PREVIEW_MAX_EDGE = 240;

    private final ChatSessionOwnerService chatSessionOwnerService;
    private final MongoChatMemoryStore mongoChatMemoryStore;
    private final MongoVisibleChatHistoryStore mongoVisibleChatHistoryStore;
    private final ChatSessionUserBindingService chatSessionUserBindingService;
    private final Set<Long> reconciledHistorySummaryUsers = ConcurrentHashMap.newKeySet();
    private final LegacyHistoryContentSanitizer legacyHistoryContentSanitizer;
    private final VisibleHistorySummarySupport visibleHistorySummarySupport;

    public ChatHistoryService(ChatSessionOwnerService chatSessionOwnerService,
                              MongoChatMemoryStore mongoChatMemoryStore,
                              MongoVisibleChatHistoryStore mongoVisibleChatHistoryStore,
                              ChatSessionUserBindingService chatSessionUserBindingService) {
        this.chatSessionOwnerService = chatSessionOwnerService;
        this.mongoChatMemoryStore = mongoChatMemoryStore;
        this.mongoVisibleChatHistoryStore = mongoVisibleChatHistoryStore;
        this.chatSessionUserBindingService = chatSessionUserBindingService;
        this.legacyHistoryContentSanitizer = new LegacyHistoryContentSanitizer(LEGACY_CONTEXT_LENGTH_THRESHOLD);
        this.visibleHistorySummarySupport = new VisibleHistorySummarySupport(DATE_TIME_FORMATTER);
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
        visibleHistorySummarySupport.refreshVisibleHistorySummary(history);
        mongoVisibleChatHistoryStore.save(history);
        syncOwnerSummary(history);
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
        visibleHistorySummarySupport.refreshVisibleHistorySummary(history);
        if (!visibleHistorySummarySupport.hasMeaningfulVisibleContent(history)) {
            return;
        }
        mongoVisibleChatHistoryStore.save(history);
        syncOwnerSummary(history);
    }

    public PagedResponse<ChatHistoryItemResponse> listHistory(Long userId,
                                                              int page,
                                                              int size,
                                                              String keyword) {
        reconcileVisibleHistorySummaries(userId);
        backfillMissingHistorySummaries(userId);
        return chatSessionOwnerService.listVisibleSummaryPage(userId, page, size, keyword);
    }

    public ChatHistoryDetailResponse detail(Long userId, Long memoryId, boolean includeInternalContent) {
        ChatSessionOwner owner = loadVisibleOwner(memoryId, userId);
        if (includeInternalContent) {
            return buildDebugDetail(owner);
        }
        ChatVisibleHistoryDocument history = loadVisibleHistory(userId, memoryId, includeInternalContent);
        if (history == null || !visibleHistorySummarySupport.hasMeaningfulVisibleContent(history)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前历史会话不存在或已删除");
        }
        List<ChatHistoryMessageResponse> visibleMessages = history == null ? List.of() : history.getMessages();
        String defaultTitle = StringUtils.hasText(history == null ? null : history.getFirstQuestion())
                ? abbreviate(singleLine(history.getFirstQuestion()), 22)
                : visibleHistorySummarySupport.summarizeTitle(visibleMessages);
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
        return listItem(owner);
    }

    public ChatHistoryItemResponse pin(Long userId, Long memoryId, boolean pinned) {
        ChatSessionOwner owner = loadVisibleOwner(memoryId, userId);
        LocalDateTime now = LocalDateTime.now();
        chatSessionOwnerService.updatePinned(memoryId, pinned);
        owner.setPinned(pinned);
        owner.setPinnedAt(pinned ? now : null);
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
            syncOwnerSummary(migrated);
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
                : visibleHistorySummarySupport.summarizeTitle(rawMessages);
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
                String content = legacyHistoryContentSanitizer.sanitize(userMessage.singleText(), includeInternalContent);
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
        visibleHistorySummarySupport.refreshVisibleHistorySummary(history);
        return history;
    }

    private void syncOwnerSummary(ChatVisibleHistoryDocument history) {
        if (history == null || history.getMemoryId() == null) {
            return;
        }
        chatSessionOwnerService.updateSummary(
                history.getMemoryId(),
                history.getFirstQuestion(),
                history.getLastPreview(),
                history.getMessages() == null ? 0 : history.getMessages().size(),
                LocalDateTime.now()
        );
    }

    private void backfillMissingHistorySummaries(Long userId) {
        for (ChatSessionOwner owner : chatSessionOwnerService.listVisibleMissingSummaryByUserId(userId, 200)) {
            VisibleHistorySummary summary = mongoVisibleChatHistoryStore.getSummary(owner.getMemoryId());
            if (summary == null || !StringUtils.hasText(summary.firstQuestion())) {
                continue;
            }
            chatSessionOwnerService.updateSummary(
                    owner.getMemoryId(),
                    summary.firstQuestion(),
                    summary.lastPreview(),
                    owner.getMessageCount() == null ? 0 : owner.getMessageCount(),
                    visibleHistorySummarySupport.visibleHistoryUpdatedAt(summary, owner.getUpdatedAt())
            );
        }
    }

    private void reconcileVisibleHistorySummaries(Long userId) {
        if (userId == null || !reconciledHistorySummaryUsers.add(userId)) {
            return;
        }
        for (ChatSessionOwner owner : chatSessionOwnerService.listVisibleByUserId(userId)) {
            VisibleHistorySummary summary = mongoVisibleChatHistoryStore.getSummary(owner.getMemoryId());
            if (summary == null || !StringUtils.hasText(summary.firstQuestion())) {
                continue;
            }
            LocalDateTime visibleUpdatedAt = visibleHistorySummarySupport.visibleHistoryUpdatedAt(summary, owner.getUpdatedAt());
            if (!visibleHistorySummarySupport.shouldRefreshOwnerSummary(owner, summary, visibleUpdatedAt)) {
                continue;
            }
            chatSessionOwnerService.updateSummary(
                    owner.getMemoryId(),
                    summary.firstQuestion(),
                    summary.lastPreview(),
                    owner.getMessageCount() == null ? 0 : owner.getMessageCount(),
                    visibleUpdatedAt
            );
        }
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
            String extension = visibleHistorySummarySupport.extensionOf(filename);
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

    private String singleLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
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
        if (!StringUtils.hasText(owner.getFirstQuestion())) {
            ChatVisibleHistoryDocument history = loadVisibleHistory(owner.getUserId(), owner.getMemoryId(), false);
            if (history == null || !visibleHistorySummarySupport.hasMeaningfulVisibleContent(history)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前历史会话不存在或已删除");
            }
            owner.setFirstQuestion(history.getFirstQuestion());
            owner.setLastPreview(history.getLastPreview());
            owner.setMessageCount(history.getMessages() == null ? 0 : history.getMessages().size());
        }
        return chatSessionOwnerService.toHistoryItem(owner);
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
