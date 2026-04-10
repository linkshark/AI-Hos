package com.linkjb.aimed.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        wiringMode = EXPLICIT,
        streamingChatModel = "localStreamingChatModel",
        chatMemoryProvider = "localChatMemoryProviderAiMed",
        tools = "appointmentTools",
        contentRetriever = "contentRetrieverKnowledgeLocal"
)
public interface LocalStreamingAiMedAgent {

    @SystemMessage(fromResource = "prompt-templates/aimed-prompt-template-local.txt")
    TokenStream chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}
