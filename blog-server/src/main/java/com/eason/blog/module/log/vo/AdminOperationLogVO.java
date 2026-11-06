package com.eason.blog.module.log.vo;

/**
 * 后台操作日志输出对象：模块、动作、操作人、IP、结果与时间。
 */
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
