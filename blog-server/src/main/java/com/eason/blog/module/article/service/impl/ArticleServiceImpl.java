package com.eason.blog.module.article.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.dto.ArticleQuery;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.article.mapper.ArticleTagMapper;
import com.eason.blog.module.article.service.ArticleService;
import com.eason.blog.module.article.service.ArticleTagResolver;
import com.eason.blog.module.article.vo.ArticleDetailVO;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.category.entity.Category;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.tag.entity.Tag;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公开文章读取服务。
 *
 * <p>所有查询都显式带上 status=1 且 visibility=0，把"只能看已发布公开文章"的规则
 * 收敛在服务层，而不是依赖调用方传对参数。</p>
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    private static final int PUBLIC_STATUS = 1;
    private static final int PUBLIC_VISIBILITY = 0;
    private static final int CHARS_PER_MINUTE = 400;

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final ArticleTagResolver tagResolver;

    public ArticleServiceImpl(ArticleMapper articleMapper,
                              ArticleTagMapper articleTagMapper,
                              CategoryMapper categoryMapper,
                              ArticleTagResolver tagResolver) {
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.categoryMapper = categoryMapper;
        this.tagResolver = tagResolver;
    }

    @Override
    public PageResult<ArticleSummaryVO> page(ArticleQuery query) {
        var wrapper = Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, PUBLIC_STATUS)
                .eq(Article::getVisibility, PUBLIC_VISIBILITY);

        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            wrapper.and(w -> w.like(Article::getTitle, keyword).or().like(Article::getSummary, keyword));
        }
        if (query.category() != null && !query.category().isBlank()) {
            Category category = categoryMapper.selectOne(Wrappers.<Category>lambdaQuery()
                    .eq(Category::getSlug, query.category().trim())
                    .last("limit 1"));
            if (category == null) {
                // 分类不存在返回空列表而不是 404：列表页更友好，也避免探测分类是否存在
                return PageResult.empty(query.pageOrDefault(), query.sizeOrDefault());
            }
            wrapper.eq(Article::getCategoryId, category.getId());
        }

        wrapper.orderByDesc(Article::getIsTop);
        if ("popular".equals(query.sortOrDefault())) {
            wrapper.orderByDesc(Article::getViewCount).orderByDesc(Article::getPublishTime);
        } else {
            wrapper.orderByDesc(Article::getPublishTime);
        }

        Page<Article> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        articleMapper.selectPage(page, wrapper);
        return PageResult.of(page, summaryMapper(page.getRecords()));
    }

    @Override
    public PageResult<ArticleSummaryVO> pageByTag(String tagSlug, int pageNo, int size) {
        List<Long> ids = articleTagMapper.selectArticleIdsByTagSlug(tagSlug);
        if (ids.isEmpty()) {
            return PageResult.empty(pageNo, size);
        }
        Page<Article> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 50));
        articleMapper.selectPage(page, Wrappers.<Article>lambdaQuery()
                .in(Article::getId, ids)
                .eq(Article::getStatus, PUBLIC_STATUS)
                .eq(Article::getVisibility, PUBLIC_VISIBILITY)
                .orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getPublishTime));
        return PageResult.of(page, summaryMapper(page.getRecords()));
    }

    @Override
    public List<ArticleSummaryVO> latest(int limit) {
        Page<Article> page = new Page<>(1, clamp(limit));
        articleMapper.selectPage(page, Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, PUBLIC_STATUS)
                .eq(Article::getVisibility, PUBLIC_VISIBILITY)
                .orderByDesc(Article::getPublishTime));
        var mapper = summaryMapper(page.getRecords());
        return page.getRecords().stream().map(mapper).toList();
    }

    @Override
    public List<ArticleSummaryVO> recommended(int limit) {
        Page<Article> page = new Page<>(1, clamp(limit));
        articleMapper.selectPage(page, Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, PUBLIC_STATUS)
                .eq(Article::getVisibility, PUBLIC_VISIBILITY)
                .eq(Article::getIsRecommend, 1)
                .orderByDesc(Article::getPublishTime));
        var mapper = summaryMapper(page.getRecords());
        return page.getRecords().stream().map(mapper).toList();
    }

    @Override
    public ArticleDetailVO detail(String slug, boolean countView) {
        Article article = articleMapper.selectOne(Wrappers.<Article>lambdaQuery()
                .eq(Article::getSlug, slug)
                .last("limit 1"));
        if (article == null || article.getStatus() != PUBLIC_STATUS || article.getVisibility() != PUBLIC_VISIBILITY) {
            throw BusinessException.notFound("文章不存在或尚未发布");
        }

        long viewCount = article.getViewCount() == null ? 0 : article.getViewCount();
        if (countView) {
            articleMapper.incrementViewCount(article.getId());
            viewCount += 1;
        }

        List<Tag> tags = articleTagMapper.selectTagsByArticleId(article.getId());
        Category category = article.getCategoryId() == null ? null : categoryMapper.selectById(article.getCategoryId());
        String categoryName = category == null ? null : category.getName();
        String categorySlug = category == null ? null : category.getSlug();
        Article prev = neighbour(article, true);
        Article next = neighbour(article, false);

        return new ArticleDetailVO(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                summaryOf(article),
                coverOf(article),
                article.getCategoryId(),
                categoryName,
                categorySlug,
                tags.stream().map(Tag::getName).toList(),
                tags.stream().map(Tag::getSlug).toList(),
                article.getContent(),
                viewCount,
                nullSafe(article.getLikeCount()),
                nullSafe(article.getCommentCount()),
                readingMinutes(article.getContent()),
                article.getPublishTime(),
                article.getUpdateTime(),
                article.getSeoTitle(),
                article.getSeoDescription(),
                prev == null ? null : prev.getSlug(),
                prev == null ? null : prev.getTitle(),
                next == null ? null : next.getSlug(),
                next == null ? null : next.getTitle());
    }

    /** 上一篇 / 下一篇：按发布时间排序的相邻公开文章。 */
    private Article neighbour(Article current, boolean previous) {
        var wrapper = Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, PUBLIC_STATUS)
                .eq(Article::getVisibility, PUBLIC_VISIBILITY)
                .isNotNull(Article::getPublishTime)
                .last("limit 1");
        if (previous) {
            wrapper.lt(Article::getPublishTime, current.getPublishTime())
                    .orderByDesc(Article::getPublishTime);
        } else {
            wrapper.gt(Article::getPublishTime, current.getPublishTime())
                    .orderByAsc(Article::getPublishTime);
        }
        return articleMapper.selectOne(wrapper);
    }

    private java.util.function.Function<Article, ArticleSummaryVO> summaryMapper(List<Article> articles) {
        Map<Long, String> categoryNames = loadCategoryNames(articles);
        Map<Long, List<String>> tagNames = tagResolver.byArticleIds(
                articles.stream().map(Article::getId).toList());
        return article -> toSummary(article, categoryNames, tagNames);
    }

    private ArticleSummaryVO toSummary(Article article, Map<Long, String> categoryNames,
                                       Map<Long, List<String>> tagNames) {
        return new ArticleSummaryVO(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                summaryOf(article),
                coverOf(article),
                article.getCategoryId(),
                article.getCategoryId() == null ? null : categoryNames.get(article.getCategoryId()),
                tagNames.getOrDefault(article.getId(), List.of()),
                article.getIsTop() != null && article.getIsTop() == 1,
                article.getIsRecommend() != null && article.getIsRecommend() == 1,
                nullSafe(article.getViewCount()),
                nullSafe(article.getLikeCount()),
                nullSafe(article.getCommentCount()),
                readingMinutes(article.getContent()),
                article.getPublishTime());
    }

    private Map<Long, String> loadCategoryNames(List<Article> articles) {
        Set<Long> ids = articles.stream()
                .map(Article::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    /**
     * 列表页展示用摘要：优先使用手工摘要，缺失时从 Markdown 正文截取，避免列表出现空白简介。
     */
    /**
     * 列表缩略图：优先用 cover；没设封面时取正文里的第一张图片，
     * 这样首页卡片也能有图。
     */
    private String coverOf(Article article) {
        if (article.getCover() != null && !article.getCover().isBlank()) {
            return article.getCover();
        }
        String content = article.getContent() == null ? "" : article.getContent();
        java.util.regex.Matcher md = java.util.regex.Pattern
                .compile("!\\[[^\\]]*]\\((\\S+?)\\)").matcher(content);
        if (md.find()) {
            return md.group(1);
        }
        java.util.regex.Matcher html = java.util.regex.Pattern
                .compile("<img[^>]+src=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(content);
        return html.find() ? html.group(1) : article.getCover();
    }

    private String summaryOf(Article article) {
        if (article.getSummary() != null && !article.getSummary().isBlank()) {
            return article.getSummary();
        }
        String content = article.getContent() == null ? "" : article.getContent();
        String plain = content
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[#>*`_~-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() <= 120 ? plain : plain.substring(0, 120) + "...";
    }

    private Integer readingMinutes(String content) {
        if (content == null || content.isBlank()) {
            return 1;
        }
        // 中文阅读速度按 400 字/分钟估算，向上取整，避免 500 字也显示 1 分钟
        return Math.max(1, (content.length() + CHARS_PER_MINUTE - 1) / CHARS_PER_MINUTE);
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    private int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 20);
    }
}
