package com.eason.blog.module.resource.vo;

/** 资源被引用到的文章。 */
public record ResourceRefVO(Long id, String title, String slug, Integer status) {
}
