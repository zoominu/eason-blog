package com.eason.blog.module.article.service;

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
