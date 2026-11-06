package com.eason.blog.common.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 取客户端 IP。
 *
 * <p>安全要点：{@code X-Forwarded-For} 是客户端可伪造的请求头，只有在“请求确实经过可信代理”
 * 时才采信；从右往左跳过可信代理，取第一个非可信地址。生产环境通过环境变量
 * {@code TRUSTED_PROXIES} 配置代理地址/网段（逗号分隔），默认只信任本机与私有网段。</p>
 */
public final class ClientIp {

    private static final List<Cidr> TRUSTED = parseTrusted();

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (!isTrusted(remote)) {
            // 直连客户端：XFF 一律不可信，直接用连接地址
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        String[] parts = forwarded.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalize(parts[i]);
            if (candidate.isEmpty() || isTrusted(candidate)) {
                continue;
            }
            return candidate;
        }
        return remote;
    }

    private static List<Cidr> parseTrusted() {
        String configured = System.getenv("TRUSTED_PROXIES");
        String source = configured == null || configured.isBlank()
                ? "127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128"
                : configured;
        List<Cidr> result = new ArrayList<>();
        for (String item : source.split(",")) {
            Cidr cidr = Cidr.parse(item.trim());
            if (cidr != null) {
                result.add(cidr);
            }
        }
        return List.copyOf(result);
    }

    private static boolean isTrusted(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return TRUSTED.stream().anyMatch(Cidr::isLoopbackV6);
        }
        byte[] bytes = ipv4(ip);
        if (bytes == null) {
            return false;
        }
        for (Cidr cidr : TRUSTED) {
            if (cidr.matches(bytes)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String ip) {
        if (ip == null) {
            return "";
        }
        String value = ip.trim();
        // 去掉 IPv6 方括号与端口
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) {
                value = value.substring(1, end);
            }
        }
        if ("0:0:0:0:0:0:0:1".equals(value)) {
            return "::1";
        }
        return value;
    }

    private static byte[] ipv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            try {
                int value = Integer.parseInt(parts[i]);
                if (value < 0 || value > 255) {
                    return null;
                }
                bytes[i] = (byte) value;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return bytes;
    }

    private record Cidr(byte[] network, int prefix, boolean loopbackV6) {

        static Cidr parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            if ("::1/128".equals(value) || "::1".equals(value)) {
                return new Cidr(new byte[0], 0, true);
            }
            String ip = value;
            int prefix = 32;
            int slash = value.indexOf('/');
            if (slash >= 0) {
                ip = value.substring(0, slash);
                try {
                    prefix = Integer.parseInt(value.substring(slash + 1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            byte[] bytes = ipv4(ip);
            if (bytes == null || prefix < 0 || prefix > 32) {
                return null;
            }
            return new Cidr(bytes, prefix, false);
        }

        boolean isLoopbackV6() {
            return loopbackV6;
        }

        boolean matches(byte[] candidate) {
            if (loopbackV6 || network.length != 4) {
                return false;
            }
            int full = prefix / 8;
            int rest = prefix % 8;
            for (int i = 0; i < full; i++) {
                if (network[i] != candidate[i]) {
                    return false;
                }
            }
            if (rest == 0) {
                return true;
            }
            int mask = 0xFF << (8 - rest);
            return (network[full] & mask) == (candidate[full] & mask);
        }
    }
}
