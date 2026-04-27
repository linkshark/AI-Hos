package com.linkjb.aimed.service.chat;

import com.linkjb.aimed.service.ChatIntentAnalysisService;

/**
 * 聊天请求的路由决策结果。
 */
public record ChatRoutingResult(
        ChatIntentAnalysisService.ChatIntentResult intentResult,
        boolean mcpIntent,
        boolean ragRequired
) {
}
