package com.linkjb.aimed.config;

import com.linkjb.aimed.store.MongoChatMemoryStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiMedAgentConfig {
    @Autowired
    private MongoChatMemoryStore mongoChatMemoryStore;

    @Bean
    ChatMemoryProvider chatMemoryProviderAiMed() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(mongoChatMemoryStore)
                .build();
    }

//    @Bean
//    ContentRetriever contentRetrieverXiaozhi() {
//        //使用FileSystemDocumentLoader读取指定目录下的知识库文档
////并使用默认的文档解析器对文档进行解析
//        Document document1 = FileSystemDocumentLoader.loadDocument("src/main/resources/knowledge/医院信息.md");
//        Document document2 = FileSystemDocumentLoader.loadDocument("src/main/resources/knowledge/科室信息.md");
//        Document document3 = FileSystemDocumentLoader.loadDocument("src/main/resources/knowledge/神经内科.md");
//        List<Document> documents = Arrays.asList(document1, document2, document3);
//        //使用内存向量存储
//        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
//        //使用默认的文档分割器
//        EmbeddingStoreIngestor.ingest(documents, embeddingStore);
//        //从嵌入存储（EmbeddingStore）里检索和查询内容相关的信息
//        return EmbeddingStoreContentRetriever.from(embeddingStore);
//    }
//
    @Autowired
    @Qualifier("localEmbeddingStore")
    private EmbeddingStore<TextSegment> localEmbeddingStore;
    @Autowired
    @Qualifier("onlineEmbeddingStore")
    private EmbeddingStore<TextSegment> onlineEmbeddingStore;
    @Autowired
    @Qualifier("localEmbeddingModel")
    private EmbeddingModel localEmbeddingModel;
    @Autowired
    @Qualifier("onlineEmbeddingModel")
    private EmbeddingModel onlineEmbeddingModel;

    @Bean(name = "contentRetrieverLocal")
    ContentRetriever contentRetrieverLocal() {
        return EmbeddingStoreContentRetriever
                .builder()
                .embeddingModel(localEmbeddingModel)
                .embeddingStore(localEmbeddingStore)
                .maxResults(1)
                .minScore(0.8)
                .build();
    }

    @Bean(name = "contentRetrieverOnline")
    ContentRetriever contentRetrieverOnline() {
        return EmbeddingStoreContentRetriever
                .builder()
                .embeddingModel(onlineEmbeddingModel)
                .embeddingStore(onlineEmbeddingStore)
                .maxResults(1)
                .minScore(0.8)
                .build();
    }

}
