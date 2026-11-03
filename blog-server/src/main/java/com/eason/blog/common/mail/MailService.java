package com.eason.blog.common.mail;

import com.eason.blog.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送。
 *
 * <p>默认关闭（本地没有 SMTP）：关闭时把邮件内容写到日志，方便开发环境直接拿链接。
 * 生产在 `.env` 配 `app.mail.enabled=true` 与 `spring.mail.*` 即可真正发信。</p>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties properties;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider, AppProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    public void send(String to, String subject, String body) {
        AppProperties.Mail mail = properties.mail();
        JavaMailSender sender = mailSenderProvider.getIfAvailable();

        if (!mail.enabled() || sender == null) {
            // 开发环境：不打完整邮件头，避免日志被刷屏，但保留可点击链接
            log.info("""
                    [mail disabled] to={} subject={}
                    {}
                    """, to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mail.from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (RuntimeException ex) {
            // 发信失败不能让注册/重置接口 500，用户可稍后重试
            log.warn("发送邮件失败: to={}, error={}", to, ex.getMessage());
        }
    }
}
