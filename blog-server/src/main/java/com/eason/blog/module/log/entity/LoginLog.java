package com.eason.blog.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志（blog_login_log）。日志表不做逻辑删除，按保留周期清理。
 */
@Data
@TableName("blog_login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String email;
    private String ip;
    private String userAgent;

    /** 1 成功 0 失败。 */
    private Integer loginStatus;

    private String message;
    private LocalDateTime createTime;
}
