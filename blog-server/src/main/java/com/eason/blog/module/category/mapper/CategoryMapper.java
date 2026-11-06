package com.eason.blog.module.category.mapper;

/**
 * 分类 Mapper：blog_category 表基础 CRUD，文章计数走聚合查询。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.category.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
