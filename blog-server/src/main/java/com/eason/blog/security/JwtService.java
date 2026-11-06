package com.eason.blog.security;

import com.eason.blog.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与解析。
 *
 * <p>安全要点：
 * <ul>
 *   <li>密钥必须 >= 256 位（32 字节），否则启动时直接失败而不是弱签名运行；</li>
 *   <li>token 只放 userId/email/roles，不放密码、手机号等敏感信息；</li>
 *   <li>签名算法由 jjwt 根据密钥长度自动选择 HS256/HS384。</li>
 * </ul>
 */
@Component
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expireMinutes;

    public JwtService(AppProperties properties) {
        this(properties, null);
    }

    @Autowired
    public JwtService(AppProperties properties, Environment environment) {
        String secret = properties.jwt().secret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 太短：至少需要 32 字节，请通过环境变量提供随机密钥");
        }
        // 非开发/测试环境禁止使用仓库里的公开默认值，避免“能启动但可被伪造”
        boolean devOrTest = environment != null
                && environment.acceptsProfiles(Profiles.of("dev", "test"));
        if (!devOrTest && secret.startsWith("dev-only-change-me")) {
            throw new IllegalStateException(
                    "生产环境不能使用默认 JWT_SECRET，请通过环境变量提供随机密钥");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMinutes = Math.max(1, properties.jwt().expireMinutes());
    }

    /**
     * 签发 token。ver 是用户当前的 token_version，
     * 改密后该值自增，旧 token 在过滤器里会被判定为失效。
     */
    public String issue(Long userId, String email, List<String> roles, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
                .claim("ver", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验签名/过期时间。失败时抛出 JwtException 由调用方处理。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long expireMinutes() {
        return expireMinutes;
    }
}
