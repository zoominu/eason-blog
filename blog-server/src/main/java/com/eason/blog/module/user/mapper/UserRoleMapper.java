package com.eason.blog.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.user.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 一次查询拿到用户所有角色编码，避免鉴权时 N+1。
     */
    @Select("""
            SELECT r.code
            FROM blog_role r
            JOIN blog_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /** 某角色下的全部用户 id：用于给评论打「博主」标记。 */
    @Select("""
            SELECT ur.user_id
            FROM blog_user_role ur
            JOIN blog_role r ON r.id = ur.role_id
            WHERE r.code = #{roleCode}
            """)
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);
}
