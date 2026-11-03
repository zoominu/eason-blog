package com.eason.blog.module.comment.vo;

/** 当前用户对某篇文章的互动状态，用于前台按钮回显。 */
public record InteractionVO(
        boolean liked,
        boolean favorited,
        long likeCount,
        long commentCount) {
}
