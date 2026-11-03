package com.eason.blog.module.system.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台仪表盘聚合数据。
 *
 * <p>拆成明确的 record 而不是 Map：字段一旦变动编译器会提醒前端与测试，
 * 也避免把内部对象直接序列化出去。</p>
 */
public record DashboardVO(
        long publishedArticles,
        long draftArticles,
        long offlineArticles,
        long recycledArticles,
        long totalViews,
        long users,
        long categories,
        long tags,
        List<RecentArticleVO> recentArticles,
        List<ArchiveItemVO> archive,
        List<RecentUserVO> recentUsers,
        List<LoginLogVO> recentLogins,
        SystemInfoVO system) {

    public record RecentArticleVO(Long id, String title, Integer status, LocalDateTime updateTime) {
    }

    public record ArchiveItemVO(String month, Long total) {
    }

    /** 最近活跃用户：最后登录时间倒序。 */
    public record RecentUserVO(
            Long id,
            String nickname,
            String email,
            String avatar,
            LocalDateTime lastLoginTime,
            String lastLoginIp,
            List<String> roles) {
    }

    public record LoginLogVO(
            Long id,
            String email,
            String ip,
            Integer loginStatus,
            String message,
            LocalDateTime createTime) {
    }

    /** 系统信息：只暴露运行时概况，不包含路径、连接串、密钥等敏感内容。 */
    public record SystemInfoVO(
            String appName,
            String profile,
            String javaVersion,
            String osName,
            String osArch,
            LocalDateTime startedAt,
            long uptimeSeconds,
            long heapUsedMb,
            long heapMaxMb,
            String dbProduct,
            String dbVersion,
            LocalDateTime serverTime) {
    }
}
