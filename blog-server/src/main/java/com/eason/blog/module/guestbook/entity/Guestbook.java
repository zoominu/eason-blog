package com.eason.blog.module.guestbook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 留言板。 */
@Data
@TableName("blog_guestbook")
public class Guestbook {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String nickname;
    private String email;
    private String content;
    private Long parentId;

    /** 0 待审核 1 已通过 2 已拒绝。 */
    private Integer status;

    private String reply;
    private LocalDateTime replyTime;
    private String ip;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
