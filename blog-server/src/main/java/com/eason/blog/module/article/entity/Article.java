package com.eason.blog.module.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章实体（blog_article）。
 *
 * <p>content 保存 Markdown 原文，是唯一内容源；content_html 只是可选缓存，
 * 渲染后必须做 XSS 净化才能使用。</p>
 */
@Data
@TableName("blog_article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long authorId;
    private Long categoryId;
    private String title;
    private String slug;
    private String summary;
    private String cover;
    private String content;
    private String contentHtml;
    private String contentFormat;

    /** 0 草稿 1 已发布 2 下线 3 回收。 */
    private Integer status;

    /** 0 公开 1 私密。 */
    private Integer visibility;

    private Integer isTop;
    private Integer isRecommend;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private String seoTitle;
    private String seoDescription;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
