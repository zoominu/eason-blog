package com.eason.blog.module.guestbook.vo;

import java.time.LocalDateTime;

/** 公开留言输出：不包含邮箱、IP 等隐私字段。 */
public record GuestbookVO(
        Long id,
        String nickname,
        String content,
        String reply,
        LocalDateTime createdAt) {
}
