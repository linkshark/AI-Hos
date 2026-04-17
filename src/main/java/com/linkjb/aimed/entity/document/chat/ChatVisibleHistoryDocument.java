package com.linkjb.aimed.entity.document.chat;

import com.linkjb.aimed.entity.dto.response.chat.ChatHistoryMessageResponse;

import java.util.ArrayList;
import java.util.List;

public class ChatVisibleHistoryDocument {

    private Long memoryId;
    private Long userId;
    private String firstQuestion;
    private String lastPreview;
    private String updatedAt;
    private Long updatedAtEpochMillis;
    private List<ChatHistoryMessageResponse> messages = new ArrayList<>();

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFirstQuestion() {
        return firstQuestion;
    }

    public void setFirstQuestion(String firstQuestion) {
        this.firstQuestion = firstQuestion;
    }

    public String getLastPreview() {
        return lastPreview;
    }

    public void setLastPreview(String lastPreview) {
        this.lastPreview = lastPreview;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    public void setUpdatedAtEpochMillis(Long updatedAtEpochMillis) {
        this.updatedAtEpochMillis = updatedAtEpochMillis;
    }

    public List<ChatHistoryMessageResponse> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatHistoryMessageResponse> messages) {
        this.messages = messages;
    }
}
