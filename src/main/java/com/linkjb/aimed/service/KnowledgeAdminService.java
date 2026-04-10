package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.KnowledgeMetadataBackfillRequest;
import com.linkjb.aimed.bean.KnowledgeMetadataBackfillResponse;
import com.linkjb.aimed.bean.KnowledgeRetrievalDiagnosticRequest;
import com.linkjb.aimed.bean.KnowledgeRetrievalDiagnosticResponse;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAdminService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService;

    public KnowledgeAdminService(KnowledgeBaseService knowledgeBaseService,
                                 HybridKnowledgeRetrieverService hybridKnowledgeRetrieverService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.hybridKnowledgeRetrieverService = hybridKnowledgeRetrieverService;
    }

    public KnowledgeMetadataBackfillResponse previewMetadataBackfill(KnowledgeMetadataBackfillRequest request) {
        return knowledgeBaseService.backfillMetadata(request == null ? null : request.getHashes(), true);
    }

    public KnowledgeMetadataBackfillResponse applyMetadataBackfill(KnowledgeMetadataBackfillRequest request) {
        return knowledgeBaseService.backfillMetadata(request == null ? null : request.getHashes(), false);
    }

    public KnowledgeRetrievalDiagnosticResponse diagnoseRetrieval(KnowledgeRetrievalDiagnosticRequest request) {
        String query = request == null ? null : request.getQuery();
        String profile = request == null ? null : request.getProfile();
        return hybridKnowledgeRetrieverService.diagnose(query, profile);
    }
}
