package com.eason.blog.module.auth.dto;

/**
 * 重置密码请求体：一次性 token + 新密码；密码强度由 Service 统一校验。
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "token 不能为空") String token,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度需在 8-72 之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
        String newPassword) {
}
