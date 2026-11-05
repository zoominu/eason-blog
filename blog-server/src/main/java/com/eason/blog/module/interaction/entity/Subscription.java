package com.eason.blog.module.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 邮件订阅：开启后新文章发布会收到邮件。 */
@Data
@TableName("blog_subscription")
public class Subscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String email;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
