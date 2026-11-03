package com.eason.blog.module.article.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台文章列表项：比前台多出状态、更新时间等信息，便于管理。
 */
public record AdminArticleListItemVO(
        Long id,
        String title,
        String slug,
        String summary,
        Long categoryId,
        String categoryName,
        List<String> tags,
        Integer status,
        Integer visibility,
        Boolean top,
        Boolean recommend,
        Long viewCount,
        LocalDateTime publishTime,
        LocalDateTime updateTime) {
}
