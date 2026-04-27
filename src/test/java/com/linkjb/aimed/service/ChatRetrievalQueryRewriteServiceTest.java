package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalMatchedDiseaseEntity;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalModelRewriteCandidate;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalQueryRewriteInfo;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void shouldSkipModelRewriteForGuideIntent() {
        CountingModelService modelService = countingModelService();
        ChatRetrievalQueryRewriteService service = new ChatRetrievalQueryRewriteService(
                modelService,
                stubLookupService(),
                true,
                4,
                "RULE_PLUS_MODEL_ACTIVE"
        );

        KnowledgeRetrievalQueryRewriteInfo rewriteInfo = service.rewriteInternalForTest(
                "原发性肝癌指南 2024 版",
                List.of("我感冒了 我该吃点什么药")
        );

        assertEquals(0, modelService.invocationCount());
        assertTrue(rewriteInfo.effectiveQuery().contains("指南"));
        assertFalse(rewriteInfo.effectiveQuery().contains("感冒"));
    }

    @Test
    void shouldAttemptModelRewriteForWeakMedicalFollowUp() {
        CountingModelService modelService = countingModelService();
        ChatRetrievalQueryRewriteService service = new ChatRetrievalQueryRewriteService(
                modelService,
                stubLookupService(),
                true,
                4,
                "RULE_PLUS_MODEL_DIAGNOSTIC"
        );

        service.rewriteInternalForTest(
                "我现在30岁 没有别的症状",
                List.of("我感冒了 我该吃点什么药")
        );

        assertEquals(1, modelService.invocationCount());
    }

    @Test
    void shouldKeepMycoplasmaRewriteFocusedOnCoreDiseaseAnchors() {
        ChatRetrievalQueryRewriteService service = new ChatRetrievalQueryRewriteService(
                disabledModelService(),
                stubLookupService(),
                true,
                4,
                "RULE_ONLY"
        );

        KnowledgeRetrievalQueryRewriteInfo rewriteInfo = service.rewrite("肺炎支原体感染有什么表现");

        assertTrue(rewriteInfo.effectiveQuery().contains("肺炎支原体感染"));
        assertFalse(rewriteInfo.effectiveQuery().contains("妊娠合并"));
        assertFalse(rewriteInfo.effectiveQuery().contains("新生儿"));
    }

    private QueryRewriteModelService disabledModelService() {
        ChatModel noopModel = new ChatModel() {
        };
        return new QueryRewriteModelService(
                new ObjectMapper(),
                noopModel,
                false,
                "ONLINE_FAST",
                "DIAGNOSTIC_ONLY"
        );
    }

    private CountingModelService countingModelService() {
        ChatModel noopModel = new ChatModel() {
        };
        return new CountingModelService(
                new ObjectMapper(),
                noopModel,
                true,
                "OLLAMA",
                "ACTIVE"
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
                if (query != null && query.contains("肺炎支原体感染")) {
                    return List.of(
                            new KnowledgeRetrievalMatchedDiseaseEntity(
                                    "CN-RSP-MP",
                                    "CN-RSP-201",
                                    "妊娠合并支原体感染",
                                    "Pregnancy with mycoplasma infection",
                                    "alias",
                                    1.0d
                            ),
                            new KnowledgeRetrievalMatchedDiseaseEntity(
                                    "CN-RSP-MP-NEO",
                                    "CN-RSP-202",
                                    "新生儿支原体肺炎",
                                    "Neonatal mycoplasma pneumonia",
                                    "alias",
                                    1.0d
                            ),
                            new KnowledgeRetrievalMatchedDiseaseEntity(
                                    "CN-RSP-MP-CORE",
                                    "CN-RSP-203",
                                    "肺炎支原体感染",
                                    "Mycoplasma pneumoniae infection",
                                    "alias",
                                    1.0d
                            )
                    );
                }
                return List.of();
            }
        };
    }

    private static final class CountingModelService extends QueryRewriteModelService {
        private final AtomicInteger invocationCount = new AtomicInteger();

        private CountingModelService(ObjectMapper objectMapper,
                                     ChatModel queryRewriteChatModel,
                                     boolean enabled,
                                     String platform,
                                     String mode) {
            super(objectMapper, queryRewriteChatModel, enabled, platform, mode);
        }

        @Override
        ModelRewriteResult rewrite(String currentQuery,
                                   List<String> recentUserSummaries,
                                   List<String> detectedMedicalAnchors,
                                   String ruleEffectiveQuery,
                                   String intentType) {
            invocationCount.incrementAndGet();
            return new ModelRewriteResult(
                    new KnowledgeRetrievalModelRewriteCandidate(
                            ruleEffectiveQuery,
                            detectedMedicalAnchors,
                            intentType,
                            0.95d,
                            List.of()
                    ),
                    true
            );
        }

        private int invocationCount() {
            return invocationCount.get();
        }
    }
}
