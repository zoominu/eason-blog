package com.eason.blog.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一次性邮箱 token：只存 SHA-256 哈希，避免库泄露后被直接使用。 */
@Data
@TableName("blog_email_token")
public class EmailToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** VERIFY_EMAIL / RESET_PASSWORD */
    private String type;

    private String tokenHash;
    private LocalDateTime expiresAt;
    private Integer used;
    private LocalDateTime createTime;
}
