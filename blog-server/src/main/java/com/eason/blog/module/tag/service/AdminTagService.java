package com.eason.blog.module.tag.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.db.RowUtils;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.util.SlugUtils;
import com.eason.blog.module.article.entity.ArticleTag;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.article.mapper.ArticleTagMapper;
import com.eason.blog.module.tag.dto.TagRequest;
import com.eason.blog.module.tag.entity.Tag;
import com.eason.blog.module.tag.mapper.TagMapper;
import com.eason.blog.module.tag.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminTagService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleMapper articleMapper;

    public AdminTagService(TagMapper tagMapper, ArticleTagMapper articleTagMapper, ArticleMapper articleMapper) {
        this.tagMapper = tagMapper;
        this.articleTagMapper = articleTagMapper;
        this.articleMapper = articleMapper;
    }

    public List<TagVO> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleMapper.countAllArticlesByTag()) {
            counts.put(RowUtils.longValue(row, "taxonomy_id"), RowUtils.longValue(row, "total"));
        }
        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery().orderByAsc(Tag::getId))
                .stream()
                .map(t -> new TagVO(t.getId(), t.getName(), t.getSlug(),
                        counts.getOrDefault(t.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public Long create(TagRequest request) {
        Tag tag = new Tag();
        apply(tag, request, null);
        tag.setArticleCount(0);
        tag.setCreateTime(LocalDateTime.now());
        tagMapper.insert(tag);
        return tag.getId();
    }

    @Transactional
    public void update(Long id, TagRequest request) {
        Tag tag = require(id);
        apply(tag, request, id);
        tagMapper.updateById(tag);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        Long used = articleTagMapper.selectCount(Wrappers.<ArticleTag>lambdaQuery()
                .eq(ArticleTag::getTagId, id));
        if (used != null && used > 0) {
            throw BusinessException.conflict("该标签已被 " + used + " 篇文章使用，请先解除关联");
        }
        tagMapper.deleteById(id);
    }

    private void apply(Tag tag, TagRequest request, Long excludeId) {
        tag.setName(request.name().trim());
        tag.setSlug(resolveSlug(request.slug(), request.name(), excludeId));
    }

    private String resolveSlug(String slug, String name, Long excludeId) {
        String base = StringUtils.hasText(slug) ? slug.trim() : SlugUtils.fromTitle(name);
        if (!SlugUtils.isValid(base)) {
            throw BusinessException.badRequest("slug 格式不合法");
        }
        String candidate = base;
        for (int i = 2; i < 100; i++) {
            Long count = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery()
                    .eq(Tag::getSlug, candidate)
                    .ne(excludeId != null, Tag::getId, excludeId));
            if (count == null || count == 0) {
                return candidate;
            }
            candidate = base + "-" + i;
        }
        throw BusinessException.conflict("slug 冲突过多，请手动指定");
    }

    private Tag require(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw BusinessException.notFound("标签不存在");
        }
        return tag;
    }
}
