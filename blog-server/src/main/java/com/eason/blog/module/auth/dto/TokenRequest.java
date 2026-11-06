package com.eason.blog.module.auth.dto;

/**
 * 一次性 token 请求体：用于邮箱验证等链接回调，token 只存哈希、短时有效。
 */
import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank(message = "token 不能为空") String token) {
}
