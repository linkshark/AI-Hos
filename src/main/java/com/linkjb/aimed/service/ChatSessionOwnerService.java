package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.linkjb.aimed.entity.ChatSessionOwner;
import com.linkjb.aimed.mapper.ChatSessionOwnerMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatSessionOwnerService {

    private final ChatSessionOwnerMapper chatSessionOwnerMapper;

    public ChatSessionOwnerService(ChatSessionOwnerMapper chatSessionOwnerMapper) {
        this.chatSessionOwnerMapper = chatSessionOwnerMapper;
    }

    public ChatSessionOwner findByMemoryId(Long memoryId) {
        return memoryId == null ? null : chatSessionOwnerMapper.selectById(memoryId);
    }

    public List<ChatSessionOwner> listVisibleByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return chatSessionOwnerMapper.selectList(new LambdaQueryWrapper<ChatSessionOwner>()
                .eq(ChatSessionOwner::getUserId, userId)
                .and(wrapper -> wrapper.isNull(ChatSessionOwner::getHidden)
                        .or()
                        .eq(ChatSessionOwner::getHidden, false))
                .orderByDesc(ChatSessionOwner::getPinned)
                .orderByDesc(ChatSessionOwner::getPinnedAt)
                .orderByDesc(ChatSessionOwner::getUpdatedAt)
                .orderByDesc(ChatSessionOwner::getCreatedAt));
    }

    public void create(ChatSessionOwner owner) {
        chatSessionOwnerMapper.insert(owner);
    }

    public void touch(Long memoryId) {
        if (memoryId == null) {
            return;
        }
        chatSessionOwnerMapper.update(null, new LambdaUpdateWrapper<ChatSessionOwner>()
                .eq(ChatSessionOwner::getMemoryId, memoryId)
                .set(ChatSessionOwner::getUpdatedAt, LocalDateTime.now()));
    }

    public void rename(Long memoryId, String title) {
        if (memoryId == null) {
            return;
        }
        chatSessionOwnerMapper.update(null, new LambdaUpdateWrapper<ChatSessionOwner>()
                .eq(ChatSessionOwner::getMemoryId, memoryId)
                .set(ChatSessionOwner::getCustomTitle, title)
                .set(ChatSessionOwner::getUpdatedAt, LocalDateTime.now()));
    }

    public void updatePinned(Long memoryId, boolean pinned) {
        if (memoryId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        chatSessionOwnerMapper.update(null, new LambdaUpdateWrapper<ChatSessionOwner>()
                .eq(ChatSessionOwner::getMemoryId, memoryId)
                .set(ChatSessionOwner::getPinned, pinned)
                .set(ChatSessionOwner::getPinnedAt, pinned ? now : null)
                .set(ChatSessionOwner::getUpdatedAt, now));
    }

    public void hide(Long memoryId) {
        if (memoryId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        chatSessionOwnerMapper.update(null, new LambdaUpdateWrapper<ChatSessionOwner>()
                .eq(ChatSessionOwner::getMemoryId, memoryId)
                .set(ChatSessionOwner::getHidden, true)
                .set(ChatSessionOwner::getHiddenAt, now)
                .set(ChatSessionOwner::getPinned, false)
                .set(ChatSessionOwner::getPinnedAt, null)
                .set(ChatSessionOwner::getUpdatedAt, now));
    }
}
