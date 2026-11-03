package com.eason.blog.module.category.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.dto.ArticleQuery;
import com.eason.blog.module.article.service.ArticleService;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.category.service.CategoryService;
import com.eason.blog.module.category.vo.CategoryVO;
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
@RequestMapping("/api/v1/categories")
@Validated
public class CategoryController {

    private final CategoryService categoryService;
    private final ArticleService articleService;

    public CategoryController(CategoryService categoryService, ArticleService articleService) {
        this.categoryService = categoryService;
        this.articleService = articleService;
    }

    @GetMapping
    public ApiResponse<List<CategoryVO>> list() {
        return ApiResponse.success(categoryService.list());
    }

    @GetMapping("/{slug}")
    public ApiResponse<CategoryVO> detail(@PathVariable String slug) {
        CategoryVO category = categoryService.getBySlug(slug);
        if (category == null) {
            throw com.eason.blog.common.exception.BusinessException.notFound("分类不存在");
        }
        return ApiResponse.success(category);
    }

    @GetMapping("/{slug}/articles")
    public ApiResponse<PageResult<ArticleSummaryVO>> articles(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(
                articleService.page(new ArticleQuery(page, size, null, slug, "latest")));
    }
}
