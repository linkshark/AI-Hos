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
    @Bean(name = "knowledgeEmbeddingStore")
    public EmbeddingStore<TextSegment> knowledgeEmbeddingStore() {
        // 向量会持久化到 MySQL 分片表，进程内只保留统一的运行时检索索引。
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
