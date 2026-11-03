package com.eason.blog.module.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.article.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 浏览量原子自增：避免 "读-改-写" 造成并发丢失。
     * 使用 GREATEST 防御负值，避免异常数据。
     */
    @Update("UPDATE blog_article SET view_count = GREATEST(view_count + 1, 0) WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    /** 点赞数增减：GREATEST 防御负数，避免并发/重复操作把计数打成负值。 */
    @Update("UPDATE blog_article SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{id}")
    int updateLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /** 评论数只统计审核通过的评论。 */
    @Update("UPDATE blog_article SET comment_count = GREATEST(comment_count + #{delta}, 0) WHERE id = #{id}")
    int updateCommentCount(@Param("id") Long id, @Param("delta") int delta);

    @Select("""
            SELECT COUNT(*) FROM blog_article
            WHERE is_deleted = 0 AND status = #{status}
            """)
    long countByStatus(@Param("status") int status);

    /**
     * 归档统计：按发布时间年月分组。
     */
    @Select("""
            SELECT DATE_FORMAT(publish_time, '%Y-%m') AS month, COUNT(*) AS total
            FROM blog_article
            WHERE is_deleted = 0 AND status = 1 AND visibility = 0 AND publish_time IS NOT NULL
            GROUP BY DATE_FORMAT(publish_time, '%Y-%m')
            ORDER BY month DESC
            """)
    List<java.util.Map<String, Object>> selectArchive();

    @Select("""
            SELECT COALESCE(SUM(view_count), 0)
            FROM blog_article
            WHERE is_deleted = 0 AND status = 1 AND visibility = 0
            """)
    long sumPublicViews();

    /**
     * 分类下的公开文章数。
     *
     * <p>不依赖 blog_category.article_count 计数器：该字段需要写路径维护，
     * 而当前文章 CRUD 尚未实现（P2），直接聚合才能保证展示数字真实。</p>
     */
    @Select("""
            SELECT category_id AS taxonomy_id, COUNT(*) AS total
            FROM blog_article
            WHERE is_deleted = 0 AND status = 1 AND visibility = 0 AND category_id IS NOT NULL
            GROUP BY category_id
            """)
    List<java.util.Map<String, Object>> countPublicArticlesByCategory();

    @Select("""
            SELECT at.tag_id AS taxonomy_id, COUNT(*) AS total
            FROM blog_article_tag at
            JOIN blog_article a ON a.id = at.article_id
            WHERE a.is_deleted = 0 AND a.status = 1 AND a.visibility = 0
            GROUP BY at.tag_id
            """)
    List<java.util.Map<String, Object>> countPublicArticlesByTag();

    @Select("""
            SELECT ca.column_id AS taxonomy_id, COUNT(*) AS total
            FROM blog_column_article ca
            JOIN blog_article a ON a.id = ca.article_id
            WHERE a.is_deleted = 0 AND a.status = 1 AND a.visibility = 0
            GROUP BY ca.column_id
            """)
    List<java.util.Map<String, Object>> countPublicArticlesByColumn();

    /** 后台统计用：不限状态（含草稿/下线/回收），只排除逻辑删除。 */
    @Select("""
            SELECT category_id AS taxonomy_id, COUNT(*) AS total
            FROM blog_article
            WHERE is_deleted = 0 AND category_id IS NOT NULL
            GROUP BY category_id
            """)
    List<java.util.Map<String, Object>> countAllArticlesByCategory();

    @Select("""
            SELECT at.tag_id AS taxonomy_id, COUNT(*) AS total
            FROM blog_article_tag at
            JOIN blog_article a ON a.id = at.article_id
            WHERE a.is_deleted = 0
            GROUP BY at.tag_id
            """)
    List<java.util.Map<String, Object>> countAllArticlesByTag();

    @Select("""
            SELECT ca.column_id AS taxonomy_id, COUNT(*) AS total
            FROM blog_column_article ca
            JOIN blog_article a ON a.id = ca.article_id
            WHERE a.is_deleted = 0
            GROUP BY ca.column_id
            """)
    List<java.util.Map<String, Object>> countAllArticlesByColumn();
}
