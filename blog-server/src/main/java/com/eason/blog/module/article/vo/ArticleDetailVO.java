package com.eason.blog.module.article.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情：返回 Markdown 原文，由前端渲染并净化。
 */
public record ArticleDetailVO(
        Long id,
        String title,
        String slug,
        String summary,
        String cover,
        Long categoryId,
        String categoryName,
        List<String> tags,
        String content,
        Long viewCount,
        Long likeCount,
        Long commentCount,
        Integer readingMinutes,
        LocalDateTime publishTime,
        LocalDateTime updateTime,
        String seoTitle,
        String seoDescription) {
}
