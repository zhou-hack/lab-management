package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 重置用户密码请求（管理员/运维操作，重置后目标用户首登强制改密）
 */
public record UserPasswordResetDTO(

        @NotBlank(message = "新密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "新密码需 8-64 位且同时包含字母和数字")
        String newPassword
) {
}
