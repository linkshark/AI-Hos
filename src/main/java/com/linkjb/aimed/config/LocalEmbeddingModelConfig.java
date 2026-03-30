package com.linkjb.aimed.config;

import com.linkjb.aimed.model.RetryingEmbeddingModel;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class LocalEmbeddingModelConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            @Value("${app.embedding.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.embedding.model-name:bge-m3:latest}") String modelName,
            @Value("${app.embedding.timeout:PT90S}") Duration timeout,
            @Value("${app.embedding.max-attempts:3}") int maxAttempts,
            @Value("${app.embedding.retry-delay:PT2S}") Duration retryDelay,
            @Value("${app.embedding.log-requests:false}") boolean logRequests,
            @Value("${app.embedding.log-responses:false}") boolean logResponses) {
        EmbeddingModel delegate = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .httpClientBuilder(JdkHttpClient.builder())
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        return new RetryingEmbeddingModel(delegate, maxAttempts, retryDelay);
    }
}
