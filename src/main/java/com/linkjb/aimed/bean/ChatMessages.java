package com.linkjb.aimed.bean;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("chat_messages")
public class ChatMessages {
    // 1. 文档id，唯一标识，映射到 MongoDB 文档的 _id 字段
    @Id // 采用ObjectId类型可使MongoDB自动生成messageId
    private ObjectId messageId;
//    private Long messageId;

    // 2. 聊天记忆id
    private Long memoryId;

    private String content; //存储当前聊天记录列表的json字符串

    public ChatMessages() {
    }

    public ChatMessages(ObjectId messageId, Long memoryId, String content) {
        this.messageId = messageId;
        this.memoryId = memoryId;
        this.content = content;
    }

    public ObjectId getMessageId() {
        return messageId;
    }

    public void setMessageId(ObjectId messageId) {
        this.messageId = messageId;
    }

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
