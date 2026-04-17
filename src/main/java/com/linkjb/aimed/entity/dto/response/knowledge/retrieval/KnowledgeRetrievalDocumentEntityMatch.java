package com.linkjb.aimed.entity.dto.response.knowledge.retrieval;

public record KnowledgeRetrievalDocumentEntityMatch(String knowledgeHash,
                                                    String conceptCode,
                                                    String standardCode,
                                                    String diseaseTitle,
                                                    String matchSource,
                                                    Double confidence) {
}
