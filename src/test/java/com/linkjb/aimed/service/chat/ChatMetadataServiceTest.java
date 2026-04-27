package com.linkjb.aimed.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.chat.ChatStreamMetadata;
import com.linkjb.aimed.entity.dto.response.chat.ChatTraceStage;
import com.linkjb.aimed.service.ChatIntentAnalysisService;
import com.linkjb.aimed.service.HybridKnowledgeRetrieverService;
import com.linkjb.aimed.service.retrieval.RetrievalSummaryStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMetadataServiceTest {

    @Test
    void shouldExposeRetrievalParallelTraceStages() {
        HybridKnowledgeRetrieverService retriever = stubRetriever();
        ChatMetadataService service = new ChatMetadataService(new ObjectMapper(), retriever);
        HybridKnowledgeRetrieverService.RetrievalSummary summary = new HybridKnowledgeRetrieverService.RetrievalSummary(
                null,
                "GENERAL",
                2,
                0,
                2,
                false,
                51,
                List.of(),
                List.of(),
                List.of(),
                new HybridKnowledgeRetrieverService.RetrievalTimings(12, 34, 5, 51, "DEGRADED"),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        ChatStreamMetadata metadata = service.buildAndRemember(
                "trace-1",
                "LOCAL_OMLX",
                System.nanoTime(),
                0,
                false,
                120,
                "STANDARD",
                new ChatIntentAnalysisService.ChatIntentResult("MEDICAL_QA", "RAG", true, "需要知识检索", "", 0.95d),
                summary
        );

        assertNotNull(metadata);
        assertEquals("trace-1", metadata.getTraceId());
        assertStageKeys(metadata.getTraceStages(), "retrieve_total", "retrieve_keyword", "retrieve_vector", "retrieve_merge", "server_total");

        ChatTraceStage vectorStage = metadata.getTraceStages().stream()
                .filter(stage -> "retrieve_vector".equals(stage.getKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("DEGRADED", vectorStage.getStatus());
        assertTrue(vectorStage.getDetail().contains("降级"));
    }

    @Test
    void shouldMarkRetrievalSkippedWhenRagIsNotApplied() {
        HybridKnowledgeRetrieverService retriever = stubRetriever();
        ChatMetadataService service = new ChatMetadataService(new ObjectMapper(), retriever);

        ChatStreamMetadata metadata = service.buildAndRemember(
                "trace-2",
                "QWEN_ONLINE_FAST",
                System.nanoTime(),
                0,
                false,
                35,
                "MCP",
                new ChatIntentAnalysisService.ChatIntentResult("TOOL", "MCP", false, "MCP 请求跳过检索", "工具问题", 0.95d),
                null
        );

        ChatTraceStage retrieveStage = metadata.getTraceStages().stream()
                .filter(stage -> "retrieve_total".equals(stage.getKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("SKIPPED", retrieveStage.getStatus());
        assertEquals("MCP 请求跳过检索", retrieveStage.getDetail());
    }

    private void assertStageKeys(List<ChatTraceStage> stages, String... keys) {
        List<String> actualKeys = stages.stream().map(ChatTraceStage::getKey).toList();
        for (String key : keys) {
            assertTrue(actualKeys.contains(key), () -> "missing trace stage: " + key);
        }
    }

    private HybridKnowledgeRetrieverService stubRetriever() {
        return new HybridKnowledgeRetrieverService(
                null,
                null,
                null,
                null,
                new RetrievalSummaryStore(),
                Runnable::run,
                8,
                8,
                6,
                0.35,
                4,
                true,
                false,
                true,
                1500L,
                300_000L
        );
    }
}
