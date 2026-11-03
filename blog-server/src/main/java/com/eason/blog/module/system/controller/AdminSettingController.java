package com.eason.blog.module.system.controller;

import com.eason.blog.common.audit.Audit;
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.system.service.AdminSettingService;
import com.eason.blog.module.system.vo.SystemConfigVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSettingController {

    private final AdminSettingService adminSettingService;

    public AdminSettingController(AdminSettingService adminSettingService) {
        this.adminSettingService = adminSettingService;
    }

    @GetMapping("/settings")
    public ApiResponse<List<SystemConfigVO>> list() {
        return ApiResponse.success(adminSettingService.list());
    }

    @PatchMapping("/settings")
    @Audit(module = "settings", action = "更新站点配置")
    public ApiResponse<Map<String, Integer>> update(@RequestBody Map<String, String> configs) {
        return ApiResponse.success(Map.of("updated", adminSettingService.update(configs)));
    }
}
