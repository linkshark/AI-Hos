package com.linkjb.aimed.entity.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatHistoryRenameRequest {

    @NotBlank(message = "会话标题不能为空")
    @Size(max = 128, message = "会话标题长度不能超过 128 个字符")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
