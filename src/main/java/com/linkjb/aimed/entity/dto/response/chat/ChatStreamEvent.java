package com.linkjb.aimed.entity.dto.response.chat;

public class ChatStreamEvent {
    private String type;
    private String phase;
    private String status;
    private String label;
    private String detail;
    private String toolName;
    private long durationMs;

    public ChatStreamEvent() {
    }

    public ChatStreamEvent(String type,
                           String phase,
                           String status,
                           String label,
                           String detail,
                           String toolName,
                           long durationMs) {
        this.type = type;
        this.phase = phase;
        this.status = status;
        this.label = label;
        this.detail = detail;
        this.toolName = toolName;
        this.durationMs = durationMs;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
