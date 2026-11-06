package com.eason.blog.module.category.controller;

/**
 * 后台分类管理接口：增删改查；删除前会检查子分类与文章引用，全部要求 SUPER_ADMIN。
 */
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.category.dto.CategoryRequest;
import com.eason.blog.module.category.service.AdminCategoryService;
import com.eason.blog.module.category.vo.CategoryVO;
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
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    public AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryVO>> list() {
        return ApiResponse.success(adminCategoryService.list());
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(Map.of("id", adminCategoryService.create(request)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        adminCategoryService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminCategoryService.delete(id);
        return ApiResponse.success();
    }
}
