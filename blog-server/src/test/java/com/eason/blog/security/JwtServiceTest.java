package com.eason.blog.security;

import com.eason.blog.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 单元测试：不加载 Spring 上下文，验证签发/解析/防篡改/密钥长度校验。
 */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService(properties("unit-test-secret-key-at-least-32-bytes!!"));

    @Test
    void issuesAndParsesToken() {
        String token = jwtService.issue(42L, "reader@example.com", List.of("USER"), 3);

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("reader@example.com");
        assertThat(claims.get("roles", List.class)).containsExactly("USER");
        assertThat(claims.get("ver", Integer.class)).isEqualTo(3);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issue(1L, "a@b.com", List.of("USER"), 0);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTooShortSecret() {
        AppProperties weak = properties("short");

        assertThatThrownBy(() -> new JwtService(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    private AppProperties properties(String secret) {
        return new AppProperties(
                new AppProperties.Jwt(secret, 120, "blog_token", false, "Lax"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.BootstrapAdmin("", "", "Eason"),
                new AppProperties.Register(true),
                new AppProperties.Storage("./data/uploads"),
                new AppProperties.DemoData(false),
                new AppProperties.Mail(false, "noreply@localhost", "http://localhost:5173"));
    }
}
