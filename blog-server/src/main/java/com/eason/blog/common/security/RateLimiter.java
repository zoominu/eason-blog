package com.eason.blog.common.security;

import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 极简内存限流器（固定窗口）。
 *
 * <p>个人博客单实例部署，不引入 Redis；多实例部署时需换成 Redis 计数器，
 * 否则每个实例各算各的。窗口内超过阈值直接抛 429。</p>
 *
 * <p>每个 key 只保存窗口起点、计数和最后访问时间；过期 key 定期清理，
 * 避免历史实现中“队列永远非空导致 Map 无界增长”的问题。</p>
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_INTERVAL_MS = 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> hits = new ConcurrentHashMap<>();
    private final AppProperties properties;
    private final AtomicLong lastCleanup = new AtomicLong();

    public RateLimiter(AppProperties properties) {
        this.properties = properties;
    }

    public void check(String key, int limit, long windowSeconds) {
        if (!properties.rateLimit().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowMs = Math.max(1, windowSeconds) * 1000;

        Window window = hits.computeIfAbsent(key, k -> new Window(now, windowMs));
        synchronized (window) {
            window.windowMs = windowMs;
            if (now - window.windowStart >= windowMs) {
                window.windowStart = now;
                window.count = 0;
            }
            if (window.count >= limit) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "操作过于频繁，请 " + Math.max(1, windowSeconds / 60) + " 分钟后再试");
            }
            window.count++;
            window.lastAccess = now;
        }

        if (hits.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        } else {
            long last = lastCleanup.get();
            if (now - last > CLEANUP_INTERVAL_MS
                    && lastCleanup.compareAndSet(last, now)) {
                cleanup(now);
            }
        }
    }

    /** 按每个 key 自己的窗口长度清理，避免把仍在生效的长窗口误删。 */
    private void cleanup(long now) {
        hits.entrySet().removeIf(entry -> {
            Window window = entry.getValue();
            long ttl = Math.max(window.windowMs, CLEANUP_INTERVAL_MS);
            return now - window.lastAccess > ttl;
        });
    }

    private static final class Window {
        private long windowStart;
        private int count;
        private volatile long lastAccess;
        private volatile long windowMs;

        private Window(long start, long windowMs) {
            this.windowStart = start;
            this.lastAccess = start;
            this.windowMs = windowMs;
        }
    }
}
