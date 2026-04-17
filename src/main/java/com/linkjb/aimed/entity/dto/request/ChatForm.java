package com.linkjb.aimed.entity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChatForm {
    @NotNull(message = "会话标识不能为空")
    private Long memoryId;//对话id
    @NotBlank(message = "消息内容不能为空")
    private String message;//用户问题
    private String modelProvider;//模型提供方

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }
}
