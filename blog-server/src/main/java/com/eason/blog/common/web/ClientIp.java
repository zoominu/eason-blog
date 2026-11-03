package com.eason.blog.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取客户端 IP。
 *
 * <p>安全要点：`X-Forwarded-For` 是客户端可伪造的请求头，必须只在“请求确实经过
 * 可信代理”时才采信，并且取代理追加的最后一跳，而不是第一段。
 * 单层 Nginx 反代下，Nginx 使用 `$proxy_add_x_forwarded_for`，最后一段是真实对端地址。</p>
 */
public final class ClientIp {

    /** 只有来自这些地址的请求才信任 XFF（本机 Nginx / 容器网关）。 */
    private static final String[] TRUSTED_PROXIES = {"127.0.0.1", "0:0:0:0:0:0:0:1", "::1"};

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isTrustedProxy(remote)) {
            // 直连客户端：XFF 一律不可信，直接用连接地址
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        // 取最后一段：由可信代理追加，客户端无法覆盖
        String[] parts = forwarded.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return remote;
    }

    private static boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }
        for (String trusted : TRUSTED_PROXIES) {
            if (trusted.equals(ip)) {
                return true;
            }
        }
        return false;
    }
}
