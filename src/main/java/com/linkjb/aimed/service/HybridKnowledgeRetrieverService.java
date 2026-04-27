package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.dto.response.chat.ChatStreamMetadata;
import com.linkjb.aimed.entity.vo.KnowledgeCitationItem;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalDocumentEntityMatch;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalDiagnosticHit;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalDiagnosticResponse;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalMatchedDiseaseEntity;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalQueryRewriteInfo;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalScoreBreakdown;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalScoringRule;
import com.linkjb.aimed.service.retrieval.RetrievalExecutionResult;
import com.linkjb.aimed.service.retrieval.RetrievalSummaryStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 混合检索服务。
 *
 * 这里刻意没有直接把检索交给某一个单独中间件，而是把两条链路并起来：
 * - 关键词召回：更擅长命中医生名、科室名、指南名、版本号这类“必须精确命中”的问题
 * - 向量召回：更擅长处理患者口语化表达、近义说法和语义相近的问题
 *
 * 最终目标不是“谁替代谁”，而是让两条召回链各自做擅长的部分，再由统一的重排逻辑输出一份更稳的上下文。
 */
@Service
public class HybridKnowledgeRetrieverService {

    private static final Logger log = LoggerFactory.getLogger(HybridKnowledgeRetrieverService.class);
    private static final int FAST_PATH_DISEASE_MATCH_LIMIT = 2;
    private static final int DIAGNOSTIC_DISEASE_MATCH_LIMIT = 6;

    public enum RetrievalProfile {
        LOCAL,
        ONLINE
    }

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeFileStatusService knowledgeFileStatusService;
    private final EmbeddingStore<TextSegment> knowledgeEmbeddingStore;
    private final EmbeddingModel knowledgeEmbeddingModel;
    private final RetrievalSummaryStore retrievalSummaryStore;
    private final int onlineKeywordTopK;
    private final int onlineVectorTopK;
    private final int onlineFinalTopK;
    private final double onlineVectorMinScore;
    private final int localFinalTopK;
    private final boolean fastMode;
    private final boolean enableContentLikeFallback;
    private final boolean enableMedicalStandardRealtime;
    private final long vectorTimeoutMs;
    private final long embeddingCacheTtlMs;
    private final Executor searchExecutor;
    private final ConcurrentMap<String, CacheEntry<Embedding>> embeddingCache = new ConcurrentHashMap<>();

    private static final String SCORE_KEY_KEYWORD = "keyword_score";
    private static final String SCORE_KEY_VECTOR = "vector_score";
    private static final String SCORE_KEY_CORE_PHRASE = "core_phrase";
    private static final String SCORE_KEY_EXACT_TITLE = "exact_title_or_filename";
    private static final String SCORE_KEY_DOCTOR = "doctor_name";
    private static final String SCORE_KEY_DEPARTMENT = "department";
    private static final String SCORE_KEY_HOSPITAL_DOC = "hospital_document";
    private static final String SCORE_KEY_AUDIENCE = "audience_match";
    private static final String SCORE_KEY_MEDICAL_STANDARD_MATCH = "medical_standard_concept_match";
    private static final String SCORE_KEY_MEDICAL_STANDARD_MISSING = "missing_medical_standard_anchor";
    private static final String SCORE_KEY_MEDICAL_ANCHOR_METADATA = "medical_anchor_metadata_match";
    private static final String SCORE_KEY_MEDICAL_ANCHOR_MISSING = "missing_medical_anchor_metadata";
    private static final String SCORE_KEY_SEMANTIC_KEYWORDS = "semantic_keywords_match";
    private static final String SCORE_KEY_SECTION_ROLE = "section_role_match";
    private static final String SCORE_KEY_MEDICAL_ENTITIES = "medical_entities_match";
    private static final String SCORE_KEY_TARGET_QUESTIONS = "target_question_match";
    private static final String SCORE_KEY_LOW_INFORMATION = "low_information_chunk";
    private static final double SCORE_WEIGHT_KEYWORD = 30.0;
    private static final double SCORE_WEIGHT_VECTOR = 20.0;
    private static final double SCORE_BONUS_CORE_PHRASE = 35.0;
    private static final double SCORE_BONUS_EXACT_TITLE = 30.0;
    private static final double SCORE_BONUS_DOCTOR = 25.0;
    private static final double SCORE_BONUS_DEPARTMENT = 20.0;
    private static final double SCORE_BONUS_HOSPITAL_DOC = 5.0;
    private static final double SCORE_BONUS_AUDIENCE = 5.0;
    private static final double SCORE_BONUS_MEDICAL_STANDARD_MATCH = 18.0;
    private static final double SCORE_PENALTY_MEDICAL_STANDARD_MISSING = -12.0;
    private static final double SCORE_BONUS_MEDICAL_ANCHOR_METADATA = 16.0;
    private static final double SCORE_PENALTY_MEDICAL_ANCHOR_MISSING = -18.0;
    private static final double SCORE_BONUS_SEMANTIC_KEYWORDS = 8.0;
    private static final double SCORE_BONUS_SECTION_ROLE = 10.0;
    private static final double SCORE_BONUS_MEDICAL_ENTITIES = 7.0;
    private static final double SCORE_BONUS_TARGET_QUESTIONS = 5.0;
    private static final double SCORE_PENALTY_LOW_INFORMATION = -24.0;

    @Autowired(required = false)
    private ChatRetrievalQueryRewriteService chatRetrievalQueryRewriteService;
    @Autowired(required = false)
    private MedicalStandardLookupService medicalStandardLookupService;
    @Autowired(required = false)
    private MedicalKnowledgeMappingService medicalStandardKnowledgeMappingService;

    @Autowired
    public HybridKnowledgeRetrieverService(JdbcTemplate jdbcTemplate,
                                           KnowledgeFileStatusService knowledgeFileStatusService,
                                           @Qualifier("knowledgeEmbeddingStore") EmbeddingStore<TextSegment> knowledgeEmbeddingStore,
                                           @Qualifier("knowledgeEmbeddingModel") EmbeddingModel knowledgeEmbeddingModel,
                                           RetrievalSummaryStore retrievalSummaryStore,
                                           @Qualifier("knowledgeSearchExecutor") Executor searchExecutor,
                                           @Value("${app.knowledge-base.search.keyword-top-k:8}") int onlineKeywordTopK,
                                           @Value("${app.knowledge-base.search.vector-top-k:8}") int onlineVectorTopK,
                                           @Value("${app.knowledge-base.search.final-top-k:6}") int onlineFinalTopK,
                                           @Value("${app.knowledge-base.search.vector-min-score:0.35}") double onlineVectorMinScore,
                                           @Value("${app.knowledge-base.search.local.final-top-k:3}") int localFinalTopK,
                                           @Value("${app.knowledge-base.search.fast-mode:true}") boolean fastMode,
                                           @Value("${app.knowledge-base.search.enable-content-like-fallback:false}") boolean enableContentLikeFallback,
                                           @Value("${app.knowledge-base.search.enable-medical-standard-realtime:true}") boolean enableMedicalStandardRealtime,
                                           @Value("${app.knowledge-base.search.vector-timeout-ms:1500}") long vectorTimeoutMs,
                                           @Value("${app.knowledge-base.search.embedding-cache-ttl-ms:300000}") long embeddingCacheTtlMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeFileStatusService = knowledgeFileStatusService;
        this.knowledgeEmbeddingStore = knowledgeEmbeddingStore;
        this.knowledgeEmbeddingModel = knowledgeEmbeddingModel;
        this.retrievalSummaryStore = retrievalSummaryStore;
        this.searchExecutor = searchExecutor == null ? ForkJoinPool.commonPool() : searchExecutor;
        this.onlineKeywordTopK = Math.max(1, onlineKeywordTopK);
        this.onlineVectorTopK = Math.max(1, onlineVectorTopK);
        this.onlineFinalTopK = Math.max(1, onlineFinalTopK);
        this.onlineVectorMinScore = Math.max(0.0, onlineVectorMinScore);
        this.localFinalTopK = Math.max(1, localFinalTopK);
        this.fastMode = fastMode;
        this.enableContentLikeFallback = enableContentLikeFallback;
        this.enableMedicalStandardRealtime = enableMedicalStandardRealtime;
        this.vectorTimeoutMs = Math.max(200L, vectorTimeoutMs);
        this.embeddingCacheTtlMs = Math.max(1_000L, embeddingCacheTtlMs);
    }

    HybridKnowledgeRetrieverService(JdbcTemplate jdbcTemplate,
                                    KnowledgeFileStatusService knowledgeFileStatusService,
                                    EmbeddingStore<TextSegment> knowledgeEmbeddingStore,
                                    EmbeddingModel knowledgeEmbeddingModel,
                                    int onlineKeywordTopK,
                                    int onlineVectorTopK,
                                    int onlineFinalTopK,
                                    double onlineVectorMinScore,
                                    int localFinalTopK) {
        this(jdbcTemplate, knowledgeFileStatusService, knowledgeEmbeddingStore, knowledgeEmbeddingModel, new RetrievalSummaryStore(), ForkJoinPool.commonPool(),
                onlineKeywordTopK, onlineVectorTopK, onlineFinalTopK, onlineVectorMinScore, localFinalTopK,
                true, false, true, 1500L, 300_000L);
    }

    HybridKnowledgeRetrieverService(JdbcTemplate jdbcTemplate,
                                    KnowledgeFileStatusService knowledgeFileStatusService,
                                    EmbeddingStore<TextSegment> knowledgeEmbeddingStore,
                                    EmbeddingModel knowledgeEmbeddingModel,
                                    Executor searchExecutor,
                                    int onlineKeywordTopK,
                                    int onlineVectorTopK,
                                    int onlineFinalTopK,
                                    double onlineVectorMinScore,
                                    int localFinalTopK) {
        this(jdbcTemplate, knowledgeFileStatusService, knowledgeEmbeddingStore, knowledgeEmbeddingModel,
                new RetrievalSummaryStore(),
                searchExecutor,
                onlineKeywordTopK, onlineVectorTopK, onlineFinalTopK, onlineVectorMinScore, localFinalTopK,
                true, false, true, 1500L, 300_000L);
    }

    HybridKnowledgeRetrieverService(JdbcTemplate jdbcTemplate,
                                    KnowledgeFileStatusService knowledgeFileStatusService,
                                    EmbeddingStore<TextSegment> knowledgeEmbeddingStore,
                                    EmbeddingModel knowledgeEmbeddingModel,
                                    RetrievalSummaryStore retrievalSummaryStore,
                                    int onlineKeywordTopK,
                                    int onlineVectorTopK,
                                    int onlineFinalTopK,
                                    double onlineVectorMinScore,
                                    int localFinalTopK,
                                    boolean fastMode,
                                    boolean enableContentLikeFallback,
                                    boolean enableMedicalStandardRealtime,
                                    long vectorTimeoutMs,
                                    long embeddingCacheTtlMs) {
        this(jdbcTemplate, knowledgeFileStatusService, knowledgeEmbeddingStore, knowledgeEmbeddingModel, retrievalSummaryStore, ForkJoinPool.commonPool(),
                onlineKeywordTopK, onlineVectorTopK, onlineFinalTopK, onlineVectorMinScore, localFinalTopK,
                fastMode, enableContentLikeFallback, enableMedicalStandardRealtime, vectorTimeoutMs, embeddingCacheTtlMs);
    }

    HybridKnowledgeRetrieverService(JdbcTemplate jdbcTemplate,
                                    KnowledgeFileStatusService knowledgeFileStatusService,
                                    EmbeddingStore<TextSegment> knowledgeEmbeddingStore,
                                    EmbeddingModel knowledgeEmbeddingModel,
                                    int onlineKeywordTopK,
                                    int onlineVectorTopK,
                                    int onlineFinalTopK,
                                    double onlineVectorMinScore,
                                    int localFinalTopK,
                                    boolean fastMode,
                                    boolean enableContentLikeFallback,
                                    boolean enableMedicalStandardRealtime,
                                    long vectorTimeoutMs,
                                    long embeddingCacheTtlMs) {
        this(jdbcTemplate, knowledgeFileStatusService, knowledgeEmbeddingStore, knowledgeEmbeddingModel, new RetrievalSummaryStore(),
                onlineKeywordTopK, onlineVectorTopK, onlineFinalTopK, onlineVectorMinScore, localFinalTopK,
                fastMode, enableContentLikeFallback, enableMedicalStandardRealtime, vectorTimeoutMs, embeddingCacheTtlMs);
    }

    public List<Content> retrieve(Query query) {
        return retrieve(query, RetrievalProfile.ONLINE);
    }

    public List<Content> retrieve(Query query, RetrievalProfile profile) {
        KnowledgeRetrievalQueryRewriteInfo rewriteInfo = rewriteQuery(query);
        RetrievalSummary summary = searchInternal(query == null ? null : query.text(), rewriteInfo, profile, false, false);
        retrievalSummaryStore.remember(query == null || query.metadata() == null ? null : query.metadata().chatMemoryId(),
                query == null ? null : query.text(),
                summary);
        return summary.finalHits().stream()
                .map(hit -> Content.from(TextSegment.from(hit.content(), hit.metadata())))
                .toList();
    }

    public RetrievalSummary search(String rawQuery) {
        return search(rawQuery, RetrievalProfile.ONLINE);
    }

    public RetrievalSummary searchFast(String rawQuery, RetrievalProfile profile) {
        return searchInternal(rawQuery, rewriteQuery(rawQuery), profile, false, true);
    }

    /**
     * 一次完整的混合检索流程。
     *
     * 顺序固定为：
     * 1. 规范化 query
     * 2. 判断当前问法更像哪类问题
     * 3. 关键词召回
     * 4. 向量召回
     * 5. 合并去重并重排
     *
     * 这里同时产出最终检索结果和结构化摘要，后者会被聊天链路拿去做引用展示和审计日志。
     */
    public RetrievalSummary search(String rawQuery, RetrievalProfile profile) {
        return searchFast(rawQuery, profile);
    }

    private RetrievalSummary searchInternal(String rawQuery,
                                            KnowledgeRetrievalQueryRewriteInfo rewriteInfo,
                                            RetrievalProfile profile,
                                            boolean diagnosticMode,
                                            boolean rememberSummary) {
        long startedAt = System.nanoTime();
        String normalizedQuery = normalizeQuery(rewriteInfo == null ? rawQuery : rewriteInfo.effectiveQuery());
        String audience = resolveAudience();
        String queryType = classifyQuery(normalizedQuery);
        List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities = resolveMatchedDiseaseEntities(normalizedQuery, diagnosticMode);

        SearchProfile searchProfile = resolveProfile(profile);
        // 关键词召回和向量召回互不依赖，串行执行会把 MySQL 和 embedding/Chroma 的耗时直接相加。
        // 这里提前解析好 audience 和 query 后并行执行，最终仍在同一个线程里合并重排，避免改变排序语义。
        CompletableFuture<TimedHits> keywordFuture = CompletableFuture.supplyAsync(
                () -> timedSearch("keyword", () -> searchByKeyword(normalizedQuery, audience, topKForKeywordSearch(searchProfile), diagnosticMode)),
                searchExecutor);
        CompletableFuture<TimedHits> vectorFuture = buildVectorFuture(normalizedQuery, audience, searchProfile, diagnosticMode);
        TimedHits keywordResult = keywordFuture.join();
        TimedHits vectorResult = vectorFuture.join();
        List<RetrievedChunk> keywordHits = keywordResult.hits();
        List<RetrievedChunk> vectorHits = vectorResult.hits();
        long keywordDurationMs = keywordResult.durationMs();
        long vectorDurationMs = vectorResult.durationMs();
        long mergeStartedAt = System.nanoTime();
        List<KnowledgeRetrievalDocumentEntityMatch> docEntityMatches =
                resolveDocumentEntityMatches(keywordHits, vectorHits, matchedDiseaseEntities, diagnosticMode);
        List<RetrievedChunk> merged = rerankAndMerge(keywordHits, vectorHits, normalizedQuery, audience, searchProfile.finalTopK(),
                matchedDiseaseEntities, docEntityMatches);
        long mergeDurationMs = millisSince(mergeStartedAt);
        long totalDurationMs = millisSince(startedAt);

        logSlowSearch(profile, queryType, normalizedQuery, keywordHits.size(), vectorHits.size(),
                vectorResult.skipped(), keywordDurationMs, vectorDurationMs, mergeDurationMs, totalDurationMs);

        RetrievalExecutionResult executionResult = new RetrievalExecutionResult(
                rewriteInfo,
                rawQuery,
                normalizedQuery,
                queryType,
                keywordHits,
                vectorHits,
                merged,
                matchedDiseaseEntities,
                docEntityMatches,
                new RetrievalTimings(
                        keywordDurationMs,
                        vectorDurationMs,
                        mergeDurationMs,
                        totalDurationMs,
                        vectorResult.status()
                ),
                totalDurationMs,
                merged.isEmpty()
        );
        RetrievalSummary summary = toRetrievalSummary(executionResult);
        if (rememberSummary) {
            retrievalSummaryStore.remember(null, rawQuery, summary);
        }
        return summary;
    }

    public KnowledgeRetrievalDiagnosticResponse diagnose(String rawQuery, String profileValue) {
        RetrievalProfile profile = "LOCAL".equalsIgnoreCase(profileValue) ? RetrievalProfile.LOCAL : RetrievalProfile.ONLINE;
        KnowledgeRetrievalQueryRewriteInfo rewriteInfo = rewriteQuery(rawQuery);
        String normalizedQuery = normalizeQuery(rewriteInfo == null ? rawQuery : rewriteInfo.effectiveQuery());
        List<String> keywordTokens = buildKeywordTokens(normalizedQuery);
        String booleanQuery = buildBooleanFullTextQuery(normalizedQuery, keywordTokens);
        List<String> matchedSymptoms = resolveMatchedSymptoms(normalizedQuery);
        List<String> matchedChiefComplaints = resolveMatchedChiefComplaints(normalizedQuery);
        // 诊断页展示的“最终排序”必须和聊天快路径一致；症状/主诉等额外字段只作为解释信息补充。
        RetrievalSummary fastPathSummary = searchInternal(rawQuery, rewriteInfo, profile, false, false);

        return new KnowledgeRetrievalDiagnosticResponse(
                rewriteInfo == null ? rawQuery : rewriteInfo.rawQuery(),
                normalizedQuery,
                rewriteInfo == null ? normalizedQuery : rewriteInfo.effectiveQuery(),
                rewriteInfo == null ? "NONE" : rewriteInfo.rewriteMode(),
                rewriteInfo != null && rewriteInfo.rewriteApplied(),
                rewriteInfo == null ? List.of() : rewriteInfo.contextMessagesUsed(),
                rewriteInfo == null ? List.of() : rewriteInfo.medicalAnchors(),
                rewriteInfo == null ? null : rewriteInfo.modelRewriteCandidate(),
                rewriteInfo != null && rewriteInfo.modelRewriteAccepted(),
                fastPathSummary.matchedDiseaseEntities(),
                matchedSymptoms,
                matchedChiefComplaints,
                fastPathSummary.docEntityMatches(),
                resolveAudience(),
                profile.name(),
                fastPathSummary.queryType(),
                booleanQuery,
                fastPathSummary.durationMs(),
                fastPathSummary.emptyRecall(),
                scoringRules(),
                keywordTokens,
                fastPathSummary.keywordHits().stream().map(this::toDiagnosticHit).toList(),
                fastPathSummary.vectorHits().stream().map(this::toDiagnosticHit).toList(),
                fastPathSummary.finalHits().stream().map(this::toDiagnosticHit).toList()
        );
    }

    /**
     * 在线链路的检索可能发生在异步线程里，因此这里按会话维度缓存最近几条摘要，
     * 并且在消费时要求“当前问题 rawQuery 精确匹配”，避免不同问题之间串 citation。
     */
    public RetrievalSummary consumeLastSummary(Object chatMemoryId, String rawQuery) {
        return retrievalSummaryStore.consume(chatMemoryId, rawQuery);
    }

    public ChatStreamMetadata toChatStreamMetadata(RetrievalSummary summary) {
        ChatStreamMetadata metadata = new ChatStreamMetadata();
        if (summary == null) {
            return metadata;
        }
        // 这里返回的是“给前端展示和给审计日志落库的摘要”，故意只保留高价值字段，不把整个检索明细都塞给前端。
        metadata.setQueryType(summary.queryType());
        metadata.setRetrievedCountKeyword(summary.retrievedCountKeyword());
        metadata.setRetrievedCountVector(summary.retrievedCountVector());
        metadata.setMergedCount(summary.mergedCount());
        metadata.setFinalCitationCount(summary.finalHits().size());
        metadata.setEmptyRecall(summary.emptyRecall());
        metadata.setDurationMs(summary.durationMs());
        metadata.setTopDocHashes(summary.finalHits().stream().map(RetrievedChunk::fileHash).distinct().limit(5).toList());
        metadata.setCitations(summary.finalHits().stream().map(this::toCitation).toList());
        return metadata;
    }

    private KnowledgeCitationItem toCitation(RetrievedChunk hit) {
        KnowledgeCitationItem item = new KnowledgeCitationItem();
        item.setFileHash(hit.fileHash());
        item.setDocumentName(hit.documentName());
        item.setSegmentId(hit.segmentId());
        item.setSnippet(displaySnippet(hit));
        item.setUpdatedAt(hit.updatedAt() == null ? null : hit.updatedAt().toString());
        item.setEffectiveAt(hit.effectiveAt() == null ? null : hit.effectiveAt().toString());
        item.setVersion(hit.version());
        item.setRetrievalType(hit.retrievalType());
        return item;
    }

    private KnowledgeRetrievalDiagnosticHit toDiagnosticHit(RetrievedChunk hit) {
        return new KnowledgeRetrievalDiagnosticHit(
                hit.fileHash(),
                hit.segmentId(),
                hit.segmentIndex(),
                hit.title(),
                hit.doctorName(),
                hit.department(),
                hit.version(),
                hit.documentName(),
                hit.retrievalType(),
                hit.keywordScore(),
                hit.vectorScore(),
                hit.combinedScore(),
                displaySnippet(hit),
                hit.scoreBreakdown()
        );
    }

    private List<RetrievedChunk> searchByKeyword(String normalizedQuery, String audience, int topK, boolean diagnosticMode) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        //扩词
        List<String> tokens = buildKeywordTokens(normalizedQuery);
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> fullTextHits = searchByFullText(normalizedQuery, tokens, audience, topK);
        if (!fullTextHits.isEmpty()) {
            return fullTextHits;
        }
        return searchByLikeFallback(normalizedQuery, tokens, audience, topK, diagnosticMode);
    }

    private RetrievalSummary toRetrievalSummary(RetrievalExecutionResult executionResult) {
        return new RetrievalSummary(
                executionResult.rewriteInfo(),
                executionResult.queryType(),
                executionResult.keywordHits().size(),
                executionResult.vectorHits().size(),
                executionResult.finalHits().size(),
                executionResult.emptyRecall(),
                executionResult.retrievalDurationMs(),
                executionResult.keywordHits(),
                executionResult.vectorHits(),
                executionResult.finalHits(),
                executionResult.timings(),
                executionResult.matchedDiseaseEntities(),
                List.of(),
                List.of(),
                executionResult.docEntityMatches()
        );
    }

    private List<RetrievedChunk> searchByFullText(String normalizedQuery, List<String> tokens, String audience, int topK) {
        String booleanQuery = buildBooleanFullTextQuery(normalizedQuery, tokens);
        if (!StringUtils.hasText(booleanQuery)) {
            return List.of();
        }
        String fullTextSql = """
                SELECT k.file_hash, k.segment_id, k.segment_index, k.content, k.preview, k.title, k.doc_type, k.department,
                       k.audience, k.version, k.effective_at, k.status, k.doctor_name, k.source_priority, k.keywords,
                       k.section_title, k.segment_kind, k.segmentation_mode,
                       k.semantic_summary, k.semantic_keywords, k.section_role, k.medical_entities_json, k.target_questions_json,
                       f.original_filename, f.updated_at,
                       MATCH(k.title, k.content, k.keywords) AGAINST (? IN BOOLEAN MODE) AS ft_score
                FROM knowledge_chunk_index k
                JOIN knowledge_file_status f ON f.hash = k.file_hash
                WHERE k.status = 'PUBLISHED'
                  AND k.generation = COALESCE(f.current_generation, 1)
                  AND (k.audience = 'BOTH' OR k.audience = ? OR ? = 'ADMIN')
                  AND MATCH(k.title, k.content, k.keywords) AGAINST (? IN BOOLEAN MODE) > 0
                ORDER BY ft_score DESC, k.source_priority DESC, f.updated_at DESC
                LIMIT ?
                """;
        try {
            List<RetrievedChunk> hits = jdbcTemplate.query(fullTextSql,
                    (rs, rowNum) -> mapRetrievedChunk(rs, "keyword", rs.getDouble("ft_score"), 0.0),
                    booleanQuery, audience, audience, booleanQuery, Math.max(topK * 4, 24));
            return hits.stream()
                    .map(hit -> hit.withKeywordScore(hit.keywordScore() + computeKeywordScore(hit, normalizedQuery, tokens)))
                    .filter(hit -> hit.keywordScore() > 0)
                    .sorted(Comparator.comparingDouble(RetrievedChunk::keywordScore).reversed()
                            .thenComparing(RetrievedChunk::sourcePriority, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(RetrievedChunk::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(topK)
                    .toList();
        } catch (Exception exception) {
            log.warn("knowledge.search.fulltext.fallback audience={} query={}", audience, normalizedQuery, exception);
            return List.of();
        }
    }

    private List<RetrievedChunk> searchByLikeFallback(String normalizedQuery,
                                                      List<String> tokens,
                                                      String audience,
                                                      int topK,
                                                      boolean diagnosticMode) {
        List<String> usefulTokens = tokens.stream()
                .filter(this::isUsefulFullTextToken)
                .limit(8)
                .toList();
        if (usefulTokens.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> metadataHits = searchByLikeMetadataFallback(normalizedQuery, usefulTokens, audience, topK, false);
        if (!metadataHits.isEmpty()) {
            return metadataHits;
        }
        if (!shouldScanContentFallback(normalizedQuery, diagnosticMode)) {
            return List.of();
        }
        return searchByLikeMetadataFallback(normalizedQuery, usefulTokens, audience, topK, true);
    }

    private List<RetrievedChunk> searchByLikeMetadataFallback(String normalizedQuery,
                                                              List<String> tokens,
                                                              String audience,
                                                              int topK,
                                                              boolean includeContent) {
        // LIKE 保底分两段：先查标题/文件名/关键词，最后才扫 content。
        // 大多数“肝癌指南/医生名/科室名”问题命中 metadata 即可，避免每次都对 chunk 正文做大范围 LIKE。
        String whereLikeClause = tokens.stream()
                .map(token -> includeContent
                        ? "(k.title LIKE ? OR f.original_filename LIKE ? OR k.keywords LIKE ? OR k.content LIKE ?)"
                        : "(k.title LIKE ? OR f.original_filename LIKE ? OR k.keywords LIKE ?)")
                .collect(Collectors.joining(" OR "));
        String fallbackSql = """
                SELECT k.file_hash, k.segment_id, k.segment_index, k.content, k.preview, k.title, k.doc_type, k.department,
                       k.audience, k.version, k.effective_at, k.status, k.doctor_name, k.source_priority, k.keywords,
                       k.section_title, k.segment_kind, k.segmentation_mode,
                       k.semantic_summary, k.semantic_keywords, k.section_role, k.medical_entities_json, k.target_questions_json,
                       f.original_filename, f.updated_at
                FROM knowledge_chunk_index k
                JOIN knowledge_file_status f ON f.hash = k.file_hash
                WHERE k.status = 'PUBLISHED'
                  AND k.generation = COALESCE(f.current_generation, 1)
                  AND (k.audience = 'BOTH' OR k.audience = ? OR ? = 'ADMIN')
                  AND (""" + whereLikeClause + """
                  )
                ORDER BY f.updated_at DESC
                LIMIT ?
                """;
        List<Object> args = new ArrayList<>();
        args.add(audience);
        args.add(audience);
        for (String token : tokens) {
            String likeToken = "%" + token + "%";
            args.add(likeToken);
            args.add(likeToken);
            args.add(likeToken);
            if (includeContent) {
                args.add(likeToken);
            }
        }
        args.add(includeContent ? Math.max(topK * 24, 160) : Math.max(topK * 12, 80));
        List<RetrievedChunk> roughHits = jdbcTemplate.query(fallbackSql,
                (rs, rowNum) -> mapRetrievedChunk(rs, "keyword", 0.0, 0.0),
                args.toArray());
        return roughHits.stream()
                .map(hit -> hit.withKeywordScore(computeKeywordScore(hit, normalizedQuery, tokens)))
                .filter(hit -> hit.keywordScore() > 0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::keywordScore).reversed()
                        .thenComparing(RetrievedChunk::sourcePriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RetrievedChunk::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .toList();
    }

    private TimedHits timedSearch(String stage, SearchSupplier supplier) {
        long startedAt = System.nanoTime();
        try {
            return new TimedHits(supplier.get(), millisSince(startedAt), "DONE");
        } catch (Exception exception) {
            log.warn("knowledge.search.{}.failed", stage, exception);
            return new TimedHits(List.of(), millisSince(startedAt), "ERROR");
        }
    }

    private String buildBooleanFullTextQuery(String normalizedQuery, List<String> tokens) {
        List<String> clauses = new ArrayList<>();
        List<String> prioritized = tokens.stream()
                .map(String::trim)
                .filter(this::isUsefulFullTextToken)
                .distinct()
                .sorted(Comparator
                        .comparingInt(this::fullTextTokenPriority)
                        .thenComparingInt(String::length)
                        .reversed())
                .toList();
        prioritized.stream()
                .filter(token -> fullTextTokenPriority(token) >= 50)
                .limit(1)
                .forEach(token -> clauses.add("+" + token + "*"));
        prioritized.stream()
                .limit(6)
                .forEach(token -> {
                    String optionalClause = token + "*";
                    if (!clauses.contains(optionalClause) && !clauses.contains("+" + optionalClause)) {
                        clauses.add(optionalClause);
                    }
                });
        return String.join(" ", clauses);
    }

    private List<RetrievedChunk> searchByVector(String normalizedQuery, String audience, int topK, double minScore) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        // 向量召回只负责补语义近似，不负责越权：
        // audience 过滤和 status=PUBLISHED 过滤依然要在这里再过一遍，避免把“只是向量接近”的脏数据带进回答。
        Embedding embedding = resolveCachedEmbedding(normalizedQuery);
        List<EmbeddingMatch<TextSegment>> matches = knowledgeEmbeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(topK)
                        .minScore(minScore)
                        .build())
                .matches();
        List<RetrievedChunk> result = new ArrayList<>();
        Map<String, Integer> currentGenerationCache = new ConcurrentHashMap<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            if (segment == null || segment.metadata() == null) {
                continue;
            }
            Metadata metadata = segment.metadata();
            if (!matchesCurrentGeneration(metadata, currentGenerationCache)) {
                continue;
            }
            String chunkAudience = metadata.getString("audience");
            if (!allowAudience(audience, chunkAudience)) {
                continue;
            }
            String status = metadata.getString("status");
            if (!"PUBLISHED".equals(status)) {
                continue;
            }
            result.add(new RetrievedChunk(
                    metadata.getString("knowledge_hash"),
                    match.embeddingId(),
                    readInteger(metadata, "segment_index"),
                    metadata.getString("title"),
                    metadata.getString("doc_type"),
                    metadata.getString("department"),
                    chunkAudience,
                    metadata.getString("version"),
                    parseDateTime(metadata.getString("effective_at")),
                    status,
                    metadata.getString("doctor_name"),
                    readInteger(metadata, "source_priority"),
                    metadata.getString("keywords"),
                    metadata.getString("section_title"),
                    metadata.getString("segment_kind"),
                    metadata.getString("segmentation_mode"),
                    metadata.getString("semantic_summary"),
                    metadata.getString("semantic_keywords"),
                    metadata.getString("section_role"),
                    metadata.getString("medical_entities_json"),
                    metadata.getString("target_questions_json"),
                    metadata.getString("document_name"),
                    parseDateTime(metadata.getString("updated_at")),
                    segment.text(),
                    preview(segment.text()),
                    0.0,
                    match.score(),
                    "vector",
                    List.of(),
                    metadata
            ));
        }
        return result;
    }

    private boolean matchesCurrentGeneration(Metadata metadata, Map<String, Integer> currentGenerationCache) {
        if (metadata == null) {
            return false;
        }
        String hash = metadata.getString("knowledge_hash");
        if (!StringUtils.hasText(hash)) {
            return false;
        }
        int currentGeneration = currentGenerationCache.computeIfAbsent(hash, ignored -> {
            var status = knowledgeFileStatusService.findByHash(hash);
            return status == null || status.getCurrentGeneration() == null || status.getCurrentGeneration() <= 0
                    ? 1
                    : status.getCurrentGeneration();
        });
        int segmentGeneration = readInteger(metadata, KnowledgeIndexingService.METADATA_GENERATION);
        return segmentGeneration <= 0 || segmentGeneration == currentGeneration;
    }

    private List<RetrievedChunk> rerankAndMerge(List<RetrievedChunk> keywordHits,
                                                List<RetrievedChunk> vectorHits,
                                                String query,
                                                String audience,
                                                int finalTopK,
                                                List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities,
                                                List<KnowledgeRetrievalDocumentEntityMatch> docEntityMatches) {
        // 先按 file_hash + segment_id 去重，再做统一打分。
        // 这样同一段内容如果同时被关键词和向量命中，不会重复喂给模型，而是升级成一条 hybrid 命中。
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        keywordHits.forEach(hit -> merged.put(dedupeKey(hit), hit));
        vectorHits.forEach(hit -> merged.merge(dedupeKey(hit), hit, this::mergeHit));

        List<RetrievedChunk> ranked = merged.values().stream()
                .map(hit -> hit.withCombinedScore(scoreHit(hit, query, audience, matchedDiseaseEntities, docEntityMatches),
                        scoreBreakdown(hit, query, audience, matchedDiseaseEntities, docEntityMatches)))
                .sorted(Comparator.comparingDouble(RetrievedChunk::combinedScore).reversed()
                        .thenComparing(RetrievedChunk::sourcePriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RetrievedChunk::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        boolean hasInformativeHit = ranked.stream().anyMatch(hit -> !isLowInformationChunk(hit));
        return ranked.stream()
                .filter(hit -> !hasInformativeHit || !isLowInformationChunk(hit))
                .limit(finalTopK)
                .toList();
    }

    private SearchProfile resolveProfile(RetrievalProfile profile) {
        if (profile == RetrievalProfile.LOCAL) {
            return new SearchProfile(onlineKeywordTopK, onlineVectorTopK, localFinalTopK, onlineVectorMinScore);
        }
        return new SearchProfile(onlineKeywordTopK, onlineVectorTopK, onlineFinalTopK, onlineVectorMinScore);
    }

    private RetrievedChunk mergeHit(RetrievedChunk left, RetrievedChunk right) {
        return new RetrievedChunk(
                left.fileHash(),
                left.segmentId(),
                left.segmentIndex(),
                left.title(),
                left.docType(),
                left.department(),
                left.audience(),
                left.version(),
                left.effectiveAt(),
                left.status(),
                left.doctorName(),
                left.sourcePriority(),
                left.keywords(),
                left.sectionTitle(),
                left.segmentKind(),
                left.segmentationMode(),
                left.semanticSummary(),
                left.semanticKeywords(),
                left.sectionRole(),
                left.medicalEntitiesJson(),
                left.targetQuestionsJson(),
                left.documentName(),
                left.updatedAt(),
                left.content(),
                left.preview(),
                Math.max(left.keywordScore(), right.keywordScore()),
                Math.max(left.vectorScore(), right.vectorScore()),
                left.retrievalType().equals(right.retrievalType()) ? left.retrievalType() : "hybrid",
                left.scoreBreakdown(),
                left.metadata()
        );
    }

    private double scoreHit(RetrievedChunk hit,
                            String query,
                            String audience,
                            List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities,
                            List<KnowledgeRetrievalDocumentEntityMatch> docEntityMatches) {
        return scoreBreakdown(hit, query, audience, matchedDiseaseEntities, docEntityMatches).stream()
                .mapToDouble(KnowledgeRetrievalScoreBreakdown::contribution)
                .sum();
    }

    private List<KnowledgeRetrievalScoreBreakdown> scoreBreakdown(RetrievedChunk hit,
                                                                  String query,
                                                                  String audience,
                                                                  List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities,
                                                                  List<KnowledgeRetrievalDocumentEntityMatch> docEntityMatches) {
        // 这里的打分故意保持“解释性优先”，便于后面线上排查为什么某条文档被排到前面。
        // 目前先用规则重排，等这套规则稳定后，再评估是否有必要接入更重的模型 reranker。
        List<KnowledgeRetrievalScoreBreakdown> breakdown = new ArrayList<>();
        addScoreBreakdown(breakdown, SCORE_KEY_KEYWORD, "关键词分", SCORE_WEIGHT_KEYWORD, hit.keywordScore(),
                "关键词召回基础分 × 关键词权重");
        addScoreBreakdown(breakdown, SCORE_KEY_VECTOR, "向量分", SCORE_WEIGHT_VECTOR, hit.vectorScore(),
                "向量相似度 × 向量权重");
        if (corePhraseMatchScore(hit, query) > 0) {
            addScoreBreakdown(breakdown, SCORE_KEY_CORE_PHRASE, "核心短语命中", SCORE_BONUS_CORE_PHRASE, 1.0,
                    "标题、文件名或关键词命中疾病/指南核心短语");
        }
        String lowerQuery = lower(query);
        String lowerTitle = lower(hit.title());
        String lowerDocumentName = lower(hit.documentName());
        if (StringUtils.hasText(lowerQuery) && (lowerTitle.contains(lowerQuery) || lowerDocumentName.contains(lowerQuery))) {
            addScoreBreakdown(breakdown, SCORE_KEY_EXACT_TITLE, "标题/文件名精确命中", SCORE_BONUS_EXACT_TITLE, 1.0,
                    "规范化问题完整出现在标题或文件名中");
        }
        if (StringUtils.hasText(query) && StringUtils.hasText(hit.doctorName()) && query.contains(hit.doctorName())) {
            addScoreBreakdown(breakdown, SCORE_KEY_DOCTOR, "医生命中", SCORE_BONUS_DOCTOR, 1.0,
                    "问题中包含该文档关联医生或专家名称");
        }
        if (StringUtils.hasText(query) && StringUtils.hasText(hit.department()) && query.contains(hit.department())) {
            addScoreBreakdown(breakdown, SCORE_KEY_DEPARTMENT, "科室命中", SCORE_BONUS_DEPARTMENT, 1.0,
                    "问题中包含该文档关联科室");
        }
        if (StringUtils.hasText(hit.documentName()) && hit.documentName().contains("树兰")) {
            addScoreBreakdown(breakdown, SCORE_KEY_HOSPITAL_DOC, "院内文档加权", SCORE_BONUS_HOSPITAL_DOC, 1.0,
                    "文件名包含树兰，优先院内知识");
        }
        if (allowAudience(audience, hit.audience())) {
            addScoreBreakdown(breakdown, SCORE_KEY_AUDIENCE, "适用对象匹配", SCORE_BONUS_AUDIENCE, 1.0,
                    "文档适用对象与当前用户身份匹配");
        }
        if (semanticKeywordMatchScore(hit, query) > 0) {
            addScoreBreakdown(breakdown, SCORE_KEY_SEMANTIC_KEYWORDS, "语义关键词命中", SCORE_BONUS_SEMANTIC_KEYWORDS, 1.0,
                    "chunk 语义关键词命中 query 核心词");
        }
        if (sectionRoleMatchScore(hit, query) > 0) {
            addScoreBreakdown(breakdown, SCORE_KEY_SECTION_ROLE, "段落角色匹配", SCORE_BONUS_SECTION_ROLE, 1.0,
                    "query 问题类型与 chunk 角色一致");
        }
        if (medicalEntitiesMatchScore(hit, query) > 0) {
            addScoreBreakdown(breakdown, SCORE_KEY_MEDICAL_ENTITIES, "医学实体命中", SCORE_BONUS_MEDICAL_ENTITIES, 1.0,
                    "query 核心术语命中 chunk 标注的疾病/症状/检查实体");
        }
        if (medicalAnchorMetadataMatchScore(hit, query) > 0) {
            addScoreBreakdown(breakdown, SCORE_KEY_MEDICAL_ANCHOR_METADATA, "疾病锚点命中", SCORE_BONUS_MEDICAL_ANCHOR_METADATA, 1.0,
                    "标题、文件名、章节或语义关键词命中 query 的疾病锚点");
        } else if (hasStrongDiseaseAnchors(query)) {
            breakdown.add(new KnowledgeRetrievalScoreBreakdown(
                    SCORE_KEY_MEDICAL_ANCHOR_MISSING,
                    "缺少疾病锚点",
                    SCORE_PENALTY_MEDICAL_ANCHOR_MISSING,
                    1.0,
                    SCORE_PENALTY_MEDICAL_ANCHOR_MISSING,
                    "query 已明确疾病锚点，但当前 chunk 的标题、文件名、章节或语义关键词未命中"
            ));
        }
        if (targetQuestionMatchScore(hit, query) > 0) {
            addScoreBreakdown(breakdown, SCORE_KEY_TARGET_QUESTIONS, "适用问法命中", SCORE_BONUS_TARGET_QUESTIONS, 1.0,
                    "chunk 标注的适用问法与当前 query 高度接近");
        }
        if (isLowInformationChunk(hit)) {
            breakdown.add(new KnowledgeRetrievalScoreBreakdown(
                    SCORE_KEY_LOW_INFORMATION,
                    "低信息密度片段",
                    SCORE_PENALTY_LOW_INFORMATION,
                    1.0,
                    SCORE_PENALTY_LOW_INFORMATION,
                    "chunk 内容过短、接近页码/目录或缺少足够语义信息，降权避免进入 citation"
            ));
        }
        if (hasMedicalConceptMatch(hit, matchedDiseaseEntities, docEntityMatches)) {
            addScoreBreakdown(breakdown, SCORE_KEY_MEDICAL_STANDARD_MATCH, "疾病标准命中", SCORE_BONUS_MEDICAL_STANDARD_MATCH, 1.0,
                    "文档映射到与 query rewrite 相同的疾病概念");
        } else if (!matchedDiseaseEntities.isEmpty()) {
            breakdown.add(new KnowledgeRetrievalScoreBreakdown(
                    SCORE_KEY_MEDICAL_STANDARD_MISSING,
                    "缺少疾病锚点",
                    SCORE_PENALTY_MEDICAL_STANDARD_MISSING,
                    1.0,
                    SCORE_PENALTY_MEDICAL_STANDARD_MISSING,
                    "当前问题已命中疾病概念，但该文档未映射到同类概念"
            ));
        }
        return breakdown;
    }

    private void addScoreBreakdown(List<KnowledgeRetrievalScoreBreakdown> breakdown,
                                   String key,
                                   String label,
                                   double weight,
                                   double basis,
                                   String description) {
        if (basis <= 0) {
            return;
        }
        breakdown.add(new KnowledgeRetrievalScoreBreakdown(key, label, weight, basis, weight * basis, description));
    }

    private List<KnowledgeRetrievalScoringRule> scoringRules() {
        return List.of(
                new KnowledgeRetrievalScoringRule(SCORE_KEY_KEYWORD, "关键词分", SCORE_WEIGHT_KEYWORD, "关键词召回基础分 × 关键词权重"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_VECTOR, "向量分", SCORE_WEIGHT_VECTOR, "向量相似度 × 向量权重"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_CORE_PHRASE, "核心短语命中", SCORE_BONUS_CORE_PHRASE, "标题、文件名或关键词命中疾病/指南核心短语时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_EXACT_TITLE, "标题/文件名精确命中", SCORE_BONUS_EXACT_TITLE, "规范化问题完整出现在标题或文件名中时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_DOCTOR, "医生命中", SCORE_BONUS_DOCTOR, "问题中包含文档关联医生或专家名称时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_DEPARTMENT, "科室命中", SCORE_BONUS_DEPARTMENT, "问题中包含文档关联科室时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_HOSPITAL_DOC, "院内文档加权", SCORE_BONUS_HOSPITAL_DOC, "文件名包含树兰时加分，优先院内知识"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_AUDIENCE, "适用对象匹配", SCORE_BONUS_AUDIENCE, "文档适用对象与当前用户身份匹配时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_SEMANTIC_KEYWORDS, "语义关键词命中", SCORE_BONUS_SEMANTIC_KEYWORDS, "query 核心词命中 chunk 语义关键词时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_SECTION_ROLE, "段落角色匹配", SCORE_BONUS_SECTION_ROLE, "query 问法与 chunk 标注角色一致时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_MEDICAL_ENTITIES, "医学实体命中", SCORE_BONUS_MEDICAL_ENTITIES, "query 核心术语命中 chunk 标注医学实体时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_MEDICAL_ANCHOR_METADATA, "疾病锚点命中", SCORE_BONUS_MEDICAL_ANCHOR_METADATA, "标题、文件名、章节或语义关键词命中 query 的疾病锚点时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_MEDICAL_ANCHOR_MISSING, "缺少疾病锚点", SCORE_PENALTY_MEDICAL_ANCHOR_MISSING, "query 已明确疾病锚点，但 chunk 的结构化字段未命中时降权"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_TARGET_QUESTIONS, "适用问法命中", SCORE_BONUS_TARGET_QUESTIONS, "query 与 chunk 标注的适用问法接近时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_LOW_INFORMATION, "低信息密度片段", SCORE_PENALTY_LOW_INFORMATION, "chunk 过短、接近页码或缺少足够语义信息时降权"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_MEDICAL_STANDARD_MATCH, "疾病标准命中", SCORE_BONUS_MEDICAL_STANDARD_MATCH, "文档映射到与 query 相同的疾病概念时加分"),
                new KnowledgeRetrievalScoringRule(SCORE_KEY_MEDICAL_STANDARD_MISSING, "缺少疾病锚点", SCORE_PENALTY_MEDICAL_STANDARD_MISSING, "query 已识别疾病概念但文档未映射到该概念时降权")
        );
    }

    private double computeKeywordScore(RetrievedChunk hit, String query, Collection<String> tokens) {
        double score = 0;
        String haystackTitle = lower(hit.title());
        String haystackDocumentName = lower(hit.documentName());
        String haystackContent = lower(hit.content());
        String haystackKeywords = lower(hit.keywords());
        String haystackSectionTitle = lower(hit.sectionTitle());
        String haystackSemanticKeywords = lower(hit.semanticKeywords());
        String haystackMedicalEntities = lower(hit.medicalEntitiesJson());
        String lowerQuery = lower(query);
        for (String coreTerm : retrievalAnchorTerms(query)) {
            String lowerTerm = lower(coreTerm);
            if (haystackTitle.contains(lowerTerm)) {
                score += 2.6;
            }
            if (haystackDocumentName.contains(lowerTerm)) {
                score += 3.0;
            }
            if (haystackKeywords.contains(lowerTerm)) {
                score += 1.8;
            }
            if (haystackSectionTitle.contains(lowerTerm)) {
                score += 2.1;
            }
            if (haystackSemanticKeywords.contains(lowerTerm)) {
                score += 1.5;
            }
            if (haystackMedicalEntities.contains(lowerTerm)) {
                score += 1.2;
            }
            if (haystackContent.contains(lowerTerm)) {
                score += 0.8;
            }
        }
        if (StringUtils.hasText(lowerQuery) && haystackTitle.contains(lowerQuery)) {
            score += 1.4;
        }
        if (StringUtils.hasText(lowerQuery) && haystackDocumentName.contains(lowerQuery)) {
            score += 2.2;
        }
        if (StringUtils.hasText(lowerQuery) && haystackKeywords.contains(lowerQuery)) {
            score += 1.0;
        }
        if (StringUtils.hasText(lowerQuery) && haystackSectionTitle.contains(lowerQuery)) {
            score += 1.2;
        }
        if (StringUtils.hasText(lowerQuery) && haystackSemanticKeywords.contains(lowerQuery)) {
            score += 1.1;
        }
        for (String token : tokens) {
            String lowerToken = lower(token);
            if (lowerToken.length() < 2) {
                continue;
            }
            if (haystackTitle.contains(lowerToken)) {
                score += 0.7;
            }
            if (haystackDocumentName.contains(lowerToken)) {
                score += 0.9;
            }
            if (haystackKeywords.contains(lowerToken)) {
                score += 0.5;
            }
            if (haystackSectionTitle.contains(lowerToken)) {
                score += 0.65;
            }
            if (haystackSemanticKeywords.contains(lowerToken)) {
                score += 0.5;
            }
            if (haystackMedicalEntities.contains(lowerToken)) {
                score += 0.45;
            }
            if (haystackContent.contains(lowerToken)) {
                score += 0.25;
            }
        }
        return score;
    }

    private double corePhraseMatchScore(RetrievedChunk hit, String query) {
        String haystackTitle = lower(hit.title());
        String haystackDocumentName = lower(hit.documentName());
        String haystackKeywords = lower(hit.keywords());
        String haystackSectionTitle = lower(hit.sectionTitle());
        String haystackSemanticKeywords = lower(hit.semanticKeywords());
        for (String coreTerm : retrievalAnchorTerms(query)) {
            String lowerTerm = lower(coreTerm);
            if (haystackTitle.contains(lowerTerm)
                    || haystackDocumentName.contains(lowerTerm)
                    || haystackKeywords.contains(lowerTerm)
                    || haystackSemanticKeywords.contains(lowerTerm)
                    || haystackSectionTitle.contains(lowerTerm)) {
                return 1.0;
            }
        }
        return 0.0;
    }

    private double semanticKeywordMatchScore(RetrievedChunk hit, String query) {
        if (!StringUtils.hasText(hit.semanticKeywords())) {
            return 0.0;
        }
        String lowerSemanticKeywords = lower(hit.semanticKeywords());
        for (String term : retrievalAnchorTerms(query)) {
            if (lowerSemanticKeywords.contains(lower(term))) {
                return 1.0;
            }
        }
        return 0.0;
    }

    private double sectionRoleMatchScore(RetrievedChunk hit, String query) {
        if (!StringUtils.hasText(hit.sectionRole())) {
            return 0.0;
        }
        String queryType = classifyQueryType(query);
        if (!StringUtils.hasText(queryType)) {
            return 0.0;
        }
        return switch (queryType) {
            case "诊断" -> hit.sectionRole().contains("诊断") ? 1.0 : 0.0;
            case "临床表现" -> hit.sectionRole().contains("临床表现") ? 1.0 : 0.0;
            case "检查" -> hit.sectionRole().contains("检查") ? 1.0 : 0.0;
            case "治疗" -> hit.sectionRole().contains("治疗") ? 1.0 : 0.0;
            case "用药" -> hit.sectionRole().contains("用药") ? 1.0 : 0.0;
            case "流程" -> (hit.sectionRole().contains("流程")) ? 1.0 : 0.0;
            case "注意事项" -> hit.sectionRole().contains("注意事项") ? 1.0 : 0.0;
            default -> 0.0;
        };
    }

    private double medicalEntitiesMatchScore(RetrievedChunk hit, String query) {
        if (!StringUtils.hasText(hit.medicalEntitiesJson())) {
            return 0.0;
        }
        String lowerEntities = lower(hit.medicalEntitiesJson());
        for (String term : retrievalAnchorTerms(query)) {
            if (lowerEntities.contains(lower(term))) {
                return 1.0;
            }
        }
        return 0.0;
    }

    private double medicalAnchorMetadataMatchScore(RetrievedChunk hit, String query) {
        List<String> diseaseAnchors = diseaseAnchorTerms(query);
        if (diseaseAnchors.isEmpty()) {
            return 0.0;
        }
        String metadataHaystack = lower(String.join(" ",
                normalizeDenseText(hit.title()),
                normalizeDenseText(hit.documentName()),
                normalizeDenseText(hit.sectionTitle()),
                normalizeDenseText(hit.keywords()),
                normalizeDenseText(hit.semanticKeywords()),
                normalizeDenseText(hit.medicalEntitiesJson())
        ));
        for (String anchor : diseaseAnchors) {
            if (metadataHaystack.contains(lower(anchor))) {
                return 1.0;
            }
        }
        return 0.0;
    }

    private double targetQuestionMatchScore(RetrievedChunk hit, String query) {
        if (!StringUtils.hasText(hit.targetQuestionsJson())) {
            return 0.0;
        }
        String lowerQuery = lower(query);
        if (!StringUtils.hasText(lowerQuery)) {
            return 0.0;
        }
        return lower(hit.targetQuestionsJson()).contains(lowerQuery) ? 1.0 : 0.0;
    }

    private String classifyQueryType(String query) {
        String lowerQuery = lower(query);
        if (!StringUtils.hasText(lowerQuery)) {
            return null;
        }
        if (containsAny(lowerQuery, "怎么诊断", "如何诊断", "诊断")) {
            return "诊断";
        }
        if (containsAny(lowerQuery, "有什么症状", "哪些症状", "临床表现", "表现")) {
            return "临床表现";
        }
        if (containsAny(lowerQuery, "什么检查", "做哪些检查", "检查")) {
            return "检查";
        }
        if (containsAny(lowerQuery, "怎么治疗", "如何治疗", "治疗方案")) {
            return "治疗";
        }
        if (containsAny(lowerQuery, "吃什么药", "用什么药", "药物")) {
            return "用药";
        }
        if (containsAny(lowerQuery, "挂号", "流程", "就诊")) {
            return "流程";
        }
        if (containsAny(lowerQuery, "注意什么", "注意事项", "什么时候就医")) {
            return "注意事项";
        }
        return null;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> coreQueryTerms(String query) {
        return KnowledgeSearchLexicon.extractCoreQueryTokens(query);
    }

    private List<String> retrievalAnchorTerms(String query) {
        Set<String> anchors = new LinkedHashSet<>(KnowledgeSearchLexicon.extractCoreQueryTokens(query));
        anchors.addAll(KnowledgeSearchLexicon.detectMedicalAnchors(query));
        return anchors.stream().filter(StringUtils::hasText).toList();
    }

    private List<String> diseaseAnchorTerms(String query) {
        return retrievalAnchorTerms(query).stream()
                .filter(this::isDiseaseLikeAnchor)
                .toList();
    }

    private boolean hasStrongDiseaseAnchors(String query) {
        return !diseaseAnchorTerms(query).isEmpty();
    }

    private boolean isDiseaseLikeAnchor(String anchor) {
        if (!StringUtils.hasText(anchor)) {
            return false;
        }
        return containsAny(anchor, "癌", "病", "症", "感染", "肺炎", "感冒", "流感");
    }

    private boolean isLowInformationChunk(RetrievedChunk hit) {
        String normalizedContent = normalizeDenseText(hit.content());
        String normalizedSummary = normalizeDenseText(hit.semanticSummary());
        String normalizedPreview = normalizeDenseText(hit.preview());
        String signal = StringUtils.hasText(normalizedContent) ? normalizedContent
                : StringUtils.hasText(normalizedSummary) ? normalizedSummary
                : normalizedPreview;
        if (!StringUtils.hasText(signal)) {
            return true;
        }
        if (isPageMarker(signal) || isPageMarker(normalizedSummary) || isPageMarker(normalizedPreview)) {
            return true;
        }
        if (signal.length() <= 2) {
            return true;
        }
        if (signal.length() <= 8 && !signal.matches(".*[\\p{IsHan}A-Za-z].*")) {
            return true;
        }
        return signal.length() <= 18
                && !StringUtils.hasText(hit.sectionTitle())
                && !StringUtils.hasText(hit.semanticKeywords())
                && !StringUtils.hasText(hit.medicalEntitiesJson());
    }

    private boolean isPageMarker(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return normalized.matches("^\\d+$")
                || normalized.matches("^\\d+\\s*/\\s*\\d+$")
                || normalized.matches("^第?\\d+页$")
                || normalized.toLowerCase(Locale.ROOT).matches("^[ivxlcdm]+$");
    }

    private String normalizeDenseText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private boolean isUsefulFullTextToken(String token) {
        return StringUtils.hasText(token) && token.trim().length() >= 2 && token.trim().length() <= 16;
    }

    private int fullTextTokenPriority(String token) {
        String normalized = token == null ? "" : token.trim();
        if (normalized.matches(".*20\\d{2}.*")) {
            return 90;
        }
        if (normalized.endsWith("指南") || normalized.endsWith("规范") || normalized.endsWith("共识")) {
            return 80;
        }
        if (normalized.matches("原发性早期.+癌")) {
            return 60;
        }
        if (normalized.endsWith("癌") || normalized.endsWith("病") || normalized.endsWith("症") || normalized.endsWith("感染")) {
            return 70;
        }
        if (normalized.length() >= 3) {
            return 40;
        }
        return 10;
    }

    private long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void logSlowSearch(RetrievalProfile profile,
                               String queryType,
                               String normalizedQuery,
                               int keywordHitCount,
                               int vectorHitCount,
                               boolean skipVector,
                               long keywordDurationMs,
                               long vectorDurationMs,
                               long mergeDurationMs,
                               long totalDurationMs) {
        if (totalDurationMs < 800) {
            return;
        }
        log.info("knowledge.search.slow(知识库慢查询) profile={} queryType={} keywordHits={} vectorHits={} vectorSkipped={} keywordMs={} vectorMs={} mergeMs={} totalMs={} query={}",
                profile.name(),
                queryType,
                keywordHitCount,
                vectorHitCount,
                skipVector,
                keywordDurationMs,
                vectorDurationMs,
                mergeDurationMs,
                totalDurationMs,
                normalizedQuery);
    }

    private String dedupeKey(RetrievedChunk hit) {
        return hit.fileHash() + "#" + hit.segmentId();
    }

    private RetrievedChunk mapRetrievedChunk(ResultSet rs, String retrievalType, double keywordScore, double vectorScore) throws SQLException {
        Metadata metadata = new Metadata();
        putMetadataIfPresent(metadata, "knowledge_hash", rs.getString("file_hash"));
        putMetadataIfPresent(metadata, "status", rs.getString("status"));
        putMetadataIfPresent(metadata, "doc_type", rs.getString("doc_type"));
        putMetadataIfPresent(metadata, "department", rs.getString("department"));
        putMetadataIfPresent(metadata, "audience", rs.getString("audience"));
        putMetadataIfPresent(metadata, "version", rs.getString("version"));
        putMetadataIfPresent(metadata, "title", rs.getString("title"));
        putMetadataIfPresent(metadata, "doctor_name", rs.getString("doctor_name"));
        metadata.put("source_priority", rs.getInt("source_priority"));
        putMetadataIfPresent(metadata, "keywords", rs.getString("keywords"));
        putMetadataIfPresent(metadata, "section_title", rs.getString("section_title"));
        putMetadataIfPresent(metadata, "segment_kind", rs.getString("segment_kind"));
        putMetadataIfPresent(metadata, "segmentation_mode", rs.getString("segmentation_mode"));
        putMetadataIfPresent(metadata, "semantic_summary", rs.getString("semantic_summary"));
        putMetadataIfPresent(metadata, "semantic_keywords", rs.getString("semantic_keywords"));
        putMetadataIfPresent(metadata, "section_role", rs.getString("section_role"));
        putMetadataIfPresent(metadata, "medical_entities_json", rs.getString("medical_entities_json"));
        putMetadataIfPresent(metadata, "target_questions_json", rs.getString("target_questions_json"));
        putMetadataIfPresent(metadata, "document_name", rs.getString("original_filename"));
        if (rs.getTimestamp("effective_at") != null) {
            metadata.put("effective_at", rs.getTimestamp("effective_at").toLocalDateTime().toString());
        }
        if (rs.getTimestamp("updated_at") != null) {
            metadata.put("updated_at", rs.getTimestamp("updated_at").toLocalDateTime().toString());
        }
        metadata.put("segment_index", rs.getInt("segment_index"));
        return new RetrievedChunk(
                rs.getString("file_hash"),
                rs.getString("segment_id"),
                rs.getInt("segment_index"),
                rs.getString("title"),
                rs.getString("doc_type"),
                rs.getString("department"),
                rs.getString("audience"),
                rs.getString("version"),
                rs.getTimestamp("effective_at") == null ? null : rs.getTimestamp("effective_at").toLocalDateTime(),
                rs.getString("status"),
                rs.getString("doctor_name"),
                rs.getInt("source_priority"),
                rs.getString("keywords"),
                rs.getString("section_title"),
                rs.getString("segment_kind"),
                rs.getString("segmentation_mode"),
                rs.getString("semantic_summary"),
                rs.getString("semantic_keywords"),
                rs.getString("section_role"),
                rs.getString("medical_entities_json"),
                rs.getString("target_questions_json"),
                rs.getString("original_filename"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("content"),
                rs.getString("preview"),
                keywordScore,
                vectorScore,
                retrievalType,
                List.of(),
                metadata
        );
    }

    private void putMetadataIfPresent(Metadata metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value);
        }
    }

    private boolean allowAudience(String currentAudience, String chunkAudience) {
        if ("ADMIN".equals(currentAudience)) {
            return true;
        }
        return "BOTH".equals(chunkAudience) || currentAudience.equals(chunkAudience);
    }

    private String resolveAudience() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "BOTH";
        }
        Set<String> authorities = new LinkedHashSet<>();
        authentication.getAuthorities().forEach(authority -> authorities.add(authority.getAuthority()));
        if (authorities.stream().anyMatch(value -> value.contains("ADMIN"))) {
            return "ADMIN";
        }
        if (authorities.stream().anyMatch(value -> value.contains("DOCTOR"))) {
            return "DOCTOR";
        }
        return "PATIENT";
    }

    private String normalizeQuery(String rawQuery) {
        return KnowledgeSearchLexicon.normalizeSearchQuery(rawQuery);
    }

    private KnowledgeRetrievalQueryRewriteInfo rewriteQuery(Query query) {
        if (chatRetrievalQueryRewriteService == null) {
            String normalized = normalizeQuery(query == null ? null : query.text());
            return new KnowledgeRetrievalQueryRewriteInfo(normalized, normalized, "NONE", false, List.of(), List.of(), null, false, 0L);
        }
        return chatRetrievalQueryRewriteService.rewrite(query);
    }

    private KnowledgeRetrievalQueryRewriteInfo rewriteQuery(String rawQuery) {
        if (chatRetrievalQueryRewriteService == null) {
            String normalized = normalizeQuery(rawQuery);
            return new KnowledgeRetrievalQueryRewriteInfo(normalized, normalized, "NONE", false, List.of(), List.of(), null, false, 0L);
        }
        return chatRetrievalQueryRewriteService.rewrite(rawQuery);
    }

    private List<String> buildKeywordTokens(String query) {
        // query 扩词是关键词检索“看起来聪明一点”的关键。
        // 例如用户问“肝胆科”，这里会尽量扩成“肝胆胰外科”等院内更正式的叫法。
        return KnowledgeSearchLexicon.expandQueryTokens(query);
    }

    private List<KnowledgeRetrievalMatchedDiseaseEntity> resolveMatchedDiseaseEntities(String normalizedQuery, boolean diagnosticMode) {
        if (medicalStandardLookupService == null || !StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        if (!diagnosticMode && !enableMedicalStandardRealtime) {
            return List.of();
        }
        List<String> anchors = KnowledgeSearchLexicon.detectMedicalAnchors(normalizedQuery);
        boolean likelyDiseaseQuery = !anchors.isEmpty()
                || normalizedQuery.contains("癌")
                || normalizedQuery.contains("瘤")
                || normalizedQuery.contains("炎")
                || normalizedQuery.contains("病")
                || normalizedQuery.contains("感染");
        if (!diagnosticMode && !likelyDiseaseQuery) {
            return List.of();
        }
        return medicalStandardLookupService.matchDiseaseEntities(
                normalizedQuery,
                diagnosticMode ? DIAGNOSTIC_DISEASE_MATCH_LIMIT : FAST_PATH_DISEASE_MATCH_LIMIT
        );
    }

    private List<String> resolveMatchedSymptoms(String normalizedQuery) {
        if (medicalStandardLookupService == null) {
            return List.of();
        }
        return medicalStandardLookupService.findMatchedSymptoms(normalizedQuery);
    }

    private List<String> resolveMatchedChiefComplaints(String normalizedQuery) {
        if (medicalStandardLookupService == null) {
            return List.of();
        }
        return medicalStandardLookupService.findMatchedChiefComplaints(normalizedQuery);
    }

    private List<KnowledgeRetrievalDocumentEntityMatch> resolveDocumentEntityMatches(List<RetrievedChunk> keywordHits,
                                                                                     List<RetrievedChunk> vectorHits,
                                                                                     List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities,
                                                                                     boolean diagnosticMode) {
        if (medicalStandardKnowledgeMappingService == null) {
            return List.of();
        }
        if (!diagnosticMode && (matchedDiseaseEntities == null || matchedDiseaseEntities.isEmpty())) {
            return List.of();
        }
        Set<String> hashes = new LinkedHashSet<>();
        keywordHits.stream().map(RetrievedChunk::fileHash).filter(StringUtils::hasText).forEach(hashes::add);
        vectorHits.stream().map(RetrievedChunk::fileHash).filter(StringUtils::hasText).forEach(hashes::add);
        return medicalStandardKnowledgeMappingService.listDocumentMatches(new ArrayList<>(hashes));
    }

    private int topKForKeywordSearch(SearchProfile profile) {
        if (!fastMode) {
            return profile.keywordTopK();
        }
        return Math.max(1, Math.min(profile.keywordTopK(), profile.finalTopK() + 2));
    }

    private boolean shouldScanContentFallback(String normalizedQuery, boolean diagnosticMode) {
        if (diagnosticMode) {
            return true;
        }
        if (!enableContentLikeFallback) {
            return false;
        }
        return StringUtils.hasText(normalizedQuery)
                && normalizedQuery.length() <= 24
                && !normalizedQuery.contains("指南")
                && !normalizedQuery.contains("规范")
                && !normalizedQuery.contains("共识")
                && !normalizedQuery.contains("主任")
                && !normalizedQuery.contains("院士");
    }

    private CompletableFuture<TimedHits> buildVectorFuture(String normalizedQuery,
                                                           String audience,
                                                           SearchProfile searchProfile,
                                                           boolean diagnosticMode) {
        CompletableFuture<TimedHits> future = CompletableFuture.supplyAsync(
                () -> timedSearch("vector", () -> searchByVector(normalizedQuery, audience, searchProfile.vectorTopK(), searchProfile.vectorMinScore())),
                searchExecutor);
        if (diagnosticMode || !fastMode) {
            return future;
        }
        return future.completeOnTimeout(new TimedHits(List.of(), vectorTimeoutMs, "DEGRADED"), vectorTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(error -> {
                    log.warn("knowledge.search.vector.degraded query={}", normalizedQuery, error);
                    return new TimedHits(List.of(), vectorTimeoutMs, "ERROR");
                });
    }

    private Embedding resolveCachedEmbedding(String normalizedQuery) {
        CacheEntry<Embedding> cached = embeddingCache.get(normalizedQuery);
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        Embedding embedding = knowledgeEmbeddingModel.embed(normalizedQuery).content();
        if (StringUtils.hasText(normalizedQuery)) {
            embeddingCache.put(normalizedQuery, new CacheEntry<>(embedding, System.currentTimeMillis() + embeddingCacheTtlMs));
            trimEmbeddingCacheIfNecessary();
        }
        return embedding;
    }

    private void trimEmbeddingCacheIfNecessary() {
        if (embeddingCache.size() <= 256) {
            return;
        }
        embeddingCache.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expired());
    }

    private boolean hasMedicalConceptMatch(RetrievedChunk hit,
                                        List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities,
                                        List<KnowledgeRetrievalDocumentEntityMatch> docEntityMatches) {
        if (hit == null || matchedDiseaseEntities == null || matchedDiseaseEntities.isEmpty()
                || docEntityMatches == null || docEntityMatches.isEmpty()) {
            return false;
        }
        Set<String> matchedUris = matchedDiseaseEntities.stream()
                .map(KnowledgeRetrievalMatchedDiseaseEntity::conceptCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return docEntityMatches.stream()
                .filter(item -> hit.fileHash().equals(item.knowledgeHash()))
                .map(KnowledgeRetrievalDocumentEntityMatch::conceptCode)
                .anyMatch(matchedUris::contains);
    }

    private String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private String classifyQuery(String query) {
        // 这里只做轻量分类，不追求 NLP 级别精度。
        // 目的只是给后续日志和少量规则加权提供一个足够稳的业务标签。
        if (!StringUtils.hasText(query)) {
            return "GENERAL";
        }
        if (query.contains("医生") || query.contains("院士") || query.contains("主任")) {
            return "DOCTOR";
        }
        if (query.contains("科室") || query.contains("门诊")) {
            return "DEPARTMENT";
        }
        if (query.contains("指南") || query.contains("规范")) {
            return "GUIDE";
        }
        if (query.contains("预约") || query.contains("挂号") || query.contains("流程")) {
            return "PROCESS";
        }
        return "GENERAL";
    }

    private int parseInteger(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Integer readInteger(Metadata metadata, String key) {
        if (metadata == null || key == null) {
            return 0;
        }
        Object rawValue = metadata.toMap().get(key);
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        if (rawValue instanceof String text) {
            return parseInteger(text);
        }
        return 0;
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return StringUtils.hasText(value) ? LocalDateTime.parse(value) : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.length() <= 180 ? content : content.substring(0, 180) + "...";
    }

    private String displaySnippet(RetrievedChunk hit) {
        if (hit == null) {
            return "";
        }
        // 老索引或部分向量召回没有 preview；引用卡片优先展示人工切分预览，
        // 缺失时再退到语义摘要和正文，避免 citation 只有标题没有摘录。
        String snippet = firstUsefulSnippet(hit.preview(), hit.semanticSummary(), hit.content());
        return preview(normalizeDenseText(snippet));
    }

    private String firstUsefulSnippet(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String snippet = normalizeDenseText(value);
            if (isUsefulDisplaySnippet(snippet)) {
                return snippet;
            }
        }
        return "";
    }

    private boolean isUsefulDisplaySnippet(String snippet) {
        if (!StringUtils.hasText(snippet)) {
            return false;
        }
        String normalized = normalizeDenseText(snippet);
        if (isPageMarker(normalized)) {
            return false;
        }
        if (normalized.matches("^[\\p{IsHan}A-Za-z0-9]{1,8}[：:]$")) {
            return false;
        }
        if (normalized.length() < 4 && !normalized.matches(".*[，。；,.].*")) {
            return false;
        }
        return true;
    }

    public record RetrievalSummary(KnowledgeRetrievalQueryRewriteInfo rewriteInfo,
                                   String queryType,
                                   int retrievedCountKeyword,
                                   int retrievedCountVector,
                                   int mergedCount,
                                   boolean emptyRecall,
                                   long durationMs,
                                   List<RetrievedChunk> keywordHits,
                                   List<RetrievedChunk> vectorHits,
                                   List<RetrievedChunk> finalHits,
                                   RetrievalTimings timings,
                                   List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities,
                                   List<String> matchedSymptoms,
                                   List<String> matchedChiefComplaints,
                                   List<KnowledgeRetrievalDocumentEntityMatch> docEntityMatches) {
    }

    public record RetrievalTimings(long keywordDurationMs,
                                   long vectorDurationMs,
                                   long mergeDurationMs,
                                   long retrievalDurationMs,
                                   String vectorStatus) {
        public boolean vectorSkipped() {
            return !"DONE".equals(vectorStatus);
        }
    }

    public record RetrievedChunk(String fileHash,
                                 String segmentId,
                                 Integer segmentIndex,
                                 String title,
                                 String docType,
                                 String department,
                                 String audience,
                                 String version,
                                 LocalDateTime effectiveAt,
                                 String status,
                                 String doctorName,
                                 Integer sourcePriority,
                                 String keywords,
                                 String sectionTitle,
                                 String segmentKind,
                                 String segmentationMode,
                                 String semanticSummary,
                                 String semanticKeywords,
                                 String sectionRole,
                                 String medicalEntitiesJson,
                                 String targetQuestionsJson,
                                 String documentName,
                                 LocalDateTime updatedAt,
                                 String content,
                                 String preview,
                                 double keywordScore,
                                 double vectorScore,
                                 String retrievalType,
                                 List<KnowledgeRetrievalScoreBreakdown> scoreBreakdown,
                                 Metadata metadata) {
        public double combinedScore() {
            return metadata == null ? 0.0 : ((Number) metadata.toMap().getOrDefault("combined_score", 0.0)).doubleValue();
        }

        public RetrievedChunk withCombinedScore(double score, List<KnowledgeRetrievalScoreBreakdown> scoreBreakdown) {
            Metadata copied = metadata == null ? new Metadata() : metadata.copy();
            copied.put("combined_score", score);
            return new RetrievedChunk(fileHash, segmentId, segmentIndex, title, docType, department, audience, version,
                    effectiveAt, status, doctorName, sourcePriority, keywords, sectionTitle, segmentKind, segmentationMode,
                    semanticSummary, semanticKeywords, sectionRole, medicalEntitiesJson, targetQuestionsJson, documentName, updatedAt, content,
                    preview, keywordScore, vectorScore, retrievalType, scoreBreakdown == null ? List.of() : scoreBreakdown, copied);
        }

        public RetrievedChunk withKeywordScore(double score) {
            return new RetrievedChunk(fileHash, segmentId, segmentIndex, title, docType, department, audience, version,
                    effectiveAt, status, doctorName, sourcePriority, keywords, sectionTitle, segmentKind, segmentationMode,
                    semanticSummary, semanticKeywords, sectionRole, medicalEntitiesJson, targetQuestionsJson, documentName, updatedAt, content,
                    preview, score, vectorScore, retrievalType, scoreBreakdown, metadata);
        }
    }

    private record SearchProfile(int keywordTopK, int vectorTopK, int finalTopK, double vectorMinScore) {
    }

    private record TimedHits(List<RetrievedChunk> hits, long durationMs, String status) {
        boolean skipped() {
            return !"DONE".equals(status);
        }
    }

    private record CacheEntry<T>(T value, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    @FunctionalInterface
    private interface SearchSupplier {
        List<RetrievedChunk> get();
    }
}
