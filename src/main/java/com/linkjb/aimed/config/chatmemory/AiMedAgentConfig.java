package com.linkjb.aimed.config.chatmemory;

import com.linkjb.aimed.store.MongoChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiMedAgentConfig {
    private final MongoChatMemoryStore mongoChatMemoryStore;
    private final int localMaxMessages;
    private final int onlineFastMaxMessages;
    private final int onlineDeepMaxMessages;

    public AiMedAgentConfig(MongoChatMemoryStore mongoChatMemoryStore,
                            @Value("${app.local-chat.max-history-messages:20}") int localMaxMessages,
                            @Value("${app.online-chat.fast.max-history-messages:8}") int onlineFastMaxMessages,
                            @Value("${app.online-chat.deep.max-history-messages:12}") int onlineDeepMaxMessages) {
        this.mongoChatMemoryStore = mongoChatMemoryStore;
        this.localMaxMessages = localMaxMessages;
        this.onlineFastMaxMessages = onlineFastMaxMessages;
        this.onlineDeepMaxMessages = onlineDeepMaxMessages;
    }

    @Bean(name = "localChatMemoryProviderAiMed")
    ChatMemoryProvider localChatMemoryProviderAiMed() {
        return buildMemoryProvider(localMaxMessages);
    }

    @Bean(name = "onlineFastChatMemoryProviderAiMed")
    ChatMemoryProvider onlineFastChatMemoryProviderAiMed() {
        return buildMemoryProvider(onlineFastMaxMessages);
    }

    @Bean(name = "onlineDeepChatMemoryProviderAiMed")
    ChatMemoryProvider onlineDeepChatMemoryProviderAiMed() {
        return buildMemoryProvider(onlineDeepMaxMessages);
    }

    private ChatMemoryProvider buildMemoryProvider(int maxMessages) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(Math.max(1, maxMessages))
                .chatMemoryStore(mongoChatMemoryStore)
                .build();
    }
}
