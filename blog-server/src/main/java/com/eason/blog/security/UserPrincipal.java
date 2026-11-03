package com.eason.blog.security;

import com.eason.blog.module.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 认证主体：包装数据库用户 + 角色。
 *
 * <p>不直接使用 Spring 的 User，因为业务需要 userId、昵称、状态等字段。
 * 角色统一加 ROLE_ 前缀，SecurityConfig 里用 hasRole("SUPER_ADMIN") 判断。</p>
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String nickname;
    private final boolean enabled;
    private final List<String> roles;
    private final int tokenVersion;

    public UserPrincipal(Long id, String email, String password, String nickname,
                         boolean enabled, List<String> roles, int tokenVersion) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.enabled = enabled;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.tokenVersion = tokenVersion;
    }

    public static UserPrincipal of(User user, List<String> roles) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPassword(),
                user.getNickname(), user.getStatus() != null && user.getStatus() == 1, roles,
                user.getTokenVersion() == null ? 0 : user.getTokenVersion());
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public List<String> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
