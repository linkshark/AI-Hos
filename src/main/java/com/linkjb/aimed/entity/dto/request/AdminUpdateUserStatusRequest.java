package com.linkjb.aimed.entity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminUpdateUserStatusRequest(
        @NotBlank(message = "状态不能为空")
        String status
) {
}
