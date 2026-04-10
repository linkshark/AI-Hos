package com.linkjb.aimed.bean;

import java.util.List;

public record KnowledgeRetrievalDiagnosticResponse(String rawQuery,
                                                   String normalizedQuery,
                                                   String audience,
                                                   String profile,
                                                   String queryType,
                                                   String booleanQuery,
                                                   long durationMs,
                                                   boolean emptyRecall,
                                                   List<String> keywordTokens,
                                                   List<KnowledgeRetrievalDiagnosticHit> keywordHits,
                                                   List<KnowledgeRetrievalDiagnosticHit> vectorHits,
                                                   List<KnowledgeRetrievalDiagnosticHit> finalHits) {
}
