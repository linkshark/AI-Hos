package com.linkjb.aimed.entity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateDoctorRequest(
        @NotBlank(message = "医生邮箱不能为空")
        @Email(message = "请输入合法的邮箱地址")
        String email,
        @NotBlank(message = "医生昵称不能为空")
        @Size(max = 128, message = "医生昵称不能超过 128 个字符")
        String nickname,
        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 64, message = "初始密码长度需在 8 到 64 位之间")
        String password
) {
}
