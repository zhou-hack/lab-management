package com.labmanage.roles.vo;

/**
 * 角色权限编辑的可选权限项（全部启用权限）
 */
public record PermissionOptionVO(
        String id,
        String permissionCode,
        String permissionName,
        String moduleCode
) {
}
