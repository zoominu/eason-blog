package com.eason.blog.module.auth.dto;

/**
 * 找回密码请求体：只接收邮箱；邮箱不存在时服务端也返回成功，避免被用来枚举注册状态。
 */
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email) {
}
