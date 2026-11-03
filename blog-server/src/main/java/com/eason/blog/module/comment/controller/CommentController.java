package com.eason.blog.module.comment.controller;

import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.common.web.ClientIp;
import com.eason.blog.module.comment.dto.CommentRequest;
import com.eason.blog.module.comment.vo.CommentVO;
import com.eason.blog.module.interaction.service.InteractionService;
import com.eason.blog.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文章评论。
 *
 * <p>GET 公开（只返回已审核通过的评论），POST 需要登录；
 * 新评论默认待审核，不会立即出现在公开列表。</p>
 */
@RestController
@RequestMapping("/api/v1/articles")
public class CommentController {

    private final InteractionService interactionService;

    public CommentController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/{slug}/comments")
    public ApiResponse<PageResult<CommentVO>> list(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(interactionService.comments(
                slug, principal == null ? null : principal.getId(), page, size));
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<Map<String, Long>> create(@PathVariable Long id,
                                                 @AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody CommentRequest request,
                                                 HttpServletRequest httpRequest) {
        Long commentId = interactionService.addComment(principal.getId(), id,
                request.content(), request.parentId(), ClientIp.of(httpRequest));
        return ApiResponse.success(Map.of("id", commentId));
    }
}
