package com.eason.blog.module.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.config.AppProperties;
import com.eason.blog.module.interaction.service.ContentAuditPolicy;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final long AVATAR_MAX_SIZE = 2L * 1024 * 1024;
    private static final Set<String> AVATAR_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final ContentAuditPolicy auditPolicy;

    public UserServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper,
                           PasswordEncoder passwordEncoder, AppProperties properties,
                           ContentAuditPolicy auditPolicy) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditPolicy = auditPolicy;
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
            String nickname = request.nickname().trim();
            checkNickname(nickname);
            user.setNickname(nickname);
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

    /** 昵称同样走违禁词过滤：命中直接拒绝，避免出现违规展示名。 */
    private void checkNickname(String nickname) {
        String hit = auditPolicy.findBannedWord(nickname);
        if (hit != null) {
            throw BusinessException.badRequest("昵称包含违禁词：" + hit);
        }
    }

    @Override
    @Transactional
    public String uploadAvatar(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择图片");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw BusinessException.badRequest("头像不能超过 2MB");
        }

        String original = StringUtils.hasText(file.getOriginalFilename())
                ? Paths.get(file.getOriginalFilename()).getFileName().toString()
                : "avatar";
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!AVATAR_EXT.contains(ext)) {
            throw BusinessException.badRequest("头像仅支持 png/jpg/jpeg/gif/webp");
        }
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!mime.startsWith("image/")) {
            throw BusinessException.badRequest("文件类型不是图片");
        }

        String month = LocalDate.now().format(MONTH);
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(properties.storage().uploadDir())
                .toAbsolutePath().normalize()
                .resolve("avatar")
                .resolve(month);
        try {
            Files.createDirectories(dir);
            try (var in = file.getInputStream()) {
                Files.copy(in, dir.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }

        String url = "/uploads/avatar/" + month + "/" + stored;
        User user = getById(id);
        user.setAvatar(url);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return url;
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
