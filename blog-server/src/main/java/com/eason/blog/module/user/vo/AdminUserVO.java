package com.eason.blog.module.user.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 后台用户列表项。 */
public record AdminUserVO(
        Long id,
        String uid,
        String email,
        String nickname,
        String avatar,
        Integer status,
        List<String> roles,
        LocalDateTime registerTime,
        LocalDateTime lastLoginTime,
        String lastLoginIp) {
}
