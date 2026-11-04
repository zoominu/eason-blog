package com.eason.blog.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** 后台修改用户资料：字段为 null 表示不修改。 */
public record AdminUserUpdateRequest(
        @Email(message = "邮箱格式不正确")
        @Size(max = 100)
        String email,

        @Size(max = 50, message = "昵称最多 50 字")
        String nickname,

        @Size(max = 500)
        String avatar,

        @Size(max = 500, message = "简介最多 500 字")
        String bio) {
}
