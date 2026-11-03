package com.eason.blog.module.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 收藏：user_id + article_id 唯一。 */
@Data
@TableName("blog_favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long articleId;
    private LocalDateTime createTime;
}
