package com.linkjb.aimed.entity.dto.response.medical;

import com.linkjb.aimed.entity.vo.medical.MedicalKnowledgeMappingItem;

import java.util.List;

public record MedicalKnowledgeMappingBackfillResponse(int targetCount,
                                                    int matchedCount,
                                                    int updatedCount,
                                                    List<MedicalKnowledgeMappingItem> items) {
}
