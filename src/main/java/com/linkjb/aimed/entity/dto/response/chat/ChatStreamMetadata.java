package com.linkjb.aimed.entity.dto.response.chat;

import com.linkjb.aimed.entity.vo.KnowledgeCitationItem;

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
    private String traceId;
    private String provider;
    private String toolMode;
    private String intentType;
    private String routeTarget;
    private boolean ragApplied;
    private String ragSkipReason;
    private long serverDurationMs;
    private long firstTokenLatencyMs;
    private List<ChatTraceStage> traceStages = new ArrayList<>();

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

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getToolMode() {
        return toolMode;
    }

    public void setToolMode(String toolMode) {
        this.toolMode = toolMode;
    }

    public String getIntentType() {
        return intentType;
    }

    public void setIntentType(String intentType) {
        this.intentType = intentType;
    }

    public String getRouteTarget() {
        return routeTarget;
    }

    public void setRouteTarget(String routeTarget) {
        this.routeTarget = routeTarget;
    }

    public boolean isRagApplied() {
        return ragApplied;
    }

    public void setRagApplied(boolean ragApplied) {
        this.ragApplied = ragApplied;
    }

    public String getRagSkipReason() {
        return ragSkipReason;
    }

    public void setRagSkipReason(String ragSkipReason) {
        this.ragSkipReason = ragSkipReason;
    }

    public long getServerDurationMs() {
        return serverDurationMs;
    }

    public void setServerDurationMs(long serverDurationMs) {
        this.serverDurationMs = serverDurationMs;
    }

    public long getFirstTokenLatencyMs() {
        return firstTokenLatencyMs;
    }

    public void setFirstTokenLatencyMs(long firstTokenLatencyMs) {
        this.firstTokenLatencyMs = firstTokenLatencyMs;
    }

    public List<ChatTraceStage> getTraceStages() {
        return traceStages;
    }

    public void setTraceStages(List<ChatTraceStage> traceStages) {
        this.traceStages = traceStages;
    }
}
