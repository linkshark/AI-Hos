package com.linkjb.aimed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.config.AuthProperties;
import com.linkjb.aimed.entity.AppUser;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class RedisAuthStateService {

    private static final String REGISTER_PURPOSE = "register";
    private static final String RESET_PASSWORD_PURPOSE = "reset-password";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties authProperties;

    public RedisAuthStateService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
    }

    public String storeRegisterCode(String email, String code) {
        String key = verificationCodeKey(email, REGISTER_PURPOSE);
        redisTemplate.opsForValue().set(key, code, authProperties.getVerificationExpires());
        return key;
    }

    public boolean consumeRegisterCode(String email, String code) {
        String key = verificationCodeKey(email, REGISTER_PURPOSE);
        String cached = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(cached) || !cached.equalsIgnoreCase(code == null ? "" : code.trim())) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    public String storePasswordResetCode(String email, String code) {
        String key = verificationCodeKey(email, RESET_PASSWORD_PURPOSE);
        redisTemplate.opsForValue().set(key, code, authProperties.getVerificationExpires());
        return key;
    }

    public boolean consumePasswordResetCode(String email, String code) {
        String key = verificationCodeKey(email, RESET_PASSWORD_PURPOSE);
        String cached = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(cached) || !cached.equalsIgnoreCase(code == null ? "" : code.trim())) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    public RefreshSession issueRefreshToken(AppUser user) {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(authProperties.getRefreshExpires());
        // Refresh token 在 Redis 中持久化，便于主动失效和服务端续签控制。
        RefreshPayload payload = new RefreshPayload(user.getId(), user.getEmail(), user.getRole(), user.getNickname(), expiresAt);
        try {
            redisTemplate.opsForValue().set(refreshTokenKey(token), objectMapper.writeValueAsString(payload), authProperties.getRefreshExpires());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存登录状态", exception);
        }
        return new RefreshSession(token, expiresAt, payload);
    }

    public RefreshPayload getRefreshPayload(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String payload = redisTemplate.opsForValue().get(refreshTokenKey(token));
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, RefreshPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法解析登录状态", exception);
        }
    }

    public void revokeRefreshToken(String token) {
        if (StringUtils.hasText(token)) {
            redisTemplate.delete(refreshTokenKey(token));
        }
    }

    public void blacklistAccessToken(String jti, Instant expiresAt) {
        if (!StringUtils.hasText(jti) || expiresAt == null) {
            return;
        }
        // Access token 仍保持 JWT 无状态；仅在 logout 后把 jti 拉入短期黑名单。
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(accessBlacklistKey(jti), "1", ttl);
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        return StringUtils.hasText(jti) && Boolean.TRUE.equals(redisTemplate.hasKey(accessBlacklistKey(jti)));
    }

    private String verificationCodeKey(String email, String purpose) {
        return "aimed:auth:code:" + purpose + ":" + normalizeEmail(email);
    }

    private String refreshTokenKey(String token) {
        return "aimed:auth:refresh:" + token;
    }

    private String accessBlacklistKey(String jti) {
        return "aimed:auth:access:blacklist:" + jti;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    public record RefreshSession(String token, OffsetDateTime expiresAt, RefreshPayload payload) {
    }

    public record RefreshPayload(Long userId, String email, String role, String nickname, OffsetDateTime expiresAt) {
    }
}
