package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 账号密码登录请求
 */
public record AccountLoginDTO(

        @NotBlank(message = "账号不能为空")
        @Size(max = 64, message = "账号长度不能超过 64")
        String accountName,

        @NotBlank(message = "密码不能为空")
        @Size(max = 512, message = "密码密文长度异常")
        String password
) {
}
