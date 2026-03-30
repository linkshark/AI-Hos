package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.KnowledgeChunkInfo;
import com.linkjb.aimed.bean.KnowledgeDocumentDetail;
import com.linkjb.aimed.bean.KnowledgeFileInfo;
import com.linkjb.aimed.bean.KnowledgeUploadItem;
import com.linkjb.aimed.bean.KnowledgeUploadResponse;
import com.linkjb.aimed.entity.KnowledgeFileStatus;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

@Service
public class KnowledgeBaseService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final Set<String> DIRECT_TEXT_EXTENSIONS = Set.of("md", "markdown", "txt", "csv");
    private static final Set<String> INLINE_EDITABLE_EXTENSIONS = Set.of("md", "markdown", "txt", "csv", "html", "htm", "xml");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp");
    private static final int MAX_CHAT_ATTACHMENTS = 5;
    private static final int MAX_SINGLE_CHAT_ATTACHMENT_CHARACTERS = 8000;
    private static final int MAX_TOTAL_CHAT_ATTACHMENT_CHARACTERS = 18000;
    private static final int CHUNK_PREVIEW_LENGTH = 180;
    private static final Set<String> SUPPORTED_EXTENSIONS = new LinkedHashSet<>(List.of(
            "pdf", "txt", "md", "markdown", "csv", "doc", "docx", "rtf",
            "html", "htm", "xml", "odt", "ods", "odp", "xls", "xlsx", "ppt", "pptx"
    ));
    private static final Set<String> CHAT_ATTACHMENT_EXTENSIONS = new LinkedHashSet<>();

    static {
        CHAT_ATTACHMENT_EXTENSIONS.addAll(SUPPORTED_EXTENSIONS);
        CHAT_ATTACHMENT_EXTENSIONS.addAll(IMAGE_EXTENSIONS);
    }

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final Path storageDirectory;
    private final TaskExecutor knowledgeBootstrapExecutor;
    private final TaskExecutor knowledgeIngestionExecutor;
    private final KnowledgeProcessingNotifier knowledgeProcessingNotifier;
    private final KnowledgeFileStatusService knowledgeFileStatusService;
    private final Set<String> ingestedHashes = ConcurrentHashMap.newKeySet();
    private final Map<String, KnowledgeDocumentRecord> knowledgeRecords = new ConcurrentHashMap<>();
    private final Object embeddingStoreMonitor = new Object();
    private final int defaultChunkSize;
    private final int defaultChunkOverlap;
    private final int maxChunkSize;
    private final int maxSegmentsPerDocument;
    private final int embeddingBatchSize;

    private final DocumentParser textParser = new TextDocumentParser();
    private final DocumentParser pdfParser = new ApachePdfBoxDocumentParser();
    private final DocumentParser tikaParser = new ApacheTikaDocumentParser();

    public KnowledgeBaseService(EmbeddingStore<TextSegment> embeddingStore,
                                EmbeddingModel embeddingModel,
                                @Qualifier("knowledgeBootstrapExecutor") TaskExecutor knowledgeBootstrapExecutor,
                                @Qualifier("knowledgeIngestionExecutor") TaskExecutor knowledgeIngestionExecutor,
                                KnowledgeProcessingNotifier knowledgeProcessingNotifier,
                                KnowledgeFileStatusService knowledgeFileStatusService,
                                @Value("${app.knowledge-base.chunk-size:1000}") int defaultChunkSize,
                                @Value("${app.knowledge-base.chunk-overlap:150}") int defaultChunkOverlap,
                                @Value("${app.knowledge-base.max-chunk-size:4000}") int maxChunkSize,
                                @Value("${app.knowledge-base.max-segments-per-document:1200}") int maxSegmentsPerDocument,
                                @Value("${app.embedding.batch-size:24}") int embeddingBatchSize,
                                @Value("${app.knowledge-base.storage-dir:data/knowledge-base}") String storageDir) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.knowledgeBootstrapExecutor = knowledgeBootstrapExecutor;
        this.knowledgeIngestionExecutor = knowledgeIngestionExecutor;
        this.knowledgeProcessingNotifier = knowledgeProcessingNotifier;
        this.knowledgeFileStatusService = knowledgeFileStatusService;
        this.defaultChunkSize = Math.max(300, defaultChunkSize);
        this.defaultChunkOverlap = Math.max(0, defaultChunkOverlap);
        this.maxChunkSize = Math.max(this.defaultChunkSize, maxChunkSize);
        this.maxSegmentsPerDocument = Math.max(50, maxSegmentsPerDocument);
        this.embeddingBatchSize = Math.max(1, embeddingBatchSize);
        this.storageDirectory = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public void loadInitialKnowledgeBase() {
        knowledgeBootstrapExecutor.execute(() -> {
            try {
                reloadKnowledgeBase();
            } catch (IOException e) {
                log.error("初始化知识库失败", e);
            }
        });
    }

    public void reloadKnowledgeBase() throws IOException {
        ensureStorageDirectoryExists();
        synchronized (this) {
            synchronized (embeddingStoreMonitor) {
                embeddingStore.removeAll();
            }
            ingestedHashes.clear();
            knowledgeRecords.clear();
            knowledgeFileStatusService.deleteAll();
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/*");
        for (Resource resource : resources) {
            if (!resource.exists() || resource.getFilename() == null) {
                continue;
            }
            try {
                ingestResource(resource);
            } catch (BlankDocumentException e) {
                log.warn("跳过空白知识资源: {}", resource.getFilename());
            } catch (Exception e) {
                log.warn("跳过无法加载的内置知识资源: {}", resource.getFilename(), e);
            }
        }

        try (Stream<Path> paths = Files.list(storageDirectory)) {
            List<Path> storedFiles = paths.filter(Files::isRegularFile).sorted().toList();
            for (Path storedFile : storedFiles) {
                try {
                    ingestStoredFile(storedFile, extractHashFromStoredFilename(storedFile.getFileName().toString()));
                } catch (Exception e) {
                    log.warn("跳过无法解析的本地知识文件: {}", storedFile.getFileName(), e);
                }
            }
        }
    }

    public KnowledgeUploadResponse upload(MultipartFile[] files) {
        KnowledgeUploadResponse response = new KnowledgeUploadResponse();
        response.setTotal(files == null ? 0 : files.length);
        if (files == null || files.length == 0) {
            return response;
        }

        for (MultipartFile file : files) {
            KnowledgeUploadItem item = new KnowledgeUploadItem();
            item.setOriginalFilename(file == null ? null : file.getOriginalFilename());
            response.getItems().add(item);

            try {
                processUpload(file, item, response);
            } catch (Exception e) {
                item.setStatus("FAILED");
                item.setMessage("入库失败: " + e.getMessage());
                response.setFailed(response.getFailed() + 1);
            }
        }
        return response;
    }

    public List<KnowledgeFileInfo> listUploadedFiles() {
        return knowledgeFileStatusService.listAll().stream()
                .map(this::toKnowledgeFileInfo)
                .toList();
    }

    public KnowledgeDocumentDetail getKnowledgeDetail(String hash) throws IOException {
        KnowledgeDocumentRecord record = knowledgeRecords.get(hash);
        if (record != null) {
            return toKnowledgeDetail(record);
        }
        KnowledgeFileStatus status = knowledgeFileStatusService.findByHash(hash);
        if (status == null) {
            throw new IOException("未找到对应的知识文件");
        }
        return toKnowledgeDetail(status);
    }

    public synchronized KnowledgeDocumentDetail updateKnowledgeContent(String hash, String content) throws IOException {
        KnowledgeDocumentRecord record = knowledgeRecords.get(hash);
        if (record == null) {
            throw new IOException("未找到对应的知识文件");
        }
        if (!STATUS_READY.equals(record.processingStatus())) {
            throw new IOException("当前知识文件仍在处理中，暂不支持编辑");
        }
        if (!record.editable()) {
            throw new IOException("当前文件格式不支持在线编辑，请删除后重新上传");
        }
        if (record.storagePath() == null) {
            throw new IOException("内置知识文件不支持在线编辑");
        }

        String normalizedContent = normalizeText(content);
        if (!StringUtils.hasText(normalizedContent)) {
            throw new IOException("保存失败，内容不能为空");
        }

        String extension = record.extension();
        Path tempFile = Files.createTempFile(storageDirectory, "edit-", "." + extension);
        Files.writeString(tempFile, normalizedContent, StandardCharsets.UTF_8);

        try {
            String newHash = calculateSha256(tempFile);
            if (!newHash.equals(hash) && knowledgeFileStatusService.existsByHash(newHash)) {
                throw new IOException("更新后的内容与现有知识文件重复");
            }

            String storedFilename = newHash + "_" + sanitizeFilename(record.originalFilename());
            Path storedFile = storageDirectory.resolve(storedFilename);
            Files.move(tempFile, storedFile, StandardCopyOption.REPLACE_EXISTING);
            if (!storedFile.equals(record.storagePath())) {
                Files.deleteIfExists(record.storagePath());
            }
            reloadKnowledgeBase();
            return getKnowledgeDetail(newHash);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public synchronized void deleteKnowledge(String hash) throws IOException {
        KnowledgeDocumentRecord record = knowledgeRecords.get(hash);
        if (record == null) {
            throw new IOException("未找到对应的知识文件");
        }
        if (!record.deletable()) {
            throw new IOException("当前知识文件不允许删除");
        }
        if (record.storagePath() == null) {
            throw new IOException("当前知识文件缺少本地存储路径");
        }
        Files.deleteIfExists(record.storagePath());
        reloadKnowledgeBase();
    }

    public String buildChatMessageWithAttachments(String userMessage, MultipartFile[] files) throws IOException {
        String attachmentContext = buildChatAttachmentTextContext(files);
        if (!StringUtils.hasText(attachmentContext)) {
            return userMessage;
        }

        return attachmentContext + "\n\n[用户问题]\n" + userMessage;
    }

    public String buildChatAttachmentTextContext(MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) {
            return "";
        }

        if (files.length > MAX_CHAT_ATTACHMENTS) {
            throw new IOException("单次对话最多上传 " + MAX_CHAT_ATTACHMENTS + " 个文件");
        }

        List<ChatAttachmentContent> attachmentContents = new ArrayList<>();
        int remainingCharacters = MAX_TOTAL_CHAT_ATTACHMENT_CHARACTERS;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed";
            String extension = getExtension(originalFilename);
            if (!CHAT_ATTACHMENT_EXTENSIONS.contains(extension)) {
                throw new IOException("聊天附件暂不支持该格式: " + originalFilename);
            }
            if (IMAGE_EXTENSIONS.contains(extension)) {
                continue;
            }

            ParsedKnowledge parsedKnowledge = parseMultipartFile(file, originalFilename, extension);
            String text = parsedKnowledge.document().text();
            int currentLimit = Math.min(MAX_SINGLE_CHAT_ATTACHMENT_CHARACTERS, remainingCharacters);
            if (currentLimit <= 0) {
                break;
            }

            boolean truncated = text.length() > currentLimit;
            String boundedText = truncated ? text.substring(0, currentLimit) : text;
            attachmentContents.add(new ChatAttachmentContent(originalFilename, boundedText, truncated));
            remainingCharacters -= boundedText.length();
        }

        if (attachmentContents.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("以下是用户在本轮对话中上传的材料内容，仅用于当前问题，不写入长期知识库。\n")
                .append("回答时请优先结合这些材料；如果材料信息不足，请明确说明，不要编造。\n");

        for (int i = 0; i < attachmentContents.size(); i++) {
            ChatAttachmentContent attachment = attachmentContents.get(i);
            builder.append("\n[附件")
                    .append(i + 1)
                    .append("：")
                    .append(attachment.fileName())
                    .append("]\n")
                    .append(attachment.content());
            if (attachment.truncated()) {
                builder.append("\n（以上内容因篇幅限制已截断）");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public boolean hasImageAttachments(MultipartFile[] files) throws IOException {
        if (files == null) {
            return false;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed";
            String extension = getExtension(originalFilename);
            if (!CHAT_ATTACHMENT_EXTENSIONS.contains(extension)) {
                throw new IOException("聊天附件暂不支持该格式: " + originalFilename);
            }
            if (IMAGE_EXTENSIONS.contains(extension)) {
                return true;
            }
        }
        return false;
    }

    public boolean isImageAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed";
        return IMAGE_EXTENSIONS.contains(getExtension(originalFilename));
    }

    public String resolveMimeType(MultipartFile file) {
        if (file == null) {
            return "application/octet-stream";
        }
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        String extension = getExtension(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "");
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    private void processUpload(MultipartFile file, KnowledgeUploadItem item, KnowledgeUploadResponse response) throws IOException {
        if (file == null || file.isEmpty()) {
            item.setStatus("FAILED");
            item.setMessage("文件为空");
            response.setFailed(response.getFailed() + 1);
            return;
        }

        String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed";
        String extension = getExtension(originalFilename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            item.setStatus("FAILED");
            item.setMessage("暂不支持该格式，当前支持: " + String.join(", ", SUPPORTED_EXTENSIONS));
            response.setFailed(response.getFailed() + 1);
            return;
        }

        ensureStorageDirectoryExists();
        Path tempFile = Files.createTempFile(storageDirectory, "upload-", "." + extension);
        file.transferTo(tempFile);

        try {
            String hash = calculateSha256(tempFile);
            item.setHash(hash);
            if (ingestedHashes.contains(hash) || knowledgeRecords.containsKey(hash) || knowledgeFileStatusService.existsByHash(hash)) {
                item.setStatus("SKIPPED");
                item.setMessage("相同内容的知识文件已存在，跳过重复入库");
                item.setStoredFilename(findExistingStoredFilename(hash));
                response.setSkipped(response.getSkipped() + 1);
                return;
            }

            String storedFilename = hash + "_" + sanitizeFilename(originalFilename);
            Path storedFile = storageDirectory.resolve(storedFilename);
            Files.move(tempFile, storedFile, StandardCopyOption.REPLACE_EXISTING);
            long size = Files.size(storedFile);
            registerPlaceholderKnowledgeRecord(
                    hash,
                    storedFilename,
                    originalFilename,
                    "uploaded",
                    extension,
                    size,
                    isEditableExtension(extension),
                    true,
                    storedFile
            );
            item.setStoredFilename(storedFilename);
            item.setStatus("QUEUED");
            item.setMessage("文件已上传，正在后台解析并构建 RAG 切分");
            response.setAccepted(response.getAccepted() + 1);
            log.info("knowledge.upload.queued hash={} file={} size={} extension={}", hash, originalFilename, size, extension);
            scheduleKnowledgeProcessing(hash, storedFilename, originalFilename, extension, size, storedFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void ingestResource(Resource resource) throws IOException {
        String filename = resource.getFilename();
        String extension = getExtension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            return;
        }
        try {
            if (resource.contentLength() == 0) {
                log.warn("跳过空白知识资源: {}", filename);
                return;
            }
        } catch (IOException ignored) {
            // 某些资源无法提前获取长度，继续走解析流程。
        }

        String hash = hashForResource(resource);
        if (ingestedHashes.contains(hash)) {
            return;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            ParsedKnowledge parsedKnowledge = parseInputStream(inputStream, filename, hash, extension, "bundled");
            log.info("knowledge.bootstrap.parsed hash={} file={} parser={} chars={}", hash, filename,
                    parsedKnowledge.parserName(), parsedKnowledge.document().text().length());
            registerKnowledgeRecord(
                    hash,
                    filename,
                    filename,
                    "bundled",
                    extension,
                    parsedKnowledge.parserName(),
                    safeContentLength(resource),
                    false,
                    false,
                    null,
                    parsedKnowledge.document(),
                    STATUS_READY,
                    buildQualityMessage(parsedKnowledge.document().text().length()),
                    100,
                    0,
                    0
            );
        }
    }

    private void ingestStoredFile(Path storedFile, String hash) throws IOException {
        if (!StringUtils.hasText(hash) || ingestedHashes.contains(hash)) {
            return;
        }
        String originalFilename = stripHashPrefix(storedFile.getFileName().toString());
        ParsedKnowledge parsedKnowledge = parseFile(storedFile, originalFilename, hash);
        log.info("knowledge.reload.parsed hash={} file={} parser={} chars={}", hash, originalFilename,
                parsedKnowledge.parserName(), parsedKnowledge.document().text().length());
        registerKnowledgeRecord(
                hash,
                storedFile.getFileName().toString(),
                originalFilename,
                "uploaded",
                getExtension(originalFilename),
                parsedKnowledge.parserName(),
                Files.size(storedFile),
                isEditableExtension(getExtension(originalFilename)),
                true,
                storedFile,
                parsedKnowledge.document(),
                STATUS_READY,
                buildQualityMessage(parsedKnowledge.document().text().length()),
                100,
                0,
                0
        );
    }

    private ParsedKnowledge parseFile(Path file, String originalFilename, String hash) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return parseInputStream(inputStream, originalFilename, hash, getExtension(originalFilename), "uploaded");
        }
    }

    private ParsedKnowledge parseMultipartFile(MultipartFile file, String originalFilename, String extension) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return parseInputStream(inputStream, originalFilename, "transient-chat", extension, "chat-upload");
        }
    }

    private ParsedKnowledge parseInputStream(InputStream inputStream,
                                             String originalFilename,
                                             String hash,
                                             String extension,
                                             String source) throws IOException {
        byte[] fileBytes = inputStream.readAllBytes();
        DocumentParser parser = selectParser(extension);
        Document parsedDocument;
        String parserName;
        try {
            parsedDocument = parser.parse(new ByteArrayInputStream(fileBytes));
            parserName = parser.getClass().getSimpleName();
        } catch (Exception primaryException) {
            if (!"pdf".equals(extension)) {
                throw primaryException;
            }
            log.warn("PDFBox 解析失败，回退到 Tika: {}", originalFilename, primaryException);
            parsedDocument = tikaParser.parse(new ByteArrayInputStream(fileBytes));
            parserName = tikaParser.getClass().getSimpleName();
        }
        String normalizedText = normalizeText(parsedDocument.text());
        if (!StringUtils.hasText(normalizedText)) {
            throw new IOException("未从文件中解析出有效文本");
        }

        Metadata metadata = parsedDocument.metadata() == null ? new Metadata() : parsedDocument.metadata().copy();
        metadata.put(Document.FILE_NAME, originalFilename);
        metadata.put("knowledge_hash", hash);
        metadata.put("knowledge_source", source);
        metadata.put("knowledge_extension", extension);
        return new ParsedKnowledge(Document.from(normalizedText, metadata), parserName);
    }

    private void registerKnowledgeRecord(String hash,
                                         String fileName,
                                         String originalFilename,
                                         String source,
                                         String extension,
                                         String parserName,
                                         long size,
                                         boolean editable,
                                         boolean deletable,
                                         Path storagePath,
                                         Document document,
                                         String processingStatus,
                                         String statusMessage,
                                         int progressPercent,
                                         int currentBatch,
                                         int totalBatches) {
        List<TextSegment> segments = splitDocument(document);
        if (segments.isEmpty()) {
            throw new IllegalStateException("知识文件切分结果为空");
        }
        log.info("knowledge.segmented hash={} file={} segments={} parser={} status={}",
                hash, originalFilename, segments.size(), parserName, processingStatus);

        List<String> segmentIds = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            segmentIds.add(hash + "-segment-" + (i + 1));
        }

        int effectiveTotalBatches = Math.max(totalBatches, calculateBatchCount(segments.size()));

        synchronized (embeddingStoreMonitor) {
            List<dev.langchain4j.data.embedding.Embedding> embeddings = embedSegmentsInBatches(hash, segments, effectiveTotalBatches);
            embeddingStore.addAll(segmentIds, embeddings, segments);
        }
        KnowledgeDocumentRecord record = new KnowledgeDocumentRecord(
                fileName,
                originalFilename,
                hash,
                source,
                parserName,
                extension,
                size,
                editable,
                deletable,
                processingStatus,
                statusMessage,
                progressPercent,
                currentBatch,
                effectiveTotalBatches,
                document.text(),
                buildChunkInfos(segments),
                storagePath
        );
        ingestedHashes.add(hash);
        knowledgeRecords.put(hash, record);
        saveKnowledgeStatus(record);
    }

    private void registerPlaceholderKnowledgeRecord(String hash,
                                                    String fileName,
                                                    String originalFilename,
                                                    String source,
                                                    String extension,
                                                    long size,
                                                    boolean editable,
                                                    boolean deletable,
                                                    Path storagePath) {
        KnowledgeDocumentRecord record = new KnowledgeDocumentRecord(
                fileName,
                originalFilename,
                hash,
                source,
                null,
                extension,
                size,
                editable,
                deletable,
                STATUS_PROCESSING,
                "文件已上传，等待后台解析",
                0,
                0,
                0,
                "",
                List.of(),
                storagePath
        );
        knowledgeRecords.put(hash, record);
        saveKnowledgeStatus(record);
    }

    private void scheduleKnowledgeProcessing(String hash,
                                             String storedFilename,
                                             String originalFilename,
                                             String extension,
                                             long size,
                                             Path storedFile) {
        try {
            knowledgeIngestionExecutor.execute(() -> processKnowledgeAsync(hash, storedFilename, originalFilename, extension, size, storedFile));
        } catch (RejectedExecutionException e) {
            log.error("知识库后台任务队列已满, hash={}, file={}", hash, originalFilename, e);
            updateKnowledgeFailure(hash, "后台任务队列已满，请稍后重试");
        }
    }

    private void processKnowledgeAsync(String hash,
                                       String storedFilename,
                                       String originalFilename,
                                       String extension,
                                       long size,
                                       Path storedFile) {
        long startedAt = System.nanoTime();
        updateKnowledgeStatus(hash, STATUS_PROCESSING, "正在解析文档并构建向量索引", 5, 0, 0);
        try {
            if (!Files.exists(storedFile)) {
                removeKnowledgeRecord(hash);
                return;
            }
            ParsedKnowledge parsedKnowledge = parseFile(storedFile, originalFilename, hash);
            log.info("knowledge.process.parsed hash={} file={} parser={} chars={} durationMs={}",
                    hash, originalFilename, parsedKnowledge.parserName(), parsedKnowledge.document().text().length(), durationMs(startedAt));
            if (!Files.exists(storedFile) || !knowledgeRecords.containsKey(hash)) {
                return;
            }
            registerKnowledgeRecord(
                    hash,
                    storedFilename,
                    originalFilename,
                    "uploaded",
                    extension,
                    parsedKnowledge.parserName(),
                    size,
                    isEditableExtension(extension),
                    true,
                    storedFile,
                    parsedKnowledge.document(),
                    STATUS_READY,
                    buildQualityMessage(parsedKnowledge.document().text().length()),
                    100,
                    0,
                    0
            );
            log.info("knowledge.process.completed hash={} file={} durationMs={}", hash, originalFilename, durationMs(startedAt));
            knowledgeProcessingNotifier.notifyCompleted(hash, originalFilename, "RAG 切分和向量索引已完成");
        } catch (Exception e) {
            log.error("后台处理知识文件失败, hash={}, file={}", hash, originalFilename, e);
            updateKnowledgeFailure(hash, e.getMessage());
        }
    }

    private void updateKnowledgeStatus(String hash, String processingStatus, String statusMessage) {
        updateKnowledgeStatus(hash, processingStatus, statusMessage, 0, 0, 0);
    }

    private void updateKnowledgeStatus(String hash,
                                       String processingStatus,
                                       String statusMessage,
                                       int progressPercent,
                                       int currentBatch,
                                       int totalBatches) {
        knowledgeRecords.computeIfPresent(hash, (ignored, existing) -> {
            KnowledgeDocumentRecord updated = new KnowledgeDocumentRecord(
                existing.fileName(),
                existing.originalFilename(),
                existing.hash(),
                existing.source(),
                existing.parser(),
                existing.extension(),
                existing.size(),
                existing.editable(),
                existing.deletable(),
                processingStatus,
                statusMessage,
                normalizeProgress(processingStatus, progressPercent),
                currentBatch,
                totalBatches > 0 ? totalBatches : existing.totalBatches(),
                existing.extractedText(),
                existing.chunks(),
                existing.storagePath()
            );
            saveKnowledgeStatus(updated);
            if ("uploaded".equals(existing.source()) && STATUS_PROCESSING.equals(processingStatus)) {
                knowledgeProcessingNotifier.notifyProgress(
                        updated.hash(),
                        updated.originalFilename(),
                        updated.statusMessage(),
                        updated.progressPercent(),
                        updated.currentBatch(),
                        updated.totalBatches()
                );
            }
            return updated;
        });
    }

    private void updateKnowledgeFailure(String hash, String errorMessage) {
        knowledgeRecords.computeIfPresent(hash, (ignored, existing) -> {
            KnowledgeDocumentRecord updated = new KnowledgeDocumentRecord(
                existing.fileName(),
                existing.originalFilename(),
                existing.hash(),
                existing.source(),
                existing.parser(),
                existing.extension(),
                existing.size(),
                existing.editable(),
                existing.deletable(),
                STATUS_FAILED,
                "处理失败：" + errorMessage,
                existing.progressPercent(),
                existing.currentBatch(),
                existing.totalBatches(),
                existing.extractedText(),
                existing.chunks(),
                existing.storagePath()
            );
            saveKnowledgeStatus(updated);
            if ("uploaded".equals(existing.source())) {
                knowledgeProcessingNotifier.notifyFailed(
                        updated.hash(),
                        updated.originalFilename(),
                        updated.statusMessage(),
                        updated.progressPercent(),
                        updated.currentBatch(),
                        updated.totalBatches()
                );
            }
            return updated;
        });
    }

    private void removeKnowledgeRecord(String hash) {
        KnowledgeDocumentRecord removed = knowledgeRecords.remove(hash);
        if (removed != null) {
            ingestedHashes.remove(hash);
        }
        knowledgeFileStatusService.deleteByHash(hash);
    }

    private DocumentParser selectParser(String extension) {
        if ("pdf".equals(extension)) {
            return pdfParser;
        }
        if (DIRECT_TEXT_EXTENSIONS.contains(extension)) {
            return textParser;
        }
        return tikaParser;
    }

    private void ensureStorageDirectoryExists() throws IOException {
        Files.createDirectories(storageDirectory);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String buildQualityMessage(int extractedCharacters) {
        if (extractedCharacters < 50) {
            return "已入库，但可提取文本较短，建议检查原文件内容是否完整";
        }
        return "知识文件已解析并完成入库";
    }

    private boolean isEditableExtension(String extension) {
        return INLINE_EDITABLE_EXTENSIONS.contains(extension);
    }

    private long safeContentLength(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private List<KnowledgeChunkInfo> buildChunkInfos(List<TextSegment> segments) {
        List<KnowledgeChunkInfo> chunks = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            KnowledgeChunkInfo chunk = new KnowledgeChunkInfo();
            chunk.setIndex(i + 1);
            chunk.setContent(segment.text());
            chunk.setCharacterCount(segment.text().length());
            chunk.setPreview(buildPreview(segment.text()));
            chunks.add(chunk);
        }
        return chunks;
    }

    private String buildPreview(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() <= CHUNK_PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, CHUNK_PREVIEW_LENGTH) + "...";
    }

    private String getExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}._-]", "_");
    }

    private String calculateSha256(Path file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return calculateSha256(inputStream);
        }
    }

    private String calculateSha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest()) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String findExistingStoredFilename(String hash) throws IOException {
        try (Stream<Path> paths = Files.list(storageDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(hash + "_"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private String extractHashFromStoredFilename(String storedFilename) {
        int separatorIndex = storedFilename.indexOf('_');
        if (separatorIndex <= 0) {
            return null;
        }
        return storedFilename.substring(0, separatorIndex);
    }

    private String stripHashPrefix(String storedFilename) {
        int separatorIndex = storedFilename.indexOf('_');
        if (separatorIndex <= 0 || separatorIndex == storedFilename.length() - 1) {
            return storedFilename;
        }
        return storedFilename.substring(separatorIndex + 1);
    }

    private String hashForResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return calculateSha256(inputStream);
        }
    }

    private List<TextSegment> splitDocument(Document document) {
        int chunkSize = defaultChunkSize;
        int chunkOverlap = Math.min(defaultChunkOverlap, Math.max(0, chunkSize / 3));
        List<TextSegment> segments = createSplitter(chunkSize, chunkOverlap).split(document);
        while (segments.size() > maxSegmentsPerDocument && chunkSize < maxChunkSize) {
            int nextChunkSize = Math.min(maxChunkSize, chunkSize * 2);
            if (nextChunkSize == chunkSize) {
                break;
            }
            chunkSize = nextChunkSize;
            chunkOverlap = Math.min(Math.max(50, chunkOverlap * 2), Math.max(0, chunkSize / 4));
            segments = createSplitter(chunkSize, chunkOverlap).split(document);
        }
        return segments;
    }

    private DocumentSplitter createSplitter(int chunkSize, int chunkOverlap) {
        return DocumentSplitters.recursive(chunkSize, chunkOverlap);
    }

    private List<dev.langchain4j.data.embedding.Embedding> embedSegmentsInBatches(String hash,
                                                                                  List<TextSegment> segments,
                                                                                  int totalBatches) {
        List<dev.langchain4j.data.embedding.Embedding> embeddings = new ArrayList<>(segments.size());
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int start = batchIndex * embeddingBatchSize;
            int end = Math.min(start + embeddingBatchSize, segments.size());
            List<TextSegment> batch = List.copyOf(segments.subList(start, end));
            int progressPercent = buildProgressPercent(batchIndex + 1, totalBatches);
            if (shouldLogBatchProgress(batchIndex + 1, totalBatches)) {
                log.info("knowledge.embedding.batch hash={} batch={}/{} batchSize={} progress={}%",
                        hash, batchIndex + 1, totalBatches, batch.size(), progressPercent);
            }
            updateKnowledgeStatus(hash, STATUS_PROCESSING, buildEmbeddingProgressMessage(segments.size(), batchIndex + 1, totalBatches), progressPercent, batchIndex + 1, totalBatches);
            embeddings.addAll(embeddingModel.embedAll(batch).content());
        }
        return embeddings;
    }

    private int calculateBatchCount(int segmentCount) {
        return Math.max(1, (segmentCount + embeddingBatchSize - 1) / embeddingBatchSize);
    }

    private String buildEmbeddingProgressMessage(int segmentCount, int currentBatch, int totalBatches) {
        if (totalBatches <= 1 || currentBatch <= 0) {
            return "文档切分完成，共 " + segmentCount + " 段，正在构建向量索引";
        }
        return "文档切分完成，共 " + segmentCount + " 段，正在构建向量索引（" + currentBatch + "/" + totalBatches + "）";
    }

    private int buildProgressPercent(int currentBatch, int totalBatches) {
        if (totalBatches <= 0 || currentBatch <= 0) {
            return 5;
        }
        int percent = (int) Math.round((currentBatch * 100.0) / totalBatches);
        return Math.max(5, Math.min(99, percent));
    }

    private int normalizeProgress(String processingStatus, int progressPercent) {
        if (STATUS_READY.equals(processingStatus)) {
            return 100;
        }
        if (STATUS_FAILED.equals(processingStatus)) {
            return Math.max(0, Math.min(99, progressPercent));
        }
        return Math.max(0, Math.min(99, progressPercent));
    }

    private boolean shouldLogBatchProgress(int currentBatch, int totalBatches) {
        return currentBatch == 1 || currentBatch == totalBatches || currentBatch % 5 == 0;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void saveKnowledgeStatus(KnowledgeDocumentRecord record) {
        KnowledgeFileStatus status = new KnowledgeFileStatus();
        status.setHash(record.hash());
        status.setFileName(record.fileName());
        status.setOriginalFilename(record.originalFilename());
        status.setSource(record.source());
        status.setParser(record.parser());
        status.setExtension(record.extension());
        status.setSize(record.size());
        status.setEditable(record.editable());
        status.setDeletable(record.deletable());
        status.setProcessingStatus(record.processingStatus());
        status.setStatusMessage(record.statusMessage());
        status.setProgressPercent(record.progressPercent());
        status.setCurrentBatch(record.currentBatch());
        status.setTotalBatches(record.totalBatches());
        status.setExtractedCharacters(record.extractedText() == null ? 0 : record.extractedText().length());
        status.setChunkCount(record.chunks() == null ? 0 : record.chunks().size());
        knowledgeFileStatusService.saveOrUpdate(status);
    }

    private record ParsedKnowledge(Document document, String parserName) {
    }

    private record ChatAttachmentContent(String fileName, String content, boolean truncated) {
    }

    private KnowledgeFileInfo toKnowledgeFileInfo(KnowledgeDocumentRecord record) {
        KnowledgeFileInfo fileInfo = new KnowledgeFileInfo();
        fileInfo.setFileName(record.fileName());
        fileInfo.setOriginalFilename(record.originalFilename());
        fileInfo.setHash(record.hash());
        fileInfo.setSource(record.source());
        fileInfo.setParser(record.parser());
        fileInfo.setExtension(record.extension());
        fileInfo.setSize(record.size());
        fileInfo.setEditable(record.editable());
        fileInfo.setDeletable(record.deletable());
        fileInfo.setProcessingStatus(record.processingStatus());
        fileInfo.setStatusMessage(record.statusMessage());
        fileInfo.setProgressPercent(record.progressPercent());
        fileInfo.setCurrentBatch(record.currentBatch());
        fileInfo.setTotalBatches(record.totalBatches());
        return fileInfo;
    }

    private KnowledgeFileInfo toKnowledgeFileInfo(KnowledgeFileStatus status) {
        KnowledgeFileInfo fileInfo = new KnowledgeFileInfo();
        fileInfo.setFileName(status.getFileName());
        fileInfo.setOriginalFilename(status.getOriginalFilename());
        fileInfo.setHash(status.getHash());
        fileInfo.setSource(status.getSource());
        fileInfo.setParser(status.getParser());
        fileInfo.setExtension(status.getExtension());
        fileInfo.setSize(status.getSize() == null ? 0L : status.getSize());
        fileInfo.setEditable(Boolean.TRUE.equals(status.getEditable()));
        fileInfo.setDeletable(Boolean.TRUE.equals(status.getDeletable()));
        fileInfo.setProcessingStatus(status.getProcessingStatus());
        fileInfo.setStatusMessage(status.getStatusMessage());
        fileInfo.setProgressPercent(status.getProgressPercent());
        fileInfo.setCurrentBatch(status.getCurrentBatch());
        fileInfo.setTotalBatches(status.getTotalBatches());
        return fileInfo;
    }

    private KnowledgeDocumentDetail toKnowledgeDetail(KnowledgeDocumentRecord record) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setFileName(record.fileName());
        detail.setOriginalFilename(record.originalFilename());
        detail.setHash(record.hash());
        detail.setSource(record.source());
        detail.setParser(record.parser());
        detail.setExtension(record.extension());
        detail.setSize(record.size());
        detail.setEditable(record.editable());
        detail.setDeletable(record.deletable());
        detail.setProcessingStatus(record.processingStatus());
        detail.setStatusMessage(record.statusMessage());
        detail.setProgressPercent(record.progressPercent());
        detail.setCurrentBatch(record.currentBatch());
        detail.setTotalBatches(record.totalBatches());
        detail.setExtractedText(record.extractedText());
        detail.setChunks(record.chunks());
        return detail;
    }

    private KnowledgeDocumentDetail toKnowledgeDetail(KnowledgeFileStatus status) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setFileName(status.getFileName());
        detail.setOriginalFilename(status.getOriginalFilename());
        detail.setHash(status.getHash());
        detail.setSource(status.getSource());
        detail.setParser(status.getParser());
        detail.setExtension(status.getExtension());
        detail.setSize(status.getSize() == null ? 0L : status.getSize());
        detail.setEditable(Boolean.TRUE.equals(status.getEditable()));
        detail.setDeletable(Boolean.TRUE.equals(status.getDeletable()));
        detail.setProcessingStatus(status.getProcessingStatus());
        detail.setStatusMessage(status.getStatusMessage());
        detail.setProgressPercent(status.getProgressPercent());
        detail.setCurrentBatch(status.getCurrentBatch());
        detail.setTotalBatches(status.getTotalBatches());
        detail.setExtractedText("");
        detail.setChunks(List.of());
        return detail;
    }

    private record KnowledgeDocumentRecord(String fileName,
                                           String originalFilename,
                                           String hash,
                                           String source,
                                           String parser,
                                           String extension,
                                           long size,
                                           boolean editable,
                                           boolean deletable,
                                           String processingStatus,
                                           String statusMessage,
                                           int progressPercent,
                                           int currentBatch,
                                           int totalBatches,
                                           String extractedText,
                                           List<KnowledgeChunkInfo> chunks,
                                           Path storagePath) {
    }
}
