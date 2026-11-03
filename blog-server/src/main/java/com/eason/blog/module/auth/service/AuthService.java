package com.eason.blog.module.auth.service;

import com.eason.blog.module.auth.dto.LoginRequest;
import com.eason.blog.module.auth.dto.RegisterRequest;
import com.eason.blog.module.auth.vo.LoginResult;
import com.eason.blog.module.user.vo.UserVO;

public interface AuthService {

    LoginResult login(LoginRequest request, String clientIp);

    UserVO register(RegisterRequest request);

    UserVO currentUser(Long userId);

    /** 发送重置密码邮件；邮箱不存在时静默返回，避免账号枚举。 */
    void requestPasswordReset(String email);

    /** 用一次性 token 重置密码，成功后该用户所有旧 token 失效。 */
    void resetPassword(String token, String newPassword);

    /** 消费邮箱验证 token。 */
    void verifyEmail(String token);

    /** 已登录用户重新发送验证邮件。 */
    void resendVerification(Long userId);
}
