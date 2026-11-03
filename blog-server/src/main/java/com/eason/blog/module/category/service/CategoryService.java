package com.eason.blog.module.category.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.db.RowUtils;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.category.entity.Category;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.category.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类只读服务。
 *
 * <p>articleCount 采用实时聚合而不是读 blog_category.article_count：
 * 计数器需要在文章发布/下线/删除时同步维护，而写路径属于 P2；
 * 在数据量很小的个人博客场景，读时聚合更准确也更简单。</p>
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    public CategoryService(CategoryMapper categoryMapper, ArticleMapper articleMapper) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
    }

    public List<CategoryVO> list() {
        Map<Long, Long> counts = counts();
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .orderByAsc(Category::getSort)
                        .orderByAsc(Category::getId))
                .stream()
                .map(c -> toVO(c, counts))
                .toList();
    }

    /**
     * 分类详情：分类下没有文章时，前端也能展示名称与描述
     * （列表接口的 categoryName 取自文章，空分类拿不到）。
     */
    public CategoryVO getBySlug(String slug) {
        Category category = categoryMapper.selectOne(Wrappers.<Category>lambdaQuery()
                .eq(Category::getSlug, slug)
                .last("limit 1"));
        return category == null ? null : toVO(category, counts());
    }

    private CategoryVO toVO(Category c, Map<Long, Long> counts) {
        return new CategoryVO(c.getId(), c.getParentId(), c.getName(), c.getSlug(),
                c.getDescription(), counts.getOrDefault(c.getId(), 0L).intValue());
    }

    private Map<Long, Long> counts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleMapper.countPublicArticlesByCategory()) {
            counts.put(RowUtils.longValue(row, "taxonomy_id"), RowUtils.longValue(row, "total"));
        }
        return counts;
    }
}
