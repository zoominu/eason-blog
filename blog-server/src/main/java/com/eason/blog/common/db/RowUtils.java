package com.eason.blog.common.db;

import java.util.Map;

/**
 * MyBatis 返回 Map 时，不同数据库/驱动对列名的处理不一致（大小写、下划线），
 * 统一在这里做大小写不敏感的取值，避免聚合查询在 MySQL / 其它库上表现不同。
 */
public final class RowUtils {

    private RowUtils() {
    }

    public static long longValue(Map<String, Object> row, String column) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(column)) {
                Object value = entry.getValue();
                return value instanceof Number number ? number.longValue() : 0L;
            }
        }
        return 0L;
    }
}
