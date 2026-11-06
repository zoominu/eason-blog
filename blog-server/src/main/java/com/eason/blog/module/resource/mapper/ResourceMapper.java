package com.eason.blog.module.resource.mapper;

/**
 * 资源 Mapper：blog_resource 表；资源删除前会检查文章正文引用。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.resource.entity.Resource;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {
}
