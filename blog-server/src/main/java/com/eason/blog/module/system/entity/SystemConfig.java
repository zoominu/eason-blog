package com.eason.blog.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统配置（键值对）。公开配置由 /api/v1/site/config 白名单输出。 */
@Data
@TableName("blog_system_config")
public class SystemConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;
    private String configValue;
    private String description;
    private Integer isPublic;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
