package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 绑定/解绑管理员角色请求（仅老师账号可绑定；运维管理员账号不可被操作）
 *
 * @param bind true=绑定 ADMIN 角色，false=解绑
 */
public record UserAdminRoleDTO(

        @NotNull(message = "绑定标志不能为空")
        Boolean bind
) {
}
