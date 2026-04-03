package com.linkjb.aimed.bean;

import jakarta.validation.constraints.NotBlank;

public record AdminUpdateUserRoleRequest(
        @NotBlank(message = "角色不能为空")
        String role
) {
}
