package com.linkjb.aimed.bean;

import java.util.ArrayList;
import java.util.List;

public class ChatStreamMetadata {
    private List<KnowledgeCitationItem> citations = new ArrayList<>();
    private String queryType;
    private int retrievedCountKeyword;
    private int retrievedCountVector;
    private int mergedCount;
    private int finalCitationCount;
    private boolean emptyRecall;
    private long durationMs;
    private List<String> topDocHashes = new ArrayList<>();

    public List<KnowledgeCitationItem> getCitations() {
        return citations;
    }

    public void setCitations(List<KnowledgeCitationItem> citations) {
        this.citations = citations;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public int getRetrievedCountKeyword() {
        return retrievedCountKeyword;
    }

    public void setRetrievedCountKeyword(int retrievedCountKeyword) {
        this.retrievedCountKeyword = retrievedCountKeyword;
    }

    public int getRetrievedCountVector() {
        return retrievedCountVector;
    }

    public void setRetrievedCountVector(int retrievedCountVector) {
        this.retrievedCountVector = retrievedCountVector;
    }

    public int getMergedCount() {
        return mergedCount;
    }

    public void setMergedCount(int mergedCount) {
        this.mergedCount = mergedCount;
    }

    public int getFinalCitationCount() {
        return finalCitationCount;
    }

    public void setFinalCitationCount(int finalCitationCount) {
        this.finalCitationCount = finalCitationCount;
    }

    public boolean isEmptyRecall() {
        return emptyRecall;
    }

    public void setEmptyRecall(boolean emptyRecall) {
        this.emptyRecall = emptyRecall;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public List<String> getTopDocHashes() {
        return topDocHashes;
    }

    public void setTopDocHashes(List<String> topDocHashes) {
        this.topDocHashes = topDocHashes;
    }
}
