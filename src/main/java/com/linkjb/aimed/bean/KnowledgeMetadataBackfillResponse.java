package com.linkjb.aimed.bean;

import java.util.List;

public record KnowledgeMetadataBackfillResponse(boolean preview,
                                                int targetCount,
                                                int matchedCount,
                                                int updatedCount,
                                                int skippedCount,
                                                List<KnowledgeMetadataBackfillItem> items) {
}
