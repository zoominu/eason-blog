package com.eason.blog.module.resource.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.resource.service.ResourceService;
import com.eason.blog.module.resource.vo.ResourceVO;
import com.eason.blog.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/resources")
@Validated
public class AdminResourceController {

    private final ResourceService resourceService;

    public AdminResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping("/upload")
    public ApiResponse<ResourceVO> upload(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestPart("file") MultipartFile file,
                                          @RequestParam(value = "type", required = false) String type) {
        return ApiResponse.success(resourceService.upload(file, principal.getId(), type));
    }

    @GetMapping
    public ApiResponse<PageResult<ResourceVO>> list(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 20) String type,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return ApiResponse.success(resourceService.page(keyword, type, page, size));
    }

    /** 资源被哪些文章引用 */
    @GetMapping("/{id}/references")
    public ApiResponse<java.util.List<com.eason.blog.module.resource.vo.ResourceRefVO>> references(
            @PathVariable Long id) {
        return ApiResponse.success(resourceService.references(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resourceService.delete(id);
        return ApiResponse.success();
    }
}
