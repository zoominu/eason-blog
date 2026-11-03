package com.eason.blog.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.config.AppProperties;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.entity.ArticleTag;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.article.mapper.ArticleTagMapper;
import com.eason.blog.module.column.entity.ColumnArticle;
import com.eason.blog.module.column.mapper.ColumnArticleMapper;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.entity.UserRole;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开发环境演示数据（仅 app.demo-data.enabled=true 时执行，生产默认关闭）。
 *
 * <p>演示数据不放进 Flyway：Flyway 迁移在所有环境都会执行，而演示文章一旦进入生产
 * 迁移历史就无法干净删除。这里用幂等的 ApplicationRunner，按 slug 判断是否已存在，
 * 因此新增演示文章时重启即可补齐，不会重复插入。</p>
 */
@Component
@Order(100)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ColumnArticleMapper columnArticleMapper;

    public DemoDataSeeder(AppProperties properties,
                          UserMapper userMapper,
                          UserRoleMapper userRoleMapper,
                          ArticleMapper articleMapper,
                          ArticleTagMapper articleTagMapper,
                          ColumnArticleMapper columnArticleMapper) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.columnArticleMapper = columnArticleMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.demoData().enabled()) {
            return;
        }
        User author = findAdmin();
        if (author == null) {
            log.warn("未找到管理员账号，跳过演示数据；请先配置 BLOG_ADMIN_EMAIL/BLOG_ADMIN_PASSWORD");
            return;
        }

        int created = 0;
        created += seed(author, new Demo(2L, "Spring Boot 3 + MyBatis-Plus 工程骨架实践",
                "spring-boot-3-mybatis-plus-skeleton",
                "从零搭建一个可运行、可测试的 Spring Boot 3 后端骨架，包含统一响应、异常处理与 Flyway 迁移。",
                SPRING_CONTENT, List.of(1L, 2L, 3L), 8));
        created += seed(author, new Demo(3L, "MySQL 索引失效的六个常见场景",
                "mysql-index-not-used-cases",
                "隐式类型转换、函数包裹列、前导模糊匹配……这些写法会让索引形同虚设。",
                MYSQL_CONTENT, List.of(4L), 5));
        created += seed(author, new Demo(4L, "Vue 3 + Vite 的极简博客前端结构",
                "vue3-vite-minimal-blog-frontend",
                "用组合式 API 与 Pinia 组织一个内容优先的博客前台，重点是数据流清晰。",
                VUE_CONTENT, List.of(5L, 6L), 2));
        created += seed(author, new Demo(1L, "为什么我在个人项目里坚持用 Java 21",
                "why-java-21-for-side-projects",
                "虚拟线程、record、模式匹配——新语法不只是「写得爽」，而是能实打实减少样板代码。",
                JAVA_CONTENT, List.of(1L), 12));
        created += seed(author, new Demo(5L, "一个人维护博客：把环境搭成一条命令",
                "one-command-dev-environment",
                "从 JDK、MySQL 到前后端启动，用脚本把重复劳动压缩成三条命令。",
                ENGINEERING_CONTENT, List.of(7L), 1));

        if (created > 0) {
            log.info("已补齐 {} 篇演示文章（作者 {}）", created, author.getEmail());
        } else {
            log.info("演示文章已存在，跳过");
        }
        linkColumn();
    }

    /**
     * 把技术类演示文章挂进「从零搭建个人博客」专栏，保证专栏计数不是 0。
     * 关联表有联合唯一键，这里先查后插，重复执行也安全。
     */
    private void linkColumn() {
        long columnId = 1L;
        List<Article> articles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .in(Article::getSlug, List.of(
                        "why-java-21-for-side-projects",
                        "spring-boot-3-mybatis-plus-skeleton",
                        "mysql-index-not-used-cases",
                        "vue3-vite-minimal-blog-frontend"))
                .orderByAsc(Article::getPublishTime));

        int sort = 1;
        for (Article article : articles) {
            Long linked = columnArticleMapper.selectCount(Wrappers.<ColumnArticle>lambdaQuery()
                    .eq(ColumnArticle::getColumnId, columnId)
                    .eq(ColumnArticle::getArticleId, article.getId()));
            if (linked != null && linked > 0) {
                sort++;
                continue;
            }
            ColumnArticle relation = new ColumnArticle();
            relation.setColumnId(columnId);
            relation.setArticleId(article.getId());
            relation.setSort(sort++);
            relation.setCreateTime(LocalDateTime.now());
            columnArticleMapper.insert(relation);
        }
    }

    private User findAdmin() {
        List<Long> adminUserIds = userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getRoleId, 1L))
                .stream().map(UserRole::getUserId).toList();
        if (adminUserIds.isEmpty()) {
            return null;
        }
        return userMapper.selectById(adminUserIds.get(0));
    }

    private int seed(User author, Demo demo) {
        Long existing = articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                .eq(Article::getSlug, demo.slug()));
        if (existing != null && existing > 0) {
            return 0;
        }

        LocalDateTime publishTime = LocalDateTime.now().minusDays(demo.daysAgo());
        Article article = new Article();
        article.setAuthorId(author.getId());
        article.setCategoryId(demo.categoryId());
        article.setTitle(demo.title());
        article.setSlug(demo.slug());
        article.setSummary(demo.summary());
        article.setContent(demo.content());
        article.setContentFormat("markdown");
        article.setStatus(1);
        article.setVisibility(0);
        article.setIsTop(0);
        article.setIsRecommend(demo.daysAgo() <= 8 ? 1 : 0);
        article.setViewCount(0L);
        article.setLikeCount(0L);
        article.setCommentCount(0L);
        article.setPublishTime(publishTime);
        article.setCreateTime(publishTime);
        article.setUpdateTime(publishTime);
        article.setIsDeleted(0);
        articleMapper.insert(article);

        for (Long tagId : demo.tagIds()) {
            ArticleTag articleTag = new ArticleTag();
            articleTag.setArticleId(article.getId());
            articleTag.setTagId(tagId);
            articleTagMapper.insert(articleTag);
        }
        return 1;
    }

    private record Demo(Long categoryId, String title, String slug, String summary,
                        String content, List<Long> tagIds, int daysAgo) {
    }

    private static final String SPRING_CONTENT = """
            ## 为什么先搭骨架

            一个能长期维护的项目，第一步不是写业务，而是确定**分层边界**与**失败方式**。

            ### 分层约定

            `Controller -> Service -> Mapper`，Entity / DTO / VO 分离：

            ```java
            @RestController
            @RequestMapping("/api/v1/articles")
            public class ArticleController {
                private final ArticleService articleService;
                // ...
            }
            ```

            ### 统一响应

            错误一律用正确的 HTTP 状态码表达，而不是 200 包裹错误：

            ```json
            { "code": 404, "message": "文章不存在或尚未发布" }
            ```

            > 这样前端只需要判断 HTTP 状态，配合拦截器就能统一处理。
            """;

    private static final String MYSQL_CONTENT = """
            ## 一、隐式类型转换

            字段是 `VARCHAR`，查询却传数字：

            ```sql
            -- 索引失效
            SELECT * FROM blog_user WHERE uid = 123;
            ```

            MySQL 会把列转成数字比较，等价于对列使用函数。

            ## 二、前导模糊匹配

            ```sql
            SELECT * FROM blog_article WHERE title LIKE '%索引%';
            ```

            需要前缀搜索时，改用全文索引或搜索引擎。

            ## 三、在索引列上做运算

            ```sql
            -- 失效
            WHERE DATE(publish_time) = '2026-11-03'
            -- 改成范围查询
            WHERE publish_time >= '2026-11-03' AND publish_time < '2026-11-04'
            ```

            其余场景还包括：联合索引不满足最左前缀、`OR` 连接非索引列、
            选择性过低导致优化器放弃索引。
            """;

    private static final String VUE_CONTENT = """
            ## 目录结构

            ```
            src/
              api/        # 请求封装，统一错误处理
              components/ # 展示型组件
              layouts/    # 页面骨架
              stores/     # Pinia
              views/      # 页面
            ```

            ## 请求封装

            所有接口都返回 `ApiResponse`，因此只需要在一处解包：

            ```ts
            const data = await http.get<PageResult<Article>>('/articles', { params })
            ```

            ## 主题

            跟随系统 + 手动覆盖，存储在 `localStorage`，并用
            `prefers-color-scheme` 监听系统变化。
            """;

    private static final String JAVA_CONTENT = """
            ## 虚拟线程解决了什么

            传统线程池的痛点是「线程数」与「任务数」强耦合：IO 阻塞时线程只能干等。
            虚拟线程把阻塞成本从操作系统线程转移到 JVM 调度，写同步代码也能获得接近异步的吞吐。

            ```java
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                IntStream.range(0, 1_000).forEach(i ->
                        executor.submit(() -> fetchRemote(i)));
            }
            ```

            ## record 与模式匹配

            DTO 从 40 行 getter/setter 变成一行：

            ```java
            public record LoginRequest(String email, String password) {}
            ```

            `switch` 模式匹配让「按类型分支」不再需要 instanceof 强转。

            ## 结论

            新语法真正的价值是**降低阅读成本**，而不是炫技。个人项目里尤其明显：
            半年后再看自己的代码，能少猜一点就少猜一点。
            """;

    private static final String ENGINEERING_CONTENT = """
            ## 目标

            新机器上从零到能访问页面，只允许三条命令：

            ```powershell
            powershell -File scripts/start-mysql.ps1
            powershell -File scripts/init-db.ps1
            powershell -File scripts/start-backend.ps1
            ```

            ## 关键点

            1. **工具链随仓库走**：JDK / Maven / MySQL 解压到 `.tools`，不依赖全局环境变量。
            2. **数据库初始化幂等**：`CREATE DATABASE IF NOT EXISTS` + `CREATE USER IF NOT EXISTS`。
            3. **迁移自动执行**：Flyway 在后端启动时校验并补齐，不需要手工跑 SQL。
            4. **失败要明显**：脚本里每一步都检查 `$LASTEXITCODE`，不静默失败。

            > 环境脚本的价值不在于「省几次点击」，而在于让新人（或者半年后的自己）
            > 不必再回忆「上次是怎么跑起来的」。
            """;
}
