package com.linkjb.aimed.config;

import com.linkjb.aimed.service.KnowledgeBaseService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingStoreConfig {
    @Bean(name = "localEmbeddingStore")
    public EmbeddingStore<TextSegment> localEmbeddingStore() {
        // 本地模式使用独立的内存向量库，避免与在线检索结果串线。
        return new InMemoryEmbeddingStore<>();
    }

    @Bean(name = "onlineEmbeddingStore")
    public EmbeddingStore<TextSegment> onlineEmbeddingStore() {
        // 在线模式维护独立的向量索引，配合在线 embedding 模型检索。
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public CommandLineRunner knowledgeBaseIngestor(KnowledgeBaseService knowledgeBaseService,
                                                   @Value("${app.knowledge-base.bootstrap-enabled:true}") boolean bootstrapEnabled) {
        return args -> {
            if (bootstrapEnabled) {
                knowledgeBaseService.loadInitialKnowledgeBase();
            }
        };
    }
}
