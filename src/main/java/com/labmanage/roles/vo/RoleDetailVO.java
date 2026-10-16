package com.labmanage.roles.vo;

import java.util.List;

/**
 * 角色权限只读视图（角色 + 绑定权限 + 绑定菜单 + 绑定用户数）
 */
public record RoleDetailVO(
        String id,
        String roleCode,
        String roleName,
        Integer enabled,
        Long userCount,
        List<PermissionItem> permissions,
        List<MenuItem> menus
) {

    public record PermissionItem(
            String permissionCode,
            String permissionName,
            String moduleCode
    ) {
    }

    public record MenuItem(
            String menuName,
            String menuType,
            String routePath
    ) {
    }
}
