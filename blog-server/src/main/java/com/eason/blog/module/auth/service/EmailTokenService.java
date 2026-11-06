package com.eason.blog.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.module.auth.entity.EmailToken;
import com.eason.blog.module.auth.mapper.EmailTokenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 一次性 token（邮箱验证 / 重置密码）。
 *
 * <p>安全约定：数据库只存 SHA-256 哈希；token 默认 30 分钟有效；同一用户同类型
 * 只保留最新一条，消费后立即标记 used，重复使用无效。</p>
 */
@Service
public class EmailTokenService {

    public static final String TYPE_VERIFY = "VERIFY_EMAIL";
    public static final String TYPE_RESET = "RESET_PASSWORD";

    private static final int TOKEN_BYTES = 32;
    private static final int VERIFY_MINUTES = 60 * 24;
    private static final int RESET_MINUTES = 30;

    private final EmailTokenMapper emailTokenMapper;
    private final SecureRandom random = new SecureRandom();

    public EmailTokenService(EmailTokenMapper emailTokenMapper) {
        this.emailTokenMapper = emailTokenMapper;
    }

    @Transactional
    public String create(Long userId, String type) {
        // 同类型旧 token 直接作废，避免用户收到多封邮件后不知道哪个有效
        emailTokenMapper.delete(Wrappers.<EmailToken>lambdaQuery()
                .eq(EmailToken::getUserId, userId)
                .eq(EmailToken::getType, type));

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        EmailToken token = new EmailToken();
        token.setUserId(userId);
        token.setType(type);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(
                TYPE_RESET.equals(type) ? RESET_MINUTES : VERIFY_MINUTES));
        token.setUsed(0);
        token.setCreateTime(LocalDateTime.now());
        emailTokenMapper.insert(token);
        return raw;
    }

    /** 校验并消费 token，返回 userId；失败抛 400，不区分"不存在/过期/已用"。 */
    @Transactional
    public Long consume(String rawToken, String type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "链接无效或已过期");
        }
        EmailToken token = emailTokenMapper.selectOne(Wrappers.<EmailToken>lambdaQuery()
                .eq(EmailToken::getTokenHash, sha256(rawToken))
                .eq(EmailToken::getType, type)
                .last("limit 1"));
        if (token == null || token.getUsed() != null && token.getUsed() == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "链接无效或已使用");
        }
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "链接已过期，请重新获取");
        }
        if (emailTokenMapper.markUsed(token.getId()) != 1) {
            // 并发请求已经抢先消费
            throw new BusinessException(ErrorCode.BAD_REQUEST, "链接无效或已使用");
        }
        return token.getUserId();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
