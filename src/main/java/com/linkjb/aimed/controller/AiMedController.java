package com.linkjb.aimed.controller;

import com.linkjb.aimed.assistant.AiMedAgent;
import com.linkjb.aimed.assistant.LocalStreamingAiMedAgent;
import com.linkjb.aimed.assistant.OnlineAiMedAgent;
import com.linkjb.aimed.bean.ChatForm;
import com.linkjb.aimed.bean.KnowledgeDocumentDetail;
import com.linkjb.aimed.bean.KnowledgeFileInfo;
import com.linkjb.aimed.bean.KnowledgeUpdateRequest;
import com.linkjb.aimed.bean.KnowledgeUploadResponse;
import com.linkjb.aimed.service.KnowledgeBaseService;
import com.linkjb.aimed.service.VisionChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Locale;

@Tag(name = "AiMed")
@RestController
@RequestMapping("/aimed")
public class AiMedController {
    private static final Logger log = LoggerFactory.getLogger(AiMedController.class);
    private static final String LOCAL_OLLAMA = "LOCAL_OLLAMA";
    private static final String QWEN_ONLINE = "QWEN_ONLINE";

    @Autowired
    private AiMedAgent aiMedAgent;
    @Autowired
    private LocalStreamingAiMedAgent localStreamingAiMedAgent;
    @Autowired
    private OnlineAiMedAgent onlineAiMedAgent;
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    @Autowired
    private VisionChatService visionChatService;

    @Operation(summary = "对话")
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "text/stream;charset=utf-8")
    public Flux<String> chat(@RequestBody ChatForm chatForm) {
        String provider = normalizeProvider(chatForm == null ? null : chatForm.getModelProvider());
        Long memoryId = chatForm == null ? null : chatForm.getMemoryId();
        String message = chatForm == null ? null : chatForm.getMessage();
        log.info("chat.request provider={} memoryId={} chars={} attachments=0", provider, memoryId, textLength(message));
        return chat(provider, memoryId, message)
                .onErrorResume(error -> {
                    log.error("chat.request.failed provider={} memoryId={} chars={}", provider, memoryId, textLength(message), error);
                    return Flux.just(errorMessageForProvider(provider));
                });
    }

    @Operation(summary = "携带附件的对话")
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "text/stream;charset=utf-8")
    public Flux<String> chatWithFiles(@RequestParam("memoryId") Long memoryId,
                                      @RequestParam("message") String message,
                                      @RequestParam(value = "modelProvider", required = false) String modelProvider,
                                      @RequestParam(value = "files", required = false) MultipartFile[] files) {
        String provider = normalizeProvider(modelProvider);
        log.info("chat.request provider={} memoryId={} chars={} attachments={}", provider, memoryId, textLength(message), fileCount(files));
        try {
            if (knowledgeBaseService.hasImageAttachments(files)) {
                return Flux.just(visionChatService.analyze(memoryId, message, files, provider));
            }

            String augmentedMessage = knowledgeBaseService.buildChatMessageWithAttachments(message, files);
            return chat(provider, memoryId, augmentedMessage)
                    .onErrorResume(error -> {
                        log.error("chat.request.failed provider={} memoryId={} attachments={}", provider, memoryId, fileCount(files), error);
                        return Flux.just(errorMessageForProvider(provider));
                    });
        } catch (Exception error) {
            log.error("chat.attachment.parse.failed provider={} memoryId={} attachments={}", provider, memoryId, fileCount(files), error);
            return Flux.just("抱歉，上传文件暂时无法解析：" + error.getMessage());
        }
    }

    @Operation(summary = "上传本地知识库文件")
    @PostMapping(value = "/knowledge/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeUploadResponse uploadKnowledge(@RequestParam("files") MultipartFile[] files) {
        log.info("knowledge.upload.request files={}", fileCount(files));
        KnowledgeUploadResponse response = knowledgeBaseService.upload(files);
        log.info("knowledge.upload.response total={} accepted={} skipped={} failed={}",
                response.getTotal(), response.getAccepted(), response.getSkipped(), response.getFailed());
        return response;
    }

    @Operation(summary = "查看已上传的本地知识库文件")
    @GetMapping("/knowledge/files")
    public List<KnowledgeFileInfo> knowledgeFiles() throws IOException {
        return knowledgeBaseService.listUploadedFiles();
    }

    @Operation(summary = "查看单个知识文件详情及切分结果")
    @GetMapping("/knowledge/files/{hash}")
    public KnowledgeDocumentDetail knowledgeFileDetail(@PathVariable("hash") String hash) throws IOException {
        return knowledgeBaseService.getKnowledgeDetail(hash);
    }

    @Operation(summary = "更新单个知识文件内容")
    @PutMapping("/knowledge/files/{hash}")
    public KnowledgeDocumentDetail updateKnowledgeFile(@PathVariable("hash") String hash,
                                                       @RequestBody KnowledgeUpdateRequest request) throws IOException {
        return knowledgeBaseService.updateKnowledgeContent(hash, request == null ? null : request.getContent());
    }

    @Operation(summary = "删除单个知识文件")
    @DeleteMapping("/knowledge/files/{hash}")
    public void deleteKnowledgeFile(@PathVariable("hash") String hash) throws IOException {
        knowledgeBaseService.deleteKnowledge(hash);
    }

    private Flux<String> chat(String provider, Long memoryId, String message) {
        if (QWEN_ONLINE.equals(provider)) {
            return withFluxLogs(provider, memoryId, message, onlineAiMedAgent.chat(memoryId, message));
        }
        return Flux.create(emitter -> {
            long startedAt = System.nanoTime();
            AtomicInteger chunkCount = new AtomicInteger();
            AtomicLong charCount = new AtomicLong();
            log.info("model.stream.start provider={} memoryId={} chars={} message={}", provider, memoryId, textLength(message),message);
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

    private String normalizeProvider(String provider) {
        if (provider == null) {
            return LOCAL_OLLAMA;
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        return QWEN_ONLINE.equals(normalized) ? QWEN_ONLINE : LOCAL_OLLAMA;
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
