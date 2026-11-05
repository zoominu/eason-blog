package com.eason.blog.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求：account 为站内账号名（兼容旧的 email 登录）。
 */
public record LoginRequest(
        String account,
        String email,

        @NotBlank(message = "密码不能为空")
        String password) {
}
