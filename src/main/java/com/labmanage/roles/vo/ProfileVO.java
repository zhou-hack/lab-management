package com.labmanage.roles.vo;

import java.util.List;

/**
 * 当前身份响应（登录后 / 刷新 / 切换身份时加载动态路由与权限）
 *
 * @param accountName     账号名（前端二次确认前按账号取 BCrypt 盐）
 * @param activeRole      活动角色（当前生效的身份视图，多角色用户可切换）
 * @param switchableRoles 可切换角色集合（持有的其他角色，不含动态 ASSISTANT）
 */
public record ProfileVO(
        String userId,
        String realName,
        String userType,
        String accountName,
        List<String> roles,
        List<String> permissions,
        List<MenuVO> menus,
        String activeRole,
        List<String> switchableRoles
) {
}
