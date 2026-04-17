package com.linkjb.aimed.entity.dto.response;

public record AuthUserResponse(
        Long id,
        String username,
        String email,
        String nickname,
        String role,
        String status
) {
}
