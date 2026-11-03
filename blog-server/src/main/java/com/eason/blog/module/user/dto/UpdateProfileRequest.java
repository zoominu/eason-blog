package com.eason.blog.module.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新个人资料。只允许改昵称/头像/简介，邮箱与角色不允许自助修改。
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 50, message = "昵称长度需在 1-50 之间") String nickname,
        @Size(max = 500, message = "头像地址过长") String avatar,
        @Size(max = 500, message = "简介长度不能超过 500") String bio) {
}
