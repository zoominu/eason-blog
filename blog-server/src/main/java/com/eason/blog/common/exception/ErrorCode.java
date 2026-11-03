package com.eason.blog.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 稳定错误码。数字与 HTTP 状态码对齐，方便前端统一处理；
 * message 面向用户，不包含堆栈、SQL 或内部细节。
 */
public enum ErrorCode {

    BAD_REQUEST(400, "请求参数不合法"),
    VALIDATION_FAILED(422, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源状态冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus httpStatus() {
        return HttpStatus.valueOf(code);
    }
}
