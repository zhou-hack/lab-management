package com.labmanage.roles.auth.vo;

import java.util.List;

/**
 * 当前用户 + 角色 + 权限 + 菜单树（登录后一次性拉取）
 *
 * @param userInfo 用户基础信息
 * @param roles 角色码列表
 * @param perms 权限标识列表
 * @param menus 菜单树（M/C/F 格式，前端据此注册动态路由）
 */
public record UserMenusVO(
        UserInfoVO userInfo,
        List<String> roles,
        List<String> perms,
        List<MenuNodeVO> menus
) {
}
