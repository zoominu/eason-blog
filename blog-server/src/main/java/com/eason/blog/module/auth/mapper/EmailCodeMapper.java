package com.eason.blog.module.auth.mapper;

/**
 * 邮箱验证码 Mapper：blog_email_code 表的基础 CRUD。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.auth.entity.EmailCode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailCodeMapper extends BaseMapper<EmailCode> {
}
