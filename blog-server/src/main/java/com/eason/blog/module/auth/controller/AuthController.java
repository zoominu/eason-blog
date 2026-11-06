package com.eason.blog.module.auth.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.security.RateLimiter;
import com.eason.blog.config.AppProperties;
import com.eason.blog.module.auth.dto.ForgotPasswordRequest;
import com.eason.blog.module.auth.dto.LoginRequest;
import com.eason.blog.module.auth.dto.RegisterRequest;
import com.eason.blog.module.auth.dto.ResetPasswordRequest;
import com.eason.blog.module.auth.dto.TokenRequest;
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
    private final RateLimiter rateLimiter;
    private final com.eason.blog.module.auth.service.EmailCodeService emailCodeService;

    public AuthController(AuthService authService, JwtService jwtService, AppProperties properties,
                          RateLimiter rateLimiter,
                          com.eason.blog.module.auth.service.EmailCodeService emailCodeService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.emailCodeService = emailCodeService;
    }

    /** 发送注册邮箱验证码：同一邮箱 60 秒一次，单 IP 每小时 20 次。 */
    @PostMapping("/email-code")
    public ApiResponse<Void> sendEmailCode(@RequestBody java.util.Map<String, String> body,
                                           HttpServletRequest httpRequest) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw com.eason.blog.common.exception.BusinessException.badRequest("邮箱格式不正确");
        }
        String ip = com.eason.blog.common.web.ClientIp.of(httpRequest);
        rateLimiter.check("email-code-ip:" + ip, 20, 3600);
        rateLimiter.check("email-code:" + email, 1, 60);
        emailCodeService.send(email, com.eason.blog.module.auth.service.EmailCodeService.PURPOSE_REGISTER);
        return ApiResponse.success();
    }

    /**
     * 账号密码登录。
     *
     * <p>优先使用 account（账号名）；为兼容旧客户端/管理员，email 字段仍可作为兜底。
     * 成功后下发 HttpOnly Cookie，响应体不返回 token。失败统一 401，避免账号枚举。</p>
     */
    @PostMapping("/login")
    public ApiResponse<UserVO> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        // 两级限流：单 IP 总闸 + 单账号防撞库（账号维度不绑定 IP，避免换 IP 绕过）
        String ip = com.eason.blog.common.web.ClientIp.of(httpRequest);
        String identifier = request.account() != null && !request.account().isBlank()
                ? request.account().trim() : (request.email() == null ? "" : request.email().trim());
        rateLimiter.check("login-ip:" + ip, 60, 60);
        rateLimiter.check("login-account:" + identifier.toLowerCase(), 10, 60);

        LoginResult result = authService.login(request, ip);
        writeAuthCookie(httpResponse, result.token(), Duration.ofMinutes(jwtService.expireMinutes()));
        return ApiResponse.success(result.user());
    }

    /**
     * 注册：账号名唯一且注册后不可修改；邮箱只用于验证码/订阅/找回密码。
     * 开启邮件功能时必须携带有效邮箱验证码，否则由 Service 拒绝。
     */
    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest httpRequest) {
        // 注册更敏感：单 IP 每小时 20 个账号 + 单邮箱每小时 3 次
        String ip = com.eason.blog.common.web.ClientIp.of(httpRequest);
        rateLimiter.check("register-ip:" + ip, 20, 3600);
        rateLimiter.check("register-mail:" + request.email().toLowerCase(), 3, 3600);

        return ApiResponse.success(authService.register(request));
    }

    /**
     * 退出登录：用同名空值 + Max-Age=0 覆盖 Cookie。
     *
     * <p>当前 SecurityConfig 要求该接口已认证；token 过期时前端调用会得到 401，
     * 但浏览器侧 Cookie 已过期，不影响安全。</p>
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse httpResponse) {
        // 用同名空值 + Max-Age=0 覆盖，确保浏览器立即删除
        writeAuthCookie(httpResponse, "", Duration.ZERO);
        return ApiResponse.success();
    }

    /** 当前登录用户资料；未登录由 Security 入口返回 401。 */
    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(authService.currentUser(principal.getId()));
    }

    /**
     * 请求重置密码邮件。
     *
     * <p>邮箱不存在也返回成功，防止被用来探测注册状态；邮件链接 30 分钟内有效。</p>
     */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                            HttpServletRequest httpRequest) {
        String ip = com.eason.blog.common.web.ClientIp.of(httpRequest);
        rateLimiter.check("forgot-ip:" + ip, 10, 3600);
        rateLimiter.check("forgot-mail:" + request.email().toLowerCase(), 3, 3600);
        authService.requestPasswordReset(request.email());
        return ApiResponse.success();
    }

    /** 用一次性 token 重置密码；成功后 token 作废，旧登录态由 token_version 失效。 */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.success();
    }

    /** 验证邮箱（一次性 token）。 */
    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody TokenRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.success();
    }

    /** 重新发送验证邮件；需登录，避免匿名触发邮件轰炸。 */
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@AuthenticationPrincipal UserPrincipal principal) {
        authService.resendVerification(principal.getId());
        return ApiResponse.success();
    }

    /**
     * 统一下发认证 Cookie：HttpOnly + SameSite + 可选 Secure。
     * maxAge=0 时即删除 Cookie（登出）；生产 HTTPS 应把 COOKIE_SECURE 设为 true。
     */
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
}
