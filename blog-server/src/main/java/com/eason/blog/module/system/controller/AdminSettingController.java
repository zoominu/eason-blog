package com.eason.blog.module.system.controller;

import com.eason.blog.common.audit.Audit;
import com.eason.blog.common.mail.MailService;
import com.eason.blog.common.response.ApiResponse;
import com.eason.blog.module.system.service.AdminSettingService;
import com.eason.blog.module.system.vo.SystemConfigVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSettingController {

    private final AdminSettingService adminSettingService;
    private final MailService mailService;

    public AdminSettingController(AdminSettingService adminSettingService, MailService mailService) {
        this.adminSettingService = adminSettingService;
        this.mailService = mailService;
    }

    @GetMapping("/settings")
    public ApiResponse<List<SystemConfigVO>> list() {
        return ApiResponse.success(adminSettingService.list());
    }

    @PatchMapping("/settings")
    @Audit(module = "settings", action = "更新站点配置")
    public ApiResponse<Map<String, Integer>> update(@RequestBody Map<String, String> configs) {
        return ApiResponse.success(Map.of("updated", adminSettingService.update(configs)));
    }

    /** 发送测试邮件，验证 SMTP 授权码是否配置正确 */
    @PostMapping("/settings/mail-test")
    @Audit(module = "settings", action = "发送测试邮件")
    public ApiResponse<Void> mailTest(@RequestBody Map<String, String> body) {
        mailService.sendStrict(body.get("to"), "Eason Blog 测试邮件",
                "这是一封来自后台的测试邮件，说明 SMTP 配置可用。");
        return ApiResponse.success();
    }
}
