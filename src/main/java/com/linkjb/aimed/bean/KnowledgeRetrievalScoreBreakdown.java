package com.linkjb.aimed.bean;

public record KnowledgeRetrievalScoreBreakdown(String key,
                                               String label,
                                               double weight,
                                               double basis,
                                               double contribution,
                                               String description) {
}
