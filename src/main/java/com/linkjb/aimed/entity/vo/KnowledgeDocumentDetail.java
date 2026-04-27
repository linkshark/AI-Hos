package com.linkjb.aimed.entity.vo;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeDocumentDetail {
    private String fileName;
    private String originalFilename;
    private String hash;
    private String source;
    private String parser;
    private String extension;
    private long size;
    private boolean editable;
    private boolean deletable;
    private String processingStatus;
    private String statusMessage;
    private Integer progressPercent;
    private Integer currentBatch;
    private Integer totalBatches;
    private String docType;
    private String department;
    private String audience;
    private String version;
    private String effectiveAt;
    private String title;
    private String doctorName;
    private Integer sourcePriority;
    private String keywords;
    private String segmentationMode;
    private boolean semanticEnhanced;
    private String indexUpgradeState;
    private String indexUpgradeMessage;
    private String extractedText;
    private List<KnowledgeChunkInfo> chunks = new ArrayList<>();

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getParser() {
        return parser;
    }

    public void setParser(String parser) {
        this.parser = parser;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public boolean isDeletable() {
        return deletable;
    }

    public void setDeletable(boolean deletable) {
        this.deletable = deletable;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getCurrentBatch() {
        return currentBatch;
    }

    public void setCurrentBatch(Integer currentBatch) {
        this.currentBatch = currentBatch;
    }

    public Integer getTotalBatches() {
        return totalBatches;
    }

    public void setTotalBatches(Integer totalBatches) {
        this.totalBatches = totalBatches;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(String effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public Integer getSourcePriority() {
        return sourcePriority;
    }

    public void setSourcePriority(Integer sourcePriority) {
        this.sourcePriority = sourcePriority;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getSegmentationMode() {
        return segmentationMode;
    }

    public void setSegmentationMode(String segmentationMode) {
        this.segmentationMode = segmentationMode;
    }

    public boolean isSemanticEnhanced() {
        return semanticEnhanced;
    }

    public void setSemanticEnhanced(boolean semanticEnhanced) {
        this.semanticEnhanced = semanticEnhanced;
    }

    public String getIndexUpgradeState() {
        return indexUpgradeState;
    }

    public void setIndexUpgradeState(String indexUpgradeState) {
        this.indexUpgradeState = indexUpgradeState;
    }

    public String getIndexUpgradeMessage() {
        return indexUpgradeMessage;
    }

    public void setIndexUpgradeMessage(String indexUpgradeMessage) {
        this.indexUpgradeMessage = indexUpgradeMessage;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public List<KnowledgeChunkInfo> getChunks() {
        return chunks;
    }

    public void setChunks(List<KnowledgeChunkInfo> chunks) {
        this.chunks = chunks;
    }
}
