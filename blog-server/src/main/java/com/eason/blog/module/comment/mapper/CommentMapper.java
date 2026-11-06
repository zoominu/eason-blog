package com.eason.blog.module.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.comment.entity.Comment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /** 物理删除某篇文章的全部评论，用于文章硬删除时清理。 */
    @Delete("DELETE FROM blog_comment WHERE article_id = #{articleId}")
    int hardDeleteByArticleId(@Param("articleId") Long articleId);
}
