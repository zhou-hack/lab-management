package com.labmanage.roles.vo;

import java.util.List;

/**
 * 当前身份响应（登录后 / 刷新时加载动态路由与权限）
 *
 * @param passwordChangeRequired 从未设置/被重置密码（管理员），须强制改密
 * @param needSetPassword        尚未设置密码（老师首登须设置敏感操作密码）
 */
public record ProfileVO(
        String userId,
        String realName,
        String userType,
        List<String> roles,
        List<String> permissions,
        List<MenuVO> menus,
        Boolean passwordChangeRequired,
        Boolean needSetPassword
) {
}
