package com.linkjb.aimed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.vo.KnowledgeChunkInfo;
import com.linkjb.aimed.entity.vo.KnowledgeDocumentDetail;
import com.linkjb.aimed.entity.vo.KnowledgeFileInfo;
import com.linkjb.aimed.entity.vo.KnowledgeMetadataBackfillItem;
import com.linkjb.aimed.entity.dto.response.KnowledgeMetadataBackfillResponse;
import com.linkjb.aimed.entity.vo.KnowledgeUploadItem;
import com.linkjb.aimed.entity.dto.response.KnowledgeUploadResponse;
import com.linkjb.aimed.service.knowledge.KnowledgeAttachmentService;
import com.linkjb.aimed.service.knowledge.KnowledgeParseService;
import com.linkjb.aimed.service.knowledge.KnowledgeParsedDocument;
import com.linkjb.aimed.entity.dto.request.KnowledgeUpdateRequest;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import com.linkjb.aimed.entity.KnowledgeFileStatus;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 知识库主服务。
 *
 * 这里同时承担了三件事：
 * 1. 管理知识文件的生命周期，例如上传、处理、发布、归档、重处理。
 * 2. 管理切片和 embedding 的持久化，以及应用启动后的恢复。
 * 3. 负责把“管理员能理解的业务状态”翻译成底层索引状态。
 *
 * 这个类故意把很多流程收在一起，是因为知识文件状态、chunk 数据、embedding 数据三者必须严格同步。
 * 如果把这些逻辑拆散到很多小服务里，排查“为什么文件显示已上线但检索不到”会变得非常痛苦。
 */
@Service
public class KnowledgeBaseService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_PENDING_SYNC = "PENDING_SYNC";
    private static final String INDEX_UPGRADE_IDLE = "IDLE";
    private static final String INDEX_UPGRADE_UPGRADING = "UPGRADING";
    private static final String INDEX_UPGRADE_FAILED = "FAILED";
    private static final int CURRENT_INDEX_BUILD_VERSION = 2;
    private static final Set<String> INLINE_EDITABLE_EXTENSIONS = Set.of("md", "markdown", "txt", "csv", "html", "htm", "xml");
    private static final int CHUNK_PREVIEW_LENGTH = 180;
    private static final Set<String> SUPPORTED_EXTENSIONS = new LinkedHashSet<>(List.of(
            "pdf", "txt", "md", "markdown", "csv", "doc", "docx", "rtf",
            "html", "htm", "xml", "odt", "ods", "odp", "xls", "xlsx", "ppt", "pptx"
    ));
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final Path storageDirectory;
    private final TaskExecutor knowledgeBootstrapExecutor;
    private final TaskExecutor knowledgeIngestionExecutor;
    private final KnowledgeProcessingNotifier knowledgeProcessingNotifier;
    private final KnowledgeFileStatusService knowledgeFileStatusService;
    private final KnowledgeChunkIndexService knowledgeChunkIndexService;
    private final KnowledgeMetadataService knowledgeMetadataService;
    private final KnowledgeIndexingService knowledgeIndexingService;
    private final KnowledgeChunkSemanticEnrichmentService knowledgeChunkSemanticEnrichmentService;
    private final KnowledgeAttachmentService knowledgeAttachmentService;
    private final KnowledgeParseService knowledgeParseService;
    private final DeptInfoService deptInfoService;
    private final ObjectMapper objectMapper;
    private final Set<String> ingestedHashes = ConcurrentHashMap.newKeySet();
    private final Map<String, KnowledgeDocumentRecord> knowledgeRecords = new ConcurrentHashMap<>();
    private final Object embeddingStoreMonitor = new Object();
    // 统一 embedding 模型后，知识恢复和上传共用同一台 Ollama；串行化可避免 CPU 模型被并发压垮。
    private final Object embeddingExecutionMonitor = new Object();
    private final int embeddingBatchSize;
    private final String embeddingModelName;
    private final boolean embeddingBuildEnabled;

    public KnowledgeBaseService(@Qualifier("knowledgeEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore,
                                @Qualifier("knowledgeEmbeddingModel") EmbeddingModel embeddingModel,
                                @Qualifier("knowledgeBootstrapExecutor") TaskExecutor knowledgeBootstrapExecutor,
                                @Qualifier("knowledgeIngestionExecutor") TaskExecutor knowledgeIngestionExecutor,
                                KnowledgeProcessingNotifier knowledgeProcessingNotifier,
                                KnowledgeFileStatusService knowledgeFileStatusService,
                                KnowledgeChunkIndexService knowledgeChunkIndexService,
                                KnowledgeMetadataService knowledgeMetadataService,
                                KnowledgeIndexingService knowledgeIndexingService,
                                KnowledgeChunkSemanticEnrichmentService knowledgeChunkSemanticEnrichmentService,
                                KnowledgeAttachmentService knowledgeAttachmentService,
                                KnowledgeParseService knowledgeParseService,
                                DeptInfoService deptInfoService,
                                ObjectMapper objectMapper,
                                @Value("${app.embedding.batch-size:24}") int embeddingBatchSize,
                                @Value("${app.embedding.model-name:bge-m3:latest}") String embeddingModelName,
                                @Value("${app.knowledge-base.embedding-build-enabled:true}") boolean embeddingBuildEnabled,
                                @Value("${app.knowledge-base.storage-dir:data/knowledge-base}") String storageDir) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.knowledgeBootstrapExecutor = knowledgeBootstrapExecutor;
        this.knowledgeIngestionExecutor = knowledgeIngestionExecutor;
        this.knowledgeProcessingNotifier = knowledgeProcessingNotifier;
        this.knowledgeFileStatusService = knowledgeFileStatusService;
        this.knowledgeChunkIndexService = knowledgeChunkIndexService;
        this.knowledgeMetadataService = knowledgeMetadataService;
        this.knowledgeIndexingService = knowledgeIndexingService;
        this.knowledgeChunkSemanticEnrichmentService = knowledgeChunkSemanticEnrichmentService;
        this.knowledgeAttachmentService = knowledgeAttachmentService;
        this.knowledgeParseService = knowledgeParseService;
        this.deptInfoService = deptInfoService;
        this.objectMapper = objectMapper;
        this.embeddingBatchSize = Math.max(1, embeddingBatchSize);
        this.embeddingModelName = embeddingModelName;
        this.embeddingBuildEnabled = embeddingBuildEnabled;
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

    /**
     * 应用启动后的知识恢复入口。
     *
     * 这里不是简单“扫目录重新导入”：
     * - 先做历史数据兼容迁移，避免旧版本状态机把新版本检索链卡住。
     * - 再优先从数据库恢复 chunk 和 embedding，减少不必要的重算。
     * - 最后才扫描内置资源和本地存储目录，补齐当前节点能看到的知识文件。
     *
     * 这样做的核心目的，是把“重启恢复”变成一次轻量恢复，而不是每次都全量重建向量索引。
     */
    public void reloadKnowledgeBase() throws IOException {
        ensureStorageDirectoryExists();
        migrateLegacyKnowledgeRecords();
        synchronized (this) {
            ingestedHashes.clear();
            knowledgeRecords.clear();
        }

        // 重启后优先从数据库恢复切片和向量；线上只读节点不会在这里补算 embedding。
        Set<String> discoveredHashes = ConcurrentHashMap.newKeySet();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/*");
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (Resource resource : resources) {
            if (!resource.exists() || resource.getFilename() == null) {
                continue;
            }
            tasks.add(CompletableFuture.runAsync(() -> {
                try {
                    String hash = hashForResource(resource);
                    discoveredHashes.add(hash);
                    ingestResource(resource, hash);
                } catch (BlankDocumentException e) {
                    log.warn("跳过空白知识资源: {}", resource.getFilename());
                } catch (Exception e) {
                    log.warn("跳过无法加载的内置知识资源: {}", resource.getFilename(), e);
                }
            }, knowledgeIngestionExecutor));
        }

        try (Stream<Path> paths = Files.list(storageDirectory)) {
            List<Path> storedFiles = paths.filter(Files::isRegularFile).sorted().toList();
            for (Path storedFile : storedFiles) {
                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        String hash = extractHashFromStoredFilename(storedFile.getFileName().toString());
                        if (StringUtils.hasText(hash)) {
                            discoveredHashes.add(hash);
                        }
                        ingestStoredFile(storedFile, hash);
                    } catch (Exception e) {
                        log.warn("跳过无法解析的本地知识文件: {}", storedFile.getFileName(), e);
                    }
                }, knowledgeIngestionExecutor));
            }
        }
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        pruneStaleKnowledge(discoveredHashes);
    }

    private void migrateLegacyKnowledgeRecords() {
        for (KnowledgeFileStatus status : knowledgeFileStatusService.listAll()) {
            if (status == null || !StringUtils.hasText(status.getHash())) {
                continue;
            }
            String normalizedDepartment = normalizeStoredKnowledgeDeptCode(status.getDepartment());
            boolean departmentMissing = !StringUtils.hasText(status.getDepartment())
                    || DeptInfoService.GENERAL_DEPT_NAME.equals(status.getDepartment())
                    || !normalizedDepartment.equals(status.getDepartment());
            boolean metadataMissing = !StringUtils.hasText(status.getDocType())
                    || !StringUtils.hasText(status.getAudience())
                    || !StringUtils.hasText(status.getTitle())
                    || departmentMissing;
            boolean legacyReady = STATUS_READY.equals(status.getProcessingStatus());
            if (!metadataMissing && !legacyReady) {
                continue;
            }
            KnowledgeFileMetadata mergedMetadata = mergeMetadata(
                    buildDefaultMetadata(status.getOriginalFilename(), status.getSource(), status.getExtension()),
                    new KnowledgeFileMetadata(
                            status.getDocType(),
                            normalizedDepartment,
                            status.getAudience(),
                            status.getVersion(),
                            status.getEffectiveAt(),
                            status.getTitle(),
                            status.getDoctorName(),
                            status.getSourcePriority(),
                            status.getKeywords()
                    )
            );
            String targetStatus = legacyReady ? STATUS_PUBLISHED : status.getProcessingStatus();
            status.setProcessingStatus(targetStatus);
            status.setStatusMessage(legacyReady
                    ? "兼容升级：历史知识文件已自动发布"
                    : status.getStatusMessage());
            status.setDocType(mergedMetadata.docType());
            status.setDepartment(mergedMetadata.department());
            status.setAudience(mergedMetadata.audience());
            status.setVersion(mergedMetadata.version());
            status.setEffectiveAt(mergedMetadata.effectiveAt());
            status.setTitle(mergedMetadata.title());
            status.setDoctorName(mergedMetadata.doctorName());
            status.setSourcePriority(mergedMetadata.sourcePriority());
            status.setKeywords(mergedMetadata.keywords());
            knowledgeFileStatusService.saveOrUpdate(status);
            knowledgeChunkIndexService.updateMetadataByHash(
                    status.getHash(),
                    targetStatus,
                    mergedMetadata.title(),
                    mergedMetadata.docType(),
                    mergedMetadata.department(),
                    mergedMetadata.audience(),
                    mergedMetadata.version(),
                    mergedMetadata.effectiveAt() == null ? null : java.sql.Timestamp.valueOf(mergedMetadata.effectiveAt()),
                    mergedMetadata.doctorName(),
                    mergedMetadata.sourcePriority(),
                    mergedMetadata.keywords()
            );
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

    public synchronized KnowledgeMetadataBackfillResponse backfillMetadata(List<String> hashes, boolean previewOnly) {
        List<KnowledgeFileStatus> targets = resolveBackfillTargets(hashes);
        List<KnowledgeMetadataBackfillItem> items = new ArrayList<>(targets.size());
        int matchedCount = 0;
        int updatedCount = 0;

        for (KnowledgeFileStatus status : targets) {
            KnowledgeMetadataService.MetadataBackfillPlan plan = knowledgeMetadataService.buildBackfillPlan(status);
            boolean hasChanges = !plan.changes().isEmpty();
            if (hasChanges) {
                matchedCount++;
            }
            boolean updated = false;
            if (hasChanges && !previewOnly) {
                applyMetadataPlan(status, plan.metadata());
                updated = true;
                updatedCount++;
            }
            items.add(new KnowledgeMetadataBackfillItem(
                    status.getHash(),
                    status.getOriginalFilename(),
                    status.getProcessingStatus(),
                    updated,
                    plan.changes(),
                    plan.skippedReasons()
            ));
        }

        return new KnowledgeMetadataBackfillResponse(
                previewOnly,
                targets.size(),
                matchedCount,
                updatedCount,
                Math.max(0, targets.size() - matchedCount),
                items
        );
    }

    /**
     * metadata 同步链统一覆盖两类入口：
     * 1. 管理员在知识运营页做批量回填。
     * 2. 管理员在知识详情页做 metadata-only 保存。
     *
     * 两条链最终都必须同时刷新文件状态表、chunk metadata 和内存镜像，避免“详情已更新但检索仍是旧 metadata”。
     */
    private List<KnowledgeFileStatus> resolveBackfillTargets(List<String> hashes) {
        List<KnowledgeFileStatus> allStatuses = knowledgeFileStatusService.listAll();
        if (hashes == null || hashes.isEmpty()) {
            return allStatuses;
        }
        Set<String> requestedHashes = new LinkedHashSet<>();
        hashes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(requestedHashes::add);
        return allStatuses.stream()
                .filter(status -> requestedHashes.contains(status.getHash()))
                .toList();
    }

    private void applyMetadataPlan(KnowledgeFileStatus status, KnowledgeFileMetadata metadata) {
        // metadata 回填和单文件 metadata 保存最终都走同一条落库路径，避免形成两套规则。
        applyKnowledgeMetadata(status, metadata);
    }

    private void applyKnowledgeMetadata(KnowledgeFileStatus status, KnowledgeFileMetadata metadata) {
        // metadata-only 保存不会重建正文和 embedding；这里只负责同步文件状态、chunk metadata 和内存中的记录镜像。
        applyMetadataToStatus(status, metadata);
        persistMetadataToChunks(status);
        refreshInMemoryMetadata(status);
        refreshSearchableEmbeddings(status);
    }

    private void applyMetadataToStatus(KnowledgeFileStatus status, KnowledgeFileMetadata metadata) {
        status.setDocType(metadata.docType());
        status.setTitle(metadata.title());
        status.setDepartment(metadata.department());
        status.setAudience(metadata.audience());
        status.setVersion(metadata.version());
        status.setEffectiveAt(metadata.effectiveAt());
        status.setDoctorName(metadata.doctorName());
        status.setSourcePriority(metadata.sourcePriority());
        status.setKeywords(metadata.keywords());
        knowledgeFileStatusService.saveOrUpdate(status);
    }

    private void persistMetadataToChunks(KnowledgeFileStatus status) {
        knowledgeChunkIndexService.updateMetadataByHash(
                status.getHash(),
                status.getProcessingStatus(),
                status.getTitle(),
                status.getDocType(),
                status.getDepartment(),
                status.getAudience(),
                status.getVersion(),
                status.getEffectiveAt() == null ? null : java.sql.Timestamp.valueOf(status.getEffectiveAt()),
                status.getDoctorName(),
                status.getSourcePriority(),
                status.getKeywords()
        );
    }

    private void refreshInMemoryMetadata(KnowledgeFileStatus status) {
        knowledgeRecords.computeIfPresent(status.getHash(), (ignored, existing) -> new KnowledgeDocumentRecord(
                existing.fileName(),
                existing.originalFilename(),
                existing.hash(),
                existing.source(),
                existing.parser(),
                existing.extension(),
                existing.size(),
                existing.editable(),
                existing.deletable(),
                existing.processingStatus(),
                existing.statusMessage(),
                existing.progressPercent(),
                existing.currentBatch(),
                existing.totalBatches(),
                status.getDocType(),
                status.getDepartment(),
                status.getAudience(),
                status.getVersion(),
                status.getEffectiveAt(),
                status.getTitle(),
                status.getDoctorName(),
                status.getSourcePriority(),
                status.getKeywords(),
                existing.segmentationMode(),
                existing.extractedText(),
                existing.chunks(),
                existing.storagePath()
        ));
    }

    private void refreshSearchableEmbeddings(KnowledgeFileStatus status) {
        if (!isSearchableStatus(status.getProcessingStatus())) {
            return;
        }

        List<KnowledgeChunkIndex> chunkIndexes = currentChunkIndexes(status);
        if (chunkIndexes.isEmpty()) {
            return;
        }
        synchronized (embeddingStoreMonitor) {
            removeEmbeddingsBySegmentIds(knowledgeChunkIndexService.listSegmentIdsByHash(status.getHash()));
            restoreEmbeddingsToStore(
                    chunkIndexes.stream().map(KnowledgeChunkIndex::getSegmentId).toList(),
                    toSegments(chunkIndexes),
                    chunkIndexes
            );
        }
    }

    public synchronized KnowledgeDocumentDetail publishKnowledge(String hash) throws IOException {
        KnowledgeDocumentRecord record = requireKnowledgeRecord(hash);
        if (!STATUS_READY.equals(record.processingStatus()) && !STATUS_ARCHIVED.equals(record.processingStatus())) {
            throw new IOException("只有待发布或已归档的知识文件才能上线");
        }
        List<KnowledgeChunkIndex> chunkIndexes = currentChunkIndexes(hash);
        if (chunkIndexes.isEmpty() || hasMissingEmbedding(chunkIndexes)) {
            throw new IOException("当前文件索引未构建完成，暂时不能发布");
        }
        knowledgeChunkIndexService.updateStatusByHash(hash, STATUS_PUBLISHED);
        synchronized (embeddingStoreMonitor) {
            restoreEmbeddingsToStore(
                    chunkIndexes.stream().map(KnowledgeChunkIndex::getSegmentId).toList(),
                    toSegments(chunkIndexes),
                    chunkIndexes
            );
        }
        String statusMessage = STATUS_ARCHIVED.equals(record.processingStatus())
                ? "知识文件已重新上线，可再次参与检索"
                : "知识文件已发布，可参与检索";
        updateKnowledgeStatus(hash, STATUS_PUBLISHED, statusMessage, 100, 0, record.totalBatches());
        return getKnowledgeDetail(hash);
    }

    public synchronized KnowledgeDocumentDetail archiveKnowledge(String hash) throws IOException {
        KnowledgeDocumentRecord record = requireKnowledgeRecord(hash);
        if (!STATUS_PUBLISHED.equals(record.processingStatus()) && !STATUS_READY.equals(record.processingStatus())) {
            throw new IOException("当前状态不支持归档");
        }
        removeEmbeddingsByHash(hash);
        knowledgeChunkIndexService.updateStatusByHash(hash, STATUS_ARCHIVED);
        updateKnowledgeStatus(hash, STATUS_ARCHIVED, "知识文件已归档，不再参与检索", record.progressPercent(), 0, record.totalBatches());
        return getKnowledgeDetail(hash);
    }

    /**
     * 重新处理会强制丢弃现有 chunk 和 embedding。
     *
     * 这个动作适合“正文改了 / metadata 改了 / 旧索引可疑”这类场景。
     * 和“重新上线”不同，它不是简单改状态，而是真的重新走一遍解析、切片、embedding。
     */
    public synchronized KnowledgeDocumentDetail reprocessKnowledge(String hash) throws IOException {
        KnowledgeDocumentRecord record = requireKnowledgeRecord(hash);
        if (record.storagePath() == null) {
            throw new IOException("内置知识文件暂不支持重新处理");
        }
        if (!embeddingBuildEnabled) {
            throw new IOException("当前节点为只读索引节点，请在本地构建环境执行重新处理");
        }
        removeEmbeddingsByHash(hash);
        knowledgeChunkIndexService.deleteByHash(hash);
        updateKnowledgeStatus(hash, STATUS_DRAFT, "已加入重处理队列", 0, 0, 0);
        scheduleKnowledgeProcessing(hash, record.fileName(), record.originalFilename(), record.extension(), record.size(), record.storagePath());
        return getKnowledgeDetail(hash);
    }

    public synchronized KnowledgeDocumentDetail updateKnowledgeContent(String hash, String content, KnowledgeUpdateRequest request) throws IOException {
        KnowledgeDocumentRecord record = knowledgeRecords.get(hash);
        if (record == null) {
            throw new IOException("未找到对应的知识文件");
        }
        if (!isEditableStatus(record.processingStatus())) {
            throw new IOException("当前知识文件仍在处理中，暂不支持编辑");
        }
        if (request != null && request.getContent() == null) {
            return updateKnowledgeMetadataOnly(hash, record, request);
        }
        if (!record.editable()) {
            throw new IOException("当前文件格式不支持在线编辑，请删除后重新上传");
        }
        if (record.storagePath() == null) {
            throw new IOException("内置知识文件不支持在线编辑");
        }

        String normalizedContent = normalizeText(content);
        if (!StringUtils.hasText(normalizedContent)) {
            normalizedContent = normalizeText(record.extractedText());
        }
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
            removeEmbeddingsByHash(hash);
            knowledgeChunkIndexService.deleteByHash(hash);
            knowledgeFileStatusService.deleteByHash(hash);
            ingestedHashes.remove(hash);
            knowledgeRecords.remove(hash);
            ParsedKnowledge parsedKnowledge = parseFile(storedFile, record.originalFilename(), newHash);
            String department = request == null || request.getDepartment() == null
                    ? normalizeStoredKnowledgeDeptCode(record.department())
                    : deptInfoService.normalizeKnowledgeDeptCode(request.getDepartment());
            KnowledgeFileMetadata metadata = mergeMetadata(buildDefaultMetadata(record.originalFilename(), record.source(), record.extension()),
                    new KnowledgeFileMetadata(
                            request == null ? record.docType() : request.getDocType(),
                            department,
                            request == null ? record.audience() : request.getAudience(),
                            request == null ? record.version() : request.getVersion(),
                            request == null ? record.effectiveAt() : parseDateTime(request.getEffectiveAt()),
                            request == null ? record.title() : request.getTitle(),
                            request == null ? record.doctorName() : request.getDoctorName(),
                            request == null ? record.sourcePriority() : request.getSourcePriority(),
                            request == null ? record.keywords() : request.getKeywords()
                    ));
            registerKnowledgeRecord(
                    newHash,
                    storedFilename,
                    record.originalFilename(),
                    record.source(),
                    record.extension(),
                    parsedKnowledge.parserName(),
                    Files.size(storedFile),
                    record.editable(),
                    record.deletable(),
                    storedFile,
                    parsedKnowledge.document(),
                    STATUS_READY,
                    buildQualityMessage(parsedKnowledge.document().text().length()),
                    100,
                    0,
                    0,
                    metadata
            );
            return getKnowledgeDetail(newHash);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private KnowledgeDocumentDetail updateKnowledgeMetadataOnly(String hash,
                                                               KnowledgeDocumentRecord record,
                                                               KnowledgeUpdateRequest request) throws IOException {
        KnowledgeFileStatus status = knowledgeFileStatusService.findByHash(hash);
        if (status == null) {
            throw new IOException("未找到对应的知识文件");
        }
        KnowledgeFileMetadata metadata = buildRequestedMetadata(record, request);
        applyKnowledgeMetadata(status, metadata);
        return getKnowledgeDetail(hash);
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
        removeEmbeddingsByHash(hash);
        removeKnowledgeRecord(hash);
    }

    /**
     * 聊天附件增强只对当前轮对话生效，不写入长期知识库。
     * 这里把附件解析成临时上下文，再统一挂到 `[用户问题]` 前面，方便 prompt 侧识别“材料”和“真实问题”的边界。
     */
    public String buildChatMessageWithAttachments(String userMessage, MultipartFile[] files) throws IOException {
        return knowledgeAttachmentService.buildChatMessageWithAttachments(userMessage, files);
    }

    public String buildChatAttachmentTextContext(MultipartFile[] files) throws IOException {
        return knowledgeAttachmentService.buildChatAttachmentTextContext(files);
    }

    public boolean hasImageAttachments(MultipartFile[] files) throws IOException {
        return knowledgeAttachmentService.hasImageAttachments(files);
    }

    public boolean isImageAttachment(MultipartFile file) {
        return knowledgeAttachmentService.isImageAttachment(file);
    }

    public String resolveMimeType(MultipartFile file) {
        return knowledgeAttachmentService.resolveMimeType(file);
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
            if (embeddingBuildEnabled) {
                item.setStatus("QUEUED");
                item.setMessage("文件已上传，进入草稿区并开始构建 RAG 索引");
            } else {
                item.setStatus("SYNC_REQUIRED");
                item.setMessage("文件已上传，但当前节点不构建 embedding，请在本地完成知识构建后重新部署同步");
            }
            response.setAccepted(response.getAccepted() + 1);
            log.info("knowledge.upload.queued hash={} file={} size={} extension={} embeddingBuildEnabled={}",
                    hash, originalFilename, size, extension, embeddingBuildEnabled);
            if (embeddingBuildEnabled) {
                // 上传接口只负责落盘和入队，解析、切分、embedding 全部在后台线程池完成。
                scheduleKnowledgeProcessing(hash, storedFilename, originalFilename, extension, size, storedFile);
            } else {
                updateKnowledgeStatus(hash, STATUS_PENDING_SYNC, "文件已上传，等待本地构建 embedding 后同步", 0, 0, 0);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void ingestResource(Resource resource, String hash) throws IOException {
        String filename = resource.getFilename();
        String extension = getExtension(filename);
        KnowledgeFileStatus persistedStatus = knowledgeFileStatusService.findByHash(hash);
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

        if (ingestedHashes.contains(hash)) {
            return;
        }

        if (restorePersistedKnowledge(hash, null)) {
            return;
        }
        if (!embeddingBuildEnabled) {
            registerPendingSyncKnowledgeRecord(
                    hash,
                    filename,
                    filename,
                    "bundled",
                    extension,
                    safeContentLength(resource),
                    persistedStatus != null && Boolean.TRUE.equals(persistedStatus.getEditable()),
                    persistedStatus != null && Boolean.TRUE.equals(persistedStatus.getDeletable()),
                    null,
                    "当前节点仅读知识索引，等待本地构建 embedding 后同步"
            );
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
                    persistedStatus != null ? Boolean.TRUE.equals(persistedStatus.getEditable()) : false,
                    persistedStatus != null ? Boolean.TRUE.equals(persistedStatus.getDeletable()) : false,
                    null,
                    parsedKnowledge.document(),
                    resolveRebuildProcessingStatus(persistedStatus, STATUS_PUBLISHED),
                    buildQualityMessage(parsedKnowledge.document().text().length()),
                    100,
                    0,
                    0,
                    resolvePersistedMetadataForRebuild(persistedStatus, filename, "bundled", extension)
            );
        }
    }

    private void ingestStoredFile(Path storedFile, String hash) throws IOException {
        if (!StringUtils.hasText(hash) || ingestedHashes.contains(hash)) {
            return;
        }
        String originalFilename = stripHashPrefix(storedFile.getFileName().toString());
        KnowledgeFileStatus persistedStatus = knowledgeFileStatusService.findByHash(hash);
        if (restorePersistedKnowledge(hash, storedFile)) {
            return;
        }
        if (!embeddingBuildEnabled) {
            registerPendingSyncKnowledgeRecord(
                    hash,
                    storedFile.getFileName().toString(),
                    originalFilename,
                    "uploaded",
                    getExtension(originalFilename),
                    Files.size(storedFile),
                    persistedStatus != null ? Boolean.TRUE.equals(persistedStatus.getEditable()) : isEditableExtension(getExtension(originalFilename)),
                    persistedStatus != null ? Boolean.TRUE.equals(persistedStatus.getDeletable()) : true,
                    storedFile,
                    "当前节点仅读知识索引，等待本地构建 embedding 后同步"
            );
            return;
        }
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
            persistedStatus != null ? Boolean.TRUE.equals(persistedStatus.getEditable()) : isEditableExtension(getExtension(originalFilename)),
            persistedStatus != null ? Boolean.TRUE.equals(persistedStatus.getDeletable()) : true,
            storedFile,
            parsedKnowledge.document(),
            resolveRebuildProcessingStatus(persistedStatus, STATUS_READY),
            buildQualityMessage(parsedKnowledge.document().text().length()),
            100,
            0,
            0,
            resolvePersistedMetadataForRebuild(persistedStatus, originalFilename, "uploaded", getExtension(originalFilename))
        );
    }

    private ParsedKnowledge parseFile(Path file, String originalFilename, String hash) throws IOException {
        KnowledgeParsedDocument parsedDocument = knowledgeParseService.parseFile(
                file,
                originalFilename,
                hash,
                knowledgeParseService.getExtension(originalFilename),
                "uploaded"
        );
        return new ParsedKnowledge(parsedDocument.document(), parsedDocument.parserName());
    }

    private ParsedKnowledge parseInputStream(InputStream inputStream,
                                             String originalFilename,
                                             String hash,
                                             String extension,
                                             String source) throws IOException {
        KnowledgeParsedDocument parsedDocument = knowledgeParseService.parse(inputStream, originalFilename, hash, extension, source);
        return new ParsedKnowledge(parsedDocument.document(), parsedDocument.parserName());
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
                                         int totalBatches,
                                         KnowledgeFileMetadata fileMetadata) {
        KnowledgeFileStatus existingStatus = knowledgeFileStatusService.findByHash(hash);
        List<KnowledgeChunkIndex> existingCurrentChunks = currentChunkIndexes(existingStatus);
        boolean upgradingExisting = existingStatus != null && !existingCurrentChunks.isEmpty();
        int currentGeneration = resolveCurrentGeneration(existingStatus);
        int targetGeneration = upgradingExisting ? resolveNextGeneration(existingStatus) : Math.max(1, currentGeneration);
        List<String> oldSegmentIds = upgradingExisting
                ? knowledgeChunkIndexService.listSegmentIdsByHashAndGeneration(hash, currentGeneration)
                : List.of();

        try {
            KnowledgeFileMetadata effectiveMetadata = mergeMetadata(buildDefaultMetadata(originalFilename, source, extension), fileMetadata);
            List<TextSegment> segmented = splitDocument(document, effectiveMetadata.docType());
            List<TextSegment> semanticSegments = enrichChunkSemantics(segmented, effectiveMetadata.title(), effectiveMetadata.docType());
            if (semanticSegments.isEmpty()) {
                throw new IllegalStateException("知识文件切分结果为空");
            }
            List<TextSegment> searchableSegments = withGenerationMetadata(
                    enrichSegments(semanticSegments, hash, source, extension, processingStatus, effectiveMetadata),
                    targetGeneration
            );
            log.info("knowledge.segmented hash={} file={} generation={} segments={} parser={} status={}",
                    hash, originalFilename, targetGeneration, searchableSegments.size(), parserName, processingStatus);

            int stageBatchCount = calculateBatchCount(searchableSegments.size(), embeddingBatchSize);
            int effectiveTotalBatches = Math.max(totalBatches, stageBatchCount);
            String finalProcessingStatus = embeddingBuildEnabled ? processingStatus : STATUS_PENDING_SYNC;
            String finalStatusMessage = embeddingBuildEnabled ? statusMessage : "切分已完成，等待本地构建 embedding 后同步";

            if (upgradingExisting) {
                markIndexUpgrade(existingStatus, "正在按新规则升级知识索引");
            } else {
                KnowledgeDocumentRecord initialRecord = new KnowledgeDocumentRecord(
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
                        effectiveMetadata.docType(),
                        effectiveMetadata.department(),
                        effectiveMetadata.audience(),
                        effectiveMetadata.version(),
                        effectiveMetadata.effectiveAt(),
                        effectiveMetadata.title(),
                        effectiveMetadata.doctorName(),
                        effectiveMetadata.sourcePriority(),
                        effectiveMetadata.keywords(),
                        KnowledgeIndexingService.SEGMENTATION_MODE_STRUCTURED,
                        document.text(),
                        List.of(),
                        storagePath
                );
                ingestedHashes.add(hash);
                knowledgeRecords.put(hash, initialRecord);
                saveKnowledgeStatus(initialRecord);
            }

            List<Embedding> embeddings = List.of();
            if (embeddingBuildEnabled) {
                synchronized (embeddingExecutionMonitor) {
                    embeddings = embedSegmentsInBatches(hash, searchableSegments, 0, effectiveTotalBatches, "知识向量索引", embeddingModel);
                }
            }

            List<KnowledgeChunkIndex> chunkIndexes = buildChunkIndexes(
                    hash,
                    targetGeneration,
                    searchableSegments,
                    embeddings,
                    finalProcessingStatus,
                    effectiveMetadata
            );
            knowledgeChunkIndexService.insertAll(chunkIndexes);

            List<String> newSegmentIds = chunkIndexes.stream()
                    .map(KnowledgeChunkIndex::getSegmentId)
                    .filter(StringUtils::hasText)
                    .toList();
            if (embeddingBuildEnabled && isSearchableStatus(finalProcessingStatus)) {
                synchronized (embeddingStoreMonitor) {
                    restoreEmbeddingsToStore(newSegmentIds, searchableSegments, chunkIndexes);
                }
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
                    finalProcessingStatus,
                    finalStatusMessage,
                    100,
                    0,
                    effectiveTotalBatches,
                    effectiveMetadata.docType(),
                    effectiveMetadata.department(),
                    effectiveMetadata.audience(),
                    effectiveMetadata.version(),
                    effectiveMetadata.effectiveAt(),
                    effectiveMetadata.title(),
                    effectiveMetadata.doctorName(),
                    effectiveMetadata.sourcePriority(),
                    effectiveMetadata.keywords(),
                    resolveSegmentationMode(chunkIndexes),
                    document.text(),
                    toChunkInfos(chunkIndexes),
                    storagePath
            );
            knowledgeRecords.put(hash, record);
            ingestedHashes.add(hash);
            saveKnowledgeStatus(record);

            KnowledgeFileStatus latestStatus = knowledgeFileStatusService.findByHash(hash);
            finishIndexUpgrade(latestStatus, targetGeneration, chunkIndexes.size(), finalProcessingStatus, finalStatusMessage, effectiveTotalBatches);
            if (upgradingExisting) {
                removeEmbeddingsBySegmentIds(oldSegmentIds);
                knowledgeChunkIndexService.deleteByHashAndGeneration(hash, currentGeneration);
            }
        } catch (Exception exception) {
            removeEmbeddingsBySegmentIds(knowledgeChunkIndexService.listSegmentIdsByHashAndGeneration(hash, targetGeneration));
            knowledgeChunkIndexService.deleteByHashAndGeneration(hash, targetGeneration);
            failIndexUpgrade(existingStatus, "索引升级失败：" + exception.getMessage());
            throw exception;
        }
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
        KnowledgeFileMetadata metadata = buildDefaultMetadata(originalFilename, source, extension);
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
                STATUS_DRAFT,
                "文件已上传，等待开始解析",
                0,
                0,
                0,
                metadata.docType(),
                metadata.department(),
                metadata.audience(),
                metadata.version(),
                metadata.effectiveAt(),
                metadata.title(),
                metadata.doctorName(),
                metadata.sourcePriority(),
                metadata.keywords(),
                KnowledgeIndexingService.SEGMENTATION_MODE_RULE_RECURSIVE,
                "",
                List.of(),
                storagePath
        );
        knowledgeRecords.put(hash, record);
        saveKnowledgeStatus(record);
    }

    private void registerPendingSyncKnowledgeRecord(String hash,
                                                    String fileName,
                                                    String originalFilename,
                                                    String source,
                                                    String extension,
                                                    long size,
                                                    boolean editable,
                                                    boolean deletable,
                                                    Path storagePath,
                                                    String statusMessage) {
        KnowledgeFileMetadata metadata = buildDefaultMetadata(originalFilename, source, extension);
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
                STATUS_PENDING_SYNC,
                statusMessage,
                0,
                0,
                0,
                metadata.docType(),
                metadata.department(),
                metadata.audience(),
                metadata.version(),
                metadata.effectiveAt(),
                metadata.title(),
                metadata.doctorName(),
                metadata.sourcePriority(),
                metadata.keywords(),
                KnowledgeIndexingService.SEGMENTATION_MODE_RULE_RECURSIVE,
                "",
                List.of(),
                storagePath
        );
        ingestedHashes.add(hash);
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
                    0,
                    buildDefaultMetadata(originalFilename, "uploaded", extension)
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
                existing.docType(),
                existing.department(),
                existing.audience(),
                existing.version(),
                existing.effectiveAt(),
                existing.title(),
                existing.doctorName(),
                existing.sourcePriority(),
                existing.keywords(),
                existing.segmentationMode(),
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
                existing.docType(),
                existing.department(),
                existing.audience(),
                existing.version(),
                existing.effectiveAt(),
                existing.title(),
                existing.doctorName(),
                existing.sourcePriority(),
                existing.keywords(),
                existing.segmentationMode(),
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
        knowledgeChunkIndexService.deleteByHash(hash);
        knowledgeFileStatusService.deleteByHash(hash);
    }

    private void ensureStorageDirectoryExists() throws IOException {
        Files.createDirectories(storageDirectory);
    }

    private String normalizeText(String text) {
        return knowledgeParseService.normalizeText(text);
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

    private void pruneStaleKnowledge(Set<String> discoveredHashes) {
        for (KnowledgeFileStatus status : knowledgeFileStatusService.listAll()) {
            if (status == null || !StringUtils.hasText(status.getHash())) {
                continue;
            }
            if (discoveredHashes.contains(status.getHash())) {
                continue;
            }
            removeEmbeddingsByHash(status.getHash());
            knowledgeChunkIndexService.deleteByHash(status.getHash());
            knowledgeFileStatusService.deleteByHash(status.getHash());
        }
    }
    /**
     * 恢复数据库里已经存在的知识索引。
     *
     * 这一步的判断标准很严格：
     * - 文件 hash 没变
     * - embedding 列完整
     * - 当前 embedding 模型名和历史模型名一致
     *
     * 只有这三项都成立，才会直接复用旧向量。否则就算文件还在，也宁可重建，避免把不同模型的向量混在一起用。
     * 对线上只读节点来说，如果索引不完整，这里不会偷偷补算，而是明确标成 PENDING_SYNC，等本地构建环境同步。
     */
    private boolean restorePersistedKnowledge(String hash, Path storagePath) {
        KnowledgeFileStatus status = knowledgeFileStatusService.findByHash(hash);
        if (status == null) {
            return false;
        }
        List<KnowledgeChunkIndex> chunkIndexes = currentChunkIndexes(status);
        if (chunkIndexes.isEmpty()) {
            return false;
        }
        boolean embeddingCompatible = isEmbeddingModelCompatible(status);
        boolean embeddingMissing = hasMissingEmbedding(chunkIndexes);
        boolean requireRebuild = requiresIndexUpgrade(status, chunkIndexes) || !embeddingCompatible || embeddingMissing;
        if (requireRebuild) {
            if (!embeddingBuildEnabled) {
                status.setIndexUpgradeState(INDEX_UPGRADE_FAILED);
                status.setIndexUpgradeMessage("当前节点仅读知识索引，等待本地构建环境升级索引");
                knowledgeFileStatusService.saveOrUpdate(status);
                KnowledgeDocumentRecord record = new KnowledgeDocumentRecord(
                        status.getFileName(),
                        status.getOriginalFilename(),
                        status.getHash(),
                        status.getSource(),
                        status.getParser(),
                        status.getExtension(),
                        status.getSize() == null ? 0L : status.getSize(),
                        Boolean.TRUE.equals(status.getEditable()),
                        Boolean.TRUE.equals(status.getDeletable()),
                        status.getProcessingStatus(),
                        status.getStatusMessage(),
                        status.getProgressPercent() == null ? 0 : status.getProgressPercent(),
                        status.getCurrentBatch() == null ? 0 : status.getCurrentBatch(),
                        status.getTotalBatches() == null ? 0 : status.getTotalBatches(),
                        status.getDocType(),
                        status.getDepartment(),
                        status.getAudience(),
                        status.getVersion(),
                        status.getEffectiveAt(),
                        status.getTitle(),
                        status.getDoctorName(),
                        status.getSourcePriority(),
                        status.getKeywords(),
                        resolveSegmentationMode(chunkIndexes),
                        status.getExtractedText() == null ? "" : status.getExtractedText(),
                        toChunkInfos(chunkIndexes),
                        storagePath
                );
                knowledgeRecords.put(hash, record);
                ingestedHashes.add(hash);
                return true;
            }
            return false;
        }

        List<TextSegment> segments = toSegments(chunkIndexes);
        List<String> segmentIds = chunkIndexes.stream()
                .map(KnowledgeChunkIndex::getSegmentId)
                .toList();

        if (isSearchableStatus(status.getProcessingStatus())) {
            synchronized (embeddingStoreMonitor) {
                restoreEmbeddingsToStore(segmentIds, segments, chunkIndexes);
            }
        }

        KnowledgeDocumentRecord record = new KnowledgeDocumentRecord(
                status.getFileName(),
                status.getOriginalFilename(),
                status.getHash(),
                status.getSource(),
                status.getParser(),
                status.getExtension(),
                status.getSize() == null ? 0L : status.getSize(),
                Boolean.TRUE.equals(status.getEditable()),
                Boolean.TRUE.equals(status.getDeletable()),
                status.getProcessingStatus(),
                status.getStatusMessage(),
                status.getProgressPercent() == null ? 0 : status.getProgressPercent(),
                status.getCurrentBatch() == null ? 0 : status.getCurrentBatch(),
                status.getTotalBatches() == null ? 0 : status.getTotalBatches(),
                status.getDocType(),
                status.getDepartment(),
                status.getAudience(),
                status.getVersion(),
                status.getEffectiveAt(),
                status.getTitle(),
                status.getDoctorName(),
                status.getSourcePriority(),
                status.getKeywords(),
                resolveSegmentationMode(chunkIndexes),
                status.getExtractedText() == null ? "" : status.getExtractedText(),
                toChunkInfos(chunkIndexes),
                storagePath
        );
        ingestedHashes.add(hash);
        knowledgeRecords.put(hash, record);
        return true;
    }

    private boolean requiresIndexUpgrade(KnowledgeFileStatus status, List<KnowledgeChunkIndex> chunkIndexes) {
        if (resolveIndexBuildVersion(status) < CURRENT_INDEX_BUILD_VERSION) {
            return true;
        }
        if (chunkIndexes == null || chunkIndexes.isEmpty()) {
            return true;
        }
        return false;
    }

    private KnowledgeFileMetadata resolvePersistedMetadataForRebuild(KnowledgeFileStatus persistedStatus,
                                                                     String originalFilename,
                                                                     String source,
                                                                     String extension) {
        KnowledgeFileMetadata defaults = buildDefaultMetadata(originalFilename, source, extension);
        if (persistedStatus == null) {
            return defaults;
        }
        return mergeMetadata(defaults, toMetadata(persistedStatus));
    }

    private String resolveRebuildProcessingStatus(KnowledgeFileStatus persistedStatus, String fallbackStatus) {
        if (persistedStatus == null || !StringUtils.hasText(persistedStatus.getProcessingStatus())) {
            return fallbackStatus;
        }
        return switch (persistedStatus.getProcessingStatus()) {
            case STATUS_PUBLISHED -> STATUS_PUBLISHED;
            case STATUS_ARCHIVED -> STATUS_ARCHIVED;
            case STATUS_READY, STATUS_FAILED, STATUS_PENDING_SYNC, STATUS_PROCESSING -> STATUS_READY;
            default -> fallbackStatus;
        };
    }

    private int resolveIndexBuildVersion(KnowledgeFileStatus status) {
        return status == null || status.getIndexBuildVersion() == null ? 0 : Math.max(0, status.getIndexBuildVersion());
    }

    private int resolveCurrentGeneration(KnowledgeFileStatus status) {
        return status == null || status.getCurrentGeneration() == null || status.getCurrentGeneration() <= 0
                ? 1
                : status.getCurrentGeneration();
    }

    private int resolveNextGeneration(KnowledgeFileStatus status) {
        return Math.max(1, resolveCurrentGeneration(status) + 1);
    }

    private List<KnowledgeChunkIndex> currentChunkIndexes(KnowledgeFileStatus status) {
        if (status == null || !StringUtils.hasText(status.getHash())) {
            return List.of();
        }
        return knowledgeChunkIndexService.listByHashAndGeneration(status.getHash(), resolveCurrentGeneration(status));
    }

    private List<KnowledgeChunkIndex> currentChunkIndexes(String hash) {
        return currentChunkIndexes(knowledgeFileStatusService.findByHash(hash));
    }

    private void markIndexUpgrade(KnowledgeFileStatus status, String message) {
        if (status == null) {
            return;
        }
        status.setIndexUpgradeState(INDEX_UPGRADE_UPGRADING);
        status.setIndexUpgradeMessage(message);
        knowledgeFileStatusService.saveOrUpdate(status);
    }

    private void finishIndexUpgrade(KnowledgeFileStatus status,
                                    int generation,
                                    int chunkCount,
                                    String processingStatus,
                                    String statusMessage,
                                    int totalBatches) {
        if (status == null) {
            return;
        }
        status.setCurrentGeneration(generation);
        status.setIndexBuildVersion(CURRENT_INDEX_BUILD_VERSION);
        status.setIndexUpgradeState(INDEX_UPGRADE_IDLE);
        status.setIndexUpgradeMessage(null);
        status.setChunkCount(chunkCount);
        status.setProcessingStatus(processingStatus);
        status.setStatusMessage(statusMessage);
        status.setProgressPercent(100);
        status.setCurrentBatch(0);
        status.setTotalBatches(totalBatches);
        status.setEmbeddingModelName(isEmbeddingsReadyStatus(processingStatus) ? embeddingModelName : null);
        knowledgeFileStatusService.saveOrUpdate(status);
    }

    private void failIndexUpgrade(KnowledgeFileStatus status, String message) {
        if (status == null) {
            return;
        }
        status.setIndexUpgradeState(INDEX_UPGRADE_FAILED);
        status.setIndexUpgradeMessage(message);
        knowledgeFileStatusService.saveOrUpdate(status);
    }

    private boolean hasMissingEmbedding(List<KnowledgeChunkIndex> chunkIndexes) {
        return knowledgeIndexingService.hasMissingEmbedding(chunkIndexes);
    }

    private void applyEmbeddings(List<KnowledgeChunkIndex> chunkIndexes, List<Embedding> embeddings) {
        knowledgeIndexingService.applyEmbeddings(chunkIndexes, embeddings);
    }

    private List<KnowledgeChunkInfo> toChunkInfos(List<KnowledgeChunkIndex> indexes) {
        return knowledgeIndexingService.toChunkInfos(indexes);
    }

    private List<KnowledgeChunkIndex> buildChunkIndexes(String hash,
                                                        int generation,
                                                        List<TextSegment> segments,
                                                        List<Embedding> embeddings,
                                                        String status,
                                                        KnowledgeFileMetadata fileMetadata) {
        return knowledgeIndexingService.buildChunkIndexes(hash, generation, segments, embeddings, status, fileMetadata);
    }

    /**
     * 把数据库里的 chunk 重新组装回 LangChain4j 的 TextSegment。
     *
     * 检索侧最终吃的是 TextSegment + Metadata，所以这里相当于把“数据库里的行”还原成“RAG 运行时对象”。
     * 之所以不直接在数据库里查询原始字符串去拼，是为了保证在线检索和启动恢复共用同一套 metadata 口径。
     */
    private List<TextSegment> toSegments(List<KnowledgeChunkIndex> chunkIndexes) {
        return knowledgeIndexingService.toSegments(chunkIndexes);
    }

    private void restoreEmbeddingsToStore(List<String> segmentIds,
                                          List<TextSegment> segments,
                                          List<KnowledgeChunkIndex> chunkIndexes) {
        knowledgeIndexingService.restoreEmbeddingsToStore(embeddingStore, segmentIds, segments, chunkIndexes);
    }

    private String getExtension(String filename) {
        return knowledgeParseService.getExtension(filename);
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

    private List<TextSegment> splitDocument(Document document, String docType) {
        return knowledgeIndexingService.splitDocument(document, docType);
    }
        //
    private List<dev.langchain4j.data.embedding.Embedding> embedSegmentsInBatches(String hash,
                                                                                  List<TextSegment> segments,
                                                                                  int batchOffset,
                                                                                  int totalBatches,
                                                                                  String stageLabel,
                                                                                  EmbeddingModel embeddingModel) {
        List<dev.langchain4j.data.embedding.Embedding> embeddings = new ArrayList<>(segments.size());
        int batchSize = resolveBatchSize(embeddingModel);
        int stageBatchCount = calculateBatchCount(segments.size(), batchSize);
        // 分批 embedding 是为了限制单次请求体积，并让前端轮询时能看到真实批次进度
        for (int batchIndex = 0; batchIndex < stageBatchCount; batchIndex++) {
            int start = batchIndex * batchSize;
            int end = Math.min(start + batchSize, segments.size());
            List<TextSegment> batch = List.copyOf(segments.subList(start, end));
            int visibleBatchIndex = batchOffset + batchIndex + 1;
            int progressPercent = buildProgressPercent(visibleBatchIndex, totalBatches);
            if (shouldLogBatchProgress(batchIndex + 1, stageBatchCount)) {
                log.info("knowledge.embedding.batch hash={} stage={} batch={}/{} totalProgress={}/{} batchSize={} progress={}%",
                        hash, stageLabel, batchIndex + 1, stageBatchCount, visibleBatchIndex, totalBatches, batch.size(), progressPercent);
            }
            updateKnowledgeStatus(hash, STATUS_PROCESSING,
                    buildEmbeddingProgressMessage(segments.size(), visibleBatchIndex, totalBatches, stageLabel),
                    progressPercent,
                    visibleBatchIndex,
                    totalBatches);
            List<Embedding> batchEmbeddings = embeddingModel.embedAll(batch).content();
            if (batchEmbeddings.size() != batch.size()) {
                throw new IllegalStateException("Embedding 返回数量异常，期望 " + batch.size() + " 实际 " + batchEmbeddings.size());
            }
            embeddings.addAll(batchEmbeddings);
        }
        return embeddings;
    }

    private int resolveBatchSize(EmbeddingModel embeddingModel) {
        return embeddingBatchSize;
    }

    private int calculateBatchCount(int segmentCount) {
        return calculateBatchCount(segmentCount, embeddingBatchSize);
    }

    private int calculateBatchCount(int segmentCount, int batchSize) {
        int effectiveBatchSize = Math.max(1, batchSize);
        return Math.max(1, (segmentCount + effectiveBatchSize - 1) / effectiveBatchSize);
    }

    private String buildEmbeddingProgressMessage(int segmentCount, int currentBatch, int totalBatches, String stageLabel) {
        if (totalBatches <= 1 || currentBatch <= 0) {
            return "文档切分完成，共 " + segmentCount + " 段，正在构建" + stageLabel;
        }
        return "文档切分完成，共 " + segmentCount + " 段，正在构建" + stageLabel + "（" + currentBatch + "/" + totalBatches + "）";
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

    private KnowledgeDocumentRecord requireKnowledgeRecord(String hash) throws IOException {
        KnowledgeDocumentRecord record = knowledgeRecords.get(hash);
        if (record != null) {
            return record;
        }
        KnowledgeFileStatus status = knowledgeFileStatusService.findByHash(hash);
        if (status == null) {
            throw new IOException("未找到对应的知识文件");
        }
        return new KnowledgeDocumentRecord(
                status.getFileName(),
                status.getOriginalFilename(),
                status.getHash(),
                status.getSource(),
                status.getParser(),
                status.getExtension(),
                status.getSize() == null ? 0L : status.getSize(),
                Boolean.TRUE.equals(status.getEditable()),
                Boolean.TRUE.equals(status.getDeletable()),
                status.getProcessingStatus(),
                status.getStatusMessage(),
                status.getProgressPercent() == null ? 0 : status.getProgressPercent(),
                status.getCurrentBatch() == null ? 0 : status.getCurrentBatch(),
                status.getTotalBatches() == null ? 0 : status.getTotalBatches(),
                status.getDocType(),
                status.getDepartment(),
                status.getAudience(),
                status.getVersion(),
                status.getEffectiveAt(),
                status.getTitle(),
                status.getDoctorName(),
                status.getSourcePriority(),
                status.getKeywords(),
                resolveSegmentationMode(currentChunkIndexes(hash)),
                status.getExtractedText() == null ? "" : status.getExtractedText(),
                toChunkInfos(currentChunkIndexes(hash)),
                resolveStoragePath(status)
        );
    }

    private Path resolveStoragePath(KnowledgeFileStatus status) {
        if (status == null || !StringUtils.hasText(status.getFileName()) || !"uploaded".equals(status.getSource())) {
            return null;
        }
        return storageDirectory.resolve(status.getFileName());
    }

    private boolean isSearchableStatus(String status) {
        return STATUS_PUBLISHED.equals(status);
    }

    private boolean isEmbeddingsReadyStatus(String status) {
        return STATUS_READY.equals(status) || STATUS_PUBLISHED.equals(status) || STATUS_ARCHIVED.equals(status);
    }

    private boolean isEditableStatus(String status) {
        return STATUS_READY.equals(status) || STATUS_PUBLISHED.equals(status);
    }

    /**
     * 根据文件名推断一份“够用但保守”的默认 metadata。
     *
     * 这里的目标不是 100% 准确，而是让新上传文件在没人手工补 metadata 时，也至少能带着基础分类进入检索链。
     * 后续管理员再通过知识库页补齐更精细的科室、医生、版本、关键词等信息。
     */
    private KnowledgeFileMetadata buildDefaultMetadata(String originalFilename, String source, String extension) {
        return knowledgeMetadataService.buildDefaultMetadata(originalFilename, source, extension);
    }

    /**
     * 用“显式编辑值”覆盖“默认推断值”。
     *
     * 这个方法的原则很简单：
     * - 用户或后台明确给出的值优先
     * - 没给的字段保留默认推断结果
     *
     * 这样既能保留系统自动推断的便利，又不会把管理员已经修正过的 metadata 覆盖掉。
     */
    private KnowledgeFileMetadata mergeMetadata(KnowledgeFileMetadata base, KnowledgeFileMetadata override) {
        return knowledgeMetadataService.mergeMetadata(base, override);
    }

    private KnowledgeFileMetadata toMetadata(KnowledgeFileStatus status) {
        return knowledgeMetadataService.toMetadata(status);
    }

    private String normalizeStoredKnowledgeDeptCode(String department) {
        try {
            return deptInfoService.normalizeKnowledgeDeptCode(department);
        } catch (IllegalArgumentException exception) {
            return DeptInfoService.GENERAL_DEPT_CODE;
        }
    }

    private KnowledgeFileMetadata buildRequestedMetadata(KnowledgeDocumentRecord record, KnowledgeUpdateRequest request) {
        String department = request == null || request.getDepartment() == null
                ? normalizeStoredKnowledgeDeptCode(record.department())
                : deptInfoService.normalizeKnowledgeDeptCode(request.getDepartment());
        return mergeMetadata(buildDefaultMetadata(record.originalFilename(), record.source(), record.extension()),
                new KnowledgeFileMetadata(
                        chooseRequestValue(request == null ? null : request.getDocType(), record.docType()),
                        department,
                        chooseRequestValue(request == null ? null : request.getAudience(), record.audience()),
                        chooseRequestValue(request == null ? null : request.getVersion(), record.version()),
                        request == null || request.getEffectiveAt() == null ? record.effectiveAt() : parseDateTime(request.getEffectiveAt()),
                        chooseRequestValue(request == null ? null : request.getTitle(), record.title()),
                        chooseRequestValue(request == null ? null : request.getDoctorName(), record.doctorName()),
                        request == null || request.getSourcePriority() == null ? record.sourcePriority() : request.getSourcePriority(),
                        chooseRequestValue(request == null ? null : request.getKeywords(), record.keywords())
                ));
    }

    private String chooseRequestValue(String requestValue, String existingValue) {
        return requestValue == null ? existingValue : requestValue;
    }

    private LocalDateTime parseDateTime(String value) {
        return knowledgeMetadataService.parseDateTime(value);
    }

    private List<TextSegment> enrichSegments(List<TextSegment> segments,
                                             String hash,
                                             String source,
                                             String extension,
                                             String status,
                                             KnowledgeFileMetadata fileMetadata) {
        return knowledgeMetadataService.enrichSegments(segments, hash, source, extension, status, fileMetadata);
    }

    private void removeEmbeddingsByHash(String hash) {
        List<String> segmentIds = knowledgeChunkIndexService.listSegmentIdsByHash(hash);
        removeEmbeddingsBySegmentIds(segmentIds);
    }

    private void removeEmbeddingsBySegmentIds(List<String> segmentIds) {
        if (segmentIds.isEmpty()) {
            return;
        }
        synchronized (embeddingStoreMonitor) {
            embeddingStore.removeAll(segmentIds);
        }
    }

    private void saveKnowledgeStatus(KnowledgeDocumentRecord record) {
        KnowledgeFileStatus existing = knowledgeFileStatusService.findByHash(record.hash());
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
        status.setEmbeddingModelName(isEmbeddingsReadyStatus(record.processingStatus()) ? embeddingModelName : null);
        status.setIndexBuildVersion(existing == null ? 0 : resolveIndexBuildVersion(existing));
        status.setCurrentGeneration(existing == null ? 1 : resolveCurrentGeneration(existing));
        status.setIndexUpgradeState(existing == null ? INDEX_UPGRADE_IDLE : existing.getIndexUpgradeState());
        status.setIndexUpgradeMessage(existing == null ? null : existing.getIndexUpgradeMessage());
        status.setDocType(record.docType());
        status.setDepartment(record.department());
        status.setAudience(record.audience());
        status.setVersion(record.version());
        status.setEffectiveAt(record.effectiveAt());
        status.setTitle(record.title());
        status.setDoctorName(record.doctorName());
        status.setSourcePriority(record.sourcePriority());
        status.setKeywords(record.keywords());
        status.setExtractedText(record.extractedText());
        status.setExtractedCharacters(record.extractedText() == null ? 0 : record.extractedText().length());
        status.setChunkCount(record.chunks() == null ? 0 : record.chunks().size());
        knowledgeFileStatusService.saveOrUpdate(status);
    }

    private boolean isEmbeddingModelCompatible(KnowledgeFileStatus status) {
        return status != null && embeddingModelName.equals(status.getEmbeddingModelName());
    }

    private record ParsedKnowledge(Document document, String parserName) {
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
        fileInfo.setDocType(record.docType());
        fileInfo.setDepartment(record.department());
        fileInfo.setAudience(record.audience());
        fileInfo.setVersion(record.version());
        fileInfo.setEffectiveAt(record.effectiveAt() == null ? null : record.effectiveAt().toString());
        fileInfo.setTitle(record.title());
        fileInfo.setDoctorName(record.doctorName());
        fileInfo.setSourcePriority(record.sourcePriority());
        fileInfo.setKeywords(record.keywords());
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
        fileInfo.setDocType(status.getDocType());
        fileInfo.setDepartment(status.getDepartment());
        fileInfo.setAudience(status.getAudience());
        fileInfo.setVersion(status.getVersion());
        fileInfo.setEffectiveAt(status.getEffectiveAt() == null ? null : status.getEffectiveAt().toString());
        fileInfo.setTitle(status.getTitle());
        fileInfo.setDoctorName(status.getDoctorName());
        fileInfo.setSourcePriority(status.getSourcePriority());
        fileInfo.setKeywords(status.getKeywords());
        return fileInfo;
    }

    private KnowledgeDocumentDetail toKnowledgeDetail(KnowledgeDocumentRecord record) {
        KnowledgeFileStatus status = knowledgeFileStatusService.findByHash(record.hash());
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
        detail.setDocType(record.docType());
        detail.setDepartment(record.department());
        detail.setAudience(record.audience());
        detail.setVersion(record.version());
        detail.setEffectiveAt(record.effectiveAt() == null ? null : record.effectiveAt().toString());
        detail.setTitle(record.title());
        detail.setDoctorName(record.doctorName());
        detail.setSourcePriority(record.sourcePriority());
        detail.setKeywords(record.keywords());
        detail.setSegmentationMode(record.segmentationMode());
        detail.setSemanticEnhanced(hasSemanticEnhancedChunks(record.chunks()));
        detail.setIndexUpgradeState(status == null ? null : status.getIndexUpgradeState());
        detail.setIndexUpgradeMessage(status == null ? null : status.getIndexUpgradeMessage());
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
        detail.setDocType(status.getDocType());
        detail.setDepartment(status.getDepartment());
        detail.setAudience(status.getAudience());
        detail.setVersion(status.getVersion());
        detail.setEffectiveAt(status.getEffectiveAt() == null ? null : status.getEffectiveAt().toString());
        detail.setTitle(status.getTitle());
        detail.setDoctorName(status.getDoctorName());
        detail.setSourcePriority(status.getSourcePriority());
        detail.setKeywords(status.getKeywords());
        List<KnowledgeChunkIndex> chunkIndexes = currentChunkIndexes(status);
        detail.setSegmentationMode(resolveSegmentationMode(chunkIndexes));
        detail.setSemanticEnhanced(hasSemanticEnhancement(chunkIndexes));
        detail.setIndexUpgradeState(status.getIndexUpgradeState());
        detail.setIndexUpgradeMessage(status.getIndexUpgradeMessage());
        detail.setExtractedText(status.getExtractedText() == null ? "" : status.getExtractedText());
        detail.setChunks(toChunkInfos(chunkIndexes));
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
                                           String docType,
                                           String department,
                                           String audience,
                                           String version,
                                           LocalDateTime effectiveAt,
                                           String title,
                                           String doctorName,
                                           Integer sourcePriority,
                                           String keywords,
                                           String segmentationMode,
                                           String extractedText,
                                           List<KnowledgeChunkInfo> chunks,
                                           Path storagePath) {
    }

    private String resolveSegmentationMode(List<KnowledgeChunkIndex> chunkIndexes) {
        if (chunkIndexes == null || chunkIndexes.isEmpty()) {
            return KnowledgeIndexingService.SEGMENTATION_MODE_RULE_RECURSIVE;
        }
        return chunkIndexes.stream()
                .map(KnowledgeChunkIndex::getSegmentationMode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(KnowledgeIndexingService.SEGMENTATION_MODE_RULE_RECURSIVE);
    }

    private List<TextSegment> enrichChunkSemantics(List<TextSegment> segments, String documentTitle, String docType) {
        if (knowledgeChunkSemanticEnrichmentService == null || segments == null || segments.isEmpty()) {
            return segments;
        }
        return knowledgeChunkSemanticEnrichmentService.enrichSegments(segments, documentTitle, docType);
    }

    private List<TextSegment> withGenerationMetadata(List<TextSegment> segments, int generation) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<TextSegment> enrichedSegments = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            Metadata metadata = segment.metadata() == null ? new Metadata() : segment.metadata().copy();
            metadata.put(KnowledgeIndexingService.METADATA_GENERATION, generation);
            enrichedSegments.add(TextSegment.from(segment.text(), metadata));
        }
        return enrichedSegments;
    }

    private boolean hasSemanticEnhancement(List<KnowledgeChunkIndex> chunkIndexes) {
        if (chunkIndexes == null || chunkIndexes.isEmpty()) {
            return false;
        }
        return chunkIndexes.stream().anyMatch(chunk ->
                StringUtils.hasText(chunk.getSemanticSummary())
                        || StringUtils.hasText(chunk.getSemanticKeywords())
                        || StringUtils.hasText(chunk.getSectionRole()));
    }

    private boolean hasSemanticEnhancedChunks(List<KnowledgeChunkInfo> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return false;
        }
        return chunks.stream().anyMatch(chunk ->
                StringUtils.hasText(chunk.getSemanticSummary())
                        || (chunk.getSemanticKeywords() != null && !chunk.getSemanticKeywords().isEmpty())
                        || StringUtils.hasText(chunk.getSectionRole()));
    }
}
