package com.eason.blog.module.article.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建 / 更新文章的统一请求体。
 *
 * <p>更新使用"整体替换"语义（PATCH 但仍传全量字段），避免局部更新与标签关联
 * 产生难以推理的中间状态；前端编辑页本来就持有完整表单。</p>
 */
public record ArticleUpsertRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        @Size(max = 200, message = "slug 不能超过 200")
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "slug 只能包含小写字母、数字和短横线")
        String slug,

        Long categoryId,

        @Size(max = 500, message = "摘要不能超过 500 字")
        String summary,

        @Size(max = 500, message = "封面地址过长")
        String cover,

        @NotBlank(message = "正文不能为空")
        String content,

        @Min(0) @Max(1) Integer visibility,
        @Min(0) @Max(1) Integer isTop,
        @Min(0) @Max(1) Integer isRecommend,

        @Size(max = 200, message = "SEO 标题过长")
        String seoTitle,

        @Size(max = 500, message = "SEO 描述过长")
        String seoDescription,

        List<Long> tagIds,

        /** 0 草稿 1 直接发布；缺省按草稿处理。 */
        @Min(0) @Max(1) Integer status) {
}
