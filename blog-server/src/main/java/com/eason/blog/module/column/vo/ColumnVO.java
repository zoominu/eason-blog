package com.eason.blog.module.column.vo;

/** 专栏输出。sort 用于后台编辑回填，避免保存时把排序重置为 0。 */
public record ColumnVO(
        Long id,
        String name,
        String slug,
        String description,
        String cover,
        Integer articleCount,
        Integer sort) {
}
