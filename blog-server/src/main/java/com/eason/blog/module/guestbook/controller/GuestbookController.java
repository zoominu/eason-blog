package com.eason.blog.module.guestbook.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.guestbook.entity.Guestbook;
import com.eason.blog.module.guestbook.mapper.GuestbookMapper;
import com.eason.blog.module.guestbook.vo.GuestbookVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开留言列表（只返回已审核通过的留言）。
 *
 * <p>查询逻辑很薄，暂时不单独抽 Service；提交留言（POST）需要登录、限流与审核，
 * 会在 P4 互动阶段补齐。</p>
 */
@RestController
@RequestMapping("/api/v1/guestbook")
@Validated
public class GuestbookController {

    private static final int APPROVED = 1;

    private final GuestbookMapper guestbookMapper;

    public GuestbookController(GuestbookMapper guestbookMapper) {
        this.guestbookMapper = guestbookMapper;
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
}
