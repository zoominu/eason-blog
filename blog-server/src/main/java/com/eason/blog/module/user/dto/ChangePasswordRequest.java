package com.eason.blog.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改密码：必须校验旧密码，避免 token 泄漏后被直接改密。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度需在 8-72 之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
        String newPassword) {
}
