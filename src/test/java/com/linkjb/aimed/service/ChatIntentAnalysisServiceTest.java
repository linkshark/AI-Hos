package com.linkjb.aimed.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatIntentAnalysisServiceTest {

    private final ChatIntentAnalysisService service = new ChatIntentAnalysisService();

    @Test
    void shouldRequireRagForMedicalQuestion() {
        ChatIntentAnalysisService.ChatIntentResult result = service.analyze("我感冒咳嗽了怎么办");

        assertTrue(result.ragRequired());
        assertTrue("MEDICAL_QA".equals(result.intentType()) || "HOSPITAL_QA".equals(result.intentType()));
    }

    @Test
    void shouldSkipRagForAppointmentIntent() {
        ChatIntentAnalysisService.ChatIntentResult result = service.analyze("帮我挂个号吧");

        assertFalse(result.ragRequired());
        assertTrue(result.routeTarget().contains("APPOINTMENT"));
    }

    @Test
    void shouldSkipRagForWeatherMcpIntent() {
        ChatIntentAnalysisService.ChatIntentResult result = service.analyze("杭州明天天气怎么样");

        assertFalse(result.ragRequired());
        assertTrue(result.routeTarget().contains("MCP"));
    }

    @Test
    void shouldRequireRagForMedicalLookupWithPoliteQueryVerb() {
        ChatIntentAnalysisService.ChatIntentResult result = service.analyze("帮我查一下早期肝癌怎么治疗");

        assertTrue(result.ragRequired());
    }

    @Test
    void shouldSkipRagForSmallTalk() {
        ChatIntentAnalysisService.ChatIntentResult result = service.analyze("谢谢");

        assertFalse(result.ragRequired());
        assertTrue(result.routeTarget().contains("SMALL_TALK"));
    }
}
