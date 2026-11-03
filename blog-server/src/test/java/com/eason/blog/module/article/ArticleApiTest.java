package com.eason.blog.module.article;

import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开文章接口测试：重点验证"游客只能看到已发布公开文章"这条业务规则。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArticleApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String publishedSlug;
    private static String draftSlug;

    @BeforeEach
    void setUp() {
        if (publishedSlug != null) {
            return;
        }
        User author = TestUsers.create(userMapper, passwordEncoder, TestUsers.uniqueEmail("author"), "Passw0rd123");
        publishedSlug = "published-" + UUID.randomUUID().toString().substring(0, 8);
        draftSlug = "draft-" + UUID.randomUUID().toString().substring(0, 8);
        insertArticle(author.getId(), publishedSlug, 1, "公开文章标题", "这是公开文章正文");
        insertArticle(author.getId(), draftSlug, 0, "草稿标题", "草稿正文不应被游客看到");
    }

    @Test
    void listOnlyReturnsPublishedPublicArticles() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[?(@.slug == '%s')]".formatted(publishedSlug)).exists())
                .andExpect(jsonPath("$.data.records[?(@.slug == '%s')]".formatted(draftSlug)).doesNotExist());
    }

    @Test
    void detailReturnsMarkdownContent() throws Exception {
        mockMvc.perform(get("/api/v1/articles/" + publishedSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("公开文章标题"))
                .andExpect(jsonPath("$.data.content").value("这是公开文章正文"));
    }

    @Test
    void draftDetailReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/articles/" + draftSlug))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void oversizedPageSizeIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("size", "500"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unsupportedSortIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/articles").param("sort", "id desc"))
                .andExpect(status().isUnprocessableEntity());
    }

    private void insertArticle(Long authorId, String slug, int status, String title, String content) {
        LocalDateTime now = LocalDateTime.now();
        Article article = new Article();
        article.setAuthorId(authorId);
        article.setTitle(title);
        article.setSlug(slug);
        article.setSummary("摘要");
        article.setContent(content);
        article.setContentFormat("markdown");
        article.setStatus(status);
        article.setVisibility(0);
        article.setIsTop(0);
        article.setIsRecommend(0);
        article.setViewCount(0L);
        article.setLikeCount(0L);
        article.setCommentCount(0L);
        article.setPublishTime(status == 1 ? now : null);
        article.setCreateTime(now);
        article.setUpdateTime(now);
        article.setIsDeleted(0);
        articleMapper.insert(article);
    }
}
