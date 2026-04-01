package com.linkjb.aimed.bean;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "请输入合法邮箱")
        String email,
        @NotBlank(message = "验证码不能为空")
        String code,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 位之间")
        String password,
        @NotBlank(message = "确认密码不能为空")
        String confirmPassword,
        String nickname
) {
}
