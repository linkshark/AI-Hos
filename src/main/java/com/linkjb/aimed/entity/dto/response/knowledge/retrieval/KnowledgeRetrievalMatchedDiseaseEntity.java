package com.linkjb.aimed.entity.dto.response.knowledge.retrieval;

public record KnowledgeRetrievalMatchedDiseaseEntity(String conceptCode,
                                                     String standardCode,
                                                     String diseaseName,
                                                     String englishName,
                                                     String matchType,
                                                     Double score) {
}
