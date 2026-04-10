package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.ChatSessionOwner;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class ChatSessionUserBindingService {

    private final ChatSessionOwnerService chatSessionOwnerService;

    public ChatSessionUserBindingService(ChatSessionOwnerService chatSessionOwnerService) {
        this.chatSessionOwnerService = chatSessionOwnerService;
    }

    public void bindOrValidateOwnership(Long memoryId, Long userId) {
        if (memoryId == null || userId == null) {
            return;
        }
        ChatSessionOwner owner = chatSessionOwnerService.findByMemoryId(memoryId);
        if (owner == null) {
            try {
                ChatSessionOwner created = new ChatSessionOwner();
                created.setMemoryId(memoryId);
                created.setUserId(userId);
                created.setPinned(false);
                created.setHidden(false);
                chatSessionOwnerService.create(created);
                return;
            } catch (DuplicateKeyException ignored) {
                owner = chatSessionOwnerService.findByMemoryId(memoryId);
            }
        }
        if (!userId.equals(owner.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前会话已绑定到其他用户，禁止继续访问");
        }
        chatSessionOwnerService.touch(memoryId);
    }

    public Long resolveUserId(Long memoryId) {
        if (memoryId == null) {
            return null;
        }
        ChatSessionOwner owner = chatSessionOwnerService.findByMemoryId(memoryId);
        return owner == null ? null : owner.getUserId();
    }

    public void validateOwnership(Long memoryId, Long userId) {
        if (memoryId == null || userId == null) {
            return;
        }
        ChatSessionOwner owner = chatSessionOwnerService.findByMemoryId(memoryId);
        if (owner == null || !userId.equals(owner.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前会话未授权访问");
        }
    }

    public Long createSession(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前用户未登录");
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            Long memoryId = nextMemoryId();
            ChatSessionOwner existing = chatSessionOwnerService.findByMemoryId(memoryId);
            if (existing != null) {
                continue;
            }
            try {
                ChatSessionOwner created = new ChatSessionOwner();
                created.setMemoryId(memoryId);
                created.setUserId(userId);
                created.setPinned(false);
                created.setHidden(false);
                chatSessionOwnerService.create(created);
                return memoryId;
            } catch (DuplicateKeyException ignored) {
                // 极低概率撞号时重试下一轮。
            }
        }
        throw new IllegalStateException("创建新会话失败，请稍后重试");
    }

    private Long nextMemoryId() {
        long base = System.currentTimeMillis() * 1000L;
        int suffix = ThreadLocalRandom.current().nextInt(100, 1000);
        return base + suffix;
    }
}
