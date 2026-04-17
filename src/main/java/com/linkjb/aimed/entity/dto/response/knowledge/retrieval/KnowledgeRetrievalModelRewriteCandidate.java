package com.linkjb.aimed.entity.dto.response.knowledge.retrieval;

import java.util.List;

public record KnowledgeRetrievalModelRewriteCandidate(String effectiveQuery,
                                                      List<String> medicalAnchors,
                                                      String intentType,
                                                      Double confidence,
                                                      List<String> droppedNoise) {
}
