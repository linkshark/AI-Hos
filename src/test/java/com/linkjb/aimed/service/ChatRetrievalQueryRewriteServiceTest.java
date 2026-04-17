package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalMatchedDiseaseEntity;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalQueryRewriteInfo;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRetrievalQueryRewriteServiceTest {

    @Test
    void shouldMergeComplaintContextForWeakFollowUp() {
        ChatRetrievalQueryRewriteService service = new ChatRetrievalQueryRewriteService(
                disabledModelService(),
                stubLookupService(),
                true,
                4,
                "RULE_ONLY"
        );

        KnowledgeRetrievalQueryRewriteInfo rewriteInfo = service.rewrite("我现在30岁 没有别的症状");

        KnowledgeRetrievalQueryRewriteInfo merged = service.rewriteInternalForTest(
                "我现在30岁 没有别的症状",
                List.of("我感冒了 我该吃点什么药")
        );

        assertEquals("我现在30岁 没有别的症状", rewriteInfo.rawQuery());
        assertTrue(merged.rewriteApplied());
        assertTrue(merged.effectiveQuery().contains("感冒"));
        assertTrue(merged.effectiveQuery().contains("30岁"));
        assertTrue(merged.effectiveQuery().contains("无其他症状"));
        assertTrue(merged.contextMessagesUsed().contains("我感冒了 我该吃点什么药"));
    }

    @Test
    void shouldNotPullMedicalComplaintIntoAppointmentIntent() {
        ChatRetrievalQueryRewriteService service = new ChatRetrievalQueryRewriteService(
                disabledModelService(),
                stubLookupService(),
                true,
                4,
                "RULE_ONLY"
        );

        KnowledgeRetrievalQueryRewriteInfo rewriteInfo = service.rewriteInternalForTest(
                "帮我挂个号吧",
                List.of("我感冒了 我该吃点什么药")
        );

        assertFalse(rewriteInfo.effectiveQuery().contains("感冒"));
        assertTrue(rewriteInfo.effectiveQuery().contains("挂号"));
    }

    @Test
    void shouldPreferCurrentDiseaseAnchorOverPreviousComplaintContext() {
        ChatRetrievalQueryRewriteService service = new ChatRetrievalQueryRewriteService(
                disabledModelService(),
                stubLookupService(),
                true,
                4,
                "RULE_ONLY"
        );

        KnowledgeRetrievalQueryRewriteInfo rewriteInfo = service.rewriteInternalForTest(
                "早期肝癌怎么治疗",
                List.of("我感冒了 我该吃点什么药")
        );

        assertTrue(rewriteInfo.effectiveQuery().contains("肝癌"));
        assertFalse(rewriteInfo.effectiveQuery().contains("感冒"));
        assertTrue(rewriteInfo.contextMessagesUsed().isEmpty());
    }

    private QueryRewriteModelService disabledModelService() {
        ChatModel noopModel = new ChatModel() {
        };
        return new QueryRewriteModelService(
                new ObjectMapper(),
                noopModel,
                noopModel,
                noopModel,
                false,
                "QWEN_ONLINE_FAST",
                "DIAGNOSTIC_ONLY"
        );
    }

    private MedicalStandardLookupService stubLookupService() {
        return new MedicalStandardLookupService(null, null, null, null) {
            @Override
            public List<KnowledgeRetrievalMatchedDiseaseEntity> matchDiseaseEntities(String query, int limit) {
                if (query != null && query.contains("感冒")) {
                    return List.of(new KnowledgeRetrievalMatchedDiseaseEntity(
                            "CN-RSP-COMMON-COLD",
                            "CN-RSP-001",
                            "感冒",
                            "Common cold",
                            "alias",
                            1.0d
                    ));
                }
                if (query != null && query.contains("肝癌")) {
                    return List.of(new KnowledgeRetrievalMatchedDiseaseEntity(
                            "CN-C00-C97-LIVER",
                            "CN-ONC-001",
                            "肝癌",
                            "Liver cancer",
                            "alias",
                            1.0d
                    ));
                }
                return List.of();
            }
        };
    }
}
