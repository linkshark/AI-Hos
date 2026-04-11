package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.bean.ChatProviderConfigResponse;
import com.linkjb.aimed.config.skywalk.TraceIdProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                new TraceIdProvider(),
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
                new TraceIdProvider(),
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
}
