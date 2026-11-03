package com.eason.blog.module.column.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.db.RowUtils;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.column.entity.Column;
import com.eason.blog.module.column.mapper.ColumnArticleMapper;
import com.eason.blog.module.column.mapper.ColumnMapper;
import com.eason.blog.module.column.vo.ColumnVO;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ColumnService {

    private final ColumnMapper columnMapper;
    private final ColumnArticleMapper columnArticleMapper;
    private final ArticleMapper articleMapper;

    public ColumnService(ColumnMapper columnMapper,
                         ColumnArticleMapper columnArticleMapper,
                         ArticleMapper articleMapper) {
        this.columnMapper = columnMapper;
        this.columnArticleMapper = columnArticleMapper;
        this.articleMapper = articleMapper;
    }

    public List<ColumnVO> list() {
        Map<Long, Long> counts = counts();
        return columnMapper.selectList(Wrappers.<Column>lambdaQuery()
                        .orderByAsc(Column::getSort)
                        .orderByAsc(Column::getId))
                .stream()
                .map(c -> new ColumnVO(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
                        c.getCover(), counts.getOrDefault(c.getId(), 0L).intValue()))
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
                counts().getOrDefault(column.getId(), 0L).intValue());
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
        return articles.stream()
                .sorted((a, b) -> Integer.compare(order.getOrDefault(a.getId(), 999), order.getOrDefault(b.getId(), 999)))
                .map(a -> new ArticleSummaryVO(
                        a.getId(), a.getTitle(), a.getSlug(), a.getSummary(), a.getCover(),
                        a.getCategoryId(), null, List.of(),
                        a.getIsTop() != null && a.getIsTop() == 1,
                        a.getIsRecommend() != null && a.getIsRecommend() == 1,
                        a.getViewCount() == null ? 0 : a.getViewCount(),
                        a.getLikeCount() == null ? 0 : a.getLikeCount(),
                        a.getCommentCount() == null ? 0 : a.getCommentCount(),
                        Math.max(1, (a.getContent() == null ? 0 : a.getContent().length()) / 400),
                        a.getPublishTime()))
                .toList();
    }

    private Map<Long, Long> counts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleMapper.countPublicArticlesByColumn()) {
            counts.put(RowUtils.longValue(row, "taxonomy_id"), RowUtils.longValue(row, "total"));
        }
        return counts;
    }
}
