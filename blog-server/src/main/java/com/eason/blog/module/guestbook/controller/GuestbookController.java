package com.eason.blog.module.guestbook.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.common.web.ClientIp;
import com.eason.blog.module.guestbook.dto.GuestbookRequest;
import com.eason.blog.module.guestbook.entity.Guestbook;
import com.eason.blog.module.guestbook.mapper.GuestbookMapper;
import com.eason.blog.module.guestbook.vo.GuestbookVO;
import com.eason.blog.module.interaction.service.InteractionService;
import com.eason.blog.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 留言板：公开列表（仅已审核） + 登录后提交（默认待审核）。
 */
@RestController
@RequestMapping("/api/v1/guestbook")
@Validated
public class GuestbookController {

    private static final int APPROVED = 1;

    private final GuestbookMapper guestbookMapper;
    private final InteractionService interactionService;

    public GuestbookController(GuestbookMapper guestbookMapper, InteractionService interactionService) {
        this.guestbookMapper = guestbookMapper;
        this.interactionService = interactionService;
    }

    @GetMapping
    public ApiResponse<PageResult<GuestbookVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        Page<Guestbook> result = guestbookMapper.selectPage(new Page<>(page, size),
                Wrappers.<Guestbook>lambdaQuery()
                        .eq(Guestbook::getStatus, APPROVED)
                        .orderByDesc(Guestbook::getCreateTime));
        return ApiResponse.success(PageResult.of(result, g ->
                new GuestbookVO(g.getId(), g.getNickname(), g.getContent(), g.getReply(), g.getCreateTime())));
    }

    @PostMapping
    public ApiResponse<Void> submit(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody GuestbookRequest request,
                                    HttpServletRequest httpRequest) {
        interactionService.addGuestbook(principal.getId(), request.content(), ClientIp.of(httpRequest));
        return ApiResponse.success();
    }
}
