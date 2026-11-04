package com.eason.blog.module.interaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.comment.entity.Comment;
import com.eason.blog.module.comment.mapper.CommentMapper;
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
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private CommentMapper commentMapper;
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

    @Test
    void rejectedParentBecomesPlaceholderAndKeepsReplies() throws Exception {
        long root = postComment(userCookie, "占位根评论-" + UUID.randomUUID().toString().substring(0, 5), null);
        mockMvc.perform(post("/api/v1/admin/comments/" + root + "/approve").cookie(adminCookie))
                .andExpect(status().isOk());

        long reply = postComment(adminCookie, "占位回复", root);
        // 拒绝父评论：回复不应被拒绝
        mockMvc.perform(post("/api/v1/admin/comments/" + root + "/reject").cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/" + slug + "/comments?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + root + ")].hidden").value(true))
                .andExpect(jsonPath("$.data.records[?(@.id == " + root + ")].content")
                        .value("当前评论已被隐藏"))
                .andExpect(jsonPath("$.data.records[?(@.id == " + root + ")].replies[?(@.id == "
                        + reply + ")]").exists());
    }

    @Test
    void deletingParentRemovesReplies() throws Exception {
        long root = postComment(userCookie, "删除根评论-" + UUID.randomUUID().toString().substring(0, 5), null);
        long reply = postComment(adminCookie, "删除回复", root);

        mockMvc.perform(delete("/api/v1/admin/comments/" + root).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(2));

        mockMvc.perform(get("/api/v1/articles/" + slug + "/comments?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + root + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id == " + reply + ")]").doesNotExist());
    }

    @Test
    void approvalNotificationIsSentOnlyOnce() throws Exception {
        long comment = postComment(userCookie, "通知只发一次-" + UUID.randomUUID().toString().substring(0, 5), null);

        mockMvc.perform(post("/api/v1/admin/comments/" + comment + "/approve").cookie(adminCookie))
                .andExpect(status().isOk());
        int first = approvalNoticeCount(comment);
        assertThat(first).isEqualTo(1);

        mockMvc.perform(post("/api/v1/admin/comments/" + comment + "/reject").cookie(adminCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/comments/" + comment + "/approve").cookie(adminCookie))
                .andExpect(status().isOk());

        assertThat(approvalNoticeCount(comment)).isEqualTo(1);
    }

    @Test
    void nicknameIsCheckedAgainstBannedWords() throws Exception {
        setConfig("audit.banned-words", "违禁昵称");

        mockMvc.perform(patch("/api/v1/users/me").cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"我的违禁昵称\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + UUID.randomUUID().toString().substring(0, 8)
                                + "@test.local\",\"password\":\"Test123456\",\"nickname\":\"违禁昵称用户\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingArticleRemovesItCompletely() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/articles/" + articleId()).cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/" + slug))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/articles?keyword=" + slug).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void resourceIsTypedAndListsReferencingArticles() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        MockMultipartFile image = new MockMultipartFile("file", "cover.png", "image/png", png);
        String body = mockMvc.perform(multipart("/api/v1/admin/resources/upload").file(image).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("article_image"))
                .andReturn().getResponse().getContentAsString();
        Number resourceId = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
        String url = com.jayway.jsonpath.JsonPath.read(body, "$.data.url");

        mockMvc.perform(get("/api/v1/admin/resources/" + resourceId.longValue() + "/references")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        Article article = articleMapper.selectById(articleId());
        article.setContent("![封面](" + url + ")");
        articleMapper.updateById(article);

        mockMvc.perform(get("/api/v1/admin/resources/" + resourceId.longValue() + "/references")
                        .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(articleId()));
    }

    @Test
    void importMarkdownRewritesEmbeddedAndRelativeImages() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        String base64 = java.util.Base64.getEncoder().encodeToString(png);
        String md = "---\ntitle: 导入测试\nslug: import-test\nsummary: 摘要\n---\n\n"
                + "![内嵌](data:image/png;base64," + base64 + ")\n\n"
                + "![绝对路径](C:\\\\Users\\\\eason\\\\Pictures\\\\a.png)\n\n"
                + "![缺失](./pics/missing.png)\n";
        MockMultipartFile mdFile = new MockMultipartFile("file", "post.md", "text/markdown",
                md.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile image = new MockMultipartFile("images", "a.png", "image/png", png);

        mockMvc.perform(multipart("/api/v1/admin/articles/import-markdown")
                        .file(mdFile).file(image).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("导入测试"))
                .andExpect(jsonPath("$.data.slug").value("import-test"))
                .andExpect(jsonPath("$.data.content")
                        .value(org.hamcrest.Matchers.containsString("/uploads/")))
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andExpect(jsonPath("$.data.warnings.length()").value(1))
                .andExpect(jsonPath("$.data.warnings[0]")
                        .value(org.hamcrest.Matchers.containsString("missing.png")));

        // 导入的图片会登记到资源库
        mockMvc.perform(get("/api/v1/admin/resources?keyword=a.png&size=10").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].url")
                        .value(org.hamcrest.Matchers.startsWith("/uploads/")));
    }

    @Test
    void commentCannotBeDeletedAfterThreeMinutes() throws Exception {
        long id = postComment(userCookie, "超时评论-" + UUID.randomUUID().toString().substring(0, 5), null);
        Comment stored = commentMapper.selectById(id);
        stored.setCreateTime(LocalDateTime.now().minusMinutes(5));
        commentMapper.updateById(stored);

        mockMvc.perform(delete("/api/v1/users/me/comments/" + id).cookie(userCookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myCommentsCanBeListedAndDeletedWithinWindow() throws Exception {
        long id = postComment(userCookie, "可删除评论-" + UUID.randomUUID().toString().substring(0, 5), null);

        mockMvc.perform(get("/api/v1/users/me/comments?size=50").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + id + ")].canDelete").value(true));

        mockMvc.perform(delete("/api/v1/users/me/comments/" + id).cookie(userCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/comments?size=50").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + id + ")]").doesNotExist());
    }

    @Test
    void likesCanBeListedAndRemovedInBatch() throws Exception {
        mockMvc.perform(post("/api/v1/articles/" + articleId() + "/like").cookie(userCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/likes?size=50").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + articleId() + ")]").exists());

        mockMvc.perform(post("/api/v1/users/me/likes/batch-remove").cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[" + articleId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(1));

        mockMvc.perform(get("/api/v1/users/me/likes?size=50").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + articleId() + ")]").doesNotExist());
    }

    @Test
    void notificationTypeFilterWorks() throws Exception {
        long id = postComment(userCookie, "通知分类-" + UUID.randomUUID().toString().substring(0, 5), null);
        mockMvc.perform(post("/api/v1/admin/comments/" + id + "/approve").cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/notifications?limit=50&type=SYSTEM").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.link =~ /.*comment=" + id + ".*/)]").exists());

        mockMvc.perform(get("/api/v1/users/me/notifications?limit=50&type=REPLY").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.link =~ /.*comment=" + id + ".*/)]").doesNotExist());
    }

    @Test
    void avatarUploadReturnsUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});
        mockMvc.perform(multipart("/api/v1/users/me/avatar").file(file).cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/uploads/")));

        // 头像也登记进资源库，类型为 avatar
        mockMvc.perform(get("/api/v1/admin/resources?type=avatar&size=20").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].type").value("avatar"));
    }

    private int approvalNoticeCount(long commentId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me/notifications?limit=50").cookie(userCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Object> links = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].link");
        return (int) links.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(link -> link.contains("?comment=" + commentId))
                .count();
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
