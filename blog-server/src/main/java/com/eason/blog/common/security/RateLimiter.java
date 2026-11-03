package com.eason.blog.common.security;

import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简内存限流器（固定窗口）。
 *
 * <p>个人博客单实例部署，不引入 Redis；多实例部署时需换成 Redis 计数器，
 * 否则每个实例各算各的。窗口内超过阈值直接抛 429。</p>
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(String key, int limit, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000;

        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "操作过于频繁，请 " + Math.max(1, windowSeconds / 60) + " 分钟后再试");
            }
            queue.addLast(now);
        }

        // 简单清理：map 过大时丢掉空队列，避免长期运行内存增长
        if (hits.size() > 10_000) {
            hits.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }
}
