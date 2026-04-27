package com.linkjb.aimed.config.chatmodel;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LocalVisionChatModelConfig {

    @Bean(name = "localVisionChatModel")
    public ChatModel localVisionChatModel(
            @Value("${app.local-vision.base-url:http://localhost:8000/v1}") String baseUrl,
            @Value("${app.local-vision.api-key:${app.local-chat.api-key:}}") String apiKey,
            @Value("${app.local-vision.model-name:${app.local-chat.model-name:Qwen3.6-35B-A3B-4bit}}") String modelName,
            @Value("${app.local-vision.temperature:0.2}") Double temperature,
            @Value("${app.local-vision.max-tokens:512}") Integer maxTokens,
            @Value("${app.local-vision.timeout:PT180S}") Duration timeout,
            @Value("${app.local-vision.log-requests:false}") Boolean logRequests,
            @Value("${app.local-vision.log-responses:false}") Boolean logResponses) {
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

    @Bean(name = "localVisionStreamingChatModel")
    public StreamingChatModel localVisionStreamingChatModel(
            @Value("${app.local-vision.base-url:http://localhost:8000/v1}") String baseUrl,
            @Value("${app.local-vision.api-key:${app.local-chat.api-key:}}") String apiKey,
            @Value("${app.local-vision.model-name:${app.local-chat.model-name:Qwen3.6-35B-A3B-4bit}}") String modelName,
            @Value("${app.local-vision.temperature:0.2}") Double temperature,
            @Value("${app.local-vision.max-tokens:512}") Integer maxTokens,
            @Value("${app.local-vision.timeout:PT180S}") Duration timeout,
            @Value("${app.local-vision.log-requests:false}") Boolean logRequests,
            @Value("${app.local-vision.log-responses:false}") Boolean logResponses) {
        return OpenAiCompatibleModelFactory.buildStreamingChatModel(
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
}
