package com.linkjb.aimed.bean.chat;

public class ChatTraceStage {

    private String key;
    private String label;
    private long durationMs;
    private String status;
    private String detail;

    public ChatTraceStage() {
    }

    public ChatTraceStage(String key, String label, long durationMs, String status, String detail) {
        this.key = key;
        this.label = label;
        this.durationMs = durationMs;
        this.status = status;
        this.detail = detail;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
