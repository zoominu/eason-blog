package com.eason.blog.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.log.entity.LoginLog;
import com.eason.blog.module.log.mapper.LoginLogMapper;
import com.eason.blog.module.system.vo.DashboardVO;
import com.eason.blog.module.tag.mapper.TagMapper;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 后台仪表盘数据。
 *
 * <p>聚合四类信息：内容统计、最近动态（文章/用户/登录）、归档、运行时信息。
 * 全部只读，且只对 SUPER_ADMIN 暴露。</p>
 */
@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 6;

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final LoginLogMapper loginLogMapper;
    private final DataSource dataSource;
    private final Environment environment;

    @Value("${spring.application.name:blog-server}")
    private String appName;

    public DashboardService(ArticleMapper articleMapper,
                            UserMapper userMapper,
                            UserRoleMapper userRoleMapper,
                            CategoryMapper categoryMapper,
                            TagMapper tagMapper,
                            LoginLogMapper loginLogMapper,
                            DataSource dataSource,
                            Environment environment) {
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.loginLogMapper = loginLogMapper;
        this.dataSource = dataSource;
        this.environment = environment;
    }

    public DashboardVO overview() {
        return new DashboardVO(
                articleMapper.countByStatus(1),
                articleMapper.countByStatus(0),
                articleMapper.countByStatus(2),
                articleMapper.countByStatus(3),
                articleMapper.sumPublicViews(),
                userMapper.selectCount(null),
                categoryMapper.selectCount(null),
                tagMapper.selectCount(null),
                recentArticles(),
                archive(),
                recentUsers(),
                recentLogins(),
                systemInfo());
    }

    private List<DashboardVO.RecentArticleVO> recentArticles() {
        Page<Article> page = new Page<>(1, RECENT_LIMIT);
        articleMapper.selectPage(page, Wrappers.<Article>lambdaQuery()
                .orderByDesc(Article::getUpdateTime));
        return page.getRecords().stream()
                .map(a -> new DashboardVO.RecentArticleVO(a.getId(), a.getTitle(), a.getStatus(), a.getUpdateTime()))
                .toList();
    }

    private List<DashboardVO.ArchiveItemVO> archive() {
        return articleMapper.selectArchive().stream()
                .map(row -> new DashboardVO.ArchiveItemVO(
                        String.valueOf(row.getOrDefault("month", row.get("MONTH"))),
                        ((Number) row.getOrDefault("total", row.getOrDefault("TOTAL", 0))).longValue()))
                .toList();
    }

    private List<DashboardVO.RecentUserVO> recentUsers() {
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .isNotNull(User::getLastLoginTime)
                .orderByDesc(User::getLastLoginTime)
                .last("limit " + RECENT_LIMIT));
        return users.stream()
                .map(u -> new DashboardVO.RecentUserVO(
                        u.getId(),
                        u.getNickname(),
                        u.getEmail(),
                        u.getAvatar(),
                        u.getLastLoginTime(),
                        u.getLastLoginIp(),
                        userRoleMapper.selectRoleCodesByUserId(u.getId())))
                .toList();
    }

    private List<DashboardVO.LoginLogVO> recentLogins() {
        List<LoginLog> logs = loginLogMapper.selectList(Wrappers.<LoginLog>lambdaQuery()
                .orderByDesc(LoginLog::getCreateTime)
                .last("limit 8"));
        return logs.stream()
                .map(l -> new DashboardVO.LoginLogVO(l.getId(), l.getEmail(), l.getIp(),
                        l.getLoginStatus(), l.getMessage(), l.getCreateTime()))
                .toList();
    }

    private DashboardVO.SystemInfoVO systemInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);

        String dbProduct = "unknown";
        String dbVersion = "unknown";
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            dbProduct = meta.getDatabaseProductName();
            dbVersion = meta.getDatabaseProductVersion();
        } catch (Exception ignored) {
            // 元数据拿不到不影响仪表盘其它部分
        }

        String[] profiles = environment.getActiveProfiles();
        return new DashboardVO.SystemInfoVO(
                appName,
                profiles.length == 0 ? "default" : String.join(",", profiles),
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(runtime.getStartTime()), ZoneId.systemDefault()),
                runtime.getUptime() / 1000,
                usedMb,
                maxMb,
                dbProduct,
                dbVersion,
                LocalDateTime.now());
    }
}
