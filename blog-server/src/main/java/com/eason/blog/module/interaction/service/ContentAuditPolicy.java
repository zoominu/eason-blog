package com.eason.blog.module.interaction.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.module.system.entity.SystemConfig;
import com.eason.blog.module.system.mapper.SystemConfigMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 评论/留言审核策略。
 *
 * <p>规则：
 * <ul>
 *   <li>设置里 `comment.audit` / `guestbook.audit` 为 true → 一律待审核；</li>
 *   <li>为 false → 默认直接展示；只有命中违禁词时才按 `audit.banned-action` 处理：</li>
 *   <li>`review`：转入待审核；`mask`：把违禁词替换为 * 后直接展示。</li>
 * </ul>
 * 管理员评论在调用方直接放行，不经过这里。</p>
 */
@Component
public class ContentAuditPolicy {

    public static final int PENDING = 0;
    public static final int APPROVED = 1;

    private final SystemConfigMapper configMapper;

    public ContentAuditPolicy(SystemConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public record Result(int status, String content, String hitWord) {
    }

    public Result check(String content, String auditKey) {
        List<String> words = bannedWords();
        String hit = firstHit(content, words);

        if (auditEnabled(auditKey)) {
            return new Result(PENDING, content, hit);
        }
        if (hit == null) {
            return new Result(APPROVED, content, null);
        }
        if ("mask".equalsIgnoreCase(config("audit.banned-action", "review"))) {
            return new Result(APPROVED, mask(content, words), hit);
        }
        return new Result(PENDING, content, hit);
    }

    public boolean auditEnabled(String key) {
        return "true".equalsIgnoreCase(config(key, "true"));
    }

    /** 返回命中的违禁词，未命中返回 null。昵称等字段复用这套词库。 */
    public String findBannedWord(String text) {
        return firstHit(text, bannedWords());
    }

    /** 违禁词：设置里每行一个，也兼容逗号分隔。 */
    public List<String> bannedWords() {
        String raw = config("audit.banned-words", "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\n\\r]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String firstHit(String content, List<String> words) {
        if (content == null) {
            return null;
        }
        String lower = content.toLowerCase();
        for (String word : words) {
            if (lower.contains(word.toLowerCase())) {
                return word;
            }
        }
        return null;
    }

    /** 用等长 * 替换，保留原文长度，避免用户困惑于内容变短。 */
    private String mask(String content, List<String> words) {
        String result = content;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            StringBuilder stars = new StringBuilder();
            stars.append("*".repeat(word.length()));
            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(word),
                    java.util.regex.Matcher.quoteReplacement(stars.toString()));
        }
        return result;
    }

    private String config(String key, String fallback) {
        SystemConfig config = configMapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, key)
                .last("limit 1"));
        if (config == null || config.getConfigValue() == null) {
            return fallback;
        }
        return config.getConfigValue();
    }
}
