package com.eason.blog.module.article.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 后台文章列表查询条件。
 *
 * <p>与前台不同：这里允许按状态筛选（草稿/已发布/下线/回收），
 * 但仍在服务层强制 is_deleted=0。</p>
 */
public record AdminArticleQuery(
        @Min(1) Integer page,
        @Min(1) @Max(50) Integer size,
        @Size(max = 100) String keyword,
        @Min(0) @Max(3) Integer status,
        Long categoryId) {

    public int pageOrDefault() {
        return page == null ? 1 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 10 : size;
    }
}
