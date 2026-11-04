package com.eason.blog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 应用自有配置（app.*）。全部支持环境变量覆盖，代码中不允许出现真实密钥。
 *
 * <p>使用 record + 构造绑定，配置缺省时用 @DefaultValue 兜底，保证测试环境也能启动。</p>
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue Cors cors,
        @DefaultValue BootstrapAdmin bootstrapAdmin,
        @DefaultValue Register register,
        @DefaultValue Storage storage,
        @DefaultValue DemoData demoData,
        @DefaultValue Mail mail,
        @DefaultValue RateLimit rateLimit) {

    public record Jwt(
            @DefaultValue("dev-only-change-me-please-32bytes-minimum-secret") String secret,
            @DefaultValue("120") long expireMinutes,
            @DefaultValue("blog_token") String cookieName,
            @DefaultValue("false") boolean cookieSecure,
            @DefaultValue("Lax") String cookieSameSite) {
    }

    public record Cors(
            @DefaultValue({"http://localhost:5173", "http://localhost:5174"}) List<String> allowedOrigins) {
    }

    public record BootstrapAdmin(
            @DefaultValue("") String email,
            @DefaultValue("") String password,
            @DefaultValue("Eason") String nickname) {
    }

    public record Register(@DefaultValue("true") boolean enabled) {
    }

    public record Storage(@DefaultValue("./data/uploads") String uploadDir) {
    }

    /** 演示数据只允许在开发环境开启，生产必须为 false。 */
    public record DemoData(@DefaultValue("false") boolean enabled) {
    }

    /**
     * 邮件：默认关闭。
     *
     * <p>关闭时验证/重置链接只写日志（开发环境够用），开启需同时配置 spring.mail.*。</p>
     */
    public record Mail(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("Eason Blog <noreply@localhost>") String from,
            @DefaultValue("http://localhost:5173") String siteUrl) {
    }

    /**
     * 接口限流开关。
     *
     * <p>测试环境会在一分钟内发起大量登录，必须关掉；生产保持开启。</p>
     */
    public record RateLimit(@DefaultValue("true") boolean enabled) {
    }
}
