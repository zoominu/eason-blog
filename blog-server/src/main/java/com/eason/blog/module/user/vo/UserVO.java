package com.eason.blog.module.user.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户资料输出。绝不包含 password / is_deleted 等内部字段。
 */
public record UserVO(
        Long id,
        String uid,
        String email,
        String username,
        String nickname,
        String avatar,
        String bio,
        List<String> roles,
        boolean emailVerified,
        LocalDateTime registerTime) {
}
