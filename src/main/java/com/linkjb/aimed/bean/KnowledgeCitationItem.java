package com.linkjb.aimed.bean;

public class KnowledgeCitationItem {
    private String fileHash;
    private String documentName;
    private String segmentId;
    private String snippet;
    private String updatedAt;
    private String effectiveAt;
    private String version;
    private String retrievalType;

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(String segmentId) {
        this.segmentId = segmentId;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(String effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRetrievalType() {
        return retrievalType;
    }

    public void setRetrievalType(String retrievalType) {
        this.retrievalType = retrievalType;
    }
}
