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
import com.eason.blog.module.user.mapper.PermissionMapper;
import com.eason.blog.module.user.mapper.RoleMapper;
import com.eason.blog.module.user.mapper.RolePermissionMapper;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import com.eason.blog.module.user.vo.AdminUserVO;
import com.eason.blog.module.user.vo.PermissionVO;
import com.eason.blog.module.user.vo.RoleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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

    public AdminUserService(UserMapper userMapper,
                            UserRoleMapper userRoleMapper,
                            RoleMapper roleMapper,
                            RolePermissionMapper rolePermissionMapper,
                            PermissionMapper permissionMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
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

        Page<User> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 50));
        userMapper.selectPage(page, wrapper);
        return PageResult.of(page, u -> new AdminUserVO(
                u.getId(), u.getUid(), u.getEmail(), u.getNickname(), u.getAvatar(), u.getStatus(),
                userRoleMapper.selectRoleCodesByUserId(u.getId()),
                u.getRegisterTime(), u.getLastLoginTime(), u.getLastLoginIp()));
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
