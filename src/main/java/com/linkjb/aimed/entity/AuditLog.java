package com.linkjb.aimed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorUserId;
    private String actorRole;
    private String actionType;
    private String targetType;
    private String targetId;
    private String summary;
    private String traceId;
    private Long memoryId;
    private String provider;
    private String queryType;
    private Integer retrievedCountKeyword;
    private Integer retrievedCountVector;
    private Integer mergedCount;
    private Integer finalCitationCount;
    private Boolean emptyRecall;
    private String topDocHashes;
    private Long durationMs;
    private Boolean hasAttachments;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public Integer getRetrievedCountKeyword() {
        return retrievedCountKeyword;
    }

    public void setRetrievedCountKeyword(Integer retrievedCountKeyword) {
        this.retrievedCountKeyword = retrievedCountKeyword;
    }

    public Integer getRetrievedCountVector() {
        return retrievedCountVector;
    }

    public void setRetrievedCountVector(Integer retrievedCountVector) {
        this.retrievedCountVector = retrievedCountVector;
    }

    public Integer getMergedCount() {
        return mergedCount;
    }

    public void setMergedCount(Integer mergedCount) {
        this.mergedCount = mergedCount;
    }

    public Integer getFinalCitationCount() {
        return finalCitationCount;
    }

    public void setFinalCitationCount(Integer finalCitationCount) {
        this.finalCitationCount = finalCitationCount;
    }

    public Boolean getEmptyRecall() {
        return emptyRecall;
    }

    public void setEmptyRecall(Boolean emptyRecall) {
        this.emptyRecall = emptyRecall;
    }

    public String getTopDocHashes() {
        return topDocHashes;
    }

    public void setTopDocHashes(String topDocHashes) {
        this.topDocHashes = topDocHashes;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Boolean getHasAttachments() {
        return hasAttachments;
    }

    public void setHasAttachments(Boolean hasAttachments) {
        this.hasAttachments = hasAttachments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
