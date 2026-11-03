package com.eason.blog.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应结构，字段与设计文档 7.1 一致。
 *
 * <p>统一在这里做 Entity -> VO 的转换，避免 Controller 里散落转换逻辑。
 * pages 由 MyBatis-Plus 计算，保证与 total/size 一致（size 已被上限裁剪）。</p>
 */
public record PageResult<T>(List<T> records, long total, long current, long size, long pages) {

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        return new PageResult<>(
                page.getRecords().stream().map(mapper).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages());
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(List.of(), 0L, current, size, 0L);
    }
}
