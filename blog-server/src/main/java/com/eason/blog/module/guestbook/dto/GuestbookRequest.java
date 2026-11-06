package com.eason.blog.module.guestbook.dto;

/**
 * 留言请求体：内容长度/敏感词由 Service 校验，默认进入待审核。
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestbookRequest(
        @NotBlank(message = "留言内容不能为空")
        @Size(max = 2000, message = "留言不能超过 2000 字")
        String content) {
}
