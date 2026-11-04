package com.eason.blog.module.system.controller;

import com.eason.blog.common.audit.Audit;
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.comment.vo.AdminCommentVO;
import com.eason.blog.module.guestbook.vo.AdminGuestbookVO;
import com.eason.blog.module.interaction.service.InteractionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
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
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                interactionService.adminComments(status, keyword, articleId, userId, page, size));
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

    /** 批量审核评论：body { "ids": [1,2,3] } */
    @PostMapping("/comments/batch")
    @Audit(module = "comment", action = "批量审核评论")
    public ApiResponse<Map<String, Integer>> batchComments(@RequestBody Map<String, Object> body) {
        List<Long> ids = idsOf(body);
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        return ApiResponse.success(Map.of("updated", interactionService.batchModerateComments(ids, approve)));
    }

    @GetMapping("/guestbook")
    public ApiResponse<PageResult<AdminGuestbookVO>> guestbook(
            @RequestParam(required = false) @Min(0) @Max(2) Integer status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(interactionService.adminGuestbook(status, keyword, userId, page, size));
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

    /** 批量审核留言：body { "ids": [1,2,3] } */
    @PostMapping("/guestbook/batch")
    @Audit(module = "guestbook", action = "批量审核留言")
    public ApiResponse<Map<String, Integer>> batchGuestbook(@RequestBody Map<String, Object> body) {
        List<Long> ids = idsOf(body);
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        return ApiResponse.success(Map.of("updated", interactionService.batchModerateGuestbook(ids, approve)));
    }

    private List<Long> idsOf(Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Number.class::isInstance)
                .map(item -> ((Number) item).longValue())
                .toList();
    }

    /** 博主回复留言：回复即审核通过并通知留言者 */
    @PatchMapping("/guestbook/{id}/reply")
    @Audit(module = "guestbook", action = "回复留言")
    public ApiResponse<Void> replyGuestbook(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        interactionService.replyGuestbook(id, body.get("reply"));
        return ApiResponse.success();
    }
}
