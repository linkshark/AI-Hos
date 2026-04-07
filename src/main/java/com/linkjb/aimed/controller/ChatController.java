package com.linkjb.aimed.controller;

import com.linkjb.aimed.bean.ChatForm;
import com.linkjb.aimed.bean.ChatProviderConfigResponse;
import com.linkjb.aimed.config.TraceIdProvider;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.service.ChatSessionUserBindingService;
import com.linkjb.aimed.service.ChatApplicationService;
import com.linkjb.aimed.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@Tag(name = "Chat")
@RestController
@RequestMapping("/aimed")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatApplicationService chatApplicationService;
    private final ChatSessionUserBindingService chatSessionUserBindingService;
    private final AuditLogService auditLogService;
    private final TraceIdProvider traceIdProvider;

    public ChatController(ChatApplicationService chatApplicationService,
                          ChatSessionUserBindingService chatSessionUserBindingService,
                          AuditLogService auditLogService,
                          TraceIdProvider traceIdProvider) {
        this.chatApplicationService = chatApplicationService;
        this.chatSessionUserBindingService = chatSessionUserBindingService;
        this.auditLogService = auditLogService;
        this.traceIdProvider = traceIdProvider;
    }

    @Operation(summary = "聊天模型入口配置")
    @GetMapping("/chat/provider-config")
    public ChatProviderConfigResponse providerConfig(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return chatApplicationService.providerConfig();
    }

    @Operation(summary = "对话")
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "text/stream;charset=utf-8")
    public Flux<String> chat(@Valid @RequestBody ChatForm chatForm,
                             @AuthenticationPrincipal AuthenticatedUser currentUser) {
        log.info("chat.controller.request type=json memoryId={} provider={} userId={}",
                chatForm.getMemoryId(), chatForm.getModelProvider(), currentUser == null ? null : currentUser.userId());
        if (currentUser != null) {
            chatSessionUserBindingService.bindOrValidateOwnership(chatForm.getMemoryId(), currentUser.userId());
        }
        String provider = chatApplicationService.normalizeProvider(chatForm.getModelProvider());
        // 流式响应结束时可能已经脱离原始请求线程，这里提前锁定本次请求的 traceId，
        // 让聊天摘要审计日志能稳定回链到 SkyWalking。
        String requestTraceId = traceIdProvider.currentTraceId();
        long startedAt = System.nanoTime();
        return chatApplicationService.chat(chatForm.getMemoryId(), chatForm.getMessage(), provider)
                .doFinally(signalType -> auditLogService.recordChatSummary(
                        currentUser == null ? null : currentUser.userId(),
                        currentUser == null ? null : currentUser.role(),
                        chatForm.getMemoryId(),
                        provider,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        false,
                        requestTraceId
                ));
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
        }
        String provider = chatApplicationService.normalizeProvider(modelProvider);
        // 附件问答同样需要在进入控制器时固定 traceId，避免 doFinally 阶段丢链路。
        String requestTraceId = traceIdProvider.currentTraceId();
        long startedAt = System.nanoTime();
        return chatApplicationService.chatWithFiles(memoryId, message, provider, files)
                .doFinally(signalType -> auditLogService.recordChatSummary(
                        currentUser == null ? null : currentUser.userId(),
                        currentUser == null ? null : currentUser.role(),
                        memoryId,
                        provider,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        files != null && files.length > 0,
                        requestTraceId
                ));
    }
}
