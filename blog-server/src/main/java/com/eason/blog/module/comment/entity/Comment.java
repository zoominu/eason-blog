package com.eason.blog.module.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 文章评论 / 回复。默认 status=0（待审核），管理员通过后展示。 */
@Data
@TableName("blog_comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;
    private Long userId;

    /** 父评论 id，0 为顶层。 */
    private Long parentId;

    private String content;

    /** 0 待审核 1 已通过 2 已拒绝。 */
    private Integer status;

    private String ip;
    private String userAgent;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
