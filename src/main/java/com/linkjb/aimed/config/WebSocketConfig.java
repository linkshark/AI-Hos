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
    private final AppSecurityProperties appSecurityProperties;

    public WebSocketConfig(KnowledgeWebSocketHandler knowledgeWebSocketHandler,
                           KnowledgeWebSocketAuthInterceptor knowledgeWebSocketAuthInterceptor,
                           AppSecurityProperties appSecurityProperties) {
        this.knowledgeWebSocketHandler = knowledgeWebSocketHandler;
        this.knowledgeWebSocketAuthInterceptor = knowledgeWebSocketAuthInterceptor;
        this.appSecurityProperties = appSecurityProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(knowledgeWebSocketHandler, "/ws/knowledge")
                .addInterceptors(knowledgeWebSocketAuthInterceptor)
                .setAllowedOriginPatterns(appSecurityProperties.getAllowedOriginPatterns().toArray(String[]::new));
    }
}
