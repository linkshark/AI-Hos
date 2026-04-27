package com.linkjb.aimed.service.retrieval;

import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalDocumentEntityMatch;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalMatchedDiseaseEntity;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalQueryRewriteInfo;
import com.linkjb.aimed.service.HybridKnowledgeRetrieverService;

import java.util.List;

/**
 * 一次检索执行的显式返回值。
 *
 * 聊天链路后续要展示 keyword/vector 并发耗时，也要避免靠 ThreadLocal 回收检索摘要，
 * 因此这里把召回结果、排序结果和阶段耗时统一收敛成一个可传递对象。
 */
public record RetrievalExecutionResult(
        KnowledgeRetrievalQueryRewriteInfo rewriteInfo,
        String rawQuery,
        String normalizedQuery,
        String queryType,
        List<HybridKnowledgeRetrieverService.RetrievedChunk> keywordHits,
        List<HybridKnowledgeRetrieverService.RetrievedChunk> vectorHits,
        List<HybridKnowledgeRetrieverService.RetrievedChunk> finalHits,
        List<KnowledgeRetrievalMatchedDiseaseEntity> matchedDiseaseEntities,
        List<KnowledgeRetrievalDocumentEntityMatch> docEntityMatches,
        HybridKnowledgeRetrieverService.RetrievalTimings timings,
        long retrievalDurationMs,
        boolean emptyRecall
) {
}
