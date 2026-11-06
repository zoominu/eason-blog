package com.eason.blog.module.user.mapper;

/**
 * 权限 Mapper：blog_permission 表；角色权限关联由 RolePermissionMapper 维护。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.user.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
