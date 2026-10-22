package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 切换活动角色请求（多角色用户会话内切换身份视图）
 *
 * @param roleCode 目标角色码（须为当前用户持有的角色，不含动态 ASSISTANT）
 */
public record SwitchRoleDTO(

        @NotBlank(message = "目标角色不能为空")
        @Size(max = 32, message = "角色码长度不能超过 32")
        String roleCode
) {
}
