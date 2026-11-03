package com.eason.blog.bootstrap;

import com.eason.blog.config.AppProperties;
import com.eason.blog.module.user.entity.Role;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.entity.UserRole;
import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 管理员一次性初始化。
 *
 * <p>为什么不在 SQL 里插入管理员：密码哈希一旦提交进 Git，等同公开密码。
 * 这里要求通过环境变量 BLOG_ADMIN_EMAIL / BLOG_ADMIN_PASSWORD 提供，
 * 已存在则跳过，因此可以安全地在每次启动时运行。</p>
 */
@Component
@Order(10)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AppProperties properties;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(AppProperties properties,
                                UserMapper userMapper,
                                RoleMapper roleMapper,
                                UserRoleMapper userRoleMapper,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.bootstrapAdmin().email();
        String password = properties.bootstrapAdmin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("未配置 BLOG_ADMIN_EMAIL/BLOG_ADMIN_PASSWORD，跳过管理员初始化");
            return;
        }
        if (password.length() < 8) {
            throw new IllegalStateException("BLOG_ADMIN_PASSWORD 至少 8 位");
        }
        if (userMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<User>lambdaQuery().eq(User::getEmail, email.trim().toLowerCase())) > 0) {
            log.info("管理员账号已存在，跳过初始化: {}", email);
            return;
        }

        Role role = roleMapper.selectByCode("SUPER_ADMIN");
        if (role == null) {
            throw new IllegalStateException("缺少 SUPER_ADMIN 角色，请先执行 Flyway 迁移");
        }

        LocalDateTime now = LocalDateTime.now();
        User admin = new User();
        admin.setUid("u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        admin.setEmail(email.trim().toLowerCase());
        admin.setPassword(passwordEncoder.encode(password));
        admin.setNickname(properties.bootstrapAdmin().nickname());
        admin.setStatus(1);
        admin.setEmailVerified(1);
        admin.setRegisterTime(now);
        admin.setCreateTime(now);
        admin.setUpdateTime(now);
        admin.setIsDeleted(0);
        userMapper.insert(admin);

        UserRole userRole = new UserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(role.getId());
        userRole.setCreateTime(now);
        userRoleMapper.insert(userRole);

        log.info("已创建超级管理员: {}", admin.getEmail());
    }
}
