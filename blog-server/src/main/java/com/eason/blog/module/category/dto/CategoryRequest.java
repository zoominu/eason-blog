package com.eason.blog.module.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 分类新建 / 更新请求。 */
public record CategoryRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 50, message = "分类名称不能超过 50 字")
        String name,

        @Size(max = 100)
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "slug 只能包含小写字母、数字和短横线")
        String slug,

        Long parentId,

        @Size(max = 500, message = "描述不能超过 500 字")
        String description,

        Integer sort) {
}
