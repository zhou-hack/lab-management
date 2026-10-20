package com.labmanage.common.security;

/**
 * 登录上下文：ThreadLocal 持有当前请求的 LoginUser。
 * 由 JwtAuthFilter 在请求进入时装配、结束时清空；业务代码经 SecurityUtils 访问。
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 当前用户；未登录返回 null */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 更新当前用户（二级认证开启有效期时使用） */
    public static void update(LoginUser user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
