package com.eason.blog.module.log.vo;

import java.time.LocalDateTime;

public record AdminOperationLogVO(
        Long id,
        String operatorName,
        String module,
        String action,
        String detail,
        String ip,
        LocalDateTime createTime) {
}
