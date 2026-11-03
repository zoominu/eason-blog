package com.eason.blog.module.article.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项（不含正文，避免列表接口传输大字段）。
 */
public record ArticleSummaryVO(
        Long id,
        String title,
        String slug,
        String summary,
        String cover,
        Long categoryId,
        String categoryName,
        List<String> tags,
        Boolean top,
        Boolean recommend,
        Long viewCount,
        Long likeCount,
        Long commentCount,
        Integer readingMinutes,
        LocalDateTime publishTime) {
}
