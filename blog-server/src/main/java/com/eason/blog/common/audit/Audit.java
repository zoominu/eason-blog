package com.eason.blog.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要写操作日志的方法。由 {@link AuditAspect} 拦截并写入 blog_operation_log。
 *
 * <p>只标注「关键写操作」，避免日志被读请求淹没。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    /** 模块名，如 article、category、settings。 */
    String module();

    /** 动作描述，如「发布文章」。 */
    String action();
}
