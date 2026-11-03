package com.eason.blog.module.column.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.db.RowUtils;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.column.entity.Column;
import com.eason.blog.module.column.mapper.ColumnMapper;
import com.eason.blog.module.column.vo.ColumnVO;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ColumnService {

    private final ColumnMapper columnMapper;
    private final ArticleMapper articleMapper;

    public ColumnService(ColumnMapper columnMapper, ArticleMapper articleMapper) {
        this.columnMapper = columnMapper;
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

    private Map<Long, Long> counts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleMapper.countPublicArticlesByColumn()) {
            counts.put(RowUtils.longValue(row, "taxonomy_id"), RowUtils.longValue(row, "total"));
        }
        return counts;
    }
}
