package com.linkjb.aimed.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.linkjb.aimed.bean.chat.ChatVisibleHistoryDocument;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MongoVisibleChatHistoryStore {

    private static final String COLLECTION_NAME = "chat_visible_history";

    private final MongoCollection<Document> collection;
    private final ObjectMapper objectMapper;

    public MongoVisibleChatHistoryStore(MongoClient mongoClient,
                                        ObjectMapper objectMapper,
                                        @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/chat_memory_db}") String mongoUri) {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        String databaseName = StringUtils.hasText(connectionString.getDatabase())
                ? connectionString.getDatabase()
                : "chat_memory_db";
        this.collection = mongoClient.getDatabase(databaseName).getCollection(COLLECTION_NAME);
        this.objectMapper = objectMapper;
    }

    public ChatVisibleHistoryDocument get(Long memoryId) {
        if (memoryId == null) {
            return null;
        }
        Document document = collection.find(Filters.eq("memoryId", memoryId)).first();
        if (document == null) {
            return null;
        }
        String content = document.getString("content");
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            return objectMapper.readValue(content, ChatVisibleHistoryDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("解析用户可见历史失败", exception);
        }
    }

    public void save(ChatVisibleHistoryDocument history) {
        if (history == null || history.getMemoryId() == null) {
            return;
        }
        try {
            String content = objectMapper.writeValueAsString(history);
            collection.updateOne(
                    Filters.eq("memoryId", history.getMemoryId()),
                    Updates.combine(
                            Updates.set("memoryId", history.getMemoryId()),
                            Updates.set("userId", history.getUserId()),
                            Updates.set("firstQuestion", history.getFirstQuestion()),
                            Updates.set("lastPreview", history.getLastPreview()),
                            Updates.set("updatedAt", history.getUpdatedAt()),
                            Updates.set("updatedAtEpochMillis", history.getUpdatedAtEpochMillis()),
                            Updates.set("content", content)
                    ),
                    new UpdateOptions().upsert(true)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("保存用户可见历史失败", exception);
        }
    }
}
