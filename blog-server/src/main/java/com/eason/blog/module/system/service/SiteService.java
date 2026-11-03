package com.eason.blog.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.column.mapper.ColumnMapper;
import com.eason.blog.module.system.entity.SystemConfig;
import com.eason.blog.module.system.mapper.SystemConfigMapper;
import com.eason.blog.module.tag.mapper.TagMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 站点公开信息。
 *
 * <p>只输出 is_public=1 的配置项，避免把后台私密配置（如审核开关、存储路径）暴露给访客。</p>
 */
@Service
public class SiteService {

    private final SystemConfigMapper systemConfigMapper;
    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ColumnMapper columnMapper;

    public SiteService(SystemConfigMapper systemConfigMapper,
                       ArticleMapper articleMapper,
                       CategoryMapper categoryMapper,
                       TagMapper tagMapper,
                       ColumnMapper columnMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.columnMapper = columnMapper;
    }

    public Map<String, String> publicConfig() {
        return systemConfigMapper.selectList(Wrappers.<SystemConfig>lambdaQuery()
                        .eq(SystemConfig::getIsPublic, 1))
                .stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, SystemConfig::getConfigValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    public Map<String, Object> statistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("articleCount", articleMapper.countByStatus(1));
        stats.put("viewCount", articleMapper.sumPublicViews());
        stats.put("categoryCount", categoryMapper.selectCount(null));
        stats.put("tagCount", tagMapper.selectCount(null));
        stats.put("columnCount", columnMapper.selectCount(null));
        return stats;
    }
}
