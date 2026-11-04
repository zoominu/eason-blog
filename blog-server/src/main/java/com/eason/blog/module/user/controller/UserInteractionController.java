package com.eason.blog.module.user.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.comment.vo.MyCommentVO;
import com.eason.blog.module.interaction.service.InteractionService;
import com.eason.blog.module.notification.vo.NotificationVO;
import com.eason.blog.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 批量取消收藏 */
    @PostMapping("/favorites/batch-remove")
    public ApiResponse<Map<String, Integer>> removeFavorites(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, List<Long>> body) {
        return ApiResponse.success(Map.of("removed",
                interactionService.unfavoriteBatch(principal.getId(),
                        body.getOrDefault("articleIds", List.of()))));
    }

    @GetMapping("/likes")
    public ApiResponse<PageResult<ArticleSummaryVO>> likes(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(interactionService.likes(principal.getId(), page, size));
    }

    /** 批量取消点赞 */
    @PostMapping("/likes/batch-remove")
    public ApiResponse<Map<String, Integer>> removeLikes(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, List<Long>> body) {
        return ApiResponse.success(Map.of("removed",
                interactionService.unlikeBatch(principal.getId(),
                        body.getOrDefault("articleIds", List.of()))));
    }

    @GetMapping("/comments")
    public ApiResponse<PageResult<MyCommentVO>> myComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(interactionService.myComments(principal.getId(), page, size));
    }

    /** 删除自己的评论（3 分钟内且没有回复） */
    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteOwnComment(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable Long id) {
        interactionService.deleteOwnComment(principal.getId(), id);
        return ApiResponse.success();
    }

    /** 批量删除自己的评论，返回实际删除数量 */
    @PostMapping("/comments/batch-delete")
    public ApiResponse<Map<String, Integer>> deleteOwnComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, List<Long>> body) {
        return ApiResponse.success(Map.of("deleted",
                interactionService.deleteOwnComments(principal.getId(),
                        body.getOrDefault("ids", List.of()))));
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationVO>> notifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) String type) {
        return ApiResponse.success(interactionService.notifications(principal.getId(), limit, type));
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

    /** 一键已读 */
    @PatchMapping("/notifications/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(Map.of("updated", interactionService.markAllRead(principal.getId())));
    }

    /** 删除已读通知 */
    @DeleteMapping("/notifications/read")
    public ApiResponse<Map<String, Integer>> deleteRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(Map.of("deleted", interactionService.deleteRead(principal.getId())));
    }
}
