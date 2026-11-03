package com.eason.blog.module.article.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 公开文章列表查询参数。
 *
 * <p>排序字段走白名单（latest/popular），禁止前端直接传 SQL 片段，防注入。</p>
 */
public record ArticleQuery(
        @Min(value = 1, message = "page 最小为 1") Integer page,
        @Min(value = 1, message = "size 最小为 1") @Max(value = 50, message = "size 最大为 50") Integer size,
        @Size(max = 100, message = "关键词过长") String keyword,
        @Size(max = 100, message = "分类标识过长") String category,
        @Pattern(regexp = "^(latest|popular)$", message = "sort 只支持 latest 或 popular") String sort) {

    public int pageOrDefault() {
        return page == null ? 1 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 10 : size;
    }

    public String sortOrDefault() {
        return sort == null || sort.isBlank() ? "latest" : sort;
    }
}
