package com.eason.blog.module.column.vo;

/** 后台专栏文章列表项。 */
public record ColumnArticleItemVO(
        Long id,
        String title,
        Integer status,
        Integer sort) {
}
