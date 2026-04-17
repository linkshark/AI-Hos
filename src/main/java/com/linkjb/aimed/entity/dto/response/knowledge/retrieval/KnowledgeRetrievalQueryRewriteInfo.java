package com.linkjb.aimed.entity.dto.response.knowledge.retrieval;

import java.util.List;

public record KnowledgeRetrievalQueryRewriteInfo(String rawQuery,
                                                 String effectiveQuery,
                                                 String rewriteMode,
                                                 boolean rewriteApplied,
                                                 List<String> contextMessagesUsed,
                                                 List<String> medicalAnchors,
                                                 KnowledgeRetrievalModelRewriteCandidate modelRewriteCandidate,
                                                 boolean modelRewriteAccepted,
                                                 long durationMs) {
}
