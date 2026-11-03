package com.eason.blog.module.auth;

import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.module.auth.service.AuthService;
import com.eason.blog.module.auth.service.EmailTokenService;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 邮箱验证 / 找回密码：token 一次性、无效 token 被拒、改密后 token_version 自增。
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountRecoveryTest {

    @Autowired
    private EmailTokenService tokenService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User newUser() {
        User user = new User();
        user.setUid(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setEmail("recovery-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
        user.setPassword(passwordEncoder.encode("Passw0rd123"));
        user.setNickname("找回测试");
        user.setStatus(1);
        user.setEmailVerified(0);
        user.setTokenVersion(0);
        user.setRegisterTime(LocalDateTime.now());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setIsDeleted(0);
        userMapper.insert(user);
        return user;
    }

    @Test
    void resetPasswordTokenIsSingleUse() {
        User user = newUser();
        String token = tokenService.create(user.getId(), EmailTokenService.TYPE_RESET);

        authService.resetPassword(token, "NewPass123");

        User updated = userMapper.selectById(user.getId());
        assertThat(passwordEncoder.matches("NewPass123", updated.getPassword())).isTrue();
        // 改密后 token_version 自增，旧 JWT 立即失效
        assertThat(updated.getTokenVersion()).isEqualTo(1);

        assertThatThrownBy(() -> authService.resetPassword(token, "Another123"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void verifyEmailMarksUserVerified() {
        User user = newUser();
        String token = tokenService.create(user.getId(), EmailTokenService.TYPE_VERIFY);

        authService.verifyEmail(token);

        assertThat(userMapper.selectById(user.getId()).getEmailVerified()).isEqualTo(1);
    }

    @Test
    void invalidTokenIsRejected() {
        User user = newUser();
        String token = tokenService.create(user.getId(), EmailTokenService.TYPE_RESET);

        assertThatThrownBy(() -> tokenService.consume("not-a-real-token", EmailTokenService.TYPE_RESET))
                .isInstanceOf(BusinessException.class);
        // 正常 token 不受影响
        assertThat(tokenService.consume(token, EmailTokenService.TYPE_RESET)).isEqualTo(user.getId());
    }
}
