package com.eason.blog.module.comment.vo;

import java.time.LocalDateTime;

/** 个人中心「我的评论」列表项。 */
public record MyCommentVO(
        Long id,
        Long articleId,
        String articleTitle,
        String articleSlug,
        String content,
        Integer status,
        LocalDateTime createTime,
        /** 3 分钟内且没有回复才允许自行删除 */
        boolean canDelete) {
}
