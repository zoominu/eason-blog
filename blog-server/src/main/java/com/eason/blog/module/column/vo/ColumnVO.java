package com.eason.blog.module.column.vo;

/** 专栏输出。 */
public record ColumnVO(
        Long id,
        String name,
        String slug,
        String description,
        String cover,
        Integer articleCount) {
}
