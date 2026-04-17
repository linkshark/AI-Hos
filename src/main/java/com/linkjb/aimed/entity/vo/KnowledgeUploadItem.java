package com.linkjb.aimed.entity.vo;

public class KnowledgeUploadItem {
    private String hash;
    private String originalFilename;
    private String storedFilename;
    private String parser;
    private String status;
    private String message;
    private int extractedCharacters;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getParser() {
        return parser;
    }

    public void setParser(String parser) {
        this.parser = parser;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getExtractedCharacters() {
        return extractedCharacters;
    }

    public void setExtractedCharacters(int extractedCharacters) {
        this.extractedCharacters = extractedCharacters;
    }
}
