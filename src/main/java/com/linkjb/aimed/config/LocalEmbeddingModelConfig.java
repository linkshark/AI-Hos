package com.linkjb.aimed.config;

import com.linkjb.aimed.model.RetryingEmbeddingModel;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

@Configuration
public class LocalEmbeddingModelConfig {

    @Bean(name = "knowledgeEmbeddingModel")
    public EmbeddingModel knowledgeEmbeddingModel(
            @Value("${app.embedding.provider:OLLAMA}") String provider,
            @Value("${app.embedding.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.embedding.model-name:bge-m3:latest}") String modelName,
            @Value("${app.embedding.timeout:PT90S}") Duration timeout,
            @Value("${app.embedding.max-attempts:3}") int maxAttempts,
            @Value("${app.embedding.retry-delay:PT2S}") Duration retryDelay,
            @Value("${app.embedding.log-requests:false}") boolean logRequests,
            @Value("${app.embedding.log-responses:false}") boolean logResponses) {
        String normalizedProvider = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        EmbeddingModel delegate;
        if ("OLLAMA".equals(normalizedProvider)) {
            delegate = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .httpClientBuilder(JdkHttpClient.builder())
                    .timeout(timeout)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .build();
        } else {
            if (!StringUtils.hasText(apiKey)) {
                throw new IllegalStateException("app.embedding.api-key 未配置，无法使用 OpenAI-compatible embedding");
            }
            delegate = OpenAiEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .httpClientBuilder(JdkHttpClient.builder())
                    .timeout(timeout)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .build();
        }
        return new RetryingEmbeddingModel(delegate, maxAttempts, retryDelay);
    }
}
