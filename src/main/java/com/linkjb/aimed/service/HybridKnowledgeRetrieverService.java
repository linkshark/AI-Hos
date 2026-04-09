package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.ChatStreamMetadata;
import com.linkjb.aimed.bean.KnowledgeCitationItem;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final ThreadLocal<RetrievalSummary> LAST_SUMMARY = new ThreadLocal<>();

    public enum RetrievalProfile {
        LOCAL,
        ONLINE
    }

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingStore<TextSegment> knowledgeEmbeddingStore;
    private final EmbeddingModel knowledgeEmbeddingModel;
    private final int onlineKeywordTopK;
    private final int onlineVectorTopK;
    private final int onlineFinalTopK;
    private final double onlineVectorMinScore;
    private final int localKeywordTopK;
    private final int localVectorTopK;
    private final int localFinalTopK;
    private final double localVectorMinScore;

    public HybridKnowledgeRetrieverService(JdbcTemplate jdbcTemplate,
                                           @Qualifier("knowledgeEmbeddingStore") EmbeddingStore<TextSegment> knowledgeEmbeddingStore,
                                           @Qualifier("knowledgeEmbeddingModel") EmbeddingModel knowledgeEmbeddingModel,
                                           @Value("${app.knowledge-base.search.keyword-top-k:8}") int onlineKeywordTopK,
                                           @Value("${app.knowledge-base.search.vector-top-k:8}") int onlineVectorTopK,
                                           @Value("${app.knowledge-base.search.final-top-k:6}") int onlineFinalTopK,
                                           @Value("${app.knowledge-base.search.vector-min-score:0.35}") double onlineVectorMinScore,
                                           @Value("${app.knowledge-base.search.local.keyword-top-k:4}") int localKeywordTopK,
                                           @Value("${app.knowledge-base.search.local.vector-top-k:4}") int localVectorTopK,
                                           @Value("${app.knowledge-base.search.local.final-top-k:3}") int localFinalTopK,
                                           @Value("${app.knowledge-base.search.local.vector-min-score:0.55}") double localVectorMinScore) {
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeEmbeddingStore = knowledgeEmbeddingStore;
        this.knowledgeEmbeddingModel = knowledgeEmbeddingModel;
        this.onlineKeywordTopK = Math.max(1, onlineKeywordTopK);
        this.onlineVectorTopK = Math.max(1, onlineVectorTopK);
        this.onlineFinalTopK = Math.max(1, onlineFinalTopK);
        this.onlineVectorMinScore = Math.max(0.0, onlineVectorMinScore);
        this.localKeywordTopK = Math.max(1, localKeywordTopK);
        this.localVectorTopK = Math.max(1, localVectorTopK);
        this.localFinalTopK = Math.max(1, localFinalTopK);
        this.localVectorMinScore = Math.max(0.0, localVectorMinScore);
    }

    public List<Content> retrieve(Query query) {
        return retrieve(query, RetrievalProfile.ONLINE);
    }

    public List<Content> retrieve(Query query, RetrievalProfile profile) {
        RetrievalSummary summary = search(query == null ? null : query.text(), profile);
        LAST_SUMMARY.set(summary);
        return summary.finalHits().stream()
                .map(hit -> Content.from(TextSegment.from(hit.content(), hit.metadata())))
                .toList();
    }

    public RetrievalSummary search(String rawQuery) {
        return search(rawQuery, RetrievalProfile.ONLINE);
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
        long startedAt = System.nanoTime();
        String normalizedQuery = normalizeQuery(rawQuery);
        String audience = resolveAudience();
        String queryType = classifyQuery(normalizedQuery);

        SearchProfile searchProfile = resolveProfile(profile);
        List<RetrievedChunk> keywordHits = searchByKeyword(normalizedQuery, audience, searchProfile.keywordTopK());
        List<RetrievedChunk> vectorHits = searchByVector(normalizedQuery, audience, searchProfile.vectorTopK(), searchProfile.vectorMinScore());
        List<RetrievedChunk> merged = rerankAndMerge(keywordHits, vectorHits, normalizedQuery, audience, searchProfile.finalTopK());

        RetrievalSummary summary = new RetrievalSummary(
                queryType,
                keywordHits.size(),
                vectorHits.size(),
                merged.size(),
                merged.isEmpty(),
                (System.nanoTime() - startedAt) / 1_000_000,
                merged
        );
        LAST_SUMMARY.set(summary);
        return summary;
    }

    public RetrievalSummary consumeLastSummary() {
        RetrievalSummary summary = LAST_SUMMARY.get();
        LAST_SUMMARY.remove();
        return summary;
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
        item.setSnippet(hit.preview());
        item.setUpdatedAt(hit.updatedAt() == null ? null : hit.updatedAt().toString());
        item.setEffectiveAt(hit.effectiveAt() == null ? null : hit.effectiveAt().toString());
        item.setVersion(hit.version());
        item.setRetrievalType(hit.retrievalType());
        return item;
    }

    private List<RetrievedChunk> searchByKeyword(String normalizedQuery, String audience, int topK) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        List<String> tokens = buildKeywordTokens(normalizedQuery);
        if (tokens.isEmpty()) {
            return List.of();
        }
        // 这一层先用 MySQL 做轻量关键词召回，不引入额外搜索中间件。
        // 这里不是追求“绝对最强的中文检索”，而是优先用最小复杂度补齐医生名、科室名、标题关键词的精确命中能力。
        String whereLikeClause = tokens.stream()
                .map(token -> "(k.title LIKE ? OR k.content LIKE ? OR k.keywords LIKE ?)")
                .collect(java.util.stream.Collectors.joining(" OR "));
        String fallbackSql = """
                SELECT k.file_hash, k.segment_id, k.segment_index, k.content, k.preview, k.title, k.doc_type, k.department,
                       k.audience, k.version, k.effective_at, k.status, k.doctor_name, k.source_priority, k.keywords,
                       f.original_filename, f.updated_at
                FROM knowledge_chunk_index k
                JOIN knowledge_file_status f ON f.hash = k.file_hash
                WHERE k.status = 'PUBLISHED'
                  AND (k.audience = 'BOTH' OR k.audience = ? OR ? = 'ADMIN')
                  AND (""" + whereLikeClause + """
                  )
                ORDER BY k.source_priority DESC, f.updated_at DESC
                LIMIT 80
                """;
        List<Object> args = new ArrayList<>();
        args.add(audience);
        args.add(audience);
        for (String token : tokens) {
            String likeToken = "%" + token + "%";
            args.add(likeToken);
            args.add(likeToken);
            args.add(likeToken);
        }
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

    private List<RetrievedChunk> searchByVector(String normalizedQuery, String audience, int topK, double minScore) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        // 向量召回只负责补语义近似，不负责越权：
        // audience 过滤和 status=PUBLISHED 过滤依然要在这里再过一遍，避免把“只是向量接近”的脏数据带进回答。
        Embedding embedding = knowledgeEmbeddingModel.embed(normalizedQuery).content();
        List<EmbeddingMatch<TextSegment>> matches = knowledgeEmbeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(topK)
                        .minScore(minScore)
                        .build())
                .matches();
        List<RetrievedChunk> result = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            if (segment == null || segment.metadata() == null) {
                continue;
            }
            Metadata metadata = segment.metadata();
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
                    parseInteger(metadata.getString("segment_index")),
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
                    metadata.getString("document_name"),
                    parseDateTime(metadata.getString("updated_at")),
                    segment.text(),
                    preview(segment.text()),
                    0.0,
                    match.score(),
                    "vector",
                    metadata
            ));
        }
        return result;
    }

    private List<RetrievedChunk> rerankAndMerge(List<RetrievedChunk> keywordHits,
                                                List<RetrievedChunk> vectorHits,
                                                String query,
                                                String audience,
                                                int finalTopK) {
        // 先按 file_hash + segment_id 去重，再做统一打分。
        // 这样同一段内容如果同时被关键词和向量命中，不会重复喂给模型，而是升级成一条 hybrid 命中。
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        keywordHits.forEach(hit -> merged.put(dedupeKey(hit), hit));
        vectorHits.forEach(hit -> merged.merge(dedupeKey(hit), hit, this::mergeHit));

        return merged.values().stream()
                .map(hit -> hit.withCombinedScore(scoreHit(hit, query, audience)))
                .sorted(Comparator.comparingDouble(RetrievedChunk::combinedScore).reversed()
                        .thenComparing(RetrievedChunk::sourcePriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RetrievedChunk::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(finalTopK)
                .toList();
    }

    private SearchProfile resolveProfile(RetrievalProfile profile) {
        if (profile == RetrievalProfile.LOCAL) {
            return new SearchProfile(localKeywordTopK, localVectorTopK, localFinalTopK, localVectorMinScore);
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
                left.documentName(),
                left.updatedAt(),
                left.content(),
                left.preview(),
                Math.max(left.keywordScore(), right.keywordScore()),
                Math.max(left.vectorScore(), right.vectorScore()),
                left.retrievalType().equals(right.retrievalType()) ? left.retrievalType() : "hybrid",
                left.metadata()
        );
    }

    private double scoreHit(RetrievedChunk hit, String query, String audience) {
        // 这里的打分故意保持“解释性优先”，便于后面线上排查为什么某条文档被排到前面。
        // 目前先用规则重排，等这套规则稳定后，再评估是否有必要接入更重的模型 reranker。
        double score = hit.keywordScore() * 30 + hit.vectorScore() * 20;
        if (StringUtils.hasText(query) && StringUtils.hasText(hit.title()) && hit.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
            score += 30;
        }
        if (StringUtils.hasText(query) && StringUtils.hasText(hit.doctorName()) && query.contains(hit.doctorName())) {
            score += 25;
        }
        if (StringUtils.hasText(query) && StringUtils.hasText(hit.department()) && query.contains(hit.department())) {
            score += 20;
        }
        if (StringUtils.hasText(hit.documentName()) && hit.documentName().contains("树兰")) {
            score += 5;
        }
        if (allowAudience(audience, hit.audience())) {
            score += 5;
        }
        return score;
    }

    private double computeKeywordScore(RetrievedChunk hit, String query, Collection<String> tokens) {
        double score = 0;
        String haystackTitle = lower(hit.title());
        String haystackContent = lower(hit.content());
        String haystackKeywords = lower(hit.keywords());
        String lowerQuery = lower(query);
        if (StringUtils.hasText(lowerQuery) && haystackTitle.contains(lowerQuery)) {
            score += 1.4;
        }
        if (StringUtils.hasText(lowerQuery) && haystackKeywords.contains(lowerQuery)) {
            score += 1.0;
        }
        for (String token : tokens) {
            String lowerToken = lower(token);
            if (lowerToken.length() < 2) {
                continue;
            }
            if (haystackTitle.contains(lowerToken)) {
                score += 0.7;
            }
            if (haystackKeywords.contains(lowerToken)) {
                score += 0.5;
            }
            if (haystackContent.contains(lowerToken)) {
                score += 0.25;
            }
        }
        return score;
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
                rs.getString("original_filename"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("content"),
                rs.getString("preview"),
                keywordScore,
                vectorScore,
                retrievalType,
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
        if (!StringUtils.hasText(rawQuery)) {
            return "";
        }
        return rawQuery
                .replace('（', '(')
                .replace('）', ')')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> buildKeywordTokens(String query) {
        // query 扩词是关键词检索“看起来聪明一点”的关键。
        // 例如用户问“肝胆科”，这里会尽量扩成“肝胆胰外科”等院内更正式的叫法。
        return KnowledgeSearchLexicon.expandQueryTokens(query);
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

    public record RetrievalSummary(String queryType,
                                   int retrievedCountKeyword,
                                   int retrievedCountVector,
                                   int mergedCount,
                                   boolean emptyRecall,
                                   long durationMs,
                                   List<RetrievedChunk> finalHits) {
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
                                 String documentName,
                                 LocalDateTime updatedAt,
                                 String content,
                                 String preview,
                                 double keywordScore,
                                 double vectorScore,
                                 String retrievalType,
                                 Metadata metadata) {
        public double combinedScore() {
            return metadata == null ? 0.0 : ((Number) metadata.toMap().getOrDefault("combined_score", 0.0)).doubleValue();
        }

        public RetrievedChunk withCombinedScore(double score) {
            Metadata copied = metadata == null ? new Metadata() : metadata.copy();
            copied.put("combined_score", score);
            return new RetrievedChunk(fileHash, segmentId, segmentIndex, title, docType, department, audience, version,
                    effectiveAt, status, doctorName, sourcePriority, keywords, documentName, updatedAt, content,
                    preview, keywordScore, vectorScore, retrievalType, copied);
        }

        public RetrievedChunk withKeywordScore(double score) {
            return new RetrievedChunk(fileHash, segmentId, segmentIndex, title, docType, department, audience, version,
                    effectiveAt, status, doctorName, sourcePriority, keywords, documentName, updatedAt, content,
                    preview, score, vectorScore, retrievalType, metadata);
        }
    }

    private record SearchProfile(int keywordTopK, int vectorTopK, int finalTopK, double vectorMinScore) {
    }
}
