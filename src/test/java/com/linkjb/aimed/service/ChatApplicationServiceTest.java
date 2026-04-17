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
                new ObjectMapper(),
                ChatApplicationService.LOCAL_OLLAMA,
                true,
                true
        );

        assertEquals(ChatApplicationService.QWEN_ONLINE_FAST, service.normalizeProvider(ChatApplicationService.QWEN_ONLINE));
        assertEquals(ChatApplicationService.QWEN_ONLINE_FAST, service.normalizeProvider(ChatApplicationService.QWEN_ONLINE_FAST));
        assertEquals(ChatApplicationService.QWEN_ONLINE_DEEP, service.normalizeProvider(ChatApplicationService.QWEN_ONLINE_DEEP));
        assertEquals(ChatApplicationService.LOCAL_OLLAMA, service.normalizeProvider(ChatApplicationService.LOCAL_OLLAMA));
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
                new ObjectMapper(),
                ChatApplicationService.QWEN_ONLINE,
                true,
                true
        );

        ChatProviderConfigResponse response = service.providerConfig();

        assertEquals(ChatApplicationService.QWEN_ONLINE_FAST, response.defaultProvider());
        assertEquals(
                List.of(
                        ChatApplicationService.LOCAL_OLLAMA,
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
                new ObjectMapper(),
                ChatApplicationService.LOCAL_OLLAMA,
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
                new HybridKnowledgeRetrieverService.RetrievalTimings(1, 1, 1, false),
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
    void shouldBuildDynamicMcpRoutingMessageWithoutHardCodedToolCall() {
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
                new ObjectMapper(),
                ChatApplicationService.LOCAL_OLLAMA,
                true,
                true
        );

        String routed = service.buildRoutedMessageForTest(
                "杭州明天会下雨吗",
                new ChatIntentAnalysisService.ChatIntentResult(
                        "MCP_WEATHER",
                        "MCP",
                        false,
                        "天气类问题走动态 MCP 工具，不需要知识库检索",
                        "天气类问题走动态 MCP 工具，不需要知识库检索",
                        0.95
                )
        );

        assertTrue(routed.contains("动态 MCP 工具上下文"));
        assertTrue(routed.contains("调用MCP工具"));
        assertTrue(routed.contains("toolName 使用清单里的 tool 名称"));
        assertFalse(routed.contains("callEnabledToolForAgent"));
    }
}
