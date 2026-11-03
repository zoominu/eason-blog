package com.eason.blog.module.tag.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.tag.dto.TagRequest;
import com.eason.blog.module.tag.service.AdminTagService;
import com.eason.blog.module.tag.vo.TagVO;
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
@RequestMapping("/api/v1/admin/tags")
public class AdminTagController {

    private final AdminTagService adminTagService;

    public AdminTagController(AdminTagService adminTagService) {
        this.adminTagService = adminTagService;
    }

    @GetMapping
    public ApiResponse<List<TagVO>> list() {
        return ApiResponse.success(adminTagService.list());
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody TagRequest request) {
        return ApiResponse.success(Map.of("id", adminTagService.create(request)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        adminTagService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminTagService.delete(id);
        return ApiResponse.success();
    }
}
