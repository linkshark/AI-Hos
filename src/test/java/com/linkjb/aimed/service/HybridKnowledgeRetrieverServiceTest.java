package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalDiagnosticResponse;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridKnowledgeRetrieverServiceTest {

    @Test
    void shouldExposeDeterministicDiagnosticResult() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        KnowledgeRetrievalDiagnosticResponse response = service.diagnose("李兰娟院士挂号 2024 指南", "ONLINE");

        assertEquals("ONLINE", response.profile());
        assertFalse(response.emptyRecall());
        assertTrue(response.keywordTokens().contains("李兰娟"));
        assertTrue(response.keywordTokens().contains("2024年"));
        assertTrue(response.booleanQuery().contains("2024"));
        assertEquals(1, response.keywordHits().size());
        assertEquals(1, response.vectorHits().size());
        assertEquals(1, response.finalHits().size());
        assertEquals("hybrid", response.finalHits().get(0).retrievalType());
        assertFalse(response.scoringRules().isEmpty());
        assertFalse(response.finalHits().get(0).scoreBreakdown().isEmpty());
        assertEquals(response.normalizedQuery(), response.effectiveQuery());
        assertTrue(response.matchedDiseaseEntities().isEmpty());
    }

    @Test
    void shouldAlwaysUseVectorForStrongOnlineKeywordQuery() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        AtomicInteger embedInvocations = new AtomicInteger();
        EmbeddingModel embeddingModel = segments -> {
            embedInvocations.incrementAndGet();
            return Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));
        };

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("李兰娟院士挂号 2024 指南", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);

        assertEquals(1, embedInvocations.get());
        assertEquals(1, summary.retrievedCountVector());
        assertEquals(1, summary.retrievedCountKeyword());
        assertFalse(summary.emptyRecall());
        assertFalse(summary.timings().vectorSkipped());
        assertTrue(summary.finalHits().size() <= 6);
    }

    @Test
    void shouldKeepVectorForGeneralOnlineQuery() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        AtomicInteger embedInvocations = new AtomicInteger();
        EmbeddingModel embeddingModel = segments -> {
            embedInvocations.incrementAndGet();
            return Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));
        };

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("腹痛怎么办", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);

        assertEquals(1, embedInvocations.get());
        assertEquals(1, summary.retrievedCountVector());
    }

    @Test
    void shouldKeepHybridRecallButUseSmallerLocalFinalWindow() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        AtomicInteger embedInvocations = new AtomicInteger();
        EmbeddingModel embeddingModel = segments -> {
            embedInvocations.incrementAndGet();
            return Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));
        };

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("李兰娟院士挂号 2024 指南", HybridKnowledgeRetrieverService.RetrievalProfile.LOCAL);

        assertEquals(1, embedInvocations.get());
        assertEquals(1, summary.retrievedCountVector());
        assertEquals(1, summary.retrievedCountKeyword());
        assertFalse(summary.timings().vectorSkipped());
        assertTrue(summary.finalHits().size() <= 4);
    }

    @Test
    void shouldPreferCoreCancerGuideOverGenericTreatmentFallbackHits() {
        JdbcTemplate jdbcTemplate = new FallbackJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new EmptyEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("原发性早期肝癌应该如何治疗", HybridKnowledgeRetrieverService.RetrievalProfile.LOCAL);

        assertEquals(1, summary.retrievedCountKeyword());
        assertFalse(summary.finalHits().isEmpty());
        assertEquals("原发性肝癌指南_2024.pdf", summary.finalHits().get(0).documentName());
        assertTrue(summary.finalHits().get(0).scoreBreakdown().stream()
                .anyMatch(score -> "core_phrase".equals(score.key())));
    }

    @Test
    void shouldConsumeSummaryByMemoryIdAndCurrentRawQuery() {
        JdbcTemplate jdbcTemplate = new FallbackJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new EmptyEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );
        UserMessage currentMessage = UserMessage.from("当前问题");

        service.retrieve(Query.from("我感冒咳嗽了怎么办", Metadata.from(currentMessage, 1001L, List.of())),
                HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);
        service.retrieve(Query.from("早期肝癌怎么治疗", Metadata.from(currentMessage, 1001L, List.of())),
                HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);

        HybridKnowledgeRetrieverService.RetrievalSummary liverSummary =
                service.consumeLastSummary(1001L, "早期肝癌怎么治疗");
        HybridKnowledgeRetrieverService.RetrievalSummary coldSummary =
                service.consumeLastSummary(1001L, "我感冒咳嗽了怎么办");

        assertEquals("早期肝癌怎么治疗", liverSummary.rewriteInfo().rawQuery());
        assertEquals("我感冒咳嗽了怎么办", coldSummary.rewriteInfo().rawQuery());
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (!sql.contains("MATCH(k.title")) {
                return List.of();
            }
            try {
                CachedRowSet rowSet = buildRowSet();
                List<T> results = new ArrayList<>();
                int rowNum = 0;
                while (rowSet.next()) {
                    results.add(rowMapper.mapRow(rowSet, rowNum++));
                }
                return results;
            } catch (Exception exception) {
                throw new IllegalStateException("stub jdbc failure", exception);
            }
        }

        private CachedRowSet buildRowSet() throws SQLException {
            CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
            RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
            metadata.setColumnCount(18);
            metadata.setColumnName(1, "file_hash");
            metadata.setColumnType(1, Types.VARCHAR);
            metadata.setColumnName(2, "segment_id");
            metadata.setColumnType(2, Types.VARCHAR);
            metadata.setColumnName(3, "segment_index");
            metadata.setColumnType(3, Types.INTEGER);
            metadata.setColumnName(4, "content");
            metadata.setColumnType(4, Types.VARCHAR);
            metadata.setColumnName(5, "preview");
            metadata.setColumnType(5, Types.VARCHAR);
            metadata.setColumnName(6, "title");
            metadata.setColumnType(6, Types.VARCHAR);
            metadata.setColumnName(7, "doc_type");
            metadata.setColumnType(7, Types.VARCHAR);
            metadata.setColumnName(8, "department");
            metadata.setColumnType(8, Types.VARCHAR);
            metadata.setColumnName(9, "audience");
            metadata.setColumnType(9, Types.VARCHAR);
            metadata.setColumnName(10, "version");
            metadata.setColumnType(10, Types.VARCHAR);
            metadata.setColumnName(11, "effective_at");
            metadata.setColumnType(11, Types.TIMESTAMP);
            metadata.setColumnName(12, "status");
            metadata.setColumnType(12, Types.VARCHAR);
            metadata.setColumnName(13, "doctor_name");
            metadata.setColumnType(13, Types.VARCHAR);
            metadata.setColumnName(14, "source_priority");
            metadata.setColumnType(14, Types.INTEGER);
            metadata.setColumnName(15, "keywords");
            metadata.setColumnType(15, Types.VARCHAR);
            metadata.setColumnName(16, "original_filename");
            metadata.setColumnType(16, Types.VARCHAR);
            metadata.setColumnName(17, "updated_at");
            metadata.setColumnType(17, Types.TIMESTAMP);
            metadata.setColumnName(18, "ft_score");
            metadata.setColumnType(18, Types.DOUBLE);
            rowSet.setMetaData(metadata);

            rowSet.moveToInsertRow();
            rowSet.updateString("file_hash", "hash-1");
            rowSet.updateString("segment_id", "hash-1-segment-1");
            rowSet.updateInt("segment_index", 1);
            rowSet.updateString("content", "李兰娟院士门诊挂号指南 2024年版");
            rowSet.updateString("preview", "李兰娟院士门诊挂号指南 2024年版");
            rowSet.updateString("title", "李兰娟院士门诊挂号指南");
            rowSet.updateString("doc_type", "GUIDE");
            rowSet.updateString("department", "肝胆胰");
            rowSet.updateString("audience", "BOTH");
            rowSet.updateString("version", "2024年版");
            rowSet.updateTimestamp("effective_at", Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 0, 0)));
            rowSet.updateString("status", "PUBLISHED");
            rowSet.updateString("doctor_name", "李兰娟");
            rowSet.updateInt("source_priority", 90);
            rowSet.updateString("keywords", "李兰娟 挂号 指南 2024年版");
            rowSet.updateString("original_filename", "李兰娟院士门诊挂号指南.md");
            rowSet.updateTimestamp("updated_at", Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 10, 0)));
            rowSet.updateDouble("ft_score", 4.2d);
            rowSet.insertRow();
            rowSet.moveToCurrentRow();
            rowSet.beforeFirst();
            return rowSet;
        }
    }

    private static final class StubEmbeddingStore implements EmbeddingStore<TextSegment> {

        @Override
        public String add(Embedding embedding) {
            return "stub";
        }

        @Override
        public void add(String id, Embedding embedding) {
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            return "stub";
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return List.of("stub");
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
            metadata.put("knowledge_hash", "hash-1");
            metadata.put("status", "PUBLISHED");
            metadata.put("doc_type", "GUIDE");
            metadata.put("department", "肝胆胰");
            metadata.put("audience", "BOTH");
            metadata.put("version", "2024年版");
            metadata.put("title", "李兰娟院士门诊挂号指南");
            metadata.put("doctor_name", "李兰娟");
            metadata.put("source_priority", 90);
            metadata.put("keywords", "李兰娟 挂号 指南 2024年版");
            metadata.put("document_name", "李兰娟院士门诊挂号指南.md");
            metadata.put("updated_at", "2025-01-01T10:00:00");
            metadata.put("effective_at", "2024-01-01T00:00:00");
            metadata.put("segment_index", "1");
            EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                    0.82,
                    "hash-1-segment-1",
                    null,
                    TextSegment.from("李兰娟院士门诊挂号指南 2024年版", metadata)
            );
            return new EmbeddingSearchResult<>(List.of(match));
        }
    }

    private static final class FallbackJdbcTemplate extends JdbcTemplate {
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("MATCH(k.title")) {
                return List.of();
            }
            try {
                CachedRowSet rowSet = buildFallbackRowSet();
                List<T> results = new ArrayList<>();
                int rowNum = 0;
                while (rowSet.next()) {
                    results.add(rowMapper.mapRow(rowSet, rowNum++));
                }
                return results;
            } catch (Exception exception) {
                throw new IllegalStateException("stub jdbc failure", exception);
            }
        }

        private CachedRowSet buildFallbackRowSet() throws SQLException {
            CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
            RowSetMetaDataImpl metadata = baseMetadata(17);
            rowSet.setMetaData(metadata);
            insertRow(rowSet,
                    "liver-guide",
                    "liver-guide-segment-1",
                    24,
                    "早期肝癌患者接受腹腔镜肝切除术与开腹手术的5年OS相当，可结合肝功能、肿瘤数量和大小选择手术切除、消融或肝移植。",
                    "早期肝癌患者接受腹腔镜肝切除术与开腹手术的5年OS相当",
                    "原发性肝癌指南_2024.pdf",
                    "GUIDE",
                    "GENERAL",
                    "BOTH",
                    "2024年版",
                    "PUBLISHED",
                    null,
                    60,
                    "原发性肝癌 肝癌 指南 早期肝癌 治疗",
                    "原发性肝癌指南_2024.pdf",
                    LocalDateTime.of(2026, 4, 8, 20, 3));
            insertRow(rowSet,
                    "pneumonia-guide",
                    "pneumonia-guide-segment-1",
                    8,
                    "重症和危重症的早期预警指标提示存在发展为重症的风险，需要及时治疗。",
                    "重症和危重症的早期预警指标",
                    "国家卫健委-儿童肺炎支原体肺炎诊疗指南-2025.pdf",
                    "GUIDE",
                    "GENERAL",
                    "BOTH",
                    "2025年版",
                    "PUBLISHED",
                    null,
                    100,
                    "肺炎支原体肺炎 指南 早期 治疗",
                    "国家卫健委-儿童肺炎支原体肺炎诊疗指南-2025.pdf",
                    LocalDateTime.of(2026, 4, 11, 20, 48));
            rowSet.beforeFirst();
            return rowSet;
        }
    }

    private static final class EmptyEmbeddingStore implements EmbeddingStore<TextSegment> {
        @Override
        public String add(Embedding embedding) {
            return "stub";
        }

        @Override
        public void add(String id, Embedding embedding) {
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            return "stub";
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return List.of();
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            return new EmbeddingSearchResult<>(List.of());
        }
    }

    private static RowSetMetaDataImpl baseMetadata(int columnCount) throws SQLException {
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(columnCount);
        metadata.setColumnName(1, "file_hash");
        metadata.setColumnType(1, Types.VARCHAR);
        metadata.setColumnName(2, "segment_id");
        metadata.setColumnType(2, Types.VARCHAR);
        metadata.setColumnName(3, "segment_index");
        metadata.setColumnType(3, Types.INTEGER);
        metadata.setColumnName(4, "content");
        metadata.setColumnType(4, Types.VARCHAR);
        metadata.setColumnName(5, "preview");
        metadata.setColumnType(5, Types.VARCHAR);
        metadata.setColumnName(6, "title");
        metadata.setColumnType(6, Types.VARCHAR);
        metadata.setColumnName(7, "doc_type");
        metadata.setColumnType(7, Types.VARCHAR);
        metadata.setColumnName(8, "department");
        metadata.setColumnType(8, Types.VARCHAR);
        metadata.setColumnName(9, "audience");
        metadata.setColumnType(9, Types.VARCHAR);
        metadata.setColumnName(10, "version");
        metadata.setColumnType(10, Types.VARCHAR);
        metadata.setColumnName(11, "effective_at");
        metadata.setColumnType(11, Types.TIMESTAMP);
        metadata.setColumnName(12, "status");
        metadata.setColumnType(12, Types.VARCHAR);
        metadata.setColumnName(13, "doctor_name");
        metadata.setColumnType(13, Types.VARCHAR);
        metadata.setColumnName(14, "source_priority");
        metadata.setColumnType(14, Types.INTEGER);
        metadata.setColumnName(15, "keywords");
        metadata.setColumnType(15, Types.VARCHAR);
        metadata.setColumnName(16, "original_filename");
        metadata.setColumnType(16, Types.VARCHAR);
        metadata.setColumnName(17, "updated_at");
        metadata.setColumnType(17, Types.TIMESTAMP);
        if (columnCount >= 18) {
            metadata.setColumnName(18, "ft_score");
            metadata.setColumnType(18, Types.DOUBLE);
        }
        return metadata;
    }

    private static void insertRow(CachedRowSet rowSet,
                                  String fileHash,
                                  String segmentId,
                                  int segmentIndex,
                                  String content,
                                  String preview,
                                  String title,
                                  String docType,
                                  String department,
                                  String audience,
                                  String version,
                                  String status,
                                  String doctorName,
                                  int sourcePriority,
                                  String keywords,
                                  String originalFilename,
                                  LocalDateTime updatedAt) throws SQLException {
        rowSet.moveToInsertRow();
        rowSet.updateString("file_hash", fileHash);
        rowSet.updateString("segment_id", segmentId);
        rowSet.updateInt("segment_index", segmentIndex);
        rowSet.updateString("content", content);
        rowSet.updateString("preview", preview);
        rowSet.updateString("title", title);
        rowSet.updateString("doc_type", docType);
        rowSet.updateString("department", department);
        rowSet.updateString("audience", audience);
        rowSet.updateString("version", version);
        rowSet.updateTimestamp("effective_at", Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 0, 0)));
        rowSet.updateString("status", status);
        rowSet.updateString("doctor_name", doctorName);
        rowSet.updateInt("source_priority", sourcePriority);
        rowSet.updateString("keywords", keywords);
        rowSet.updateString("original_filename", originalFilename);
        rowSet.updateTimestamp("updated_at", Timestamp.valueOf(updatedAt));
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
    }
}
