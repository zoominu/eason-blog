package com.eason.blog.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank(message = "token 不能为空") String token) {
}
