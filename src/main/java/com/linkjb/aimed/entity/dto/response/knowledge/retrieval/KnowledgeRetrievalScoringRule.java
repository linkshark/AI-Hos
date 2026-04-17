package com.linkjb.aimed.entity.dto.response.knowledge.retrieval;

public record KnowledgeRetrievalScoringRule(String key,
                                            String label,
                                            double weight,
                                            String description) {
}
