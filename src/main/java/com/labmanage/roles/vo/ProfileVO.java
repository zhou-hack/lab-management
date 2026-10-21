package com.labmanage.roles.vo;

import java.util.List;

/**
 * 当前身份响应（登录后 / 刷新时加载动态路由与权限）
 *
 * @param activeRole             活动角色（当前生效的身份视图，多角色用户可切换）
 * @param switchableRoles        可切换角色集合（持有的其他角色，不含动态 ASSISTANT）
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
        String activeRole,
        List<String> switchableRoles,
        Boolean passwordChangeRequired,
        Boolean needSetPassword
) {
}
