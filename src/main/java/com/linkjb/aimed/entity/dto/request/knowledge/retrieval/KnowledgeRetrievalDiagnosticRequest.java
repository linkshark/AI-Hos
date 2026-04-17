package com.linkjb.aimed.entity.dto.request.knowledge.retrieval;

public class KnowledgeRetrievalDiagnosticRequest {
    private String query;
    private String profile;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }
}
