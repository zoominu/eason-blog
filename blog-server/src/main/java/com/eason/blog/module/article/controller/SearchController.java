package com.eason.blog.module.article.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.common.security.RateLimiter;
import com.eason.blog.common.web.ClientIp;
import com.eason.blog.module.article.service.SearchService;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内搜索（公开）。
 *
 * <p>只搜索已发布且公开的文章；关键词为空时返回空分页而不是全量数据。</p>
 */
@RestController
@RequestMapping("/api/v1/search")
@Validated
public class SearchController {

    private final SearchService searchService;
    private final RateLimiter rateLimiter;

    public SearchController(SearchService searchService, RateLimiter rateLimiter) {
        this.searchService = searchService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ApiResponse<PageResult<ArticleSummaryVO>> search(
            @RequestParam @Size(min = 1, max = 100, message = "关键词长度需在 1-100 之间") String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size,
            HttpServletRequest request) {
        // 搜索会扫 content 大字段，按 IP 限制频率，降低被当作 DoS 放大器的风险
        rateLimiter.check("search:" + ClientIp.of(request), 30, 60);
        return ApiResponse.success(searchService.search(keyword, page, size));
    }
}
