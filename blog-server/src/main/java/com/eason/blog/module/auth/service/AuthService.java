package com.eason.blog.module.auth.service;

import com.eason.blog.module.auth.dto.LoginRequest;
import com.eason.blog.module.auth.dto.RegisterRequest;
import com.eason.blog.module.auth.vo.LoginResult;
import com.eason.blog.module.user.vo.UserVO;

public interface AuthService {

    LoginResult login(LoginRequest request, String clientIp);

    UserVO register(RegisterRequest request);

    UserVO currentUser(Long userId);
}
