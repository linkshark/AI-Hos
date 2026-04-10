package com.linkjb.aimed.bean;

import java.util.List;

public record KnowledgeMetadataBackfillItem(String hash,
                                            String originalFilename,
                                            String processingStatus,
                                            boolean updated,
                                            List<KnowledgeMetadataBackfillFieldChange> changes,
                                            List<String> skippedReasons) {
}
