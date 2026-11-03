package com.eason.blog.security;

import com.eason.blog.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
        String secret = properties.jwt().secret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 太短：至少需要 32 字节，请通过环境变量提供随机密钥");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMinutes = Math.max(1, properties.jwt().expireMinutes());
    }

    public String issue(Long userId, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
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
