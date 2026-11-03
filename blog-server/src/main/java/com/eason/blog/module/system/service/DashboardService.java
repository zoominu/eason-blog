package com.eason.blog.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.tag.mapper.TagMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台仪表盘数据。仅管理员可访问（由 SecurityConfig 拦截 /api/v1/admin/**）。
 */
@Service
public class DashboardService {

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;

    public DashboardService(ArticleMapper articleMapper,
                            UserMapper userMapper,
                            CategoryMapper categoryMapper,
                            TagMapper tagMapper) {
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
    }

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("publishedArticles", articleMapper.countByStatus(1));
        data.put("draftArticles", articleMapper.countByStatus(0));
        data.put("offlineArticles", articleMapper.countByStatus(2));
        data.put("totalViews", articleMapper.sumPublicViews());
        data.put("users", userMapper.selectCount(null));
        data.put("categories", categoryMapper.selectCount(null));
        data.put("tags", tagMapper.selectCount(null));

        Page<Article> latest = new Page<>(1, 5);
        articleMapper.selectPage(latest, Wrappers.<Article>lambdaQuery()
                .orderByDesc(Article::getUpdateTime));
        List<Map<String, Object>> recent = latest.getRecords().stream()
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("title", a.getTitle());
                    item.put("status", a.getStatus());
                    item.put("updateTime", a.getUpdateTime());
                    return item;
                })
                .toList();
        data.put("recentArticles", recent);
        data.put("archive", articleMapper.selectArchive());
        return data;
    }
}
