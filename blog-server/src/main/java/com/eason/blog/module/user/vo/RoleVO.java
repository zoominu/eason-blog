package com.eason.blog.module.user.vo;

import java.util.List;

/** 角色及其权限点 id。 */
public record RoleVO(
        Long id,
        String code,
        String name,
        String description,
        List<Long> permissionIds) {
}
