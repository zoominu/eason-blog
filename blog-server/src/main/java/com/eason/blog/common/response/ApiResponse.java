package com.eason.blog.common.response;

import java.time.OffsetDateTime;

/**
 * 统一响应体：{code, message, data, timestamp}。
 *
 * <p>约定：code 与 HTTP 状态码保持一致（业务错误码细节放在 ErrorCode 中，后续可扩展为
 * 5 位业务码）。这样前端只判断 HTTP 状态 + code 即可，避免出现 200 包裹错误的情况。</p>
 */
public record ApiResponse<T>(int code, String message, T data, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, OffsetDateTime.now());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, OffsetDateTime.now());
    }
}
