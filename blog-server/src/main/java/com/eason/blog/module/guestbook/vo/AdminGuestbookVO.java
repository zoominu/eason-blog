package com.eason.blog.module.guestbook.vo;

import java.time.LocalDateTime;

/** 后台留言审核列表项。 */
public record AdminGuestbookVO(
        Long id,
        String nickname,
        String email,
        String content,
        Integer status,
        LocalDateTime createTime) {
}
