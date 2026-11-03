package com.eason.blog.module.system.vo;

/** 后台配置项（含非公开配置）。 */
public record SystemConfigVO(
        Long id,
        String key,
        String value,
        String description,
        Integer isPublic) {
}
