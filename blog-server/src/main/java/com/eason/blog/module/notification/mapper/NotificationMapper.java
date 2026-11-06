package com.eason.blog.module.notification.mapper;

/**
 * 通知 Mapper：blog_notification 表，按用户与已读状态查询。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
