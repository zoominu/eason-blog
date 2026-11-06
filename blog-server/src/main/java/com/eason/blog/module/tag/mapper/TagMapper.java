package com.eason.blog.module.tag.mapper;

/**
 * 标签 Mapper：blog_tag 表基础 CRUD。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.tag.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
