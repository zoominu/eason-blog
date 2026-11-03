package com.eason.blog.module.user.service;

import com.eason.blog.module.user.dto.ChangePasswordRequest;
import com.eason.blog.module.user.dto.UpdateProfileRequest;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.vo.UserVO;

public interface UserService {

    User getById(Long id);

    User getByEmail(String email);

    UserVO getProfile(Long id);

    UserVO updateProfile(Long id, UpdateProfileRequest request);

    void changePassword(Long id, ChangePasswordRequest request);
}
