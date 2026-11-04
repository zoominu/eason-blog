package com.eason.blog.module.user.controller;

import com.eason.blog.common.audit.Audit;
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.user.dto.AdminUserCreateRequest;
import com.eason.blog.module.user.dto.AdminUserUpdateRequest;
import com.eason.blog.module.user.service.AdminUserService;
import com.eason.blog.module.user.vo.AdminUserVO;
import com.eason.blog.module.user.vo.PermissionVO;
import com.eason.blog.module.user.vo.RoleVO;
import com.eason.blog.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 后台用户、角色、权限管理。 */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<AdminUserVO>> users(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(adminUserService.page(keyword, status, page, size));
    }

    @PostMapping("/users/{id}/disable")
    @Audit(module = "user", action = "禁用用户")
    public ApiResponse<Void> disable(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        adminUserService.setStatus(id, 0, principal.getId());
        return ApiResponse.success();
    }

    @PostMapping("/users/{id}/enable")
    @Audit(module = "user", action = "启用用户")
    public ApiResponse<Void> enable(@PathVariable Long id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        adminUserService.setStatus(id, 1, principal.getId());
        return ApiResponse.success();
    }

    @PatchMapping("/users/{id}/roles")
    @Audit(module = "user", action = "调整用户角色")
    public ApiResponse<Void> assignRoles(@PathVariable Long id,
                                         @AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, List<Long>> body) {
        adminUserService.assignRoles(id, body.getOrDefault("roleIds", List.of()), principal.getId());
        return ApiResponse.success();
    }

    @DeleteMapping("/users/{id}")
    @Audit(module = "user", action = "删除用户")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        adminUserService.delete(id, principal.getId());
        return ApiResponse.success();
    }

    @PostMapping("/users")
    @Audit(module = "user", action = "新建用户")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody AdminUserCreateRequest request) {
        return ApiResponse.success(Map.of("id", adminUserService.create(request)));
    }

    @PatchMapping("/users/{id}")
    @Audit(module = "user", action = "修改用户资料")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody AdminUserUpdateRequest request) {
        adminUserService.update(id, request);
        return ApiResponse.success();
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleVO>> roles() {
        return ApiResponse.success(adminUserService.roles());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionVO>> permissions() {
        return ApiResponse.success(adminUserService.permissions());
    }

    @PatchMapping("/roles/{id}/permissions")
    public ApiResponse<Void> updatePermissions(@PathVariable Long id,
                                               @RequestBody Map<String, List<Long>> body) {
        adminUserService.updateRolePermissions(id, body.getOrDefault("permissionIds", List.of()));
        return ApiResponse.success();
    }
}
