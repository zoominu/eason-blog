package com.eason.blog.common.audit;

import com.eason.blog.common.web.ClientIp;
import com.eason.blog.module.log.entity.OperationLog;
import com.eason.blog.module.log.mapper.OperationLogMapper;
import com.eason.blog.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计切面：方法成功返回后写一条操作日志。
 *
 * <p>写日志失败只记 warn，绝不影响业务；参数与返回值不入库，
 * 避免把正文、密码等敏感内容写进日志表。</p>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final OperationLogMapper operationLogMapper;

    public AuditAspect(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            OperationLog entry = new OperationLog();
            entry.setModule(audit.module());
            entry.setAction(audit.action());
            entry.setCreateTime(LocalDateTime.now());

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
                entry.setOperatorId(principal.getId());
                entry.setOperatorName(principal.getNickname());
            }

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                entry.setIp(ClientIp.of(request));
                entry.setDetail(request.getMethod() + " " + request.getRequestURI());
            }

            operationLogMapper.insert(entry);
        } catch (RuntimeException ex) {
            log.warn("写操作日志失败: {}", ex.getMessage());
        }
        return result;
    }
}
