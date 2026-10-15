package com.labmanage.roles.auth.vo;

import java.util.List;

/**
 * 菜单节点（前端动态路由契约：menuType M=目录 / C=页面 / F=动作）
 *
 * @param menuId 菜单 ID（字符串化避免前端精度损失）
 * @param menuName 菜单名称
 * @param menuType M/C/F
 * @param path 路由地址
 * @param componentKey 前端组件键（侧栏图标白名单映射依据）
 * @param perms 绑定权限标识（无绑定为 null）
 * @param children 子菜单
 */
public record MenuNodeVO(
        String menuId,
        String menuName,
        String menuType,
        String path,
        String componentKey,
        String perms,
        List<MenuNodeVO> children
) {
}
