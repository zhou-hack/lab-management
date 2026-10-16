package com.labmanage.roles.support;

import cn.dev33.satoken.stp.StpUtil;
import com.labmanage.common.log.OperationLog;
import com.labmanage.roles.entity.SysOperationLog;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 操作日志采集切面：拦截 @OperationLog 标注的控制器方法，
 * 记录操作者、动作、目标、结果与请求上下文；业务异常同样落一条 success=0 的审计。
 * 敏感值（密码、验证码、token）不进入日志，一期 before/after 预留为 NULL。
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private static final String REQUEST_ID_ATTRIBUTE = "labRequestId";

    private final SysLogService logService;

    private final SysUserMapper userMapper;

    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public OperationLogAspect(SysLogService logService, SysUserMapper userMapper) {
        this.logService = logService;
        this.userMapper = userMapper;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        Long actorId = currentUserId();
        Object result = null;
        boolean success = false;
        try {
            result = joinPoint.proceed();
            success = true;
            return result;
        } finally {
            try {
                // 登录成功场景：执行前尚未建立会话，执行后补取一次操作者；注销则保留执行前的操作者
                if (actorId == null) {
                    actorId = currentUserId();
                }
                SysOperationLog entry = new SysOperationLog();
                entry.setActorId(actorId);
                entry.setActorType(resolveActorType(actorId));
                entry.setModuleCode(operationLog.moduleCode());
                entry.setActionCode(operationLog.actionCode());
                entry.setTargetTable(operationLog.targetTable());
                entry.setTargetId(resolveTargetId(joinPoint, operationLog, result));
                entry.setRequestId(resolveRequestId());
                entry.setClientIp(resolveClientIp());
                entry.setSuccess(success ? 1 : 0);
                entry.setOccurredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
                logService.record(entry);
            } catch (Exception e) {
                log.warn("操作日志采集失败：{} {}", operationLog.actionCode(), e.getMessage());
            }
        }
    }

    private Long currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 操作者类型：已登录取 sys_user.user_type；匿名（如登录失败）记 UNKNOWN */
    private String resolveActorType(Long actorId) {
        if (actorId == null) {
            return "UNKNOWN";
        }
        SysUser user = userMapper.selectById(actorId);
        return user == null ? "UNKNOWN" : user.getUserType();
    }

    /** SpEL 求值目标 ID；可引用参数名与 #result，求值失败或为空白按 NULL */
    private Long resolveTargetId(ProceedingJoinPoint joinPoint, OperationLog operationLog, Object result) {
        String spel = operationLog.targetId();
        if (spel == null || spel.isBlank()) {
            return null;
        }
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] names = parameterNameDiscoverer.getParameterNames(signature.getMethod());
            Object[] args = joinPoint.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    context.setVariable(names[i], args[i]);
                }
            }
            context.setVariable("result", result);
            Expression expression = spelParser.parseExpression(spel);
            Object value = expression.getValue(context);
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.valueOf(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveRequestId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String id && !id.isBlank()) {
            return id;
        }
        String id = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, id);
        return id;
    }

    private String resolveClientIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
