package com.eason.blog.support;

import com.eason.blog.module.user.entity.Role;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.entity.UserRole;
import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 测试数据工厂：集中管理测试账号的创建规则，避免每个测试重复写 20 行。
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    public static User create(UserMapper userMapper, PasswordEncoder encoder, String email, String password) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUid("t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setNickname(email.substring(0, email.indexOf('@')));
        user.setStatus(1);
        user.setEmailVerified(1);
        user.setRegisterTime(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setIsDeleted(0);
        userMapper.insert(user);
        return user;
    }

    public static void assignRole(UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                                  Long userId, String roleCode) {
        Role role = roleMapper.selectByCode(roleCode);
        if (role == null) {
            throw new IllegalStateException("role not seeded: " + roleCode);
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRole.setCreateTime(LocalDateTime.now());
        userRoleMapper.insert(userRole);
    }
}
