package com.eason.blog.module.article;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台文章管理集成测试：状态机、权限、slug 唯一、删除引用校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminArticleApiTest {

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
    private PasswordEncoder passwordEncoder;

    private Cookie adminCookie;
    private Cookie userCookie;

    @BeforeEach
    void setUp() throws Exception {
        if (adminCookie == null) {
            String adminEmail = TestUsers.uniqueEmail("admin-p2");
            var admin = TestUsers.create(userMapper, passwordEncoder, adminEmail, PASSWORD);
            TestUsers.assignRole(userRoleMapper, roleMapper, admin.getId(), "SUPER_ADMIN");
            adminCookie = login(adminEmail);

            String userEmail = TestUsers.uniqueEmail("user-p2");
            var user = TestUsers.create(userMapper, passwordEncoder, userEmail, PASSWORD);
            TestUsers.assignRole(userRoleMapper, roleMapper, user.getId(), "USER");
            userCookie = login(userEmail);
        }
    }

    @Test
    void createDraftThenPublishThenOffline() throws Exception {
        String slug = "p2-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode created = createArticle(slug, 0);
        long id = created.path("id").asLong();
        assertThat(id).isPositive();

        // 草稿：前台看不到
        mockMvc.perform(get("/api/v1/articles/" + slug))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/articles/" + id + "/publish").cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("P2 测试文章"));

        mockMvc.perform(post("/api/v1/admin/articles/" + id + "/offline").cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/articles/" + slug))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesArticleCompletely() throws Exception {
        String slug = "p2-delete-" + UUID.randomUUID().toString().substring(0, 6);
        long id = createArticle(slug, 1).path("id").asLong();

        mockMvc.perform(delete("/api/v1/admin/articles/" + id).cookie(adminCookie))
                .andExpect(status().isOk());

        // 不再有回收站：前台和后台列表都不再出现
        mockMvc.perform(get("/api/v1/articles/" + slug)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/articles/" + id).cookie(adminCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void slugConflictGetsSuffix() throws Exception {
        String slug = "p2-dup-" + UUID.randomUUID().toString().substring(0, 6);
        createArticle(slug, 0);
        JsonNode second = createArticle(slug, 0);

        String storedSlug = mockMvc.perform(get("/api/v1/admin/articles/" + second.path("id").asLong())
                        .cookie(adminCookie))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(storedSlug);
        assertThat(json.path("data").path("slug").asText()).isEqualTo(slug + "-2");
    }

    @Test
    void normalUserCannotManageArticles() throws Exception {
        mockMvc.perform(post("/api/v1/admin/articles")
                        .cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("p2-forbidden", 0)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/articles").cookie(userCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationRejectsEmptyTitle() throws Exception {
        mockMvc.perform(post("/api/v1/admin/articles")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","content":"正文","tagIds":[]}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidCategoryIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/articles")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"不存在的分类","slug":"p2-bad-cat-%s","categoryId":999999,"content":"正文","tagIds":[]}
                                """.formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateKeepsContentAndTags() throws Exception {
        String slug = "p2-update-" + UUID.randomUUID().toString().substring(0, 6);
        long id = createArticle(slug, 0).path("id").asLong();

        mockMvc.perform(patch("/api/v1/admin/articles/" + id)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"改过的标题","slug":"%s","content":"## 新正文","tagIds":[1,2]}
                                """.formatted(slug)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/articles/" + id).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("改过的标题"))
                .andExpect(jsonPath("$.data.content").value("## 新正文"))
                .andExpect(jsonPath("$.data.tagIds.length()").value(2));
    }

    private JsonNode createArticle(String slug, int status) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/articles")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(slug, status)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private String payload(String slug, int status) {
        return """
                {
                  "title": "P2 测试文章",
                  "slug": "%s",
                  "content": "# 正文\\n\\n这是测试内容。",
                  "summary": "摘要",
                  "tagIds": [1],
                  "status": %d
                }
                """.formatted(slug, status);
    }

    private Cookie login(String email) throws Exception {
        String header = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = header.split("blog_token=")[1].split(";")[0];
        return new Cookie("blog_token", token);
    }
}
