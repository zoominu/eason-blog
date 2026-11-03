package com.eason.blog.module.column.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.db.RowUtils;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.article.service.ArticleTagResolver;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.category.entity.Category;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.column.entity.Column;
import com.eason.blog.module.column.mapper.ColumnArticleMapper;
import com.eason.blog.module.column.mapper.ColumnMapper;
import com.eason.blog.module.column.vo.ColumnVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ColumnService {

    private final ColumnMapper columnMapper;
    private final ColumnArticleMapper columnArticleMapper;
    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final ArticleTagResolver tagResolver;

    public ColumnService(ColumnMapper columnMapper,
                         ColumnArticleMapper columnArticleMapper,
                         ArticleMapper articleMapper,
                         CategoryMapper categoryMapper,
                         ArticleTagResolver tagResolver) {
        this.columnMapper = columnMapper;
        this.columnArticleMapper = columnArticleMapper;
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.tagResolver = tagResolver;
    }

    public List<ColumnVO> list() {
        Map<Long, Long> counts = counts();
        return columnMapper.selectList(Wrappers.<Column>lambdaQuery()
                        .orderByAsc(Column::getSort)
                        .orderByAsc(Column::getId))
                .stream()
                .map(c -> new ColumnVO(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
                        c.getCover(), counts.getOrDefault(c.getId(), 0L).intValue(),
                        c.getSort() == null ? 0 : c.getSort()))
                .toList();
    }

    public ColumnVO getBySlug(String slug) {
        Column column = columnMapper.selectOne(Wrappers.<Column>lambdaQuery()
                .eq(Column::getSlug, slug)
                .last("limit 1"));
        if (column == null) {
            return null;
        }
        return new ColumnVO(column.getId(), column.getName(), column.getSlug(),
                column.getDescription(), column.getCover(),
                counts().getOrDefault(column.getId(), 0L).intValue(),
                column.getSort() == null ? 0 : column.getSort());
    }

    /**
     * 专栏内的公开文章，按 blog_column_article.sort 排序。
     * 数量通常不大（一个专栏几十篇），一次取回即可，不做分页。
     */
    public List<ArticleSummaryVO> articles(String slug) {
        List<Long> ids = columnArticleMapper.selectArticleIdsByColumnSlug(slug);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Article> articles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .in(Article::getId, ids)
                .eq(Article::getStatus, 1)
                .eq(Article::getVisibility, 0));

        // 按关联表里的 sort 顺序还原（IN 查询不保证顺序）
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            order.put(ids.get(i), i);
        }

        Map<Long, String> categoryNames = categoryNames(articles);
        Map<Long, List<String>> tagNames = tagResolver.byArticleIds(
                articles.stream().map(Article::getId).toList());

        return articles.stream()
                .sorted((a, b) -> Integer.compare(order.getOrDefault(a.getId(), 999), order.getOrDefault(b.getId(), 999)))
                .map(a -> new ArticleSummaryVO(
                        a.getId(), a.getTitle(), a.getSlug(), summaryOf(a), a.getCover(),
                        a.getCategoryId(),
                        a.getCategoryId() == null ? null : categoryNames.get(a.getCategoryId()),
                        tagNames.getOrDefault(a.getId(), List.of()),
                        a.getIsTop() != null && a.getIsTop() == 1,
                        a.getIsRecommend() != null && a.getIsRecommend() == 1,
                        a.getViewCount() == null ? 0 : a.getViewCount(),
                        a.getLikeCount() == null ? 0 : a.getLikeCount(),
                        a.getCommentCount() == null ? 0 : a.getCommentCount(),
                        Math.max(1, (a.getContent() == null ? 0 : a.getContent().length()) / 400),
                        a.getPublishTime()))
                .toList();
    }

    /** 摘要缺失时从正文截取，和文章列表保持一致 */
    private String summaryOf(Article article) {
        if (article.getSummary() != null && !article.getSummary().isBlank()) {
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

    private Map<Long, Long> counts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleMapper.countPublicArticlesByColumn()) {
            counts.put(RowUtils.longValue(row, "taxonomy_id"), RowUtils.longValue(row, "total"));
        }
        return counts;
    }
}
