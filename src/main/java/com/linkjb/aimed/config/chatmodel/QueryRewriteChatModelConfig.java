package com.linkjb.aimed.config.chatmodel;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * Query rewrite 专用小模型配置。
 *
 * 这里故意不复用聊天主模型 bean：
 * - 聊天主链可以继续走 OMLX 或在线大模型
 * - rewrite 则可以单独选择更轻的小模型执行引擎
 *
 * 这样可以让本地和线上分别调参，而不会把 rewrite 和主对话链路绑死。
 */
@Configuration
public class QueryRewriteChatModelConfig {

    @Bean(name = "queryRewriteChatModel")
    public ChatModel queryRewriteChatModel(
            @Value("${app.rag.query-rewrite.model.platform:ONLINE_FAST}") String platform,
            @Value("${app.rag.query-rewrite.model.base-url:${app.online-chat.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}}") String baseUrl,
            @Value("${app.rag.query-rewrite.model.api-key:${app.online-chat.api-key:}}") String apiKey,
            @Value("${app.rag.query-rewrite.model.model-name:${app.online-chat.fast.model-name:qwen3.6-flash}}") String modelName,
            @Value("${app.rag.query-rewrite.model.temperature:0.1}") Double temperature,
            @Value("${app.rag.query-rewrite.model.max-tokens:128}") Integer maxTokens,
            @Value("${app.rag.query-rewrite.model.timeout:PT3S}") Duration timeout,
            @Value("${app.rag.query-rewrite.model.log-requests:false}") Boolean logRequests,
            @Value("${app.rag.query-rewrite.model.log-responses:false}") Boolean logResponses) {
        String normalizedPlatform = normalizePlatform(platform);
        if ("OLLAMA".equals(normalizedPlatform)) {
            return OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .httpClientBuilder(JdkHttpClient.builder())
                    .temperature(temperature)
                    .numPredict(maxTokens)
                    .timeout(timeout)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .build();
        }
        if ("OMLX".equals(normalizedPlatform)) {
            return OpenAiCompatibleModelFactory.buildChatModel(
                    baseUrl,
                    apiKey,
                    modelName,
                    temperature,
                    maxTokens,
                    timeout,
                    logRequests,
                    logResponses
            );
        }
        if (!StringUtils.hasText(apiKey)) {
            // rewrite 小模型只是增强层，配置缺失时宁可退化为“不返回候选”，也不能阻断应用启动。
            return new ChatModel() {
            };
        }
        return OpenAiCompatibleModelFactory.buildChatModel(
                baseUrl,
                apiKey,
                modelName,
                temperature,
                maxTokens,
                timeout,
                logRequests,
                logResponses
        );
    }

    String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return "ONLINE_FAST";
        }
        String normalized = platform.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOCAL_OLLAMA" -> "OLLAMA";
            case "LOCAL_OMLX", "OMLX" -> "OMLX";
            case "QWEN_ONLINE_FAST" -> "ONLINE_FAST";
            default -> normalized;
        };
    }
}
