package com.eason.blog.module.tag.controller;

/**
 * 公开标签接口：只读列表与标签下公开文章。
 */
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.service.ArticleService;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.tag.service.TagService;
import com.eason.blog.module.tag.vo.TagVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@Validated
public class TagController {

    private final TagService tagService;
    private final ArticleService articleService;

    public TagController(TagService tagService, ArticleService articleService) {
        this.tagService = tagService;
        this.articleService = articleService;
    }

    @GetMapping
    public ApiResponse<List<TagVO>> list() {
        return ApiResponse.success(tagService.list());
    }

    @GetMapping("/{slug}/articles")
    public ApiResponse<PageResult<ArticleSummaryVO>> articles(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(articleService.pageByTag(slug, page, size));
    }
}
