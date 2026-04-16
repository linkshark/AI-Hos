package com.linkjb.aimed.service;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 旧历史内容清洗器。
 *
 * 早期会话里可能混入 RAG 包装层、附件摘要或内部增强上下文。
 * 当前聊天恢复默认只还原“用户实际看到的问题”，因此这层负责把内部脚手架剥离掉。
 */
final class LegacyHistoryContentSanitizer {

    private final int legacyContextLengthThreshold;

    LegacyHistoryContentSanitizer(int legacyContextLengthThreshold) {
        this.legacyContextLengthThreshold = legacyContextLengthThreshold;
    }

    String sanitize(String content, boolean includeDebugDetails) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String normalized = content.trim();
        // 历史恢复默认只保留用户当时看到的提问，RAG 包装层和附件增强提示都在这里统一剥掉。
        normalized = stripInternalPromptScaffolding(normalized);
        normalized = stripAttachmentSummary(normalized);
        if (!includeDebugDetails && looksLikeInjectedKnowledge(normalized)) {
            return "";
        }
        return normalized;
    }

    String stripInternalPromptScaffolding(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String normalized = content.trim();
        normalized = stripByMarker(normalized, "[用户问题]");
        normalized = stripByMarker(normalized, "Answer using the following information:");
        normalized = stripByClosingMarker(normalized, "</hcsb>");
        return normalized;
    }

    String stripAttachmentSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        int attachmentIndex = content.indexOf("\n附件：");
        if (attachmentIndex > 0) {
            String visibleQuestion = content.substring(0, attachmentIndex).trim();
            if (StringUtils.hasText(visibleQuestion)) {
                return visibleQuestion;
            }
        }
        return content;
    }

    boolean looksLikeInjectedKnowledge(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String normalized = content.trim();
        int newlineCount = 0;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '\n') {
                newlineCount++;
            }
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.length() > legacyContextLengthThreshold
                || newlineCount > 8
                || lower.contains("医院系统集成解决方案")
                || lower.contains("answer using the following information")
                || lower.contains("证据等级")
                || lower.contains("推荐 a")
                || lower.contains("推荐 b");
    }

    private String stripByMarker(String content, String marker) {
        int markerIndex = content.lastIndexOf(marker);
        if (markerIndex < 0) {
            return content;
        }
        String suffix = content.substring(markerIndex + marker.length()).trim();
        return StringUtils.hasText(suffix) ? suffix : content;
    }

    private String stripByClosingMarker(String content, String marker) {
        int markerIndex = content.lastIndexOf(marker);
        if (markerIndex < 0) {
            return content;
        }
        String suffix = content.substring(markerIndex + marker.length()).trim();
        return StringUtils.hasText(suffix) ? suffix : content;
    }
}
