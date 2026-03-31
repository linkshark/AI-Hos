package com.linkjb.aimed.config;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OnlineEmbeddingModelConfig {

    @Bean(name = "onlineEmbeddingModel")
    public EmbeddingModel onlineEmbeddingModel(
            @Value("${app.online-embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${app.online-embedding.api-key:${BL_KEY}}") String apiKey,
            @Value("${app.online-embedding.model-name:text-embedding-v4}") String modelName,
            @Value("${app.online-embedding.timeout:PT90S}") Duration timeout,
            @Value("${app.online-embedding.log-requests:false}") boolean logRequests,
            @Value("${app.online-embedding.log-responses:false}") boolean logResponses) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .httpClientBuilder(JdkHttpClient.builder())
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
