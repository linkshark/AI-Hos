package com.linkjb.aimed.entity.vo;

import java.util.List;

public record KnowledgeMetadataBackfillItem(String hash,
                                            String originalFilename,
                                            String processingStatus,
                                            boolean updated,
                                            List<KnowledgeMetadataBackfillFieldChange> changes,
                                            List<String> skippedReasons) {
}
