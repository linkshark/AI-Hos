package com.linkjb.aimed.entity.dto.response;

import com.linkjb.aimed.entity.vo.KnowledgeMetadataBackfillItem;

import java.util.List;

public record KnowledgeMetadataBackfillResponse(boolean preview,
                                                int targetCount,
                                                int matchedCount,
                                                int updatedCount,
                                                int skippedCount,
                                                List<KnowledgeMetadataBackfillItem> items) {
}
