package com.linkjb.aimed.entity.dto.response.medical;

/**
 * 疾病标准本地目录摘要。
 */
public record MedicalStandardSummaryResponse(String versionTag,
                                          int entityCount,
                                          int aliasCount,
                                          int symptomMappingCount,
                                          int knowledgeMappingCount) {
}
