package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linkjb.aimed.bean.PagedResponse;
import com.linkjb.aimed.bean.chat.ChatHistoryItemResponse;
import com.linkjb.aimed.entity.ChatSessionOwner;
import com.linkjb.aimed.mapper.ChatSessionOwnerMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public PagedResponse<ChatHistoryItemResponse> listVisibleSummaryPage(Long userId, int page, int size, String keyword) {
        if (userId == null) {
            return new PagedResponse<>(0, Math.max(1, page), Math.max(1, size), List.of());
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        LambdaQueryWrapper<ChatSessionOwner> wrapper = visibleSummaryWrapper(userId);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(condition -> condition
                    .like(ChatSessionOwner::getCustomTitle, normalizedKeyword)
                    .or()
                    .like(ChatSessionOwner::getFirstQuestion, normalizedKeyword)
                    .or()
                    .like(ChatSessionOwner::getLastPreview, normalizedKeyword));
        }
        Page<ChatSessionOwner> resultPage = chatSessionOwnerMapper.selectPage(
                new Page<>(safePage, safeSize),
                wrapper.orderByDesc(ChatSessionOwner::getPinned)
                        .orderByDesc(ChatSessionOwner::getPinnedAt)
                        .orderByDesc(ChatSessionOwner::getUpdatedAt)
                        .orderByDesc(ChatSessionOwner::getCreatedAt)
        );
        List<ChatHistoryItemResponse> items = resultPage.getRecords().stream()
                .map(this::toHistoryItem)
                .toList();
        return new PagedResponse<>(resultPage.getTotal(), safePage, safeSize, items);
    }

    public List<ChatSessionOwner> listVisibleMissingSummaryByUserId(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        return chatSessionOwnerMapper.selectList(visibleOwnerWrapper(userId)
                .and(wrapper -> wrapper.isNull(ChatSessionOwner::getFirstQuestion)
                        .or()
                        .eq(ChatSessionOwner::getFirstQuestion, ""))
                .orderByDesc(ChatSessionOwner::getUpdatedAt)
                .last("LIMIT " + Math.max(1, limit)));
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

    public void updateSummary(Long memoryId,
                              String firstQuestion,
                              String lastPreview,
                              int messageCount,
                              LocalDateTime updatedAt) {
        if (memoryId == null) {
            return;
        }
        chatSessionOwnerMapper.update(null, new LambdaUpdateWrapper<ChatSessionOwner>()
                .eq(ChatSessionOwner::getMemoryId, memoryId)
                .set(ChatSessionOwner::getFirstQuestion, firstQuestion)
                .set(ChatSessionOwner::getLastPreview, lastPreview)
                .set(ChatSessionOwner::getMessageCount, Math.max(0, messageCount))
                .set(ChatSessionOwner::getUpdatedAt, updatedAt == null ? LocalDateTime.now() : updatedAt));
    }

    public void rename(Long memoryId, String title) {
        if (memoryId == null) {
            return;
        }
        chatSessionOwnerMapper.update(null, new LambdaUpdateWrapper<ChatSessionOwner>()
                .eq(ChatSessionOwner::getMemoryId, memoryId)
                .set(ChatSessionOwner::getCustomTitle, title)
                .setSql("updated_at = updated_at"));
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
                .setSql("updated_at = updated_at"));
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

    private LambdaQueryWrapper<ChatSessionOwner> visibleOwnerWrapper(Long userId) {
        return new LambdaQueryWrapper<ChatSessionOwner>()
                .eq(ChatSessionOwner::getUserId, userId)
                .and(wrapper -> wrapper.isNull(ChatSessionOwner::getHidden)
                        .or()
                        .eq(ChatSessionOwner::getHidden, false));
    }

    private LambdaQueryWrapper<ChatSessionOwner> visibleSummaryWrapper(Long userId) {
        return visibleOwnerWrapper(userId)
                .isNotNull(ChatSessionOwner::getFirstQuestion)
                .ne(ChatSessionOwner::getFirstQuestion, "");
    }

    public ChatHistoryItemResponse toHistoryItem(ChatSessionOwner owner) {
        String firstQuestion = owner.getFirstQuestion();
        String defaultTitle = abbreviate(singleLine(firstQuestion), 22);
        String displayTitle = StringUtils.hasText(owner.getCustomTitle()) ? owner.getCustomTitle().trim() : defaultTitle;
        return new ChatHistoryItemResponse(
                owner.getMemoryId(),
                displayTitle,
                owner.getCustomTitle(),
                firstQuestion,
                owner.getLastPreview(),
                formatDateTime(owner.getUpdatedAt() != null ? owner.getUpdatedAt() : owner.getCreatedAt()),
                toEpochMillis(owner.getUpdatedAt() != null ? owner.getUpdatedAt() : owner.getCreatedAt()),
                owner.getMessageCount() == null ? 0 : owner.getMessageCount(),
                Boolean.TRUE.equals(owner.getPinned())
        );
    }

    private String singleLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private Long toEpochMillis(LocalDateTime value) {
        return value == null ? null : value.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
