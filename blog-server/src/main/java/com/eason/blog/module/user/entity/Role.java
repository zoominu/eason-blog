package com.eason.blog.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 角色：V1 只有 SUPER_ADMIN / USER 两种，但保留表结构便于扩展 RBAC。 */
@Data
@TableName("blog_role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String description;
}
