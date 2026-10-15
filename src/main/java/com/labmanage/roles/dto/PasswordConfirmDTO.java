package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 敏感操作二次密码确认请求
 */
public record PasswordConfirmDTO(

        @NotBlank(message = "密码不能为空")
        @Size(max = 512, message = "密码密文长度异常")
        String password
) {
}
