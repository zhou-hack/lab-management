package com.labmanage.config;

import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * B3：敏感操作二级认证守卫。受保护入口先经 {@link StpUtil#checkSafe()} 校验——
 * 要求调用方在 openSafe 有效期内（经 POST /auth/password/confirm 完成密码确认，
 * 有效期 300 秒），否则抛 NotSafeException，由全局异常处理映射为 40301，
 * 前端据此弹出密码确认框后自动重试原请求。
 * <p>
 * 经 lab.security.sensitive-safe-check 开关控制（默认 true）；集成测试置 false 以
 * 独立验证业务逻辑，二级认证路径由专用测试类覆盖。
 */
@Component
public class SensitiveOperationGuard {

    private static final Logger log = LoggerFactory.getLogger(SensitiveOperationGuard.class);

    @Value("${lab.security.sensitive-safe-check:true}")
    private boolean enabled;

    /** 校验二级认证；operation 用于日志留痕（如"场地删除"、"预约强制取消"） */
    public void checkSafe(String operation) {
        if (!enabled) {
            return;
        }
        StpUtil.checkSafe();
        log.info("[二级认证] 敏感操作「{}」已通过二级认证，操作人={}", operation, StpUtil.getLoginId());
    }
}
