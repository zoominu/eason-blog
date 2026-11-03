package com.eason.blog.module.log.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.module.log.entity.LoginLog;
import com.eason.blog.module.log.entity.OperationLog;
import com.eason.blog.module.log.mapper.LoginLogMapper;
import com.eason.blog.module.log.mapper.OperationLogMapper;
import com.eason.blog.module.log.vo.AdminLoginLogVO;
import com.eason.blog.module.log.vo.AdminOperationLogVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 登录日志与操作日志查询（只读）。 */
@Service
public class LogQueryService {

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;

    public LogQueryService(LoginLogMapper loginLogMapper, OperationLogMapper operationLogMapper) {
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
    }

    public PageResult<AdminLoginLogVO> loginLogs(Integer status, String keyword, int pageNo, int size) {
        var wrapper = Wrappers.<LoginLog>lambdaQuery();
        if (status != null) {
            wrapper.eq(LoginLog::getLoginStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(LoginLog::getEmail, keyword.trim());
        }
        wrapper.orderByDesc(LoginLog::getCreateTime);

        Page<LoginLog> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 50));
        loginLogMapper.selectPage(page, wrapper);
        return PageResult.of(page, l -> new AdminLoginLogVO(l.getId(), l.getEmail(), l.getIp(),
                l.getLoginStatus(), l.getMessage(), l.getCreateTime()));
    }

    public PageResult<AdminOperationLogVO> operationLogs(String module, int pageNo, int size) {
        var wrapper = Wrappers.<OperationLog>lambdaQuery();
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLog::getModule, module.trim());
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);

        Page<OperationLog> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 50));
        operationLogMapper.selectPage(page, wrapper);
        return PageResult.of(page, o -> new AdminOperationLogVO(o.getId(), o.getOperatorName(),
                o.getModule(), o.getAction(), o.getDetail(), o.getIp(), o.getCreateTime()));
    }
}
