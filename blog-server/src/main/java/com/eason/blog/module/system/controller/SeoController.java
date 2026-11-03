package com.eason.blog.module.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.category.entity.Category;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.tag.entity.Tag;
import com.eason.blog.module.tag.mapper.TagMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面向搜索引擎的最小基础设施：sitemap.xml 与 robots.txt。
 *
 * <p>域名从请求推断（生产由 Nginx 透传 Host），不硬编码。
 * 纯 SPA 的正文对爬虫不友好，这里至少保证 URL 可被发现。</p>
 */
@RestController
public class SeoController {

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;

    public SeoController(ArticleMapper articleMapper, CategoryMapper categoryMapper, TagMapper tagMapper) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(HttpServletRequest request) {
        String base = baseUrl(request);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        xml.append("<url><loc>").append(base).append("/</loc><priority>1.0</priority></url>");
        xml.append("<url><loc>").append(base).append("/blog</loc><priority>0.9</priority></url>");

        List<Article> articles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, 1)
                .eq(Article::getVisibility, 0)
                .orderByDesc(Article::getPublishTime));
        for (Article article : articles) {
            xml.append("<url><loc>").append(base).append("/article/").append(article.getSlug())
                    .append("</loc>");
            if (article.getUpdateTime() != null) {
                xml.append("<lastmod>").append(article.getUpdateTime().toLocalDate()).append("</lastmod>");
            }
            xml.append("<priority>0.8</priority></url>");
        }

        for (Category category : categoryMapper.selectList(Wrappers.<Category>lambdaQuery())) {
            xml.append("<url><loc>").append(base).append("/category/").append(category.getSlug())
                    .append("</loc><priority>0.5</priority></url>");
        }
        for (Tag tag : tagMapper.selectList(Wrappers.<Tag>lambdaQuery())) {
            xml.append("<url><loc>").append(base).append("/tag/").append(tag.getSlug())
                    .append("</loc><priority>0.4</priority></url>");
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots(HttpServletRequest request) {
        String base = baseUrl(request);
        return """
                User-agent: *
                Allow: /
                Disallow: /user
                Disallow: /login
                Disallow: /register
                Disallow: /api/
                Sitemap: %s/sitemap.xml
                """.formatted(base);
    }

    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        return scheme + "://" + request.getServerName()
                + (isDefaultPort(request) ? "" : ":" + request.getServerPort());
    }

    private boolean isDefaultPort(HttpServletRequest request) {
        int port = request.getServerPort();
        return ("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && port == 443);
    }
}
