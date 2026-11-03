package com.eason.blog.module.column.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.column.entity.ColumnArticle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ColumnArticleMapper extends BaseMapper<ColumnArticle> {

    /**
     * 专栏内的文章 id，按 sort 升序（同 sort 按加入时间）。
     * 先取 id 再查文章，避免 JOIN + 分页的计数问题。
     */
    @Select("""
            SELECT ca.article_id
            FROM blog_column_article ca
            JOIN blog_column c ON c.id = ca.column_id
            WHERE c.slug = #{slug} AND c.is_deleted = 0
            ORDER BY ca.sort ASC, ca.id ASC
            """)
    List<Long> selectArticleIdsByColumnSlug(@Param("slug") String slug);
}
