package com.eason.blog.module.guestbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestbookRequest(
        @NotBlank(message = "留言内容不能为空")
        @Size(max = 2000, message = "留言不能超过 2000 字")
        String content) {
}
