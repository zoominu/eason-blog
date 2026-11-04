package com.eason.blog.module.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 资源元数据：文件本体在 UPLOAD_DIR，数据库只存路径与校验信息。 */
@Data
@TableName("blog_resource")
public class Resource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;
    private String originalName;
    private String storedName;
    private String path;
    private String url;
    private String mimeType;
    /** 资源分类：avatar / article_image / music / video / document / other */
    private String type;
    private Long size;
    private String sha256;
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDeleted;
}
