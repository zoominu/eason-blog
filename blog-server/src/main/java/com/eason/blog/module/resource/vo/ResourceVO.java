package com.eason.blog.module.resource.vo;

import java.time.LocalDateTime;

public record ResourceVO(
        Long id,
        String originalName,
        String url,
        String mimeType,
        Long size,
        String sha256,
        LocalDateTime createTime) {
}
