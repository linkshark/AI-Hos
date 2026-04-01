package com.linkjb.aimed.config;

import com.linkjb.aimed.security.JwtTokenService;
import com.linkjb.aimed.service.RedisAuthStateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class KnowledgeWebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtTokenService jwtTokenService;
    private final RedisAuthStateService redisAuthStateService;

    public KnowledgeWebSocketAuthInterceptor(JwtTokenService jwtTokenService, RedisAuthStateService redisAuthStateService) {
        this.jwtTokenService = jwtTokenService;
        this.redisAuthStateService = redisAuthStateService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            JwtTokenService.ParsedAccessToken parsed = jwtTokenService.parseAccessToken(token);
            if (redisAuthStateService.isAccessTokenBlacklisted(parsed.jti())) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            // 知识库处理通知只开放给管理员，避免普通用户监听后台任务状态。
            if (!"ADMIN".equalsIgnoreCase(parsed.role())) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put("userId", parsed.userId());
            attributes.put("role", parsed.role());
            return true;
        } catch (Exception exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private String resolveToken(ServerHttpRequest request) {
        String queryToken = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("access_token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authorization = servletRequest.getServletRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
                return authorization.substring(7);
            }
        }
        return null;
    }
}
