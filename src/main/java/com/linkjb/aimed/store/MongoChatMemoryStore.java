package com.linkjb.aimed.store;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedList;
import java.util.List;

@Component
public class MongoChatMemoryStore implements ChatMemoryStore {

    private static final String COLLECTION_NAME = "chat_messages";
    private static final Logger log = LoggerFactory.getLogger(MongoChatMemoryStore.class);

    private final MongoCollection<Document> collection;

    public MongoChatMemoryStore(MongoClient mongoClient,
                                @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/chat_memory_db}") String mongoUri) {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        String databaseName = StringUtils.hasText(connectionString.getDatabase())
                ? connectionString.getDatabase()
                : "chat_memory_db";
        this.collection = mongoClient.getDatabase(databaseName).getCollection(COLLECTION_NAME);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Long normalizedMemoryId = normalizeMemoryId(memoryId);
        if (normalizedMemoryId == null) {
            log.debug("对话记忆获取 memoryId=null size=0");
            return new LinkedList<>();
        }
        Document chatMessages = collection.find(Filters.eq("memoryId", normalizedMemoryId)).first();
        if (chatMessages == null) {
            log.debug("对话记忆创建 memoryId={} size=0", normalizedMemoryId);
            return new LinkedList<>();
        }

        String content = chatMessages.getString("content");
        if (!StringUtils.hasText(content)) {
            log.debug("无对话记忆-创建 memoryId={} size=0", normalizedMemoryId);
            return new LinkedList<>();
        }
        List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(content);
        log.debug("对话记忆获取 memoryId={} size={}", normalizedMemoryId, messages.size());
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Long normalizedMemoryId = normalizeMemoryId(memoryId);
        if (normalizedMemoryId == null) {
            log.debug("对话记忆保存更新 memoryId=null size=0");
            return;
        }
        log.debug("对话记忆保存更新 memoryId={} size={}", normalizedMemoryId, messages == null ? 0 : messages.size());
        collection.updateOne(
                Filters.eq("memoryId", normalizedMemoryId),
                Updates.combine(
                        Updates.set("memoryId", normalizedMemoryId),
                        Updates.set("content", ChatMessageSerializer.messagesToJson(messages))
                ),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Long normalizedMemoryId = normalizeMemoryId(memoryId);
        if (normalizedMemoryId == null) {
            return;
        }
        log.debug("对话记忆删除 memoryId={}", normalizedMemoryId);
        collection.deleteMany(Filters.eq("memoryId", normalizedMemoryId));
    }

    private Long normalizeMemoryId(Object memoryId) {
        if (memoryId == null) {
            return null;
        }
        if (memoryId instanceof Number number) {
            return number.longValue();
        }
        String value = String.valueOf(memoryId).trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("非法 memoryId: " + value, exception);
        }
    }
}
