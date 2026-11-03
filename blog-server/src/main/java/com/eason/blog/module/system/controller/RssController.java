package com.eason.blog.module.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RSS 2.0 订阅源：最新 20 篇公开文章。
 *
 * <p>只输出摘要，不输出全文：避免被采集站整站搬运，也符合个人博客的习惯。</p>
 */
@RestController
public class RssController {

    private static final int LIMIT = 20;
    private static final DateTimeFormatter RFC_1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z").withLocale(java.util.Locale.US);

    private final ArticleMapper articleMapper;

    public RssController(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @GetMapping(value = "/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    public String rss(HttpServletRequest request) {
        String base = baseUrl(request);
        List<Article> articles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, 1)
                .eq(Article::getVisibility, 0)
                .orderByDesc(Article::getPublishTime)
                .last("limit " + LIMIT));

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<rss version=\"2.0\"><channel>");
        xml.append("<title>Eason Blog</title>");
        xml.append("<link>").append(base).append("</link>");
        xml.append("<description>记录 Java、Spring Boot、MySQL 与 Vue 的技术笔记与实践。</description>");
        xml.append("<language>zh-CN</language>");
        xml.append("<lastBuildDate>").append(now()).append("</lastBuildDate>");

        for (Article article : articles) {
            String url = base + "/article/" + article.getSlug();
            xml.append("<item>");
            xml.append("<title>").append(escape(article.getTitle())).append("</title>");
            xml.append("<link>").append(url).append("</link>");
            xml.append("<guid isPermaLink=\"true\">").append(url).append("</guid>");
            xml.append("<description>").append(escape(summary(article))).append("</description>");
            if (article.getPublishTime() != null) {
                xml.append("<pubDate>").append(RFC_1123.format(
                        article.getPublishTime().atZone(ZoneId.systemDefault()))).append("</pubDate>");
            }
            xml.append("</item>");
        }

        xml.append("</channel></rss>");
        return xml.toString();
    }

    private String summary(Article article) {
        if (article.getSummary() != null && !article.getSummary().isBlank()) {
            return article.getSummary();
        }
        String content = article.getContent() == null ? "" : article.getContent()
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("[#>*`_~-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return content.length() <= 160 ? content : content.substring(0, 160) + "...";
    }

    private String now() {
        return RFC_1123.format(java.time.ZonedDateTime.now());
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
