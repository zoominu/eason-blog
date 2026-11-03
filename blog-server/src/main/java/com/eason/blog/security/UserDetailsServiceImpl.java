package com.eason.blog.security;

import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从数据库加载登录用户（邮箱或 id）。
 *
 * <p>邮箱不存在时统一抛 UsernameNotFoundException，由 Spring Security 转成
 * "用户名或密码错误"，避免账号枚举。</p>
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    public UserDetailsServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery()
                        .eq(User::getEmail, email)
                        .last("limit 1"));
        if (user == null) {
            throw new UsernameNotFoundException("user not found");
        }
        return build(user);
    }

    public UserPrincipal loadById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return build(user);
    }

    private UserPrincipal build(User user) {
        List<String> roles = userRoleMapper.selectRoleCodesByUserId(user.getId());
        return UserPrincipal.of(user, roles);
    }
}
