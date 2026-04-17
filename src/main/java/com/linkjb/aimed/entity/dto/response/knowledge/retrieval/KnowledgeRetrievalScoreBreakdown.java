package com.linkjb.aimed.entity.dto.response.knowledge.retrieval;

public record KnowledgeRetrievalScoreBreakdown(String key,
                                               String label,
                                               double weight,
                                               double basis,
                                               double contribution,
                                               String description) {
}
