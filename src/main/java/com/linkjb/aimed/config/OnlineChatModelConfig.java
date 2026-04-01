package com.linkjb.aimed.config;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OnlineChatModelConfig {

    @Bean(name = "onlineStreamingChatModel")
    public StreamingChatModel onlineStreamingChatModel(
            @Value("${app.online-chat.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${app.online-chat.api-key:}") String apiKey,
            @Value("${app.online-chat.model-name:${ONLINE_CHAT_MODEL_NAME:qwen3.5-plus}}") String modelName,
            @Value("${app.online-chat.temperature:0.9}") Double temperature,
            @Value("${app.online-chat.timeout:PT90S}") Duration timeout,
            @Value("${app.online-chat.log-requests:true}") Boolean logRequests,
            @Value("${app.online-chat.log-responses:true}") Boolean logResponses) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .httpClientBuilder(JdkHttpClient.builder())
                .temperature(temperature)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
