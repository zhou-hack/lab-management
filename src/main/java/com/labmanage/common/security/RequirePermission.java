package com.labmanage.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解（替代 @SaCheckPermission）：标注在 Controller 方法上，
 * 由 RequirePermissionAspect 校验当前登录用户是否持有指定权限标识，无权限抛 40300。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 权限标识（模块:资源:操作，如 item:material:create） */
    String[] value();

    /** 多权限匹配模式：AND 全部持有（默认）/ OR 任一持有 */
    Mode mode() default Mode.AND;

    enum Mode {
        AND, OR
    }
}
