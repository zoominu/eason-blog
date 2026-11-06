package com.eason.blog.module.favorite.mapper;

/**
 * 收藏 Mapper：blog_favorite 表；user_id + article_id 唯一，删除文章时级联清理。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.favorite.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
