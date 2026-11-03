package com.eason.blog.module.like.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 点赞：user_id + article_id 唯一，保证幂等。 */
@Data
@TableName("blog_article_like")
public class ArticleLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long articleId;
    private LocalDateTime createTime;
}
