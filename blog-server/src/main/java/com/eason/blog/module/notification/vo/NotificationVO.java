package com.eason.blog.module.notification.vo;

import java.time.LocalDateTime;

public record NotificationVO(
        Long id,
        String type,
        String title,
        String content,
        String link,
        boolean read,
        LocalDateTime createTime) {
}
