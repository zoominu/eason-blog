package com.eason.blog.module.tag.dto;

/**
 * 标签创建/更新请求体：名称 + 可选 slug。
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagRequest(
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 50, message = "标签名称不能超过 50 字")
        String name,

        @Size(max = 100)
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "slug 只能包含小写字母、数字和短横线")
        String slug) {
}
