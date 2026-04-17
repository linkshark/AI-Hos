package com.linkjb.aimed.entity.vo.medical;

public record MedicalKnowledgeMappingItem(Long id,
                                          String knowledgeHash,
                                          String conceptCode,
                                          String standardCode,
                                          String diseaseTitle,
                                          String matchSource,
                                          Double confidence,
                                          Integer sortOrder) {
}
