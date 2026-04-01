package com.linkjb.aimed.bean;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "refresh token 不能为空")
        String refreshToken
) {
}
