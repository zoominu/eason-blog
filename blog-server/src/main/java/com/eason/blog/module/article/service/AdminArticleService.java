package com.eason.blog.module.article.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.common.util.SlugUtils;
import com.eason.blog.module.article.dto.AdminArticleQuery;
import com.eason.blog.module.article.dto.ArticleUpsertRequest;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.entity.ArticleTag;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.article.mapper.ArticleTagMapper;
import com.eason.blog.module.article.vo.AdminArticleListItemVO;
import com.eason.blog.module.article.vo.AdminArticleVO;
import com.eason.blog.module.category.entity.Category;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.tag.entity.Tag;
import com.eason.blog.module.tag.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台文章管理。
 *
 * <p>状态机（设计文档 6.2）：
 * <pre>
 *   0 草稿 --publish--> 1 已发布 --offline--> 2 已下线 --publish--> 1
 *     ^                    |                     |
 *     +------ restore -----+---- delete --> 3 回收站
 * </pre>
 * 所有查询都带 is_deleted=0（逻辑删除由 MyBatis-Plus 自动附加）。</p>
 */
@Service
public class AdminArticleService {

    private static final int DRAFT = 0;
    private static final int PUBLISHED = 1;
    private static final int OFFLINE = 2;
    private static final int RECYCLED = 3;

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;

    public AdminArticleService(ArticleMapper articleMapper,
                               ArticleTagMapper articleTagMapper,
                               CategoryMapper categoryMapper,
                               TagMapper tagMapper) {
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
    }

    public PageResult<AdminArticleListItemVO> page(AdminArticleQuery query) {
        var wrapper = Wrappers.<Article>lambdaQuery();
        if (query.status() != null) {
            wrapper.eq(Article::getStatus, query.status());
        }
        if (query.categoryId() != null) {
            wrapper.eq(Article::getCategoryId, query.categoryId());
        }
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(w -> w.like(Article::getTitle, keyword).or().like(Article::getSummary, keyword));
        }
        wrapper.orderByDesc(Article::getUpdateTime);

        Page<Article> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        articleMapper.selectPage(page, wrapper);

        Map<Long, String> categoryNames = categoryNames(page.getRecords());
        Map<Long, List<String>> tagNames = tagNamesByArticle(page.getRecords());

        return PageResult.of(page, article -> new AdminArticleListItemVO(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getSummary(),
                article.getCategoryId(),
                article.getCategoryId() == null ? null : categoryNames.get(article.getCategoryId()),
                tagNames.getOrDefault(article.getId(), List.of()),
                article.getStatus(),
                article.getVisibility(),
                article.getIsTop() != null && article.getIsTop() == 1,
                article.getIsRecommend() != null && article.getIsRecommend() == 1,
                article.getViewCount() == null ? 0L : article.getViewCount(),
                article.getPublishTime(),
                article.getUpdateTime()));
    }

    public AdminArticleVO get(Long id) {
        Article article = require(id);
        List<Tag> tags = articleTagMapper.selectTagsByArticleId(id);
        Category category = article.getCategoryId() == null ? null : categoryMapper.selectById(article.getCategoryId());
        return new AdminArticleVO(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getSummary(),
                article.getCover(),
                article.getCategoryId(),
                tags.stream().map(Tag::getId).toList(),
                tags.stream().map(Tag::getName).toList(),
                article.getContent(),
                article.getStatus(),
                article.getVisibility(),
                article.getIsTop() != null && article.getIsTop() == 1,
                article.getIsRecommend() != null && article.getIsRecommend() == 1,
                article.getSeoTitle(),
                article.getSeoDescription(),
                article.getViewCount() == null ? 0L : article.getViewCount(),
                article.getPublishTime(),
                article.getCreateTime(),
                article.getUpdateTime());
    }

    @Transactional
    public Long create(Long authorId, ArticleUpsertRequest request) {
        validateRelations(request);
        LocalDateTime now = LocalDateTime.now();
        boolean publishNow = request.status() != null && request.status() == PUBLISHED;

        Article article = new Article();
        article.setAuthorId(authorId);
        applyFields(article, request, null);
        article.setStatus(publishNow ? PUBLISHED : DRAFT);
        article.setPublishTime(publishNow ? now : null);
        article.setViewCount(0L);
        article.setLikeCount(0L);
        article.setCommentCount(0L);
        article.setCreateTime(now);
        article.setUpdateTime(now);
        article.setIsDeleted(0);
        articleMapper.insert(article);

        syncTags(article.getId(), request.tagIds());
        return article.getId();
    }

    @Transactional
    public void update(Long id, ArticleUpsertRequest request) {
        Article article = require(id);
        validateRelations(request);

        applyFields(article, request, id);

        // 明确处理状态迁移，让后台「保存草稿 / 保存并发布」的语义与按钮一致
        if (request.status() != null) {
            if (request.status() == PUBLISHED) {
                article.setStatus(PUBLISHED);
                if (article.getPublishTime() == null) {
                    article.setPublishTime(LocalDateTime.now());
                }
            } else if (article.getStatus() != null && article.getStatus() == PUBLISHED) {
                // 已发布文章点「保存草稿」= 撤回为草稿（下架）
                article.setStatus(DRAFT);
            }
        } else if (article.getStatus() != null && article.getStatus() == PUBLISHED
                && article.getPublishTime() == null) {
            article.setPublishTime(LocalDateTime.now());
        }

        article.setUpdateTime(LocalDateTime.now());
        articleMapper.updateById(article);

        syncTags(id, request.tagIds());
    }

    @Transactional
    public void publish(Long id) {
        Article article = require(id);
        if (article.getStatus() != null && article.getStatus() == PUBLISHED) {
            return;
        }
        article.setStatus(PUBLISHED);
        if (article.getPublishTime() == null) {
            article.setPublishTime(LocalDateTime.now());
        }
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    @Transactional
    public void offline(Long id) {
        Article article = require(id);
        if (article.getStatus() == null || article.getStatus() != PUBLISHED) {
            throw BusinessException.badRequest("只有已发布的文章可以下线");
        }
        article.setStatus(OFFLINE);
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    /**
     * 删除文章：直接逻辑删除（不再进回收站）。
     *
     * <p>想让文章对访客不可见时用「下线」，不要用删除；删除后前台和后台列表都不再出现。</p>
     */
    @Transactional
    public void delete(Long id) {
        require(id);
        articleMapper.deleteById(id);
    }

    @Transactional
    public void restore(Long id) {
        Article article = require(id);
        if (article.getStatus() == null || article.getStatus() != RECYCLED) {
            throw BusinessException.badRequest("只有回收站中的文章可以恢复");
        }
        article.setStatus(DRAFT);
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    private void applyFields(Article article, ArticleUpsertRequest request, Long excludeId) {
        article.setTitle(request.title().trim());
        article.setSlug(resolveSlug(request.slug(), request.title(), excludeId));
        article.setCategoryId(request.categoryId());
        article.setSummary(request.summary());
        article.setCover(request.cover());
        article.setContent(request.content());
        article.setContentFormat("markdown");
        article.setVisibility(request.visibility() == null ? 0 : request.visibility());
        article.setIsTop(request.isTop() == null ? 0 : request.isTop());
        article.setIsRecommend(request.isRecommend() == null ? 0 : request.isRecommend());
        article.setSeoTitle(request.seoTitle());
        article.setSeoDescription(request.seoDescription());
    }

    /**
     * slug 是公开路由标识，必须唯一且稳定。
     * 手填优先；未填则按标题生成；冲突时追加 -2、-3…
     */
    private String resolveSlug(String slug, String title, Long excludeId) {
        String base = StringUtils.hasText(slug) ? slug.trim() : SlugUtils.fromTitle(title);
        if (!SlugUtils.isValid(base)) {
            throw BusinessException.badRequest("slug 格式不合法，只能是小写字母、数字与短横线");
        }
        String candidate = base;
        for (int i = 2; i < 100; i++) {
            Long count = articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                    .eq(Article::getSlug, candidate)
                    .ne(excludeId != null, Article::getId, excludeId));
            if (count == null || count == 0) {
                return candidate;
            }
            candidate = base + "-" + i;
        }
        throw BusinessException.conflict("slug 冲突过多，请手动指定一个");
    }

    private void validateRelations(ArticleUpsertRequest request) {
        if (request.categoryId() != null && categoryMapper.selectById(request.categoryId()) == null) {
            throw BusinessException.badRequest("分类不存在");
        }
        List<Long> tagIds = request.tagIds();
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Long> distinct = tagIds.stream().filter(Objects::nonNull).distinct().toList();
            long found = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery().in(Tag::getId, distinct));
            if (found != distinct.size()) {
                throw BusinessException.badRequest("存在无效的标签");
            }
        }
    }

    private void syncTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, articleId));
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds.stream().filter(Objects::nonNull).distinct().toList()) {
            ArticleTag relation = new ArticleTag();
            relation.setArticleId(articleId);
            relation.setTagId(tagId);
            articleTagMapper.insert(relation);
        }
    }

    private Article require(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw BusinessException.notFound("文章不存在");
        }
        return article;
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

    private Map<Long, List<String>> tagNamesByArticle(List<Article> articles) {
        if (articles.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();
        List<ArticleTag> relations = articleTagMapper.selectList(
                Wrappers.<ArticleTag>lambdaQuery().in(ArticleTag::getArticleId, ids));
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> tagIds = relations.stream().map(ArticleTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> names = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));

        return relations.stream().collect(Collectors.groupingBy(
                ArticleTag::getArticleId,
                Collectors.mapping(r -> names.getOrDefault(r.getTagId(), ""), Collectors.toList())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().filter(StringUtils::hasText).toList()));
    }
}
