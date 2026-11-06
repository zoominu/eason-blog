package com.eason.blog.module.interaction.mapper;

/**
 * 邮件订阅 Mapper：blog_subscription 表；文章发布时按 enabled 查询订阅者。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.interaction.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubscriptionMapper extends BaseMapper<Subscription> {
}
