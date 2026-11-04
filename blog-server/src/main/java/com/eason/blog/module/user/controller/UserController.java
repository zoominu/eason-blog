package com.eason.blog.module.user.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.user.dto.ChangePasswordRequest;
import com.eason.blog.module.user.dto.UpdateProfileRequest;
import com.eason.blog.module.user.service.UserService;
import com.eason.blog.module.user.vo.UserVO;
import com.eason.blog.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 当前用户自助接口（/api/v1/users/me）。
 *
 * <p>userId 一律取自认证主体，绝不接受前端传入的 id，避免越权改他人资料。</p>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/me")
    public ApiResponse<UserVO> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(principal.getId(), request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> uploadAvatar(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestParam("file") MultipartFile file) {
        String url = userService.uploadAvatar(principal.getId(), file);
        return ApiResponse.success(Map.of("url", url));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
        return ApiResponse.success();
    }
}
