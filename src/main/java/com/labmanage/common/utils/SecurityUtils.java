package com.labmanage.common.utils;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 登录上下文工具：基于 Sa-Token 读取当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 当前登录用户 ID；未登录抛 NotLoginException（由全局异常处理转 40100）。
     */
    public static Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }
}
