package com.labmanage.common.utils;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.security.AuthContext;
import com.labmanage.common.security.LoginUser;

import java.time.Instant;
import java.util.List;

/**
 * 登录上下文工具：基于 AuthContext（JWT 过滤器装配）读取当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 敏感操作二级认证有效期（秒） */
    public static final long SAFE_AUTH_SECONDS = 300;

    /**
     * 当前登录用户 ID；未登录抛 40100（由全局异常处理转响应）。
     */
    public static Long currentUserId() {
        return requireLogin().userId();
    }

    /** 是否已登录（审计字段填充等容忍未登录场景使用） */
    public static boolean isLogin() {
        return AuthContext.get() != null;
    }

    /** 当前用户角色码集合；未登录返回空表 */
    public static List<String> currentRoles() {
        LoginUser user = AuthContext.get();
        return user == null ? List.of() : user.roles();
    }

    /** 当前用户是否持有指定权限标识 */
    public static boolean hasPermission(String permission) {
        LoginUser user = AuthContext.get();
        return user != null && user.hasPermission(permission);
    }

    /** 敏感操作二级认证：未通过或已过期抛 40101（前端弹密码确认而非跳登录） */
    public static void checkSafe() {
        LoginUser user = requireLogin();
        if (!user.isSafeAuthed()) {
            throw new BusinessException(ResultCode.SAFE_AUTH_REQUIRED.getCode(),
                    ResultCode.SAFE_AUTH_REQUIRED.getMsg());
        }
    }

    /** 开启二级认证有效期（二次密码确认通过后调用） */
    public static void openSafe() {
        LoginUser user = requireLogin();
        AuthContext.update(user.withSafeUntil(Instant.now().plusSeconds(SAFE_AUTH_SECONDS)));
    }

    private static LoginUser requireLogin() {
        LoginUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
        }
        return user;
    }
}
