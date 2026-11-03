package com.eason.blog.security;

import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全配置。
 *
 * <p>权限矩阵（设计文档 4.2）在这里落地：
 * <ul>
 *   <li>公开：文章/分类/标签/专栏/搜索/站点配置/已审核留言 的只读接口；</li>
 *   <li>登录用户：/api/v1/users/**、点赞收藏评论等写接口；</li>
 *   <li>管理员：/api/v1/admin/** 全部要求 SUPER_ADMIN 角色。</li>
 * </ul>
 * 前端隐藏按钮只是体验优化，真正的拦截必须在这里或 Service 层完成。</p>
 *
 * <p>CSRF：使用 SameSite=Lax 的 HttpOnly Cookie + 仅 JSON 请求体，跨站表单无法携带 Cookie；
 * 若后续改为跨域部署必须重新评估（见 docs/DEPLOYMENT.md）。</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   @Qualifier("corsConfigurationSource")
                                                   CorsConfigurationSource corsSource,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-email",
                                "/actuator/health",
                                "/actuator/info",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/articles/**",
                                "/api/v1/categories/**",
                                "/api/v1/tags/**",
                                "/api/v1/columns/**",
                                "/api/v1/search",
                                "/api/v1/archive",
                                "/api/v1/site/**",
                                "/api/v1/guestbook",
                                "/sitemap.xml",
                                "/robots.txt",
                                "/rss.xml",
                                "/uploads/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/users/**", "/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, objectMapper, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeJson(response, objectMapper, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength=10 是安全性与登录耗时的平衡点；升级策略见 DEPLOYMENT.md
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsServiceImpl userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 默认即隐藏 UserNotFound，避免通过响应差异枚举邮箱
        return new ProviderManager(provider);
    }

    private void writeJson(HttpServletResponse response, ObjectMapper mapper, ErrorCode code) throws IOException {
        response.setStatus(code.code());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), ApiResponse.error(code.code(), code.defaultMessage()));
    }
}
