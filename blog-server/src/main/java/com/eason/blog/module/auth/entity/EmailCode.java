package com.eason.blog.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 邮箱验证码（注册等场景）。 */
@Data
@TableName("blog_email_code")
public class EmailCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;
    private String codeHash;
    private String purpose;
    private LocalDateTime expiresAt;
    private Integer used;
    private LocalDateTime createTime;
}
