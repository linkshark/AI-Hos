package com.linkjb.aimed.assistant;

import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

// 基础Assistant
@AiService(wiringMode = EXPLICIT, chatModel = "localChatModel")
public interface Assistant {
    String chat(String userMessage);
}
