package com.eason.blog.module.interaction.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.comment.vo.InteractionVO;
import com.eason.blog.module.interaction.service.InteractionService;
import com.eason.blog.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞与收藏。POST = 点赞/收藏，DELETE = 取消；两者都幂等。
 */
@RestController
@RequestMapping("/api/v1/articles")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/{id}/interaction")
    public ApiResponse<InteractionVO> status(@PathVariable Long id,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(interactionService.interaction(
                principal == null ? null : principal.getId(), id));
    }

    @PostMapping("/{id}/like")
    public ApiResponse<InteractionVO> like(@PathVariable Long id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(interactionService.like(principal.getId(), id));
    }

    @DeleteMapping("/{id}/like")
    public ApiResponse<InteractionVO> unlike(@PathVariable Long id,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(interactionService.unlike(principal.getId(), id));
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<InteractionVO> favorite(@PathVariable Long id,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(interactionService.favorite(principal.getId(), id));
    }

    @DeleteMapping("/{id}/favorite")
    public ApiResponse<InteractionVO> unfavorite(@PathVariable Long id,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(interactionService.unfavorite(principal.getId(), id));
    }
}
