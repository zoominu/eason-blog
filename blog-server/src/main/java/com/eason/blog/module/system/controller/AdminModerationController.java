package com.eason.blog.module.system.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.comment.vo.AdminCommentVO;
import com.eason.blog.module.guestbook.vo.AdminGuestbookVO;
import com.eason.blog.module.interaction.service.InteractionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台内容审核：评论与留言。
 *
 * <p>列表默认按创建时间倒序，可通过 status 筛选（0 待审核 / 1 已通过 / 2 已拒绝）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminModerationController {

    private final InteractionService interactionService;

    public AdminModerationController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/comments")
    public ApiResponse<PageResult<AdminCommentVO>> comments(
            @RequestParam(required = false) @Min(0) @Max(2) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(interactionService.adminComments(status, page, size));
    }

    @PostMapping("/comments/{id}/approve")
    public ApiResponse<Void> approveComment(@PathVariable Long id) {
        interactionService.moderateComment(id, true);
        return ApiResponse.success();
    }

    @PostMapping("/comments/{id}/reject")
    public ApiResponse<Void> rejectComment(@PathVariable Long id) {
        interactionService.moderateComment(id, false);
        return ApiResponse.success();
    }

    @GetMapping("/guestbook")
    public ApiResponse<PageResult<AdminGuestbookVO>> guestbook(
            @RequestParam(required = false) @Min(0) @Max(2) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(interactionService.adminGuestbook(status, page, size));
    }

    @PostMapping("/guestbook/{id}/approve")
    public ApiResponse<Void> approveGuestbook(@PathVariable Long id) {
        interactionService.moderateGuestbook(id, true);
        return ApiResponse.success();
    }

    @PostMapping("/guestbook/{id}/reject")
    public ApiResponse<Void> rejectGuestbook(@PathVariable Long id) {
        interactionService.moderateGuestbook(id, false);
        return ApiResponse.success();
    }
}
