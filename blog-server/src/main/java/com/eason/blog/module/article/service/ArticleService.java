package com.eason.blog.module.article.service;

/**
 * 前台文章服务接口：分页、最新、推荐、详情、归档与相邻文章；实现必须强制 status=1 且 visibility=0，禁止把草稿/私密文章暴露给公开接口。
 */
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.dto.ArticleQuery;
import com.eason.blog.module.article.vo.ArticleDetailVO;
import com.eason.blog.module.article.vo.ArticleSummaryVO;

import java.util.List;

public interface ArticleService {

    PageResult<ArticleSummaryVO> page(ArticleQuery query);

    PageResult<ArticleSummaryVO> pageByTag(String tagSlug, int page, int size);

    List<ArticleSummaryVO> latest(int limit);

    List<ArticleSummaryVO> recommended(int limit);

    ArticleDetailVO detail(String slug, boolean countView);
}
