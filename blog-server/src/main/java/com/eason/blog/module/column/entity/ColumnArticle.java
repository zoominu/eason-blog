package com.eason.blog.module.column.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 专栏-文章关联，sort 决定专栏内的阅读顺序。 */
@Data
@TableName("blog_column_article")
public class ColumnArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long columnId;
    private Long articleId;
    private Integer sort;
    private LocalDateTime createTime;
}
