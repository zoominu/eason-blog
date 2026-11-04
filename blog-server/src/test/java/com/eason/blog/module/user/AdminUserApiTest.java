package com.eason.blog.module.user;

import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import com.eason.blog.support.TestUsers;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台删除用户：自我保护、逻辑删除、解除角色绑定。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserApiTest {

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
    private PasswordEncoder passwordEncoder;

    private Cookie adminCookie;
    private User admin;

    @BeforeEach
    void setUp() throws Exception {
        // 测试库是持久化的，每次用独立管理员账号，避免相互影响
        String adminEmail = TestUsers.uniqueEmail("admin-delete");
        admin = TestUsers.create(userMapper, passwordEncoder, adminEmail, PASSWORD);
        TestUsers.assignRole(userRoleMapper, roleMapper, admin.getId(), "SUPER_ADMIN");
        adminCookie = login(adminEmail);
    }

    private Cookie login(String email) throws Exception {
        String header = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        return new Cookie("blog_token", header.split("blog_token=")[1].split(";")[0]);
    }

    @Test
    void cannotDeleteSelf() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/" + admin.getId()).cookie(adminCookie))
                .andExpect(status().isBadRequest());
        assertThat(userMapper.selectById(admin.getId())).isNotNull();
    }

    @Test
    void deleteOtherUserIsLogicalAndRemovesRoles() throws Exception {
        User target = TestUsers.create(userMapper, passwordEncoder,
                TestUsers.uniqueEmail("delete-me"), PASSWORD);
        TestUsers.assignRole(userRoleMapper, roleMapper, target.getId(), "USER");

        mockMvc.perform(delete("/api/v1/admin/users/" + target.getId()).cookie(adminCookie))
                .andExpect(status().isOk());

        // 逻辑删除：查询默认过滤，登录失败
        assertThat(userMapper.selectById(target.getId())).isNull();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(target.getEmail(), PASSWORD)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));

        // 角色绑定被清理
        Long rel = userRoleMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<com.eason.blog.module.user.entity.UserRole>lambdaQuery()
                .eq(com.eason.blog.module.user.entity.UserRole::getUserId, target.getId()));
        assertThat(rel).isZero();
    }

    @Test
    void deletedUserDisappearsFromAdminList() throws Exception {
        User target = TestUsers.create(userMapper, passwordEncoder,
                TestUsers.uniqueEmail("gone"), PASSWORD);
        mockMvc.perform(delete("/api/v1/admin/users/" + target.getId()).cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/users?keyword=" + target.getEmail()).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(target.getEmail()));
    }

    @Test
    void createUserThenLogin() throws Exception {
        String email = TestUsers.uniqueEmail("created");
        mockMvc.perform(post("/api/v1/admin/users").cookie(adminCookie)
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"Passw0rd123","nickname":"后台创建"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        // 新账号可以正常登录
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"Passw0rd123"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        // 重复邮箱冲突
        mockMvc.perform(post("/api/v1/admin/users").cookie(adminCookie)
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"Passw0rd123"}
                                """.formatted(email)))
                .andExpect(status().isConflict());

        // 弱密码被校验拦截
        mockMvc.perform(post("/api/v1/admin/users").cookie(adminCookie)
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"12345678"}
                                """.formatted(TestUsers.uniqueEmail("weak"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateUserProfile() throws Exception {
        User target = TestUsers.create(userMapper, passwordEncoder,
                TestUsers.uniqueEmail("edit"), PASSWORD);

        String newEmail = TestUsers.uniqueEmail("edited");
        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId()).cookie(adminCookie)
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","nickname":"改过的昵称","bio":"简介"}
                                """.formatted(newEmail)))
                .andExpect(status().isOk());

        User updated = userMapper.selectById(target.getId());
        assertThat(updated.getEmail()).isEqualTo(newEmail);
        assertThat(updated.getNickname()).isEqualTo("改过的昵称");
        assertThat(updated.getEmailVerified()).isZero();

        // 邮箱与其他账号冲突
        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId()).cookie(adminCookie)
                        .contentType("application/json")
                        .content("""
                                {"email":"%s"}
                                """.formatted(admin.getEmail())))
                .andExpect(status().isConflict());
    }
}
