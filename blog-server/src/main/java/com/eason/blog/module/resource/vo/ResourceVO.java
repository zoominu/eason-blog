package com.eason.blog.module.resource.vo;

import java.time.LocalDateTime;

public record ResourceVO(
        Long id,
        String originalName,
        String url,
        String mimeType,
        String type,
        Long size,
        String sha256,
        /** 被多少篇文章引用（按正文包含资源 URL 统计） */
        long articleCount,
        LocalDateTime createTime) {
}
