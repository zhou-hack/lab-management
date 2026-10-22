package com.labmanage.common.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志采集注解：标注在需要审计的控制器方法上。
 * 动作码与权限码同规范（模块:资源:操作）；targetId 支持 SpEL，
 * 可引用方法参数名与 #result（如 "#id"、"#result?.data?.userId"），求值失败按 NULL 处理。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 模块码（如 system、auth、grant） */
    String moduleCode();

    /** 动作码（如 system:user:create、auth:login） */
    String actionCode();

    /** 目标表（如 sys_user） */
    String targetTable();

    /** 目标行 ID 的 SpEL 表达式；留空表示不记录目标 ID */
    String targetId() default "";
}
