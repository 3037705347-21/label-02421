package com.library.seat.aop;

import com.library.seat.entity.OperationLog;
import com.library.seat.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;

    @Pointcut("@annotation(com.library.seat.aop.LogOperation)")
    public void logPointcut() {}

    @Around("logPointcut() && @annotation(logOperation)")
    public Object around(ProceedingJoinPoint point, LogOperation logOperation) throws Throwable {
        Object result = point.proceed();
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                OperationLog opLog = new OperationLog();
                Object userIdAttr = request.getAttribute("userId");
                if (userIdAttr instanceof Long) {
                    opLog.setUserId((Long) userIdAttr);
                } else if (userIdAttr instanceof Integer) {
                    opLog.setUserId(((Integer) userIdAttr).longValue());
                }
                opLog.setModule(logOperation.module());
                opLog.setAction(logOperation.action());
                opLog.setDetail(logOperation.detail());
                opLog.setIp(getIpAddress(request));
                opLog.setCreateTime(LocalDateTime.now());
                operationLogMapper.insert(opLog);
            }
        } catch (Exception e) {
            log.error("记录操作日志失败: ", e);
        }
        return result;
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
