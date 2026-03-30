package com.linkjb.aimed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.bean.KnowledgeProcessingMessage;
import com.linkjb.aimed.websocket.KnowledgeWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeProcessingNotifier {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeProcessingNotifier.class);

    private final ObjectMapper objectMapper;
    private final KnowledgeWebSocketHandler knowledgeWebSocketHandler;

    public KnowledgeProcessingNotifier(ObjectMapper objectMapper, KnowledgeWebSocketHandler knowledgeWebSocketHandler) {
        this.objectMapper = objectMapper;
        this.knowledgeWebSocketHandler = knowledgeWebSocketHandler;
    }

    public void notifyQueued(String hash, String originalFilename, String message) {
        broadcast("KNOWLEDGE_QUEUED", hash, originalFilename, "PROCESSING", message, 0, 0, 0);
    }

    public void notifyProgress(String hash,
                               String originalFilename,
                               String message,
                               Integer progressPercent,
                               Integer currentBatch,
                               Integer totalBatches) {
        broadcast("KNOWLEDGE_PROGRESS", hash, originalFilename, "PROCESSING", message, progressPercent, currentBatch, totalBatches);
    }

    public void notifyCompleted(String hash, String originalFilename, String message) {
        broadcast("KNOWLEDGE_READY", hash, originalFilename, "READY", message, 100, 0, 0);
    }

    public void notifyFailed(String hash,
                             String originalFilename,
                             String message,
                             Integer progressPercent,
                             Integer currentBatch,
                             Integer totalBatches) {
        broadcast("KNOWLEDGE_FAILED", hash, originalFilename, "FAILED", message, progressPercent, currentBatch, totalBatches);
    }

    private void broadcast(String eventType,
                           String hash,
                           String originalFilename,
                           String processingStatus,
                           String message,
                           Integer progressPercent,
                           Integer currentBatch,
                           Integer totalBatches) {
        KnowledgeProcessingMessage payload = new KnowledgeProcessingMessage();
        payload.setEventType(eventType);
        payload.setHash(hash);
        payload.setOriginalFilename(originalFilename);
        payload.setProcessingStatus(processingStatus);
        payload.setStatusMessage(message);
        payload.setProgressPercent(progressPercent);
        payload.setCurrentBatch(currentBatch);
        payload.setTotalBatches(totalBatches);
        try {
            knowledgeWebSocketHandler.broadcast(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.warn("知识库处理通知序列化失败, hash={}", hash, e);
        }
    }
}
