package com.eason.blog.module.interaction;

import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import com.eason.blog.support.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4 互动集成测试：评论审核可见性、点赞幂等、收藏、留言审核、通知。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InteractionApiTest {

    private static final String PASSWORD = "Passw0rd123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private com.eason.blog.module.guestbook.mapper.GuestbookMapper guestbookMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Cookie adminCookie;
    private Cookie userCookie;
    private Long articleId;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        if (adminCookie != null) {
            return;
        }
        String adminEmail = TestUsers.uniqueEmail("p4-admin");
        var admin = TestUsers.create(userMapper, passwordEncoder, adminEmail, PASSWORD);
        TestUsers.assignRole(userRoleMapper, roleMapper, admin.getId(), "SUPER_ADMIN");
        adminCookie = login(adminEmail);

        String userEmail = TestUsers.uniqueEmail("p4-user");
        var user = TestUsers.create(userMapper, passwordEncoder, userEmail, PASSWORD);
        TestUsers.assignRole(userRoleMapper, roleMapper, user.getId(), "USER");
        userCookie = login(userEmail);

        slug = "p4-" + UUID.randomUUID().toString().substring(0, 8);
        Article article = new Article();
        article.setAuthorId(admin.getId());
        article.setTitle("P4 互动测试文章");
        article.setSlug(slug);
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
        articleId = article.getId();
    }

    @Test
    void commentIsHiddenUntilApproved() throws Exception {
        long commentId = createComment(null);

        mockMvc.perform(get("/api/v1/articles/" + slug + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/v1/admin/comments/" + commentId + "/approve").cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/" + slug + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("这是一条测试评论"));
    }

    @Test
    void likeIsIdempotentAndToggleable() throws Exception {
        mockMvc.perform(post("/api/v1/articles/" + articleId + "/like").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        // 重复点赞不重复计数
        mockMvc.perform(post("/api/v1/articles/" + articleId + "/like").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(delete("/api/v1/articles/" + articleId + "/like").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0));
    }

    @Test
    void favoriteAppearsInMyFavorites() throws Exception {
        mockMvc.perform(post("/api/v1/articles/" + articleId + "/favorite").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(get("/api/v1/users/me/favorites").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].slug").value(slug));

        mockMvc.perform(delete("/api/v1/articles/" + articleId + "/favorite").cookie(userCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me/favorites").cookie(userCookie))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void guestbookNeedsLoginAndApproval() throws Exception {
        // 测试库是持久化的，用唯一内容断言，避免受历史数据影响
        String content = "留言-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/guestbook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"游客留言\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/guestbook").cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk());

        // 待审核：公开列表看不到这条
        mockMvc.perform(get("/api/v1/guestbook?size=50"))
                .andExpect(jsonPath("$.data.records[?(@.content == '" + content + "')]").doesNotExist());

        // 直接从库里取 id，避免依赖响应编码与分页顺序
        var stored = guestbookMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.eason.blog.module.guestbook.entity.Guestbook>lambdaQuery()
                        .eq(com.eason.blog.module.guestbook.entity.Guestbook::getContent, content));
        if (stored.isEmpty()) {
            throw new AssertionError("未找到刚提交的留言");
        }
        var guestbook = stored.get(0);
        org.assertj.core.api.Assertions.assertThat(guestbook.getStatus()).isZero();
        long guestbookId = guestbook.getId();

        mockMvc.perform(post("/api/v1/admin/guestbook/" + guestbookId + "/approve").cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/guestbook?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void commentNotifiesArticleAuthor() throws Exception {
        createComment(null);
        mockMvc.perform(get("/api/v1/users/me/notifications").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("COMMENT"))
                .andExpect(jsonPath("$.data[0].read").value(false));
    }

    @Test
    void anonymousCannotComment() throws Exception {
        mockMvc.perform(post("/api/v1/articles/" + articleId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"匿名评论\"}"))
                .andExpect(status().isUnauthorized());
    }

    private long createComment(Long parentId) throws Exception {
        String payload = "{\"content\":\"这是一条测试评论\",\"parentId\":%s}"
                .formatted(parentId == null ? "null" : parentId);
        String body = mockMvc.perform(post("/api/v1/articles/" + articleId + "/comments")
                        .cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        long id = json.path("data").path("id").asLong();
        assertThat(id).isPositive();
        return id;
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
}
