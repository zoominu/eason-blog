package com.eason.blog.module.article.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.category.entity.Category;
import com.eason.blog.module.category.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站内搜索。
 *
 * <p>V1 用 MySQL LIKE 覆盖标题/摘要/正文，数据量小时足够且零额外组件；
 * 设计文档已把全文搜索引擎（Elasticsearch / Meilisearch）列为后续能力，
 * 届时只需替换这里的实现，接口保持不变。</p>
 */
@Service
public class SearchService {

    private static final int MAX_SIZE = 30;

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final ArticleTagResolver tagResolver;

    public SearchService(ArticleMapper articleMapper, CategoryMapper categoryMapper,
                         ArticleTagResolver tagResolver) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.tagResolver = tagResolver;
    }

    public PageResult<ArticleSummaryVO> search(String keyword, int page, int size) {
        if (!StringUtils.hasText(keyword)) {
            return PageResult.empty(page, size);
        }
        // 去掉 LIKE 通配符，避免用户输入 % / _ 触发全表大字段扫描
        String kw = keyword.trim().replace('%', ' ').replace('_', ' ')
                .replaceAll("\\s+", " ").trim();
        if (kw.isEmpty()) {
            return PageResult.empty(page, size);
        }

        Page<Article> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_SIZE));
        articleMapper.selectPage(result, Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, 1)
                .eq(Article::getVisibility, 0)
                .and(w -> w.like(Article::getTitle, kw)
                        .or().like(Article::getSummary, kw)
                        .or().like(Article::getContent, kw))
                .orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getPublishTime));

        Map<Long, String> categoryNames = categoryNames(result.getRecords());
        Map<Long, List<String>> tagNames = tagResolver.byArticleIds(
                result.getRecords().stream().map(Article::getId).toList());

        return PageResult.of(result, article -> new ArticleSummaryVO(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                summary(article),
                article.getCover(),
                article.getCategoryId(),
                article.getCategoryId() == null ? null : categoryNames.get(article.getCategoryId()),
                tagNames.getOrDefault(article.getId(), List.of()),
                article.getIsTop() != null && article.getIsTop() == 1,
                article.getIsRecommend() != null && article.getIsRecommend() == 1,
                article.getViewCount() == null ? 0 : article.getViewCount(),
                article.getLikeCount() == null ? 0 : article.getLikeCount(),
                article.getCommentCount() == null ? 0 : article.getCommentCount(),
                Math.max(1, (article.getContent() == null ? 0 : article.getContent().length()) / 400),
                article.getPublishTime()));
    }

    private String summary(Article article) {
        if (StringUtils.hasText(article.getSummary())) {
            return article.getSummary();
        }
        String content = article.getContent() == null ? "" : article.getContent()
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("[#>*`_~-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return content.length() <= 120 ? content : content.substring(0, 120) + "...";
    }

    private Map<Long, String> categoryNames(List<Article> articles) {
        Set<Long> ids = articles.stream().map(Article::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }
}
