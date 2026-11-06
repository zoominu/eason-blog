package com.eason.blog.module.user.mapper;

/**
 * 用户 Mapper：blog_user 表；包含按邮箱/账号名查询与登录信息更新。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
