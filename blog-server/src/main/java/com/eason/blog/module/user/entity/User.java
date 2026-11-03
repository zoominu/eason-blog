package com.eason.blog.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表实体（blog_user）。
 *
 * <p>password 只存 BCrypt 哈希；任何 VO 都不得包含该字段。</p>
 */
@Data
@TableName("blog_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;
    private String email;
    private String password;
    private String nickname;
    private String avatar;
    private String bio;

    /** 0 禁用，1 正常。 */
    private Integer status;

    private Integer emailVerified;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime registerTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** JWT 版本号：改密后自增，旧 token 立即失效。 */
    private Integer tokenVersion;

    @TableLogic
    private Integer isDeleted;
}
