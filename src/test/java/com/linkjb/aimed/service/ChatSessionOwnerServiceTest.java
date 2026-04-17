package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.dto.response.chat.ChatHistoryItemResponse;
import com.linkjb.aimed.entity.ChatSessionOwner;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatSessionOwnerServiceTest {

    @Test
    void shouldBuildHistoryListItemFromMysqlSummaryOnly() {
        ChatSessionOwnerService service = new ChatSessionOwnerService(null);
        ChatSessionOwner owner = new ChatSessionOwner();
        owner.setMemoryId(1001L);
        owner.setUserId(7L);
        owner.setCustomTitle(null);
        owner.setFirstQuestion("原发性早期肝癌应该如何治疗");
        owner.setLastPreview("建议结合指南由医生评估");
        owner.setMessageCount(2);
        owner.setPinned(false);
        owner.setCreatedAt(LocalDateTime.of(2026, 4, 13, 16, 0));
        owner.setUpdatedAt(LocalDateTime.of(2026, 4, 13, 16, 20));

        ChatHistoryItemResponse item = service.toHistoryItem(owner);

        assertEquals(1001L, item.memoryId());
        assertEquals("原发性早期肝癌应该如何治疗", item.title());
        assertEquals("原发性早期肝癌应该如何治疗", item.firstQuestion());
        assertEquals("建议结合指南由医生评估", item.preview());
        assertEquals(2, item.messageCount());
        assertFalse(item.pinned());
    }
}
