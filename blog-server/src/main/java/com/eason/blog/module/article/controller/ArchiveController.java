package com.eason.blog.module.article.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.article.mapper.ArticleMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前台归档：按年月统计公开文章数量。数据量小，直接复用后台的聚合查询。
 */
@RestController
@RequestMapping("/api/v1/archive")
public class ArchiveController {

    private final ArticleMapper articleMapper;

    public ArchiveController(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(articleMapper.selectArchive());
    }
}
