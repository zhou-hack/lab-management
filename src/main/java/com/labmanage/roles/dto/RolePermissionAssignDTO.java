package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 角色权限整集替换入参（目标权限 ID 全集；空数组表示清空该角色全部权限绑定）
 */
public record RolePermissionAssignDTO(
        @NotNull(message = "权限集不能为空")
        List<@NotNull(message = "权限 ID 不能为空") Long> permissionIds
) {
}
