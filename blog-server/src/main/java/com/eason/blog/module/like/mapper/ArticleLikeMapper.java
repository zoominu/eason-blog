package com.eason.blog.module.like.mapper;

/**
 * 点赞 Mapper：blog_article_like 表；user_id + article_id 唯一，计数更新走 ArticleMapper。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.like.entity.ArticleLike;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArticleLikeMapper extends BaseMapper<ArticleLike> {
}
