package com.eason.blog.module.user.mapper;

/**
 * 角色 Mapper：blog_role 表；角色编码用于 Spring Security 的 ROLE_ 前缀。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.user.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT * FROM blog_role WHERE code = #{code} LIMIT 1")
    Role selectByCode(@Param("code") String code);
}
