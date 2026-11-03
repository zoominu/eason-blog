package com.eason.blog.module.category.vo;

/** 分类输出。 */
public record CategoryVO(
        Long id,
        Long parentId,
        String name,
        String slug,
        String description,
        Integer articleCount) {
}
