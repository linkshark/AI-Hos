package com.linkjb.aimed.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.request.ChatForm;
import com.linkjb.aimed.entity.dto.response.PagedResponse;
import com.linkjb.aimed.entity.dto.response.chat.ChatHistoryDetailResponse;
import com.linkjb.aimed.entity.dto.response.chat.ChatHistoryItemResponse;
import com.linkjb.aimed.entity.dto.response.chat.ChatSessionCreateResponse;
import com.linkjb.aimed.entity.dto.request.chat.ChatHistoryPinRequest;
import com.linkjb.aimed.entity.dto.request.chat.ChatHistoryRenameRequest;
import com.linkjb.aimed.entity.dto.response.ChatProviderConfigResponse;
import com.linkjb.aimed.entity.dto.response.chat.ChatStreamMetadata;
import com.linkjb.aimed.config.skywalk.TraceIdProvider;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.service.ChatSessionUserBindingService;
import com.linkjb.aimed.service.ChatApplicationService;
import com.linkjb.aimed.service.AuditLogService;
import com.linkjb.aimed.service.ChatHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Tag(name = "Chat")
@RestController
@RequestMapping("/aimed")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatApplicationService chatApplicationService;
    private final ChatSessionUserBindingService chatSessionUserBindingService;
    private final AuditLogService auditLogService;
    private final TraceIdProvider traceIdProvider;
    private final ChatHistoryService chatHistoryService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatApplicationService chatApplicationService,
                          ChatSessionUserBindingService chatSessionUserBindingService,
                          AuditLogService auditLogService,
                          TraceIdProvider traceIdProvider,
                          ChatHistoryService chatHistoryService,
                          ObjectMapper objectMapper) {
        this.chatApplicationService = chatApplicationService;
        this.chatSessionUserBindingService = chatSessionUserBindingService;
        this.auditLogService = auditLogService;
        this.traceIdProvider = traceIdProvider;
        this.chatHistoryService = chatHistoryService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "聊天模型入口配置")
    @GetMapping("/chat/provider-config")
    public ChatProviderConfigResponse providerConfig(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return chatApplicationService.providerConfig();
    }

    @Operation(summary = "当前用户的历史会话摘要")
    @GetMapping("/chat/histories")
    public PagedResponse<ChatHistoryItemResponse> histories(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                            @RequestParam(value = "page", defaultValue = "1") int page,
                                                            @RequestParam(value = "size", defaultValue = "20") int size,
                                                            @RequestParam(value = "keyword", required = false) String keyword) {
        return chatHistoryService.listHistory(
                currentUser.userId(),
                page,
                size,
                keyword
        );
    }

    @Operation(summary = "当前用户的历史会话详情")
    @GetMapping("/chat/histories/{memoryId}")
    public ChatHistoryDetailResponse historyDetail(@PathVariable("memoryId") Long memoryId,
                                                   @AuthenticationPrincipal AuthenticatedUser currentUser,
                                                   @RequestParam(value = "debug", required = false, defaultValue = "false") boolean debug) {
        return chatHistoryService.detail(currentUser.userId(), memoryId, currentUser != null && currentUser.isAdmin() && debug);
    }

    @Operation(summary = "重命名当前用户的历史会话")
    @PutMapping("/chat/histories/{memoryId}/rename")
    public ChatHistoryItemResponse renameHistory(@PathVariable("memoryId") Long memoryId,
                                                 @Valid @RequestBody ChatHistoryRenameRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return chatHistoryService.rename(currentUser.userId(), memoryId, request.getTitle());
    }

    @Operation(summary = "置顶或取消置顶当前用户的历史会话")
    @PutMapping("/chat/histories/{memoryId}/pin")
    public ChatHistoryItemResponse pinHistory(@PathVariable("memoryId") Long memoryId,
                                              @RequestBody(required = false) ChatHistoryPinRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        boolean pinned = request == null || request.getPinned() == null || request.getPinned();
        return chatHistoryService.pin(currentUser.userId(), memoryId, pinned);
    }

    @Operation(summary = "隐藏当前用户的历史会话")
    @DeleteMapping("/chat/histories/{memoryId}")
    public void deleteHistory(@PathVariable("memoryId") Long memoryId,
                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        chatHistoryService.hide(currentUser.userId(), memoryId);
    }

    @Operation(summary = "创建当前用户的新会话")
    @PostMapping("/chat/sessions")
    public ChatSessionCreateResponse createSession(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return new ChatSessionCreateResponse(chatHistoryService.createSession(currentUser.userId()));
    }

    @Operation(summary = "对话")
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "text/stream;charset=utf-8")
    public Flux<String> chat(@Valid @RequestBody ChatForm chatForm,
                             @AuthenticationPrincipal AuthenticatedUser currentUser) {
        log.info("对话请求开始 type=json memoryId={} provider={} userId={}",
                chatForm.getMemoryId(), chatForm.getModelProvider(), currentUser == null ? null : currentUser.userId());
        if (currentUser != null) {
            chatSessionUserBindingService.bindOrValidateOwnership(chatForm.getMemoryId(), currentUser.userId());
            chatHistoryService.recordUserMessage(currentUser.userId(), chatForm.getMemoryId(), chatForm.getMessage());
        }
        String provider = chatApplicationService.normalizeProvider(chatForm.getModelProvider());
        return wrapChatStream(
                chatApplicationService.chat(chatForm.getMemoryId(), chatForm.getMessage(), provider),
                currentUser,
                chatForm.getMemoryId(),
                provider,
                false
        );
    }

    @Operation(summary = "携带附件的对话")
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "text/stream;charset=utf-8")
    public Flux<String> chatWithFiles(@RequestParam("memoryId") Long memoryId,
                                      @RequestParam("message") String message,
                                      @RequestParam(value = "modelProvider", required = false) String modelProvider,
                                      @RequestParam(value = "files", required = false) MultipartFile[] files,
                                      @AuthenticationPrincipal AuthenticatedUser currentUser) {
        log.info("chat.controller.request type=multipart memoryId={} provider={} attachments={} userId={}",
                memoryId, modelProvider, files == null ? 0 : files.length, currentUser == null ? null : currentUser.userId());
        if (currentUser != null) {
            chatSessionUserBindingService.bindOrValidateOwnership(memoryId, currentUser.userId());
            chatHistoryService.recordUserMessage(currentUser.userId(), memoryId, message, files);
        }
        String provider = chatApplicationService.normalizeProvider(modelProvider);
        return wrapChatStream(
                chatApplicationService.chatWithFiles(memoryId, message, provider, files),
                currentUser,
                memoryId,
                provider,
                files != null && files.length > 0
        );
    }

    private Flux<String> wrapChatStream(Flux<String> source,
                                        AuthenticatedUser currentUser,
                                        Long memoryId,
                                        String provider,
                                        boolean hasAttachments) {
        String requestTraceId = traceIdProvider.currentTraceId();
        long startedAt = System.nanoTime();
        StringBuilder streamedContent = new StringBuilder();
        AtomicBoolean assistantPersisted = new AtomicBoolean(false);
        return source
                .doOnNext(streamedContent::append)
                .doOnComplete(() -> persistAssistantVisibleMessage(memoryId, streamedContent, assistantPersisted))
                .doOnError(error -> persistAssistantVisibleMessage(memoryId, streamedContent, assistantPersisted))
                .doFinally(signalType -> auditLogService.recordChatSummary(
                        currentUser == null ? null : currentUser.userId(),
                        currentUser == null ? null : currentUser.role(),
                        memoryId,
                        provider,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        hasAttachments,
                        requestTraceId,
                        chatApplicationService.consumeCompletedStreamMetadata(requestTraceId)
                ));
    }

    private void persistAssistantVisibleMessage(Long memoryId, StringBuilder streamedContent, AtomicBoolean assistantPersisted) {
        if (!assistantPersisted.compareAndSet(false, true)) {
            return;
        }
        ParsedStreamResult parsed = parseStreamResult(streamedContent == null ? "" : streamedContent.toString());
        if (!StringUtils.hasText(parsed.content()) && parsed.metadata() == null) {
            return;
        }
        chatHistoryService.recordAssistantMessage(
                memoryId,
                parsed.content(),
                parsed.metadata() == null ? List.of() : parsed.metadata().getCitations()
        );
    }

    private ParsedStreamResult parseStreamResult(String rawContent) {
        if (!StringUtils.hasText(rawContent) || !rawContent.contains(ChatApplicationService.STREAM_METADATA_MARKER)) {
            return new ParsedStreamResult(rawContent, null);
        }
        int markerIndex = rawContent.lastIndexOf(ChatApplicationService.STREAM_METADATA_MARKER);
        String content = rawContent.substring(0, markerIndex);
        String metadataText = rawContent.substring(markerIndex + ChatApplicationService.STREAM_METADATA_MARKER.length()).trim();
        if (!StringUtils.hasText(metadataText)) {
            return new ParsedStreamResult(content, null);
        }
        try {
            return new ParsedStreamResult(content, objectMapper.readValue(metadataText, ChatStreamMetadata.class));
        } catch (Exception exception) {
            log.warn("chat.stream.metadata.parse.failed rawLength={}", rawContent == null ? 0 : rawContent.length(), exception);
            return new ParsedStreamResult(rawContent, null);
        }
    }

    private record ParsedStreamResult(String content, ChatStreamMetadata metadata) {
    }
}
