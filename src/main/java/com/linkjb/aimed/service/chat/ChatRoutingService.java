package com.linkjb.aimed.service.chat;

import com.linkjb.aimed.service.ChatIntentAnalysisService;
import org.springframework.stereotype.Service;

/**
 * 聊天路由诊断服务。
 *
 * 它只负责把原始用户问题归一化成“普通问答 / RAG / MCP”这类执行决策，
 * 不直接触碰模型调用、流式输出或 metadata 组装。
 */
@Service
public class ChatRoutingService {

    private final ChatIntentAnalysisService chatIntentAnalysisService;

    public ChatRoutingService(ChatIntentAnalysisService chatIntentAnalysisService) {
        this.chatIntentAnalysisService = chatIntentAnalysisService;
    }

    public ChatRoutingResult analyze(String query) {
        ChatIntentAnalysisService.ChatIntentResult intentResult = chatIntentAnalysisService.analyze(query);
        boolean mcpIntent = intentResult != null && !intentResult.ragRequired() && "MCP".equals(intentResult.routeTarget());
        return new ChatRoutingResult(intentResult, mcpIntent, intentResult == null || intentResult.ragRequired());
    }
}
