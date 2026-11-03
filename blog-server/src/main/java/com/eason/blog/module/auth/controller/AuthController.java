package com.eason.blog.module.auth.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.config.AppProperties;
import com.eason.blog.module.auth.dto.LoginRequest;
import com.eason.blog.module.auth.dto.RegisterRequest;
import com.eason.blog.module.auth.service.AuthService;
import com.eason.blog.module.auth.vo.LoginResult;
import com.eason.blog.module.user.vo.UserVO;
import com.eason.blog.security.JwtService;
import com.eason.blog.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证接口（/api/v1/auth）。
 *
 * <p>Token 通过 HttpOnly + SameSite Cookie 下发，响应体中不返回 token，
 * 前端只依赖 Cookie 自动携带，避免 XSS 直接读取凭证。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthController(AuthService authService, JwtService jwtService, AppProperties properties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ApiResponse<UserVO> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        LoginResult result = authService.login(request, clientIp(httpRequest));
        writeAuthCookie(httpResponse, result.token(), Duration.ofMinutes(jwtService.expireMinutes()));
        return ApiResponse.success(result.user());
    }

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse httpResponse) {
        // 用同名空值 + Max-Age=0 覆盖，确保浏览器立即删除
        writeAuthCookie(httpResponse, "", Duration.ZERO);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(authService.currentUser(principal.getId()));
    }

    private void writeAuthCookie(HttpServletResponse response, String value, Duration maxAge) {
        AppProperties.Jwt jwt = properties.jwt();
        ResponseCookie cookie = ResponseCookie.from(jwt.cookieName(), value)
                .httpOnly(true)
                .secure(jwt.cookieSecure())
                .path("/")
                .sameSite(jwt.cookieSameSite())
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
