package com.linkjb.aimed.entity.vo;

import java.time.LocalDateTime;

public record AdminUserItem(
        Long id,
        String username,
        String email,
        String nickname,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
}
