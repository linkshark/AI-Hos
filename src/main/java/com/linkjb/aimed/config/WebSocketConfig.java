package com.linkjb.aimed.config;

import com.linkjb.aimed.websocket.KnowledgeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final KnowledgeWebSocketHandler knowledgeWebSocketHandler;
    private final KnowledgeWebSocketAuthInterceptor knowledgeWebSocketAuthInterceptor;

    public WebSocketConfig(KnowledgeWebSocketHandler knowledgeWebSocketHandler,
                           KnowledgeWebSocketAuthInterceptor knowledgeWebSocketAuthInterceptor) {
        this.knowledgeWebSocketHandler = knowledgeWebSocketHandler;
        this.knowledgeWebSocketAuthInterceptor = knowledgeWebSocketAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(knowledgeWebSocketHandler, "/ws/knowledge")
                .addInterceptors(knowledgeWebSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
