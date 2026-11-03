package com.eason.blog.module.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.article.entity.ArticleTag;
import com.eason.blog.module.tag.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    @Select("""
            SELECT t.id, t.name, t.slug
            FROM blog_tag t
            JOIN blog_article_tag at ON at.tag_id = t.id
            WHERE at.article_id = #{articleId}
            ORDER BY t.id
            """)
    List<Tag> selectTagsByArticleId(@Param("articleId") Long articleId);

    /**
     * 按标签 slug 找出文章 id。先取 id 再用 IN 查询，避免 JOIN + 分页导致的计数错误。
     */
    @Select("""
            SELECT at.article_id
            FROM blog_article_tag at
            JOIN blog_tag t ON t.id = at.tag_id
            WHERE t.slug = #{slug}
            """)
    List<Long> selectArticleIdsByTagSlug(@Param("slug") String slug);
}
