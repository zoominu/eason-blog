package com.eason.blog.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。V1 只支持邮箱 + 密码，邮箱验证为后续可选能力。
 */
public record RegisterRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱过长")
        String email,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度需在 8-72 之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
        String password,

        @Size(min = 1, max = 50, message = "昵称长度需在 1-50 之间")
        String nickname,

        /** 站内账号名（唯一，不可修改）；不传时用邮箱前缀 */
        String username,

        /** 开启邮件后注册需要邮箱验证码 */
        String code) {
}
