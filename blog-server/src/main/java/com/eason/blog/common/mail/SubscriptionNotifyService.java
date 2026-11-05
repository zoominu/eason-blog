package com.eason.blog.common.mail;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.interaction.entity.Subscription;
import com.eason.blog.module.interaction.mapper.SubscriptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 新文章发布后给订阅用户发邮件（异步调用，失败不影响发布）。 */
@Service
public class SubscriptionNotifyService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionNotifyService.class);
    private static final int MAX_RECIPIENTS = 100;

    private final SubscriptionMapper subscriptionMapper;
    private final MailService mailService;

    public SubscriptionNotifyService(SubscriptionMapper subscriptionMapper, MailService mailService) {
        this.subscriptionMapper = subscriptionMapper;
        this.mailService = mailService;
    }

    public void notifyPublished(Article article) {
        if (!mailService.enabled() || article == null) {
            return;
        }
        List<Subscription> subs = subscriptionMapper.selectList(Wrappers.<Subscription>lambdaQuery()
                .eq(Subscription::getEnabled, 1)
                .orderByDesc(Subscription::getId)
                .last("limit " + MAX_RECIPIENTS));
        if (subs.isEmpty()) {
            return;
        }
        String link = mailService.siteUrl() + "/article/" + article.getSlug();
        String subject = "新文章：" + article.getTitle();
        String body = "发布了新文章《" + article.getTitle() + "》\n\n" + link + "\n\n如果不想再收到，可在个人中心关闭订阅。";
        for (Subscription sub : subs) {
            try {
                mailService.send(sub.getEmail(), subject, body);
            } catch (RuntimeException ex) {
                log.warn("订阅邮件发送失败 email={}, error={}", sub.getEmail(), ex.getMessage());
            }
        }
    }
}
