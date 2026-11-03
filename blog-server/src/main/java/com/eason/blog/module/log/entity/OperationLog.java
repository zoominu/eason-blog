package com.eason.blog.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 后台操作日志：记录谁在什么时候改了什么。 */
@Data
@TableName("blog_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;
    private String operatorName;
    private String module;
    private String action;
    private Long targetId;
    private String detail;
    private String ip;
    private LocalDateTime createTime;
}
