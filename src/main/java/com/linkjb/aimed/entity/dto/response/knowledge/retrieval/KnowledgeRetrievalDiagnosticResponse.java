package com.linkjb.aimed.entity.dto.response.knowledge.retrieval;

import java.util.List;

public record KnowledgeRetrievalDiagnosticResponse(String rawQuery,
                                                   String normalizedQuery,
                                                   String effectiveQuery,
                                                   String rewriteMode,
                                                   boolean rewriteApplied,
                                                   List<String> contextMessagesUsed,
                                                   List<String> medicalAnchors,
                                                   KnowledgeRetrievalModelRewriteCandidate modelRewriteCandidate,
                                                   boolean modelRewriteAccepted,
                                                   List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities,
                                                   List<String> matchedSymptoms,
                                                   List<String> matchedChiefComplaints,
                                                   List<KnowledgeRetrievalDocumentEntityMatch> docEntityMatches,
                                                   String audience,
                                                   String profile,
                                                   String queryType,
                                                   String booleanQuery,
                                                   long durationMs,
                                                   boolean emptyRecall,
                                                   List<KnowledgeRetrievalScoringRule> scoringRules,
                                                   List<String> keywordTokens,
                                                   List<KnowledgeRetrievalDiagnosticHit> keywordHits,
                                                   List<KnowledgeRetrievalDiagnosticHit> vectorHits,
                                                   List<KnowledgeRetrievalDiagnosticHit> finalHits) {
}
