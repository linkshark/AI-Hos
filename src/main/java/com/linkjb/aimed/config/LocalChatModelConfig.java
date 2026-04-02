package com.linkjb.aimed.config;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LocalChatModelConfig {

    @Bean(name = "localChatModel")
    public ChatModel localChatModel(
            @Value("${app.local-chat.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.local-chat.model-name:qwen2.5:3b}") String modelName,
            @Value("${app.local-chat.temperature:0.3}") Double temperature,
            @Value("${app.local-chat.num-predict:1024}") Integer numPredict,
            @Value("${app.local-chat.num-ctx:4096}") Integer numCtx,
            @Value("${app.local-chat.timeout:PT180S}") Duration timeout,
            @Value("${app.local-chat.log-requests:false}") Boolean logRequests,
            @Value("${app.local-chat.log-responses:false}") Boolean logResponses) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .httpClientBuilder(JdkHttpClient.builder())
                .temperature(temperature)
                .numPredict(numPredict)
                .numCtx(numCtx)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    @Bean(name = "localStreamingChatModel")
    public StreamingChatModel localStreamingChatModel(
            @Value("${app.local-chat.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.local-chat.model-name:qwen2.5:3b}") String modelName,
            @Value("${app.local-chat.temperature:0.3}") Double temperature,
            @Value("${app.local-chat.num-predict:1024}") Integer numPredict,
            @Value("${app.local-chat.num-ctx:4096}") Integer numCtx,
            @Value("${app.local-chat.timeout:PT180S}") Duration timeout,
            @Value("${app.local-chat.log-requests:false}") Boolean logRequests,
            @Value("${app.local-chat.log-responses:false}") Boolean logResponses) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .httpClientBuilder(JdkHttpClient.builder())
                .temperature(temperature)
                .numPredict(numPredict)
                .numCtx(numCtx)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
