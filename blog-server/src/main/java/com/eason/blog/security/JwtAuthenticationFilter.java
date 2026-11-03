package com.eason.blog.security;

import com.eason.blog.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 Cookie（或 Authorization: Bearer）恢复登录态。
 *
 * <p>设计取舍：每次请求都会按 userId 查一次库，保证禁用用户立即失效（JWT 本身无法撤销）。
 * 个人博客流量小，这个代价可接受；后续可在 P5 引入 Redis 缓存或在 token 中带版本号。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final String cookieName;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsServiceImpl userDetailsService,
                                   AppProperties properties) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.cookieName = properties.jwt().cookieName();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                UserPrincipal principal = userDetailsService.loadById(userId);
                int tokenVersion = claims.get("ver", Integer.class) == null
                        ? 0
                        : claims.get("ver", Integer.class);
                if (principal != null && principal.isEnabled() && principal.getTokenVersion() == tokenVersion) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if (principal != null && principal.getTokenVersion() != tokenVersion) {
                    // 改密或强制下线后，旧 token 立即失效
                    log.debug("token version mismatch, user={}", principal.getId());
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // token 无效/过期：不抛异常，交给后面的 401 入口处理，避免影响匿名接口
                log.debug("invalid jwt: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
