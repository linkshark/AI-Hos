package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.KnowledgeFileStatus;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridKnowledgeRetrieverServiceTest {

    private static KnowledgeFileStatusService statusService() {
        return new KnowledgeFileStatusService(null) {
            @Override
            public KnowledgeFileStatus findByHash(String hash) {
                KnowledgeFileStatus status = new KnowledgeFileStatus();
                status.setHash(hash);
                status.setCurrentGeneration(1);
                return status;
            }
        };
    }

    @Test
    void shouldExposeDeterministicDiagnosticResult() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
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
                statusService(),
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
                statusService(),
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
    void shouldUseDedicatedSearchExecutorForKeywordAndVectorRecall() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));
        AtomicInteger dispatchCount = new AtomicInteger();
        Executor executor = command -> {
            dispatchCount.incrementAndGet();
            command.run();
        };

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
                embeddingStore,
                embeddingModel,
                executor,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("腹痛怎么办", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);

        assertFalse(summary.emptyRecall());
        assertTrue(dispatchCount.get() >= 2);
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
                statusService(),
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
                statusService(),
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
    void shouldPreferMycoplasmaGuideOverGenericInfluenzaContent() {
        JdbcTemplate jdbcTemplate = new FallbackJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new EmptyEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("肺炎支原体感染有什么表现", HybridKnowledgeRetrieverService.RetrievalProfile.LOCAL);

        assertFalse(summary.finalHits().isEmpty());
        assertEquals("国家卫健委-儿童肺炎支原体肺炎诊疗指南-2025.pdf", summary.finalHits().get(0).documentName());
        assertTrue(summary.finalHits().get(0).scoreBreakdown().stream()
                .anyMatch(score -> "medical_anchor_metadata_match".equals(score.key())));
    }

    @Test
    void shouldDemoteLowInformationChunkEvenIfKeywordMatches() {
        JdbcTemplate jdbcTemplate = new LowInformationJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new EmptyEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("我感冒咳嗽了怎么办", HybridKnowledgeRetrieverService.RetrievalProfile.LOCAL);

        assertFalse(summary.finalHits().isEmpty());
        assertEquals("国家卫健委-流行性感冒诊疗方案-2025.pdf", summary.finalHits().get(0).documentName());
        assertTrue(summary.finalHits().stream().noneMatch(hit -> "noise-fragment".equals(hit.fileHash())));
    }

    @Test
    void shouldFallbackCitationSnippetToContentWhenPreviewIsMissing() {
        JdbcTemplate jdbcTemplate = new MissingPreviewJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new EmptyEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("肺炎支原体感染有什么表现", HybridKnowledgeRetrieverService.RetrievalProfile.LOCAL);

        assertFalse(service.toChatStreamMetadata(summary).getCitations().get(0).getSnippet().isBlank());
        assertTrue(service.toChatStreamMetadata(summary).getCitations().get(0).getSnippet().contains("发热"));
    }

    @Test
    void shouldIgnoreHeadingOnlyPreviewWhenBuildingCitationSnippet() {
        JdbcTemplate jdbcTemplate = new HeadingOnlyPreviewJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new EmptyEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("原发性肝癌怎么治疗", HybridKnowledgeRetrieverService.RetrievalProfile.LOCAL);

        String snippet = service.toChatStreamMetadata(summary).getCitations().get(0).getSnippet();
        assertFalse(snippet.contains("其他:"));
        assertTrue(snippet.contains("手术切除"));
    }

    @Test
    void shouldConsumeSummaryByMemoryIdAndCurrentRawQuery() {
        JdbcTemplate jdbcTemplate = new FallbackJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new EmptyEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
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

    @Test
    void shouldReuseEmbeddingCacheForSameEffectiveQuery() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        AtomicInteger embedInvocations = new AtomicInteger();
        EmbeddingModel embeddingModel = segments -> {
            embedInvocations.incrementAndGet();
            return Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));
        };

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4
        );

        service.search("腹痛怎么办", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);
        service.search("腹痛怎么办", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);

        assertEquals(1, embedInvocations.get());
    }

    @Test
    void shouldDegradeToKeywordOnlyWhenVectorSearchTimesOutInFastMode() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        AtomicBoolean vectorStarted = new AtomicBoolean(false);
        EmbeddingModel embeddingModel = segments -> {
            vectorStarted.set(true);
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));
        };

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4,
                true, false, true, 50L, 300_000L
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("李兰娟院士挂号 2024 指南", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);

        assertTrue(vectorStarted.get());
        assertTrue(summary.timings().vectorSkipped());
        assertEquals(0, summary.retrievedCountVector());
        assertEquals(1, summary.retrievedCountKeyword());
        assertFalse(summary.emptyRecall());
    }

    @Test
    void diagnosticFinalHitsShouldUseSameFastPathAsChatSearch() {
        JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        EmbeddingStore<TextSegment> embeddingStore = new StubEmbeddingStore();
        EmbeddingModel embeddingModel = segments -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Response.from(List.of(Embedding.from(new float[]{0.2f, 0.3f})));
        };

        HybridKnowledgeRetrieverService service = new HybridKnowledgeRetrieverService(
                jdbcTemplate,
                statusService(),
                embeddingStore,
                embeddingModel,
                8, 8, 6, 0.35, 4,
                true, false, true, 50L, 300_000L
        );

        HybridKnowledgeRetrieverService.RetrievalSummary chatSummary =
                service.search("李兰娟院士挂号 2024 指南", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);
        KnowledgeRetrievalDiagnosticResponse diagnostic =
                service.diagnose("李兰娟院士挂号 2024 指南", "ONLINE");

        assertEquals(chatSummary.finalHits().get(0).segmentId(), diagnostic.finalHits().get(0).segmentId());
        assertEquals(chatSummary.retrievedCountVector(), diagnostic.vectorHits().size());
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
            metadata.setColumnCount(26);
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
            metadata.setColumnName(16, "section_title");
            metadata.setColumnType(16, Types.VARCHAR);
            metadata.setColumnName(17, "segment_kind");
            metadata.setColumnType(17, Types.VARCHAR);
            metadata.setColumnName(18, "segmentation_mode");
            metadata.setColumnType(18, Types.VARCHAR);
            metadata.setColumnName(19, "semantic_summary");
            metadata.setColumnType(19, Types.VARCHAR);
            metadata.setColumnName(20, "semantic_keywords");
            metadata.setColumnType(20, Types.VARCHAR);
            metadata.setColumnName(21, "section_role");
            metadata.setColumnType(21, Types.VARCHAR);
            metadata.setColumnName(22, "medical_entities_json");
            metadata.setColumnType(22, Types.VARCHAR);
            metadata.setColumnName(23, "target_questions_json");
            metadata.setColumnType(23, Types.VARCHAR);
            metadata.setColumnName(24, "original_filename");
            metadata.setColumnType(24, Types.VARCHAR);
            metadata.setColumnName(25, "updated_at");
            metadata.setColumnType(25, Types.TIMESTAMP);
            metadata.setColumnName(26, "ft_score");
            metadata.setColumnType(26, Types.DOUBLE);
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
            rowSet.updateString("section_title", "挂号指引");
            rowSet.updateString("segment_kind", "SECTION");
            rowSet.updateString("segmentation_mode", "STRUCTURED");
            rowSet.updateString("semantic_summary", "挂号流程与门诊安排说明");
            rowSet.updateString("semantic_keywords", "挂号 流程 门诊 李兰娟");
            rowSet.updateString("section_role", "挂号流程");
            rowSet.updateString("medical_entities_json", "[\"李兰娟\"]");
            rowSet.updateString("target_questions_json", "[\"李兰娟怎么挂号\"]");
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
            metadata.put("section_title", "挂号指引");
            metadata.put("segment_kind", "SECTION");
            metadata.put("segmentation_mode", "STRUCTURED");
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
            RowSetMetaDataImpl metadata = baseMetadata(25);
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
                    "治疗方案",
                    "SECTION",
                    "STRUCTURED",
                    "原发性肝癌指南_2024.pdf",
                    LocalDateTime.of(2026, 4, 8, 20, 3));
            insertRow(rowSet,
                    "pneumonia-guide",
                    "pneumonia-guide-segment-1",
                    8,
                    "肺炎支原体肺炎的临床表现以发热、刺激性干咳为主，部分患者可伴咽痛、乏力和肺部影像学异常。",
                    "肺炎支原体肺炎常见表现为发热和刺激性干咳",
                    "国家卫健委-儿童肺炎支原体肺炎诊疗指南-2025.pdf",
                    "GUIDE",
                    "GENERAL",
                    "BOTH",
                    "2025年版",
                    "PUBLISHED",
                    null,
                    100,
                    "肺炎支原体肺炎 肺炎支原体感染 支原体肺炎 指南 临床表现",
                    "临床表现",
                    "SECTION",
                    "STRUCTURED",
                    "国家卫健委-儿童肺炎支原体肺炎诊疗指南-2025.pdf",
                    LocalDateTime.of(2026, 4, 11, 20, 48));
            rowSet.beforeFirst();
            return rowSet;
        }
    }

    private static final class LowInformationJdbcTemplate extends JdbcTemplate {
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("MATCH(k.title")) {
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
            rowSet.setMetaData(baseMetadata(25));
            insertRow(rowSet,
                    "noise-fragment",
                    "noise-fragment-segment-1",
                    1,
                    "1",
                    "1",
                    "国家卫健委-流行性感冒诊疗方案-2025.pdf",
                    "GUIDE",
                    "GENERAL",
                    "BOTH",
                    "2025年版",
                    "PUBLISHED",
                    null,
                    100,
                    "感冒 咳嗽",
                    null,
                    "SECTION",
                    "STRUCTURED",
                    "国家卫健委-流行性感冒诊疗方案-2025.pdf",
                    LocalDateTime.of(2026, 4, 24, 12, 0));
            insertRow(rowSet,
                    "cold-guide",
                    "cold-guide-segment-1",
                    12,
                    "普通感冒以上呼吸道卡他症状为主要表现，可有咳嗽、鼻塞、流涕，多数以对症支持治疗为主。",
                    "普通感冒以上呼吸道卡他症状为主要表现，可有咳嗽、鼻塞、流涕",
                    "国家卫健委-流行性感冒诊疗方案-2025.pdf",
                    "GUIDE",
                    "GENERAL",
                    "BOTH",
                    "2025年版",
                    "PUBLISHED",
                    null,
                    80,
                    "感冒 咳嗽 普通感冒 上呼吸道感染",
                    "普通感冒",
                    "SECTION",
                    "STRUCTURED",
                    "国家卫健委-流行性感冒诊疗方案-2025.pdf",
                    LocalDateTime.of(2026, 4, 24, 12, 1));
            rowSet.beforeFirst();
            return rowSet;
        }
    }

    private static final class MissingPreviewJdbcTemplate extends JdbcTemplate {
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("MATCH(k.title")) {
                return List.of();
            }
            try {
                CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
                rowSet.setMetaData(baseMetadata(25));
                insertRow(rowSet,
                        "missing-preview-guide",
                        "missing-preview-guide-segment-1",
                        8,
                        "肺炎支原体肺炎的临床表现以发热、刺激性干咳为主，部分患者可伴咽痛、乏力和肺部影像学异常。",
                        null,
                        "国家卫健委-儿童肺炎支原体肺炎诊疗指南-2025.pdf",
                        "GUIDE",
                        "GENERAL",
                        "BOTH",
                        "2025年版",
                        "PUBLISHED",
                        null,
                        100,
                        "肺炎支原体肺炎 肺炎支原体感染 支原体肺炎 指南 临床表现",
                        "临床表现",
                        "SECTION",
                        "STRUCTURED",
                        "国家卫健委-儿童肺炎支原体肺炎诊疗指南-2025.pdf",
                        LocalDateTime.of(2026, 4, 11, 20, 48));
                rowSet.beforeFirst();
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
    }

    private static final class HeadingOnlyPreviewJdbcTemplate extends JdbcTemplate {
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("MATCH(k.title")) {
                return List.of();
            }
            try {
                CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
                rowSet.setMetaData(baseMetadata(25));
                insertRow(rowSet,
                        "heading-only-preview-guide",
                        "heading-only-preview-guide-segment-1",
                        16,
                        "手术切除是早期肝癌的首选根治性方案，中晚期或不可切除患者可考虑经动脉介入治疗、消融、放疗或系统治疗。",
                        "其他:",
                        "原发性肝癌指南_2024.pdf",
                        "GUIDE",
                        "GENERAL",
                        "BOTH",
                        "2024年版",
                        "PUBLISHED",
                        null,
                        100,
                        "原发性肝癌 肝癌 指南 治疗 手术切除",
                        "其他",
                        "SECTION",
                        "STRUCTURED",
                        "原发性肝癌指南_2024.pdf",
                        LocalDateTime.of(2026, 4, 24, 17, 49));
                rowSet.beforeFirst();
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
        if (columnCount >= 16) {
            metadata.setColumnName(16, "section_title");
            metadata.setColumnType(16, Types.VARCHAR);
        }
        if (columnCount >= 17) {
            metadata.setColumnName(17, "segment_kind");
            metadata.setColumnType(17, Types.VARCHAR);
        }
        if (columnCount >= 18) {
            metadata.setColumnName(18, "segmentation_mode");
            metadata.setColumnType(18, Types.VARCHAR);
        }
        if (columnCount >= 19) {
            metadata.setColumnName(19, "semantic_summary");
            metadata.setColumnType(19, Types.VARCHAR);
        }
        if (columnCount >= 20) {
            metadata.setColumnName(20, "semantic_keywords");
            metadata.setColumnType(20, Types.VARCHAR);
        }
        if (columnCount >= 21) {
            metadata.setColumnName(21, "section_role");
            metadata.setColumnType(21, Types.VARCHAR);
        }
        if (columnCount >= 22) {
            metadata.setColumnName(22, "medical_entities_json");
            metadata.setColumnType(22, Types.VARCHAR);
        }
        if (columnCount >= 23) {
            metadata.setColumnName(23, "target_questions_json");
            metadata.setColumnType(23, Types.VARCHAR);
        }
        if (columnCount >= 24) {
            metadata.setColumnName(24, "original_filename");
            metadata.setColumnType(24, Types.VARCHAR);
        }
        if (columnCount >= 25) {
            metadata.setColumnName(25, "updated_at");
            metadata.setColumnType(25, Types.TIMESTAMP);
        }
        if (columnCount >= 26) {
            metadata.setColumnName(26, "ft_score");
            metadata.setColumnType(26, Types.DOUBLE);
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
                                  String sectionTitle,
                                  String segmentKind,
                                  String segmentationMode,
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
        rowSet.updateString("section_title", sectionTitle);
        rowSet.updateString("segment_kind", segmentKind);
        rowSet.updateString("segmentation_mode", segmentationMode);
        rowSet.updateString("semantic_summary", preview);
        rowSet.updateString("semantic_keywords", keywords);
        rowSet.updateString("section_role", "通用");
        rowSet.updateString("medical_entities_json", "[]");
        rowSet.updateString("target_questions_json", "[]");
        rowSet.updateString("original_filename", originalFilename);
        rowSet.updateTimestamp("updated_at", Timestamp.valueOf(updatedAt));
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
    }
}
