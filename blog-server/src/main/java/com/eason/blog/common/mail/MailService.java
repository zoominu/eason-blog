package com.eason.blog.common.mail;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.module.system.entity.SystemConfig;
import com.eason.blog.module.system.mapper.SystemConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 邮件发送：SMTP 参数从后台「系统设置 → 邮件设置」读取，不再依赖环境变量。
 *
 * <p>关闭时只把内容写日志，方便开发环境取验证码/链接。</p>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final SystemConfigMapper configMapper;

    public MailService(SystemConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public boolean enabled() {
        return "true".equalsIgnoreCase(config("mail.enabled", "false"));
    }

    public String siteUrl() {
        String url = config("site.url", "");
        return StringUtils.hasText(url) ? url.replaceAll("/+$", "") : "http://localhost";
    }

    /** 从系统设置读模板并替换占位符；模板不存在时用 fallback。 */
    public String render(String templateKey, String fallback, java.util.Map<String, String> vars) {
        String template = config(templateKey, "");
        String body = StringUtils.hasText(template) ? template : fallback;
        for (var entry : vars.entrySet()) {
            body = body.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return body;
    }

    /** 发送失败只记日志，不影响业务（注册、发布等）。 */
    public void send(String to, String subject, String body) {
        try {
            sendStrict(to, subject, body);
        } catch (BusinessException ex) {
            log.warn("邮件发送失败 to={}, error={}", to, ex.getMessage());
        }
    }

    /** 后台测试邮件用：失败直接抛给前端。 */
    public void sendStrict(String to, String subject, String body) {
        if (!enabled()) {
            throw BusinessException.badRequest("邮件功能未启用，请先在系统设置里开启并填写 SMTP");
        }
        if (!StringUtils.hasText(to) || !to.contains("@")) {
            throw BusinessException.badRequest("收件邮箱不正确");
        }
        String host = config("mail.host", "");
        String username = config("mail.username", "");
        String password = config("mail.password", "");
        if (!StringUtils.hasText(host) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw BusinessException.badRequest("SMTP 服务器 / 账号 / 授权码未填写完整");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(Integer.parseInt(config("mail.port", "465")));
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");
        if ("true".equalsIgnoreCase(config("mail.ssl", "true"))) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }

        String from = config("mail.from", "");
        if (!StringUtils.hasText(from)) {
            from = username;
        }

        try {
            var message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
            log.info("邮件已发送 to={}, subject={}", to, subject);
        } catch (Exception ex) {
            log.warn("SMTP 发送失败 to={}, error={}", to, ex.getMessage());
            throw BusinessException.badRequest("发送失败：" + ex.getMessage());
        }
    }

    private String config(String key, String fallback) {
        SystemConfig config = configMapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, key)
                .last("limit 1"));
        return config == null || config.getConfigValue() == null ? fallback : config.getConfigValue();
    }
}
