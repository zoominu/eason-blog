package com.eason.blog.module.log.mapper;

/**
 * 登录日志 Mapper：blog_login_log 表，记录成功/失败登录与 IP。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.log.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
