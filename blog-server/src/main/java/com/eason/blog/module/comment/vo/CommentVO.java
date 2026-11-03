package com.eason.blog.module.comment.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 公开评论（树形）。邮箱等隐私字段不返回。 */
public record CommentVO(
        Long id,
        Long userId,
        String nickname,
        String avatar,
        String content,
        Long parentId,
        LocalDateTime createTime,
        boolean mine,
        boolean author,
        List<CommentVO> replies) {
}
