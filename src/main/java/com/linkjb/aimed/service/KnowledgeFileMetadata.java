package com.linkjb.aimed.service;

import java.time.LocalDateTime;

record KnowledgeFileMetadata(String docType,
                             String department,
                             String audience,
                             String version,
                             LocalDateTime effectiveAt,
                             String title,
                             String doctorName,
                             Integer sourcePriority,
                             String keywords) {
}
