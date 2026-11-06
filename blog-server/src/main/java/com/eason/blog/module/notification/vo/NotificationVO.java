package com.eason.blog.module.notification.vo;

/**
 * 通知输出对象：标题、内容、跳转链接、已读状态；不暴露接收者内部 id。
 */
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
