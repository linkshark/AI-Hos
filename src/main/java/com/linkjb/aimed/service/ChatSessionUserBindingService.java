package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.ChatSessionOwner;
import com.linkjb.aimed.mapper.ChatSessionOwnerMapper;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatSessionUserBindingService {

    private final ChatSessionOwnerMapper chatSessionOwnerMapper;

    public ChatSessionUserBindingService(ChatSessionOwnerMapper chatSessionOwnerMapper) {
        this.chatSessionOwnerMapper = chatSessionOwnerMapper;
    }

    public void bindOrValidateOwnership(Long memoryId, Long userId) {
        if (memoryId == null || userId == null) {
            return;
        }
        ChatSessionOwner owner = chatSessionOwnerMapper.selectById(memoryId);
        if (owner == null) {
            try {
                ChatSessionOwner created = new ChatSessionOwner();
                created.setMemoryId(memoryId);
                created.setUserId(userId);
                chatSessionOwnerMapper.insert(created);
                return;
            } catch (DuplicateKeyException ignored) {
                owner = chatSessionOwnerMapper.selectById(memoryId);
            }
        }
        if (!userId.equals(owner.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前会话已绑定到其他用户，禁止继续访问");
        }
    }

    public Long resolveUserId(Long memoryId) {
        if (memoryId == null) {
            return null;
        }
        ChatSessionOwner owner = chatSessionOwnerMapper.selectById(memoryId);
        return owner == null ? null : owner.getUserId();
    }
}
