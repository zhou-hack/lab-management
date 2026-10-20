package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 身份一键切换入参（同一账号多身份：老师⇄管理员、学生⇄助理）。
 * roleCode 必须是当前账号可用的身份（主身份 + 持久化角色 + 有效 ASSISTANT 授权），
 * 否则返回 40300。
 */
public record IdentitySwitchDTO(

        @NotBlank(message = "目标身份不能为空")
        @Size(max = 32, message = "身份码长度不能超过 32")
        String roleCode
) {
}