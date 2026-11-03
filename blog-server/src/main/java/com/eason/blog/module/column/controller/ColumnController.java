package com.eason.blog.module.column.controller;

import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.column.service.ColumnService;
import com.eason.blog.module.column.vo.ColumnVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/columns")
public class ColumnController {

    private final ColumnService columnService;

    public ColumnController(ColumnService columnService) {
        this.columnService = columnService;
    }

    @GetMapping
    public ApiResponse<List<ColumnVO>> list() {
        return ApiResponse.success(columnService.list());
    }

    @GetMapping("/{slug}")
    public ApiResponse<ColumnVO> detail(@PathVariable String slug) {
        ColumnVO column = columnService.getBySlug(slug);
        if (column == null) {
            throw BusinessException.notFound("专栏不存在");
        }
        return ApiResponse.success(column);
    }
}
