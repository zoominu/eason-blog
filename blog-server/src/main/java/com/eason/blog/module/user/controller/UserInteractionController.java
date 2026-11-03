package com.eason.blog.module.user.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.interaction.service.InteractionService;
import com.eason.blog.module.notification.vo.NotificationVO;
import com.eason.blog.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 当前用户的收藏与通知。userId 一律取自登录主体。 */
@RestController
@RequestMapping("/api/v1/users/me")
@Validated
public class UserInteractionController {

    private final InteractionService interactionService;

    public UserInteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/favorites")
    public ApiResponse<PageResult<ArticleSummaryVO>> favorites(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(interactionService.favorites(principal.getId(), page, size));
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationVO>> notifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ApiResponse.success(interactionService.notifications(principal.getId(), limit));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<Map<String, Long>> unread(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(Map.of("count", interactionService.unreadCount(principal.getId())));
    }

    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id) {
        interactionService.markRead(principal.getId(), id);
        return ApiResponse.success();
    }
}
