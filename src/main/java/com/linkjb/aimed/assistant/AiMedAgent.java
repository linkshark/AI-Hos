package com.linkjb.aimed.assistant;

import dev.langchain4j.service.*;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;
@AiService(
        wiringMode = EXPLICIT,
        streamingChatModel = "localStreamingChatModel",
        chatMemoryProvider = "chatMemoryProviderAiMed",
        tools = "appointmentTools",
        contentRetriever = "contentRetrieverLocal"
)
public interface AiMedAgent {
    @SystemMessage(fromResource = "prompt-templates/aimed-prompt-template.txt")
    Flux<String> chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}
