package com.eason.blog.module.auth.service.impl;

import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.common.mail.MailService;
import com.eason.blog.config.AppProperties;
import com.eason.blog.module.auth.dto.LoginRequest;
import com.eason.blog.module.auth.dto.RegisterRequest;
import com.eason.blog.module.auth.service.AuthService;
import com.eason.blog.module.auth.service.EmailTokenService;
import com.eason.blog.module.auth.vo.LoginResult;
import com.eason.blog.module.log.entity.LoginLog;
import com.eason.blog.module.log.mapper.LoginLogMapper;
import com.eason.blog.module.user.entity.Role;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.entity.UserRole;
import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import com.eason.blog.module.user.service.UserService;
import com.eason.blog.module.user.vo.UserVO;
import com.eason.blog.security.JwtService;
import com.eason.blog.security.UserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final LoginLogMapper loginLogMapper;
    private final EmailTokenService emailTokenService;
    private final MailService mailService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           UserMapper userMapper,
                           RoleMapper roleMapper,
                           UserRoleMapper userRoleMapper,
                           UserService userService,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AppProperties properties,
                           LoginLogMapper loginLogMapper,
                           EmailTokenService emailTokenService,
                           MailService mailService) {
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.loginLogMapper = loginLogMapper;
        this.emailTokenService = emailTokenService;
        this.mailService = mailService;
    }

    @Override
    public LoginResult login(LoginRequest request, String clientIp) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));
        } catch (BadCredentialsException ex) {
            // 不区分"邮箱不存在"和"密码错误"，防止账号枚举
            recordLogin(null, request.email(), clientIp, 0, "密码错误或账号不存在");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "邮箱或密码错误");
        } catch (DisabledException ex) {
            recordLogin(null, request.email(), clientIp, 0, "账号已禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userService.getById(principal.getId());

        // 记录登录信息：失败不影响登录流程，因此单独更新并忽略并发问题
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(truncateIp(clientIp));
        userMapper.updateById(user);
        recordLogin(user.getId(), user.getEmail(), clientIp, 1, "登录成功");

        String token = jwtService.issue(user.getId(), user.getEmail(), principal.getRoles(),
                user.getTokenVersion() == null ? 0 : user.getTokenVersion());
        UserVO vo = new UserVO(user.getId(), user.getUid(), user.getEmail(), user.getNickname(),
                user.getAvatar(), user.getBio(), principal.getRoles(),
                user.getEmailVerified() != null && user.getEmailVerified() == 1,
                user.getRegisterTime());
        return new LoginResult(token, vo);
    }

    /**
     * 写登录日志。日志属于审计辅助信息，写入失败不能影响登录本身，
     * 因此这里吞掉异常只记 warn。
     */
    private void recordLogin(Long userId, String email, String ip, int status, String message) {
        try {
            LoginLog log = new LoginLog();
            log.setUserId(userId);
            log.setEmail(email == null ? null : email.trim().toLowerCase());
            log.setIp(truncateIp(ip));
            log.setLoginStatus(status);
            log.setMessage(message);
            log.setCreateTime(LocalDateTime.now());
            loginLogMapper.insert(log);
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(AuthServiceImpl.class)
                    .warn("记录登录日志失败: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public UserVO register(RegisterRequest request) {
        if (!properties.register().enabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "站点已关闭注册");
        }
        String email = request.email().trim().toLowerCase();
        if (userService.getByEmail(email) != null) {
            throw BusinessException.conflict("该邮箱已注册");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUid("u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname())
                ? request.nickname().trim()
                : email.substring(0, email.indexOf('@')));
        user.setStatus(1);
        user.setEmailVerified(0);
        user.setRegisterTime(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setIsDeleted(0);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 并发注册同一邮箱时由唯一索引兜底
            throw BusinessException.conflict("该邮箱已注册");
        }

        Role role = roleMapper.selectByCode(DEFAULT_ROLE);
        if (role == null) {
            // 种子数据缺失属于部署错误，必须显式失败而不是注册出无角色用户
            throw new IllegalStateException("缺少默认角色 USER，请检查 Flyway 种子迁移");
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setCreateTime(now);
        userRoleMapper.insert(userRole);

        // 注册后发一封验证邮件；邮件发送失败不影响注册结果
        sendVerificationEmail(user);

        return userService.getProfile(user.getId());
    }

    @Override
    public UserVO currentUser(Long userId) {
        return userService.getProfile(userId);
    }

    @Override
    public void requestPasswordReset(String email) {
        User user = userService.getByEmail(email == null ? "" : email.trim().toLowerCase());
        // 邮箱不存在也返回成功，避免被用来探测注册状态
        if (user == null) {
            return;
        }
        String token = emailTokenService.create(user.getId(), EmailTokenService.TYPE_RESET);
        String link = properties.mail().siteUrl() + "/reset-password?token=" + token;
        mailService.send(user.getEmail(), "重置 Eason Blog 密码", """
                你好 %s：

                点击下面的链接重置密码（30 分钟内有效，仅可使用一次）：
                %s

                如果不是你本人操作，请忽略这封邮件。
                """.formatted(user.getNickname(), link));
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Long userId = emailTokenService.consume(token, EmailTokenService.TYPE_RESET);
        User user = userService.getById(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        // 重置密码后旧 token 全部失效
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        Long userId = emailTokenService.consume(token, EmailTokenService.TYPE_VERIFY);
        User user = userService.getById(userId);
        if (user.getEmailVerified() != null && user.getEmailVerified() == 1) {
            return;
        }
        user.setEmailVerified(1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void resendVerification(Long userId) {
        User user = userService.getById(userId);
        if (user.getEmailVerified() != null && user.getEmailVerified() == 1) {
            throw BusinessException.badRequest("邮箱已验证，无需重复发送");
        }
        sendVerificationEmail(user);
    }

    private void sendVerificationEmail(User user) {
        String token = emailTokenService.create(user.getId(), EmailTokenService.TYPE_VERIFY);
        String link = properties.mail().siteUrl() + "/verify-email?token=" + token;
        mailService.send(user.getEmail(), "验证你的 Eason Blog 邮箱", """
                你好 %s：

                点击下面的链接完成邮箱验证（24 小时内有效）：
                %s

                如果这不是你本人注册，请忽略这封邮件。
                """.formatted(user.getNickname(), link));
    }

    private String truncateIp(String ip) {
        if (ip == null) {
            return null;
        }
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
