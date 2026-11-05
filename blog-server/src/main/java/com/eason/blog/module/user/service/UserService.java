package com.eason.blog.module.user.service;

import com.eason.blog.module.user.dto.ChangePasswordRequest;
import com.eason.blog.module.user.dto.UpdateProfileRequest;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    User getById(Long id);

    User getByEmail(String email);

    User getByUsername(String username);

    UserVO getProfile(Long id);

    UserVO updateProfile(Long id, UpdateProfileRequest request);

    void changePassword(Long id, ChangePasswordRequest request);

    /** 上传头像并写回 user.avatar，返回可访问的 URL。 */
    String uploadAvatar(Long id, MultipartFile file);
}
