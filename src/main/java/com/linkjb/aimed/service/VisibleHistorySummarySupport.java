package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.chat.ChatHistoryMessageResponse;
import com.linkjb.aimed.bean.chat.ChatVisibleHistoryDocument;
import com.linkjb.aimed.entity.ChatSessionOwner;
import com.linkjb.aimed.store.MongoVisibleChatHistoryStore.VisibleHistorySummary;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * visible history 的摘要计算器。
 *
 * Mongo 中保存完整可见消息，MySQL 中只保留列表页需要的摘要字段。
 * 这层负责把标题、预览、更新时间等摘要规则集中起来，避免双写逻辑散落在主服务里。
 */
final class VisibleHistorySummarySupport {

    private final DateTimeFormatter dateTimeFormatter;

    VisibleHistorySummarySupport(DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    void refreshVisibleHistorySummary(ChatVisibleHistoryDocument history) {
        if (history == null) {
            return;
        }
        history.setFirstQuestion(findFirstQuestion(history.getMessages()));
        history.setLastPreview(summarizePreview(history.getMessages()));
        LocalDateTime now = LocalDateTime.now();
        history.setUpdatedAt(formatDateTime(now));
        history.setUpdatedAtEpochMillis(toEpochMillis(now));
    }

    boolean hasMeaningfulVisibleContent(ChatVisibleHistoryDocument history) {
        return history != null && hasMeaningfulVisibleMessages(history.getMessages());
    }

    boolean hasMeaningfulVisibleMessages(List<ChatHistoryMessageResponse> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return messages.stream().anyMatch(message ->
                message != null
                        && message.user()
                        && (StringUtils.hasText(message.content())
                        || (message.attachments() != null && !message.attachments().isEmpty()))
        );
    }

    String summarizeTitle(List<ChatHistoryMessageResponse> messages) {
        for (ChatHistoryMessageResponse message : messages) {
            if (message.user() && StringUtils.hasText(message.content())) {
                return abbreviate(singleLine(message.content()), 22);
            }
        }
        return "未命名对话";
    }

    LocalDateTime visibleHistoryUpdatedAt(VisibleHistorySummary summary, LocalDateTime fallback) {
        if (summary != null && summary.updatedAtEpochMillis() != null) {
            return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(summary.updatedAtEpochMillis()),
                    ZoneId.systemDefault()
            );
        }
        return fallback == null ? LocalDateTime.now() : fallback;
    }

    boolean shouldRefreshOwnerSummary(ChatSessionOwner owner,
                                      VisibleHistorySummary summary,
                                      LocalDateTime visibleUpdatedAt) {
        if (!Objects.equals(owner.getFirstQuestion(), summary.firstQuestion())
                || !Objects.equals(owner.getLastPreview(), summary.lastPreview())) {
            return true;
        }
        LocalDateTime currentUpdatedAt = owner.getUpdatedAt();
        if (currentUpdatedAt == null || visibleUpdatedAt == null) {
            return currentUpdatedAt != visibleUpdatedAt;
        }
        return Math.abs(java.time.Duration.between(currentUpdatedAt, visibleUpdatedAt).toMillis()) > 1_000;
    }

    String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String findFirstQuestion(List<ChatHistoryMessageResponse> messages) {
        for (ChatHistoryMessageResponse message : messages) {
            if (message.user()
                    && (StringUtils.hasText(message.content())
                    || (message.attachments() != null && !message.attachments().isEmpty()))) {
                if (StringUtils.hasText(message.content())) {
                    return message.content().trim();
                }
                return "附件对话";
            }
        }
        return null;
    }

    private String summarizePreview(List<ChatHistoryMessageResponse> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        ChatHistoryMessageResponse last = messages.get(messages.size() - 1);
        return abbreviate(singleLine(last.content()), 40);
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
        return value == null ? null : value.format(dateTimeFormatter);
    }

    private Long toEpochMillis(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
