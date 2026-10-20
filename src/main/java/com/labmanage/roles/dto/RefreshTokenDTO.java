package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 刷新 token 请求
 */
public record RefreshTokenDTO(

        @NotBlank(message = "refreshToken 不能为空")
        @Size(max = 2048, message = "refreshToken 长度异常")
        String refreshToken
) {
}
