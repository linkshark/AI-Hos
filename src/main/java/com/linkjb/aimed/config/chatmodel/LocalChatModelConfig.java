package com.linkjb.aimed.config.chatmodel;

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
            @Value("${app.local-chat.platform:OMLX}") String platform,
            @Value("${app.local-chat.base-url:http://localhost:8000/v1}") String baseUrl,
            @Value("${app.local-chat.api-key:}") String apiKey,
            @Value("${app.local-chat.model-name:Qwen3.6-35B-A3B-4bit}") String modelName,
            @Value("${app.local-chat.temperature:0.3}") Double temperature,
            @Value("${app.local-chat.num-predict:1024}") Integer numPredict,
            @Value("${app.local-chat.num-ctx:4096}") Integer numCtx,
            @Value("${app.local-chat.timeout:PT180S}") Duration timeout,
            @Value("${app.local-chat.log-requests:false}") Boolean logRequests,
            @Value("${app.local-chat.log-responses:false}") Boolean logResponses) {
        if (isOllama(platform)) {
            return buildOllamaChatModel(baseUrl, modelName, temperature, numPredict, numCtx, timeout, logRequests, logResponses);
        }
        return buildOpenAiCompatibleChatModel(baseUrl, apiKey, modelName, temperature, numPredict, timeout, logRequests, logResponses);
    }

    @Bean(name = "localStreamingChatModel")
    public StreamingChatModel localStreamingChatModel(
            @Value("${app.local-chat.platform:OMLX}") String platform,
            @Value("${app.local-chat.base-url:http://localhost:8000/v1}") String baseUrl,
            @Value("${app.local-chat.api-key:}") String apiKey,
            @Value("${app.local-chat.model-name:Qwen3.6-35B-A3B-4bit}") String modelName,
            @Value("${app.local-chat.temperature:0.3}") Double temperature,
            @Value("${app.local-chat.num-predict:1024}") Integer numPredict,
            @Value("${app.local-chat.num-ctx:4096}") Integer numCtx,
            @Value("${app.local-chat.timeout:PT180S}") Duration timeout,
            @Value("${app.local-chat.log-requests:false}") Boolean logRequests,
            @Value("${app.local-chat.log-responses:false}") Boolean logResponses) {
        if (isOllama(platform)) {
            return buildOllamaStreamingChatModel(baseUrl, modelName, temperature, numPredict, numCtx, timeout, logRequests, logResponses);
        }
        return buildOpenAiCompatibleStreamingChatModel(baseUrl, apiKey, modelName, temperature, numPredict, timeout, logRequests, logResponses);
    }

    private boolean isOllama(String platform) {
        return "OLLAMA".equalsIgnoreCase(platform);
    }

    private ChatModel buildOllamaChatModel(String baseUrl,
                                           String modelName,
                                           Double temperature,
                                           Integer numPredict,
                                           Integer numCtx,
                                           Duration timeout,
                                           Boolean logRequests,
                                           Boolean logResponses) {
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

    private StreamingChatModel buildOllamaStreamingChatModel(String baseUrl,
                                                             String modelName,
                                                             Double temperature,
                                                             Integer numPredict,
                                                             Integer numCtx,
                                                             Duration timeout,
                                                             Boolean logRequests,
                                                             Boolean logResponses) {
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

    private ChatModel buildOpenAiCompatibleChatModel(String baseUrl,
                                                     String apiKey,
                                                     String modelName,
                                                     Double temperature,
                                           Integer maxTokens,
                                           Duration timeout,
                                           Boolean logRequests,
                                           Boolean logResponses) {
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

    private StreamingChatModel buildOpenAiCompatibleStreamingChatModel(String baseUrl,
                                                                       String apiKey,
                                                                       String modelName,
                                                                       Double temperature,
                                                                       Integer maxTokens,
                                                                       Duration timeout,
                                                                       Boolean logRequests,
                                                                       Boolean logResponses) {
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
