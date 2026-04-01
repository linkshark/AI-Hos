package com.linkjb.aimed.security;

import com.linkjb.aimed.config.AuthProperties;
import com.linkjb.aimed.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private static final String TOKEN_TYPE_ACCESS = "access";
    private final AuthProperties authProperties;
    private final SecretKey secretKey;

    public JwtTokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.secretKey = Keys.hmacShaKeyFor(authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedAccessToken issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(authProperties.getAccessExpires());
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("type", TOKEN_TYPE_ACCESS)
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .signWith(secretKey)
                .compact();
        return new IssuedAccessToken(token, jti, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public ParsedAccessToken parseAccessToken(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        if (!TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("非法 access token");
        }
        return new ParsedAccessToken(
                Long.parseLong(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class),
                claims.getId(),
                claims.getExpiration().toInstant()
        );
    }

    public record IssuedAccessToken(String token, String jti, OffsetDateTime expiresAt) {
    }

    public record ParsedAccessToken(Long userId, String email, String role, String jti, Instant expiresAt) {
    }
}
