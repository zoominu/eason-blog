package com.eason.blog.module.column.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.column.dto.ColumnRequest;
import com.eason.blog.module.column.service.AdminColumnService;
import com.eason.blog.module.column.vo.ColumnVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/columns")
public class AdminColumnController {

    private final AdminColumnService adminColumnService;

    public AdminColumnController(AdminColumnService adminColumnService) {
        this.adminColumnService = adminColumnService;
    }

    @GetMapping
    public ApiResponse<List<ColumnVO>> list() {
        return ApiResponse.success(adminColumnService.list());
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ColumnRequest request) {
        return ApiResponse.success(Map.of("id", adminColumnService.create(request)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ColumnRequest request) {
        adminColumnService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminColumnService.delete(id);
        return ApiResponse.success();
    }
}
