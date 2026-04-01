package com.linkjb.aimed.security;

public record AuthenticatedUser(
        Long userId,
        String email,
        String role
) {
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
