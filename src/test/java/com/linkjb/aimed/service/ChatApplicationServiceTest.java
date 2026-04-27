package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.ChatProviderConfigResponse;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalQueryRewriteInfo;
import com.linkjb.aimed.config.skywalk.TraceIdProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatApplicationServiceTest {

    @Test
    void shouldMapLegacyOnlineProviderToFastProvider() {
        ChatApplicationService service = new ChatApplicationService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TraceIdProvider(),
                new ChatIntentAnalysisService(),
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                ChatApplicationService.LOCAL_OMLX,
                true,
                true
        );

        assertEquals(ChatApplicationService.QWEN_ONLINE_FAST, service.normalizeProvider(ChatApplicationService.QWEN_ONLINE));
        assertEquals(ChatApplicationService.QWEN_ONLINE_FAST, service.normalizeProvider(ChatApplicationService.QWEN_ONLINE_FAST));
        assertEquals(ChatApplicationService.QWEN_ONLINE_DEEP, service.normalizeProvider(ChatApplicationService.QWEN_ONLINE_DEEP));
        assertEquals(ChatApplicationService.LOCAL_OMLX, service.normalizeProvider(ChatApplicationService.LOCAL_OLLAMA));
        assertEquals(ChatApplicationService.LOCAL_OMLX, service.normalizeProvider(ChatApplicationService.LOCAL_OMLX));
    }

    @Test
    void shouldExposeFastAndDeepOnlineProviders() {
        ChatApplicationService service = new ChatApplicationService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TraceIdProvider(),
                new ChatIntentAnalysisService(),
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                ChatApplicationService.QWEN_ONLINE,
                true,
                true
        );

        ChatProviderConfigResponse response = service.providerConfig();

        assertEquals(ChatApplicationService.QWEN_ONLINE_FAST, response.defaultProvider());
        assertEquals(
                List.of(
                        ChatApplicationService.LOCAL_OMLX,
                        ChatApplicationService.QWEN_ONLINE_FAST,
                        ChatApplicationService.QWEN_ONLINE_DEEP
                ),
                response.enabledProviders()
        );
    }

    @Test
    void shouldUseStructuredSafeFallbackForLocalChildFeverQuestion() {
        ChatApplicationService service = new ChatApplicationService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TraceIdProvider(),
                new ChatIntentAnalysisService(),
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                ChatApplicationService.LOCAL_OMLX,
                true,
                true
        );
        HybridKnowledgeRetrieverService.RetrievalSummary summary = new HybridKnowledgeRetrieverService.RetrievalSummary(
                new KnowledgeRetrievalQueryRewriteInfo(
                        "小孩子感冒发烧怎么处理比较好",
                        "小孩子 感冒 发烧 怎么处理",
                        "RULE",
                        true,
                        List.of(),
                        List.of("感冒", "发烧"),
                        null,
                        false,
                        2
                ),
                "GENERAL",
                1,
                1,
                1,
                false,
                12,
                List.of(),
                List.of(),
                List.of(new HybridKnowledgeRetrieverService.RetrievedChunk(
                        "hash-1",
                        "hash-1-segment-1",
                        1,
                        "国家卫健委-流行性感冒诊疗方案-2025",
                        "GUIDE",
                        "GENERAL",
                        "BOTH",
                        "2025",
                        null,
                        "PUBLISHED",
                        null,
                        80,
                        "儿童 发热 咳嗽 流感",
                        "重症提示",
                        "SECTION",
                        "STRUCTURED",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "国家卫健委-流行性感冒诊疗方案-2025.pdf",
                        null,
                        "儿童符合下列任何一条：超高热或持续高热超过 3 天；呼吸急促……",
                        "儿童符合下列任何一条：超高热或持续高热超过 3 天；呼吸急促……",
                        1.0,
                        0.8,
                        "hybrid",
                        List.of(),
                        new dev.langchain4j.data.document.Metadata()
                )),
                new HybridKnowledgeRetrieverService.RetrievalTimings(1, 1, 1, 3, "DONE"),
                List.of(),
                List.of("发烧"),
                List.of(),
                List.of()
        );

        String answer = service.fallbackAnswerFromCitations(summary, "小孩子感冒发烧怎么处理比较好");

        assertTrue(answer.contains("结论："));
        assertTrue(answer.contains("建议："));
        assertTrue(answer.contains("何时尽快就医："));
        assertTrue(answer.contains("说明："));
        assertTrue(answer.contains("儿童"));
        assertTrue(answer.contains("国家卫健委-流行性感冒诊疗方案-2025"));
        assertFalse(answer.contains("根据已命中的知识资料，先给你一个简要总结"));
        assertFalse(answer.contains("切换到千问在线"));
        assertFalse(answer.contains("儿童符合下列任何一条"));
    }

    @Test
    void shouldBuildMcpSummaryPromptWithoutSecondToolCallInstruction() {
        ChatApplicationService service = new ChatApplicationService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TraceIdProvider(),
                new ChatIntentAnalysisService(),
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                ChatApplicationService.LOCAL_OMLX,
                true,
                true
        );

        String prompt = service.buildMcpSummaryPrompt("杭州明天会下雨吗", "status=SUCCESS\nmessage=有小雨");

        assertTrue(prompt.contains("不要再次调用任何工具"));
        assertTrue(prompt.contains("MCP 工具执行结果"));
        assertTrue(prompt.contains("有小雨"));
    }
}
