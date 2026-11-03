package com.eason.blog.module.auth;

import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import com.eason.blog.support.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与权限集成测试：真实走 Spring Security + MySQL（测试库）+ Flyway 迁移。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

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

    @Test
    void registerThenLoginThenMeThenLogout() throws Exception {
        String email = TestUsers.uniqueEmail("register");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","nickname":"测试读者"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname").value("测试读者"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));

        Cookie cookie = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isOk());
    }

    @Test
    void loginWithWrongPasswordReturns401AndGenericMessage() throws Exception {
        String email = TestUsers.uniqueEmail("wrongpwd");
        TestUsers.create(userMapper, passwordEncoder, email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"totally-wrong"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("邮箱或密码错误"));
    }

    @Test
    void normalUserCannotAccessAdminApi() throws Exception {
        String email = TestUsers.uniqueEmail("user");
        var user = TestUsers.create(userMapper, passwordEncoder, email, PASSWORD);
        TestUsers.assignRole(userRoleMapper, roleMapper, user.getId(), "USER");

        Cookie cookie = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/admin/dashboard").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void superAdminCanAccessDashboard() throws Exception {
        String email = TestUsers.uniqueEmail("admin");
        var user = TestUsers.create(userMapper, passwordEncoder, email, PASSWORD);
        TestUsers.assignRole(userRoleMapper, roleMapper, user.getId(), "SUPER_ADMIN");

        Cookie cookie = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/admin/dashboard").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedArticles").isNumber());
    }

    @Test
    void registerValidatesPasswordStrength() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak@test.local","password":"12345678"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    private Cookie login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("code").asInt()).isEqualTo(200);

        String setCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andReturn().getResponse().getHeader("Set-Cookie");

        assertThat(setCookie).contains("blog_token=");
        String token = setCookie.split("blog_token=")[1].split(";")[0];
        return new Cookie("blog_token", token);
    }
}
