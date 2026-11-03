package com.eason.blog.module.system.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.system.service.SiteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/site")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, String>> config() {
        return ApiResponse.success(siteService.publicConfig());
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics() {
        return ApiResponse.success(siteService.statistics());
    }
}
