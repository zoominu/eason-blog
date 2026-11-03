package com.eason.blog.module.tag.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.db.RowUtils;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.tag.entity.Tag;
import com.eason.blog.module.tag.mapper.TagMapper;
import com.eason.blog.module.tag.vo.TagVO;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TagService {

    private final TagMapper tagMapper;
    private final ArticleMapper articleMapper;

    public TagService(TagMapper tagMapper, ArticleMapper articleMapper) {
        this.tagMapper = tagMapper;
        this.articleMapper = articleMapper;
    }

    public List<TagVO> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleMapper.countPublicArticlesByTag()) {
            counts.put(RowUtils.longValue(row, "taxonomy_id"), RowUtils.longValue(row, "total"));
        }

        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery().orderByAsc(Tag::getId))
                .stream()
                // 只展示有文章或本来就是常用标签的项，先按数量降序更符合阅读习惯
                .map(t -> new TagVO(t.getId(), t.getName(), t.getSlug(),
                        counts.getOrDefault(t.getId(), 0L).intValue()))
                .sorted((a, b) -> Integer.compare(b.articleCount(), a.articleCount()))
                .toList();
    }
}
