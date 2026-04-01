package com.linkjb.aimed.bean;

public record AuthUserResponse(
        Long id,
        String username,
        String email,
        String nickname,
        String role,
        String status
) {
}
