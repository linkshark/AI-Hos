package com.linkjb.aimed.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyHistoryContentSanitizerTest {

    private final LegacyHistoryContentSanitizer sanitizer = new LegacyHistoryContentSanitizer(500);

    @Test
    void shouldStripPromptScaffoldingAndAttachmentSummary() {
        String raw = """
                以下是用户在本轮对话中上传的材料内容，仅用于当前问题，不写入长期知识库。
                [用户问题]
                原发性肝癌应该如何治疗
                附件：
                指南.pdf
                """;

        String sanitized = sanitizer.sanitize(raw, false);

        assertEquals("原发性肝癌应该如何治疗", sanitized);
    }

    @Test
    void shouldHideInjectedKnowledgeWhenDebugDisabled() {
        String raw = """
                Answer using the following information:
                医院系统集成解决方案
                证据等级 A
                推荐 B
                这里是一大段内部增强上下文
                """;

        String sanitized = sanitizer.sanitize(raw, false);

        assertEquals("", sanitized);
    }

    @Test
    void shouldKeepSanitizedQuestionWhenDebugEnabled() {
        String raw = """
                <hcsb>内部增强</hcsb>
                小孩子感冒发烧怎么处理比较好
                """;

        String sanitized = sanitizer.sanitize(raw, true);

        assertEquals("小孩子感冒发烧怎么处理比较好", sanitized);
    }
}
