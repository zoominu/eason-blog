package com.eason.blog.module.column.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.db.RowUtils;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.util.SlugUtils;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.column.dto.ColumnRequest;
import com.eason.blog.module.column.entity.Column;
import com.eason.blog.module.column.entity.ColumnArticle;
import com.eason.blog.module.column.mapper.ColumnArticleMapper;
import com.eason.blog.module.column.mapper.ColumnMapper;
import com.eason.blog.module.column.vo.ColumnVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminColumnService {

    private final ColumnMapper columnMapper;
    private final ColumnArticleMapper columnArticleMapper;
    private final ArticleMapper articleMapper;

    public AdminColumnService(ColumnMapper columnMapper,
                              ColumnArticleMapper columnArticleMapper,
                              ArticleMapper articleMapper) {
        this.columnMapper = columnMapper;
        this.columnArticleMapper = columnArticleMapper;
        this.articleMapper = articleMapper;
    }

    public List<ColumnVO> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleMapper.countAllArticlesByColumn()) {
            counts.put(RowUtils.longValue(row, "taxonomy_id"), RowUtils.longValue(row, "total"));
        }
        return columnMapper.selectList(Wrappers.<Column>lambdaQuery()
                        .orderByAsc(Column::getSort)
                        .orderByAsc(Column::getId))
                .stream()
                .map(c -> new ColumnVO(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
                        c.getCover(), counts.getOrDefault(c.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public Long create(ColumnRequest request) {
        Column column = new Column();
        apply(column, request, null);
        column.setArticleCount(0);
        LocalDateTime now = LocalDateTime.now();
        column.setCreateTime(now);
        column.setUpdateTime(now);
        column.setIsDeleted(0);
        columnMapper.insert(column);
        return column.getId();
    }

    @Transactional
    public void update(Long id, ColumnRequest request) {
        Column column = require(id);
        apply(column, request, id);
        column.setUpdateTime(LocalDateTime.now());
        columnMapper.updateById(column);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        Long used = columnArticleMapper.selectCount(Wrappers.<ColumnArticle>lambdaQuery()
                .eq(ColumnArticle::getColumnId, id));
        if (used != null && used > 0) {
            throw BusinessException.conflict("该专栏下还有 " + used + " 篇文章，请先移出");
        }
        columnMapper.deleteById(id);
    }

    private void apply(Column column, ColumnRequest request, Long excludeId) {
        column.setName(request.name().trim());
        column.setSlug(resolveSlug(request.slug(), request.name(), excludeId));
        column.setDescription(request.description());
        column.setCover(request.cover());
        column.setSort(request.sort() == null ? 0 : request.sort());
    }

    private String resolveSlug(String slug, String name, Long excludeId) {
        String base = StringUtils.hasText(slug) ? slug.trim() : SlugUtils.fromTitle(name);
        if (!SlugUtils.isValid(base)) {
            throw BusinessException.badRequest("slug 格式不合法");
        }
        String candidate = base;
        for (int i = 2; i < 100; i++) {
            Long count = columnMapper.selectCount(Wrappers.<Column>lambdaQuery()
                    .eq(Column::getSlug, candidate)
                    .ne(excludeId != null, Column::getId, excludeId));
            if (count == null || count == 0) {
                return candidate;
            }
            candidate = base + "-" + i;
        }
        throw BusinessException.conflict("slug 冲突过多，请手动指定");
    }

    private Column require(Long id) {
        Column column = columnMapper.selectById(id);
        if (column == null) {
            throw BusinessException.notFound("专栏不存在");
        }
        return column;
    }
}
