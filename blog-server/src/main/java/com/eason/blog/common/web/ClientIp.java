package com.eason.blog.common.web;

import jakarta.servlet.http.HttpServletRequest;

/** 取客户端 IP：优先 X-Forwarded-For（Nginx 反代），否则远端地址。 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
