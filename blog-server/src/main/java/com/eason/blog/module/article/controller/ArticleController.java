package com.eason.blog.module.article.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.dto.ArticleQuery;
import com.eason.blog.module.article.service.ArticleService;
import com.eason.blog.module.article.vo.ArticleDetailVO;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开文章接口（只读，无需登录）。
 *
 * <p>注意：这里的 status/visibility 过滤由 Service 强制，Controller 不提供任何
 * "顺便返回草稿"的开关，防止后续加参数时越权。</p>
 */
@RestController
@RequestMapping("/api/v1/articles")
@Validated
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public ApiResponse<PageResult<ArticleSummaryVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) @Size(max = 100, message = "关键词过长") String keyword,
            @RequestParam(required = false) @Size(max = 100, message = "分类标识过长") String category,
            @RequestParam(defaultValue = "latest")
            @Pattern(regexp = "^(latest|popular)$", message = "sort 只支持 latest 或 popular") String sort) {
        return ApiResponse.success(
                articleService.page(new ArticleQuery(page, size, keyword, category, sort)));
    }

    @GetMapping("/latest")
    public ApiResponse<List<ArticleSummaryVO>> latest(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return ApiResponse.success(articleService.latest(limit));
    }

    @GetMapping("/recommended")
    public ApiResponse<List<ArticleSummaryVO>> recommended(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return ApiResponse.success(articleService.recommended(limit));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ArticleDetailVO> detail(@PathVariable String slug) {
        // 阅读量计数放在详情接口，列表/预览不计数，避免刷量
        return ApiResponse.success(articleService.detail(slug, true));
    }
}
