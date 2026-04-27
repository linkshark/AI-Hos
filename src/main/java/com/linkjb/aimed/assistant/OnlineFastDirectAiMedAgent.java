package com.linkjb.aimed.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        wiringMode = EXPLICIT,
        streamingChatModel = "onlineFastStreamingChatModel",
        chatMemoryProvider = "onlineFastChatMemoryProviderAiMed",
        tools = {"appointmentTools"}
)
public interface OnlineFastDirectAiMedAgent {

    @SystemMessage(fromResource = "prompt-templates/aimed-prompt-template-online-fast.txt")
    Flux<String> chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}
