package com.eason.blog.common.util;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 由标题生成 URL 友好的 slug。
 *
 * <p>中文标题无法直接音译（项目不引入拼音库），此时退化为
 * {@code post-20261103-ab12} 形式：可读性一般，但保证唯一、可访问、可 SEO 编辑。
 * 编辑者在后台可以手动改成更漂亮的英文 slug。</p>
 */
public final class SlugUtils {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private SlugUtils() {
    }

    public static String fromTitle(String title) {
        String normalized = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80).replaceAll("-+$", "");
        }
        if (normalized.length() < 3) {
            normalized = "post-" + LocalDate.now().format(DATE) + "-"
                    + UUID.randomUUID().toString().substring(0, 4);
        }
        return normalized;
    }

    /** 校验用户手填的 slug：只允许小写字母、数字、短横线。 */
    public static boolean isValid(String slug) {
        return slug != null && slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$") && slug.length() <= 200;
    }
}
