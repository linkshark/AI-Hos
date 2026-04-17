package com.linkjb.aimed.service.contentRetriever;

import com.linkjb.aimed.service.HybridKnowledgeRetrieverService;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("contentRetrieverKnowledgeOnline")
public class HybridKnowledgeContentRetriever implements ContentRetriever {

    private final HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService;

    public HybridKnowledgeContentRetriever(HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService) {
        this.hybridKnowledgeRetrieverService = hybridKnowledgeRetrieverService;
    }

    @Override
    public List<Content> retrieve(Query query) {
        return hybridKnowledgeRetrieverService.retrieve(query, HybridKnowledgeRetrieverService.RetrievalProfile.ONLINE);
    }
}
