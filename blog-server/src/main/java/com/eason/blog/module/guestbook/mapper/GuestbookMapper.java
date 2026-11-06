package com.eason.blog.module.guestbook.mapper;

/**
 * 留言 Mapper：blog_guestbook 表基础 CRUD。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.guestbook.entity.Guestbook;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GuestbookMapper extends BaseMapper<Guestbook> {
}
