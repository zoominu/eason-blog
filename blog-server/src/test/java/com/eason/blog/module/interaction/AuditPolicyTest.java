package com.eason.blog.module.interaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.system.entity.SystemConfig;
import com.eason.blog.module.system.mapper.SystemConfigMapper;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import com.eason.blog.support.TestUsers;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审核策略：管理员免审、关闭审核时的违禁词拦截与替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditPolicyTest {

    private static final String PASSWORD = "Passw0rd123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private SystemConfigMapper configMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Cookie adminCookie;
    private Cookie userCookie;
    private String slug;
    private User adminUser;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = TestUsers.uniqueEmail("audit-admin");
        User admin = TestUsers.create(userMapper, passwordEncoder, adminEmail, PASSWORD);
        adminUser = admin;
        TestUsers.assignRole(userRoleMapper, roleMapper, admin.getId(), "SUPER_ADMIN");
        adminCookie = login(adminEmail);

        String userEmail = TestUsers.uniqueEmail("audit-user");
        TestUsers.create(userMapper, passwordEncoder, userEmail, PASSWORD);
        userCookie = login(userEmail);

        Article article = new Article();
        article.setAuthorId(admin.getId());
        article.setTitle("审核策略测试文章");
        article.setSlug("audit-" + UUID.randomUUID().toString().substring(0, 8));
        article.setSummary("用于审核测试");
        article.setContent("正文");
        article.setContentFormat("markdown");
        article.setStatus(1);
        article.setVisibility(0);
        article.setIsTop(0);
        article.setIsRecommend(0);
        article.setViewCount(0L);
        article.setLikeCount(0L);
        article.setCommentCount(0L);
        article.setPublishTime(LocalDateTime.now());
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        article.setIsDeleted(0);
        articleMapper.insert(article);
        slug = article.getSlug();
    }

    @AfterEach
    void tearDown() {
        // 恢复默认：评论先审后发、无违禁词、命中转审核
        setConfig("comment.audit", "true");
        setConfig("audit.banned-words", "");
        setConfig("audit.banned-action", "review");
    }

    @Test
    void adminCommentSkipsAudit() throws Exception {
        mockMvc.perform(post("/api/v1/articles/" + articleId() + "/comments").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"博主直接发布\"}"))
                .andExpect(status().isOk());

        // 未审核也应立即出现在公开列表
        mockMvc.perform(get("/api/v1/articles/" + slug + "/comments?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.content == '博主直接发布')].author").value(true));
    }

    @Test
    void bannedWordGoesToReviewWhenAuditDisabled() throws Exception {
        setConfig("comment.audit", "false");
        setConfig("audit.banned-words", "坏词");
        setConfig("audit.banned-action", "review");

        mockMvc.perform(post("/api/v1/articles/" + articleId() + "/comments").cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"这里有坏词\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/" + slug + "/comments?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void bannedWordIsMaskedWhenConfigured() throws Exception {
        setConfig("comment.audit", "false");
        setConfig("audit.banned-words", "坏词");
        setConfig("audit.banned-action", "mask");

        mockMvc.perform(post("/api/v1/articles/" + articleId() + "/comments").cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"这里有坏词\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/" + slug + "/comments?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].content").value("这里有**"));
    }

    @Test
    void cleanCommentPublishesWhenAuditDisabled() throws Exception {
        setConfig("comment.audit", "false");
        setConfig("audit.banned-words", "坏词");

        mockMvc.perform(post("/api/v1/articles/" + articleId() + "/comments").cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"正常评论\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/" + slug + "/comments?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].content").value("正常评论"));
    }

    @Test
    void replyDepthIsLimitedToFourLevels() throws Exception {
        long level1 = postComment(userCookie, "层级1-" + UUID.randomUUID().toString().substring(0, 6), null);
        long level2 = postComment(adminCookie, "层级2", level1);
        long level3 = postComment(userCookie, "层级3", level2);
        long level4 = postComment(adminCookie, "层级4", level3);

        // 第 5 级应被拒绝
        mockMvc.perform(post("/api/v1/articles/" + articleId() + "/comments").cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"层级5\",\"parentId\":" + level4 + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanFilterCommentsByKeywordAndUser() throws Exception {
        String keyword = "筛选词-" + UUID.randomUUID().toString().substring(0, 6);
        postComment(userCookie, keyword + "-A", null);
        postComment(adminCookie, keyword + "-B", null);
        postComment(userCookie, "无关内容", null);

        mockMvc.perform(get("/api/v1/admin/comments?keyword=" + keyword + "&size=50").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/admin/comments?keyword=" + keyword + "&userId=" + adminId())
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/v1/admin/comments?articleId=" + articleId()).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    private long postComment(Cookie cookie, String content, Long parentId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/articles/" + articleId() + "/comments").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"parentId\":"
                                + (parentId == null ? "null" : parentId) + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
        return id.longValue();
    }

    private long adminId() {
        return adminUser.getId();
    }

    private long articleId() {
        Article article = articleMapper.selectOne(Wrappers.<Article>lambdaQuery()
                .eq(Article::getSlug, slug).last("limit 1"));
        return article.getId();
    }

    private Cookie login(String email) throws Exception {
        String header = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        return new Cookie("blog_token", header.split("blog_token=")[1].split(";")[0]);
    }

    private void setConfig(String key, String value) {
        SystemConfig config = configMapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, key).last("limit 1"));
        assertThat(config).as("缺少配置项 " + key).isNotNull();
        config.setConfigValue(value);
        config.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(config);
    }
}
