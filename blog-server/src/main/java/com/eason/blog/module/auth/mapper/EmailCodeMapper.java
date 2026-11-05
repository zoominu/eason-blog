package com.eason.blog.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.auth.entity.EmailCode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailCodeMapper extends BaseMapper<EmailCode> {
}
