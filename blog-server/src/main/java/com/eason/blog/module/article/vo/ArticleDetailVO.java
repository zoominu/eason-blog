package com.eason.blog.module.article.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情：返回 Markdown 原文，由前端渲染并净化。
 *
 * <p>tags/tagSlugs 一一对应，前端据此生成可点击的标签链接；
 * prev/next 用于阅读连续性，避免前端再发两次请求。</p>
 */
public record ArticleDetailVO(
        Long id,
        String title,
        String slug,
        String summary,
        String cover,
        Long categoryId,
        String categoryName,
        String categorySlug,
        List<String> tags,
        List<String> tagSlugs,
        String content,
        Long viewCount,
        Long likeCount,
        Long commentCount,
        Integer readingMinutes,
        LocalDateTime publishTime,
        LocalDateTime updateTime,
        String seoTitle,
        String seoDescription,
        String prevSlug,
        String prevTitle,
        String nextSlug,
        String nextTitle) {
}
