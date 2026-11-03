package com.eason.blog.module.article.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.module.article.entity.ArticleTag;
import com.eason.blog.module.article.mapper.ArticleTagMapper;
import com.eason.blog.module.tag.entity.Tag;
import com.eason.blog.module.tag.mapper.TagMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量解析文章标签名。
 *
 * <p>列表类接口（文章列表、搜索、专栏、收藏）都需要标签，逐个查会 N+1；
 * 这里统一用两次 IN 查询完成：文章-标签关联 + 标签表。</p>
 */
@Component
public class ArticleTagResolver {

    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;

    public ArticleTagResolver(ArticleTagMapper articleTagMapper, TagMapper tagMapper) {
        this.articleTagMapper = articleTagMapper;
        this.tagMapper = tagMapper;
    }

    public Map<Long, List<String>> byArticleIds(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ArticleTag> relations = articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
                .in(ArticleTag::getArticleId, articleIds));
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> tagIds = relations.stream().map(ArticleTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> names = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));

        return relations.stream()
                .filter(r -> names.containsKey(r.getTagId()))
                .collect(Collectors.groupingBy(ArticleTag::getArticleId,
                        Collectors.mapping(r -> names.get(r.getTagId()), Collectors.toList())));
    }
}
