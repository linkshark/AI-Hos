package com.linkjb.aimed.config.chatmodel;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class VisionChatModelConfig {

    @Bean(name = "qwenVisionChatModel")
    OpenAiChatModel qwenVisionChatModel(
            @Value("${app.vision-chat.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${app.vision-chat.api-key:}") String apiKey,
            @Value("${app.vision-chat.model-name:qwen3.6-plus}") String modelName,
            @Value("${app.vision-chat.timeout:PT90S}") Duration timeout) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(timeout)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
