package com.eason.blog.module.log.vo;

/**
 * 后台登录日志输出对象：只包含审计需要的字段，不含密码/token。
 */
import java.time.LocalDateTime;

public record AdminLoginLogVO(
        Long id,
        String email,
        String ip,
        Integer loginStatus,
        String message,
        LocalDateTime createTime) {
}
