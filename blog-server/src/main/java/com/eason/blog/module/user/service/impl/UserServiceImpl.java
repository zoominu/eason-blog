package com.eason.blog.module.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.module.user.dto.ChangePasswordRequest;
import com.eason.blog.module.user.dto.UpdateProfileRequest;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import com.eason.blog.module.user.service.UserService;
import com.eason.blog.module.user.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    @Override
    public User getByEmail(String email) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, email)
                .last("limit 1"));
    }

    @Override
    public UserVO getProfile(Long id) {
        return toVO(getById(id));
    }

    @Override
    @Transactional
    public UserVO updateProfile(Long id, UpdateProfileRequest request) {
        User user = getById(id);
        // 只覆盖显式传入的字段，未传字段保持原值（PATCH 语义）
        if (StringUtils.hasText(request.nickname())) {
            user.setNickname(request.nickname().trim());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request) {
        User user = getById(id);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            // 统一文案，避免提示"旧密码错误"以外的信息
            throw BusinessException.badRequest("旧密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw BusinessException.badRequest("新密码不能与旧密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // 自增 token 版本：改密后所有旧 Cookie 立即失效，必须重新登录
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private UserVO toVO(User user) {
        List<String> roles = userRoleMapper.selectRoleCodesByUserId(user.getId());
        return new UserVO(user.getId(), user.getUid(), user.getEmail(), user.getNickname(),
                user.getAvatar(), user.getBio(), roles,
                user.getEmailVerified() != null && user.getEmailVerified() == 1,
                user.getRegisterTime());
    }
}
