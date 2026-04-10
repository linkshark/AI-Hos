package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.KnowledgeRetrievalDiagnosticResponse;
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
                8, 8, 6, 0.35,
                4, 4, 3, 0.55
        );

        KnowledgeRetrievalDiagnosticResponse response = service.diagnose("李兰娟院士挂号 2024 指南", "ONLINE");

        assertEquals("ONLINE", response.profile());
        assertFalse(response.emptyRecall());
        assertTrue(response.keywordTokens().contains("李兰娟"));
        assertTrue(response.keywordTokens().contains("2024年"));
        assertTrue(response.booleanQuery().contains("2024"));
        assertEquals(1, response.keywordHits().size());
        assertEquals(0, response.vectorHits().size());
        assertEquals(1, response.finalHits().size());
        assertEquals("keyword", response.finalHits().get(0).retrievalType());
    }

    @Test
    void shouldSkipVectorForStrongOnlineKeywordQuery() {
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
                8, 8, 6, 0.35,
                4, 4, 3, 0.55
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("李兰娟院士挂号 2024 指南", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);

        assertEquals(0, embedInvocations.get());
        assertEquals(0, summary.retrievedCountVector());
        assertEquals(1, summary.retrievedCountKeyword());
        assertFalse(summary.emptyRecall());
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
                8, 8, 6, 0.35,
                4, 4, 3, 0.55
        );

        HybridKnowledgeRetrieverService.RetrievalSummary summary =
                service.search("腹痛怎么办", HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);

        assertEquals(1, embedInvocations.get());
        assertEquals(1, summary.retrievedCountVector());
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
}
