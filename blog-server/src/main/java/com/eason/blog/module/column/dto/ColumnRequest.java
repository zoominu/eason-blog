package com.eason.blog.module.column.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ColumnRequest(
        @NotBlank(message = "专栏名称不能为空")
        @Size(max = 100, message = "专栏名称不能超过 100 字")
        String name,

        @Size(max = 100)
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "slug 只能包含小写字母、数字和短横线")
        String slug,

        @Size(max = 500, message = "描述不能超过 500 字")
        String description,

        @Size(max = 500, message = "封面地址过长")
        String cover,

        Integer sort) {
}
