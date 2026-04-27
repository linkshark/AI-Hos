package com.linkjb.aimed.config.chatmodel;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryRewriteChatModelConfigTest {

    private final QueryRewriteChatModelConfig config = new QueryRewriteChatModelConfig();

    @Test
    void shouldNormalizeOmlxPlatforms() {
        assertEquals("OMLX", config.normalizePlatform("LOCAL_OMLX"));
        assertEquals("OMLX", config.normalizePlatform("OMLX"));
        assertEquals("OLLAMA", config.normalizePlatform("LOCAL_OLLAMA"));
        assertEquals("ONLINE_FAST", config.normalizePlatform("QWEN_ONLINE_FAST"));
    }

    @Test
    void shouldPreferExplicitApiKeyBeforeOmlxSettingsFallback() throws Exception {
        Path settingsFile = Files.createTempFile("omlx-settings", ".json");
        Files.writeString(settingsFile, "{\"api_key\":\"from-settings\"}");

        assertEquals("explicit-key", OpenAiCompatibleModelFactory.resolveApiKey("explicit-key", settingsFile));
        assertEquals("from-settings", OpenAiCompatibleModelFactory.resolveApiKey("", settingsFile));
    }
}
