package com.eason.blog.module.user.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 后台用户列表项。 */
public record AdminUserVO(
        Long id,
        String uid,
        String email,
        String username,
        String nickname,
        String avatar,
        String bio,
        Integer status,
        List<String> roles,
        LocalDateTime registerTime,
        LocalDateTime lastLoginTime,
        String lastLoginIp) {
}
