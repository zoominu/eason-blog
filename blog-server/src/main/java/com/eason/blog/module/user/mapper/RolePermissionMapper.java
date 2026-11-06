package com.eason.blog.module.user.mapper;

/**
 * 角色-权限关联 Mapper：blog_role_permission 表。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.user.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
