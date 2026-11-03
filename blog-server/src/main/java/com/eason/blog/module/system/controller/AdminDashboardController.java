package com.eason.blog.module.system.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.system.service.DashboardService;
import com.eason.blog.module.system.vo.DashboardVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端仪表盘。整个 /api/v1/admin/** 在 SecurityConfig 中要求 SUPER_ADMIN，
 * 这里不再重复判断，但仍建议对写操作加方法级 @PreAuthorize。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardVO> dashboard() {
        return ApiResponse.success(dashboardService.overview());
    }
}
