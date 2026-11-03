package com.eason.blog.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 权限点（RBAC）。V1 只用于展示与分配，接口级鉴权仍以角色为主。 */
@Data
@TableName("blog_permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String description;
}
