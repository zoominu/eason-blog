package com.eason.blog.module.article.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.dto.AdminArticleQuery;
import com.eason.blog.module.article.dto.ArticleUpsertRequest;
import com.eason.blog.module.article.service.AdminArticleService;
import com.eason.blog.module.article.vo.AdminArticleListItemVO;
import com.eason.blog.module.article.vo.AdminArticleVO;
import com.eason.blog.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台文章管理（/api/v1/admin/articles）。
 *
 * <p>整个 /admin/** 已由 SecurityConfig 限制为 SUPER_ADMIN；
 * author_id 取自认证主体，不接受前端传入。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/articles")
@Validated
public class AdminArticleController {

    private final AdminArticleService adminArticleService;

    public AdminArticleController(AdminArticleService adminArticleService) {
        this.adminArticleService = adminArticleService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminArticleListItemVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Min(0) @Max(3) Integer status,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(
                adminArticleService.page(new AdminArticleQuery(page, size, keyword, status, categoryId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminArticleVO> detail(@PathVariable Long id) {
        return ApiResponse.success(adminArticleService.get(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody ArticleUpsertRequest request) {
        Long id = adminArticleService.create(principal.getId(), request);
        return ApiResponse.success(Map.of("id", id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ArticleUpsertRequest request) {
        adminArticleService.update(id, request);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        adminArticleService.publish(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable Long id) {
        adminArticleService.offline(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        adminArticleService.restore(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminArticleService.delete(id);
        return ApiResponse.success();
    }
}
