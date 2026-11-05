package com.eason.blog.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.mail.MailService;
import com.eason.blog.module.auth.entity.EmailCode;
import com.eason.blog.module.auth.mapper.EmailCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 邮箱验证码：6 位数字，10 分钟有效，库中只存 SHA-256。
 */
@Service
public class EmailCodeService {

    public static final String PURPOSE_REGISTER = "register";

    private static final int VALID_MINUTES = 10;

    private final EmailCodeMapper emailCodeMapper;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    public EmailCodeService(EmailCodeMapper emailCodeMapper, MailService mailService) {
        this.emailCodeMapper = emailCodeMapper;
        this.mailService = mailService;
    }

    @Transactional
    public void send(String email, String purpose) {
        if (!mailService.enabled()) {
            throw BusinessException.badRequest("站点未开启邮件功能，请联系管理员");
        }
        emailCodeMapper.delete(Wrappers.<EmailCode>lambdaQuery()
                .eq(EmailCode::getEmail, email)
                .eq(EmailCode::getPurpose, purpose));

        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailCode entity = new EmailCode();
        entity.setEmail(email);
        entity.setPurpose(purpose);
        entity.setCodeHash(sha256(code));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(VALID_MINUTES));
        entity.setUsed(0);
        entity.setCreateTime(LocalDateTime.now());
        emailCodeMapper.insert(entity);

        mailService.sendStrict(email, "邮箱验证码", mailService.render(
                "mail.template.register-code",
                "你的注册验证码是 {code}，{minutes} 分钟内有效。\n\n{site}",
                java.util.Map.of("code", code, "minutes", String.valueOf(VALID_MINUTES),
                        "site", mailService.siteUrl())));
    }

    @Transactional
    public void verify(String email, String purpose, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw BusinessException.badRequest("请输入 6 位邮箱验证码");
        }
        EmailCode entity = emailCodeMapper.selectOne(Wrappers.<EmailCode>lambdaQuery()
                .eq(EmailCode::getEmail, email)
                .eq(EmailCode::getPurpose, purpose)
                .eq(EmailCode::getUsed, 0)
                .orderByDesc(EmailCode::getId)
                .last("limit 1"));
        if (entity == null || entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.badRequest("验证码已过期，请重新获取");
        }
        if (!sha256(code).equals(entity.getCodeHash())) {
            throw BusinessException.badRequest("验证码不正确");
        }
        entity.setUsed(1);
        emailCodeMapper.updateById(entity);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
