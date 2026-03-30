package com.linkjb.aimed.config;

import com.linkjb.aimed.service.KnowledgeBaseService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingStoreConfig {
    @Autowired
    private EmbeddingModel embeddingModel;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // 本地开发默认使用内存向量库，避免依赖外部 Pinecone 服务。
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
