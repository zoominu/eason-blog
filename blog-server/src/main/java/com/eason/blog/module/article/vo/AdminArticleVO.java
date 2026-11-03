package com.eason.blog.module.article.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 后台文章详情 / 编辑回显：包含 Markdown 原文与标签 id。 */
public record AdminArticleVO(
        Long id,
        String title,
        String slug,
        String summary,
        String cover,
        Long categoryId,
        List<Long> tagIds,
        List<String> tagNames,
        String content,
        Integer status,
        Integer visibility,
        Boolean top,
        Boolean recommend,
        String seoTitle,
        String seoDescription,
        Long viewCount,
        LocalDateTime publishTime,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
