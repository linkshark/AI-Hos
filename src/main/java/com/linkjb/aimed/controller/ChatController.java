package com.linkjb.aimed.controller;

import com.linkjb.aimed.bean.ChatForm;
import com.linkjb.aimed.bean.ChatProviderConfigResponse;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.service.ChatApplicationService;
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

    public ChatController(ChatApplicationService chatApplicationService) {
        this.chatApplicationService = chatApplicationService;
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
        return chatApplicationService.chat(chatForm.getMemoryId(), chatForm.getMessage(), chatForm.getModelProvider());
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
        return chatApplicationService.chatWithFiles(memoryId, message, modelProvider, files);
    }
}
