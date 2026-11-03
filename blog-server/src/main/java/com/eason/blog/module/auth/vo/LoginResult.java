package com.eason.blog.module.auth.vo;

import com.eason.blog.module.user.vo.UserVO;

/**
 * 登录结果。token 由 Controller 写入 HttpOnly Cookie，不会出现在响应体里，
 * 避免前端 JS 读取后被 XSS 窃取。
 */
public record LoginResult(String token, UserVO user) {
}
