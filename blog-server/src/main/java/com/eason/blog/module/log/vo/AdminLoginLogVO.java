package com.eason.blog.module.log.vo;

import java.time.LocalDateTime;

public record AdminLoginLogVO(
        Long id,
        String email,
        String ip,
        Integer loginStatus,
        String message,
        LocalDateTime createTime) {
}
