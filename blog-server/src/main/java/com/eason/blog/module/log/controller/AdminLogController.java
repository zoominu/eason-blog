package com.eason.blog.module.log.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.log.service.LogQueryService;
import com.eason.blog.module.log.vo.AdminLoginLogVO;
import com.eason.blog.module.log.vo.AdminOperationLogVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminLogController {

    private final LogQueryService logQueryService;

    public AdminLogController(LogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    @GetMapping("/login-logs")
    public ApiResponse<PageResult<AdminLoginLogVO>> loginLogs(
            @RequestParam(required = false) @Min(0) @Max(1) Integer status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.success(logQueryService.loginLogs(status, keyword, page, size));
    }

    @GetMapping("/operation-logs")
    public ApiResponse<PageResult<AdminOperationLogVO>> operationLogs(
            @RequestParam(required = false) @Size(max = 50) String module,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.success(logQueryService.operationLogs(module, page, size));
    }
}
