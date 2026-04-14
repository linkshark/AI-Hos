package com.linkjb.aimed.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeSearchLexiconTest {

    @Test
    void shouldInferDepartmentAndDoctorFromTitle() {
        assertEquals("肝胆胰", KnowledgeSearchLexicon.inferDepartment("肝胆胰外科专家门诊指南.md"));
        assertTrue(KnowledgeSearchLexicon.inferDoctorName("李兰娟院士资源.md").contains("李兰娟"));
    }

    @Test
    void shouldExpandMedicalQueryTokens() {
        List<String> tokens = KnowledgeSearchLexicon.expandQueryTokens("树兰院士怎么挂号");
        assertTrue(tokens.contains("院士"));
        assertTrue(tokens.contains("专家"));
        assertTrue(tokens.contains("挂号"));
        assertTrue(tokens.contains("预约"));
    }

    @Test
    void shouldExtractCoreCancerTermsWithoutQuestionNoise() {
        List<String> tokens = KnowledgeSearchLexicon.expandQueryTokens("原发性早期肝癌应该如何治疗");

        assertTrue(tokens.contains("原发性肝癌"));
        assertTrue(tokens.contains("早期肝癌"));
        assertTrue(tokens.contains("肝癌"));
        assertFalse(tokens.contains("发性"));
        assertFalse(tokens.contains("性早"));
        assertFalse(tokens.contains("期肝"));
        assertFalse(tokens.contains("应该"));
        assertFalse(tokens.contains("如何"));
    }

    @Test
    void shouldExpandDoctorYearAndVersionTokens() {
        List<String> tokens = KnowledgeSearchLexicon.expandQueryTokens("李兰娟院士挂号 2024年版指南");
        assertTrue(tokens.contains("李兰娟"));
        assertTrue(tokens.contains("2024年"));
        assertTrue(tokens.contains("2024年版"));
    }

    @Test
    void shouldBuildKeywordSeedWithSynonyms() {
        String keywordSeed = KnowledgeSearchLexicon.buildKeywordSeed(
                "肝胆胰外科门诊指南",
                "md",
                "GUIDE",
                "肝胆胰",
                null
        );
        assertTrue(keywordSeed.contains("肝胆胰"));
        assertTrue(keywordSeed.contains("挂号"));
        assertTrue(keywordSeed.contains("指南"));
    }
}
