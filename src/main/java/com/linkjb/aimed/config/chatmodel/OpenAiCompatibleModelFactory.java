package com.linkjb.aimed.config.chatmodel;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpenAiCompatibleModelFactory {

    private static final Pattern OMLX_API_KEY_PATTERN = Pattern.compile("\"api_key\"\\s*:\\s*\"([^\"]+)\"");

    private OpenAiCompatibleModelFactory() {
    }

    static ChatModel buildChatModel(String baseUrl,
                                    String apiKey,
                                    String modelName,
                                    Double temperature,
                                    Integer maxTokens,
                                    Duration timeout,
                                    Boolean logRequests,
                                    Boolean logResponses) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(resolveApiKey(apiKey))
                .modelName(modelName)
                .httpClientBuilder(JdkHttpClient.builder())
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    static StreamingChatModel buildStreamingChatModel(String baseUrl,
                                                      String apiKey,
                                                      String modelName,
                                                      Double temperature,
                                                      Integer maxTokens,
                                                      Duration timeout,
                                                      Boolean logRequests,
                                                      Boolean logResponses) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(resolveApiKey(apiKey))
                .modelName(modelName)
                .httpClientBuilder(JdkHttpClient.builder())
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    static String resolveApiKey(String configuredApiKey) {
        return resolveApiKey(configuredApiKey, defaultSettingsPath());
    }

    static String resolveApiKey(String configuredApiKey, Path settingsPath) {
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            return configuredApiKey.trim();
        }
        String omlxApiKey = readApiKey(settingsPath);
        return omlxApiKey == null || omlxApiKey.isBlank() ? "local" : omlxApiKey;
    }

    static Path defaultSettingsPath() {
        return Path.of(System.getProperty("user.home"), ".omlx", "settings.json");
    }

    private static String readApiKey(Path settingsPath) {
        if (settingsPath == null || !Files.isRegularFile(settingsPath)) {
            return "";
        }
        try {
            String settingsJson = Files.readString(settingsPath);
            Matcher matcher = OMLX_API_KEY_PATTERN.matcher(settingsJson);
            return matcher.find() ? matcher.group(1) : "";
        } catch (IOException ignored) {
            return "";
        }
    }
}
