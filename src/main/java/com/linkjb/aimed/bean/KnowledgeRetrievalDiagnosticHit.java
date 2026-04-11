package com.linkjb.aimed.bean;

import java.util.List;

public record KnowledgeRetrievalDiagnosticHit(String fileHash,
                                              String segmentId,
                                              Integer segmentIndex,
                                              String title,
                                              String doctorName,
                                              String department,
                                              String version,
                                              String documentName,
                                              String retrievalType,
                                              double keywordScore,
                                              double vectorScore,
                                              double combinedScore,
                                              String preview,
                                              List<KnowledgeRetrievalScoreBreakdown> scoreBreakdown) {
}
