package com.eason.blog.module.column.mapper;

/**
 * 专栏 Mapper：blog_column 表基础 CRUD。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.column.entity.Column;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ColumnMapper extends BaseMapper<Column> {
}
