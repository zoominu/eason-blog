package com.eason.blog.module.system.mapper;

/**
 * 系统配置 Mapper：blog_system_config 表，后台设置与邮件模板都存这里。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.system.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {
}
