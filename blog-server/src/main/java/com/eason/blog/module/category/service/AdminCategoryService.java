package com.eason.blog.module.category.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.db.RowUtils;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.util.SlugUtils;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.category.dto.CategoryRequest;
import com.eason.blog.module.category.entity.Category;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.category.vo.CategoryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类管理（后台）。删除前会检查文章引用，避免产生孤儿数据。
 */
@Service
public class AdminCategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    public AdminCategoryService(CategoryMapper categoryMapper, ArticleMapper articleMapper) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
    }

    public List<CategoryVO> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleMapper.countAllArticlesByCategory()) {
            counts.put(RowUtils.longValue(row, "taxonomy_id"), RowUtils.longValue(row, "total"));
        }
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .orderByAsc(Category::getSort)
                        .orderByAsc(Category::getId))
                .stream()
                .map(c -> new CategoryVO(c.getId(), c.getParentId(), c.getName(), c.getSlug(),
                        c.getDescription(), counts.getOrDefault(c.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public Long create(CategoryRequest request) {
        Category category = new Category();
        apply(category, request, null);
        category.setArticleCount(0);
        LocalDateTime now = LocalDateTime.now();
        category.setCreateTime(now);
        category.setUpdateTime(now);
        category.setIsDeleted(0);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Transactional
    public void update(Long id, CategoryRequest request) {
        Category category = require(id);
        apply(category, request, id);
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.updateById(category);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        Long children = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                .eq(Category::getParentId, id));
        if (children != null && children > 0) {
            throw BusinessException.conflict("该分类下还有子分类，请先处理子分类");
        }
        Long articles = articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                .eq(Article::getCategoryId, id));
        if (articles != null && articles > 0) {
            throw BusinessException.conflict("该分类下还有 " + articles + " 篇文章，请先移动或删除");
        }
        categoryMapper.deleteById(id);
    }

    private void apply(Category category, CategoryRequest request, Long excludeId) {
        category.setName(request.name().trim());
        category.setSlug(resolveSlug(request.slug(), request.name(), excludeId));
        category.setParentId(request.parentId() == null ? 0L : request.parentId());
        if (excludeId != null && excludeId.equals(category.getParentId())) {
            throw BusinessException.badRequest("父分类不能是自己");
        }
        category.setDescription(request.description());
        category.setSort(request.sort() == null ? 0 : request.sort());
    }

    private String resolveSlug(String slug, String name, Long excludeId) {
        String base = StringUtils.hasText(slug) ? slug.trim() : SlugUtils.fromTitle(name);
        if (!SlugUtils.isValid(base)) {
            throw BusinessException.badRequest("slug 格式不合法");
        }
        String candidate = base;
        for (int i = 2; i < 100; i++) {
            Long count = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                    .eq(Category::getSlug, candidate)
                    .ne(excludeId != null, Category::getId, excludeId));
            if (count == null || count == 0) {
                return candidate;
            }
            candidate = base + "-" + i;
        }
        throw BusinessException.conflict("slug 冲突过多，请手动指定");
    }

    private Category require(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw BusinessException.notFound("分类不存在");
        }
        return category;
    }
}
