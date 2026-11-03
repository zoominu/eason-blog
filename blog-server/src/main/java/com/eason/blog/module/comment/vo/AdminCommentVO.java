package com.eason.blog.module.comment.vo;

import java.time.LocalDateTime;

/** 后台评论审核列表项。 */
public record AdminCommentVO(
        Long id,
        Long articleId,
        String articleTitle,
        String nickname,
        String email,
        String content,
        Integer status,
        LocalDateTime createTime) {
}
