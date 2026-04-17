package com.linkjb.aimed.entity.dto.response;

import java.time.OffsetDateTime;

public record TokenPairResponse(
        String accessToken,
        OffsetDateTime accessTokenExpiresAt,
        String refreshToken,
        OffsetDateTime refreshTokenExpiresAt,
        AuthUserResponse user
) {
}
