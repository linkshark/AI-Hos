package com.linkjb.aimed.entity.vo;

public class KnowledgeChunkInfo {
    private int index;
    private int characterCount;
    private String sectionTitle;
    private String segmentKind;
    private String segmentationMode;
    private String semanticSummary;
    private java.util.List<String> semanticKeywords;
    private String sectionRole;
    private java.util.List<String> medicalEntities;
    private java.util.List<String> targetQuestions;
    private String preview;
    private String content;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getCharacterCount() {
        return characterCount;
    }

    public void setCharacterCount(int characterCount) {
        this.characterCount = characterCount;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public void setSectionTitle(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }

    public String getSegmentKind() {
        return segmentKind;
    }

    public void setSegmentKind(String segmentKind) {
        this.segmentKind = segmentKind;
    }

    public String getSegmentationMode() {
        return segmentationMode;
    }

    public void setSegmentationMode(String segmentationMode) {
        this.segmentationMode = segmentationMode;
    }

    public String getSemanticSummary() {
        return semanticSummary;
    }

    public void setSemanticSummary(String semanticSummary) {
        this.semanticSummary = semanticSummary;
    }

    public java.util.List<String> getSemanticKeywords() {
        return semanticKeywords;
    }

    public void setSemanticKeywords(java.util.List<String> semanticKeywords) {
        this.semanticKeywords = semanticKeywords;
    }

    public String getSectionRole() {
        return sectionRole;
    }

    public void setSectionRole(String sectionRole) {
        this.sectionRole = sectionRole;
    }

    public java.util.List<String> getMedicalEntities() {
        return medicalEntities;
    }

    public void setMedicalEntities(java.util.List<String> medicalEntities) {
        this.medicalEntities = medicalEntities;
    }

    public java.util.List<String> getTargetQuestions() {
        return targetQuestions;
    }

    public void setTargetQuestions(java.util.List<String> targetQuestions) {
        this.targetQuestions = targetQuestions;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
