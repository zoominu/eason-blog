package com.eason.blog.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.user.entity.Permission;
import com.eason.blog.module.user.entity.Role;
import com.eason.blog.module.user.entity.RolePermission;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.entity.UserRole;
import com.eason.blog.module.user.dto.AdminUserCreateRequest;
import com.eason.blog.module.user.dto.AdminUserUpdateRequest;
import com.eason.blog.module.user.mapper.PermissionMapper;
import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.RolePermissionMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import com.eason.blog.module.user.vo.AdminUserVO;
import com.eason.blog.module.user.vo.PermissionVO;
import com.eason.blog.module.user.vo.RoleVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户与角色管理。
 *
 * <p>安全约束：
 * <ul>
 *   <li>不能禁用自己（避免把自己锁在门外）；</li>
 *   <li>不能移除自己最后一个管理员角色；</li>
 *   <li>角色权限变更对已登录用户即时生效（每次请求都会重新查角色）。</li>
 * </ul></p>
 */
@Service
public class AdminUserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.eason.blog.module.interaction.service.ContentAuditPolicy auditPolicy;

    public AdminUserService(UserMapper userMapper,
                            UserRoleMapper userRoleMapper,
                            RoleMapper roleMapper,
                            RolePermissionMapper rolePermissionMapper,
                            PermissionMapper permissionMapper,
                            PasswordEncoder passwordEncoder,
                            com.eason.blog.module.interaction.service.ContentAuditPolicy auditPolicy) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditPolicy = auditPolicy;
    }

    public PageResult<AdminUserVO> page(String keyword, Integer status, int pageNo, int size) {
        var wrapper = Wrappers.<User>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getEmail, kw).or().like(User::getNickname, kw));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getRegisterTime);

        Page<User> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 100));
        userMapper.selectPage(page, wrapper);
        return PageResult.of(page, u -> new AdminUserVO(
                u.getId(), u.getUid(), u.getEmail(), u.getUsername(), u.getNickname(), u.getAvatar(), u.getBio(),
                u.getStatus(),
                userRoleMapper.selectRoleCodesByUserId(u.getId()),
                u.getRegisterTime(), u.getLastLoginTime(), u.getLastLoginIp()));
    }

    /**
     * 后台新建用户。
     *
     * <p>与注册的区别：不需要邮箱验证、可指定角色；邮箱唯一性由数据库索引兜底。</p>
     */
    @Transactional
    public Long create(AdminUserCreateRequest request) {
        String email = request.email().trim().toLowerCase();
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("该邮箱已注册");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUid(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        String username = StringUtils.hasText(request.username())
                ? request.username().trim()
                : (StringUtils.hasText(request.nickname())
                    ? request.nickname().trim()
                    : email.substring(0, email.indexOf('@')));
        if (!username.matches("^[\\w\\u4e00-\\u9fa5-]{2,30}$")) {
            throw BusinessException.badRequest("账号名需为 2-30 位字母、数字、下划线或中文");
        }
        Long usernameExists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (usernameExists != null && usernameExists > 0) {
            // 未显式指定账号名时自动加后缀，避免后台创建撞名
            if (!StringUtils.hasText(request.username())) {
                username = username + "_" + UUID.randomUUID().toString().substring(0, 4);
            } else {
                throw BusinessException.conflict("账号名已被占用");
            }
        }
        String hitNickname = auditPolicy.findBannedWord(username);
        if (hitNickname != null) {
            throw BusinessException.badRequest("账号名包含违禁词：" + hitNickname);
        }
        user.setUsername(username);
        user.setNickname(username);
        user.setStatus(1);
        user.setEmailVerified(0);
        user.setTokenVersion(0);
        user.setRegisterTime(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setIsDeleted(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("该邮箱已注册");
        }

        List<Long> roleIds = request.roleIds() == null ? List.of()
                : request.roleIds().stream().filter(Objects::nonNull).distinct().toList();
        if (roleIds.isEmpty()) {
            Role defaultRole = roleMapper.selectByCode("USER");
            if (defaultRole == null) {
                throw new IllegalStateException("缺少默认角色 USER，请检查 Flyway 种子迁移");
            }
            roleIds = List.of(defaultRole.getId());
        } else {
            long found = roleMapper.selectCount(Wrappers.<Role>lambdaQuery().in(Role::getId, roleIds));
            if (found != roleIds.size()) {
                throw BusinessException.badRequest("存在无效的角色");
            }
        }

        for (Long roleId : roleIds) {
            UserRole relation = new UserRole();
            relation.setUserId(user.getId());
            relation.setRoleId(roleId);
            relation.setCreateTime(now);
            userRoleMapper.insert(relation);
        }
        return user.getId();
    }

    /** 后台修改用户资料：邮箱、昵称、头像、简介；null 表示不修改。 */
    @Transactional
    public void update(Long id, AdminUserUpdateRequest request) {
        User user = require(id);

        if (StringUtils.hasText(request.email())) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(user.getEmail())) {
                Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                        .eq(User::getEmail, email)
                        .ne(User::getId, id));
                if (exists != null && exists > 0) {
                    throw BusinessException.conflict("该邮箱已被其他账号使用");
                }
                user.setEmail(email);
                // 邮箱变了，验证状态需要重新确认
                user.setEmailVerified(0);
            }
        }
        if (request.nickname() != null) {
            if (!StringUtils.hasText(request.nickname())) {
                throw BusinessException.badRequest("账号名不能为空");
            }
            String nickname = request.nickname().trim();
            // 账号名站内唯一且注册后不可修改
            if (!nickname.equals(user.getUsername())) {
                throw BusinessException.badRequest("账号名不可修改");
            }
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().trim());
        }

        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void setStatus(Long id, Integer status, Long operatorId) {
        User user = require(id);
        if (Objects.equals(id, operatorId) && status != null && status == 0) {
            throw BusinessException.badRequest("不能禁用自己的账号");
        }
        user.setStatus(status == null || status == 0 ? 0 : 1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void assignRoles(Long id, List<Long> roleIds, Long operatorId) {
        require(id);
        List<Long> distinct = roleIds == null ? List.of()
                : roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() != roleIds.size()) {
            throw BusinessException.badRequest("角色列表包含重复项");
        }
        if (distinct.isEmpty()) {
            throw BusinessException.badRequest("至少保留一个角色");
        }
        long found = roleMapper.selectCount(Wrappers.<Role>lambdaQuery().in(Role::getId, distinct));
        if (found != distinct.size()) {
            throw BusinessException.badRequest("存在无效的角色");
        }

        Role superAdmin = roleMapper.selectByCode("SUPER_ADMIN");
        boolean keepsAdmin = superAdmin != null && distinct.contains(superAdmin.getId());
        if (Objects.equals(id, operatorId) && !keepsAdmin) {
            throw BusinessException.badRequest("不能移除自己的管理员角色");
        }

        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, id));
        for (Long roleId : distinct) {
            UserRole relation = new UserRole();
            relation.setUserId(id);
            relation.setRoleId(roleId);
            relation.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(relation);
        }
    }

    /**
     * 删除用户：逻辑删除 + 解除角色绑定。
     *
     * <p>保护规则：不能删自己；不能删掉最后一个管理员，
     * 否则站点会进入没人能进后台的死锁状态。</p>
     */
    @Transactional
    public void delete(Long id, Long operatorId) {
        User user = require(id);
        if (Objects.equals(id, operatorId)) {
            throw BusinessException.badRequest("不能删除自己的账号");
        }

        List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(id);
        if (roleCodes.contains("SUPER_ADMIN")) {
            long admins = userRoleMapper.selectUserIdsByRoleCode("SUPER_ADMIN").size();
            if (admins <= 1) {
                throw BusinessException.badRequest("至少保留一个管理员账号");
            }
        }

        // 逻辑删除用户（@TableLogic 字段只能通过 deleteById 更新），
        // 历史评论/文章仍保留，作者显示为已注销
        userMapper.deleteById(id);
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, id));
    }

    public List<RoleVO> roles() {
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId)).stream()
                .map(role -> new RoleVO(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                        rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                                        .eq(RolePermission::getRoleId, role.getId()))
                                .stream().map(RolePermission::getPermissionId).toList()))
                .toList();
    }

    public List<PermissionVO> permissions() {
        return permissionMapper.selectList(Wrappers.<Permission>lambdaQuery().orderByAsc(Permission::getId))
                .stream()
                .map(p -> new PermissionVO(p.getId(), p.getCode(), p.getName(), p.getDescription()))
                .toList();
    }

    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        }
        List<Long> distinct = permissionIds == null ? List.of()
                : permissionIds.stream().filter(Objects::nonNull).distinct().toList();
        long found = distinct.isEmpty() ? 0
                : permissionMapper.selectCount(Wrappers.<Permission>lambdaQuery().in(Permission::getId, distinct));
        if (found != distinct.size()) {
            throw BusinessException.badRequest("存在无效的权限点");
        }
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, roleId));
        for (Long permissionId : distinct) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreateTime(LocalDateTime.now());
            rolePermissionMapper.insert(relation);
        }
    }

    private User require(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }
}
