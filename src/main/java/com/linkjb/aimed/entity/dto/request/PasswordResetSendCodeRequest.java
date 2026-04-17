package com.linkjb.aimed.entity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetSendCodeRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "请输入合法邮箱")
        String email
) {
}
