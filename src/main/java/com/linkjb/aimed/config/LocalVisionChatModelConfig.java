package com.linkjb.aimed.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LocalVisionChatModelConfig {

    @Bean(name = "localVisionChatModel")
    public ChatModel localVisionChatModel(
            @Value("${app.local-vision.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.local-vision.model-name:qwen2.5vl:7b}") String modelName,
            @Value("${app.local-vision.temperature:0.2}") Double temperature,
            @Value("${app.local-vision.num-ctx:8192}") Integer numCtx,
            @Value("${app.local-vision.timeout:PT180S}") Duration timeout,
            @Value("${app.local-vision.log-requests:false}") Boolean logRequests,
            @Value("${app.local-vision.log-responses:false}") Boolean logResponses) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .numCtx(numCtx)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
