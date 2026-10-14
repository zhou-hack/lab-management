package com.labmanage.roles.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 账号密码登录请求
 *
 * @param username 登录账号
 * @param password 密码明文（HTTPS 传输）
 */
public record LoginRequest(
        @NotBlank(message = "请输入账号") String username,
        @NotBlank(message = "请输入密码") String password
) {
}
