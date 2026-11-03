package com.eason.blog.module.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.comment.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
