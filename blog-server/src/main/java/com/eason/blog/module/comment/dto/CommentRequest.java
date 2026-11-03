package com.eason.blog.module.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 发表评论 / 回复。parentId 为空表示顶层评论。 */
public record CommentRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 2000, message = "评论不能超过 2000 字")
        String content,

        Long parentId) {
}
