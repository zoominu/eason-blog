package com.eason.blog.module.log.mapper;

/**
 * 操作日志 Mapper：blog_operation_log 表，由 @Audit 切面写入。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
